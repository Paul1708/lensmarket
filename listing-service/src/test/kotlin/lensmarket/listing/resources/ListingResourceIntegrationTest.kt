package lensmarket.listing.resources

import io.agroal.api.AgroalDataSource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import io.smallrye.reactive.messaging.memory.InMemoryConnector
import jakarta.enterprise.inject.Any as AnyQualifier
import jakarta.inject.Inject
import io.vertx.mutiny.core.Vertx
import io.smallrye.common.vertx.VertxContext
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle
import lensmarket.listing.events.ListingCreatedEvent
import lensmarket.listing.events.OutboxRelay
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@QuarkusTest
class ListingResourceIntegrationTest {
    @Inject
    lateinit var dataSource: AgroalDataSource

    @Inject
    @field:AnyQualifier
    lateinit var connector: InMemoryConnector

    @Inject
    lateinit var outboxRelay: OutboxRelay

    @Inject
    lateinit var vertx: Vertx

    @Test
    fun `listing CRUD status and soft-delete flow is persisted and emits contracts`() {
        val owner = UUID.randomUUID().toString()
        val token = testToken(owner)
        val created = createListing(token, "Portrait Prime Lens", listOf("portrait", "prime"), "49.90")

        created.then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .header("Location", matchesPattern("/listings/[0-9a-f-]{36}"))
            .body("ownerUserId", equalTo(owner))
            .body("status", equalTo("ACTIVE"))
            .body("tags", contains("portrait", "prime"))

        val id = created.jsonPath().getString("id")
        relayOutbox()
        val published = connector.sink<ListingCreatedEvent>("listing-created-out").received().last().payload
        assertEquals("listing-created", published.eventType)
        assertEquals(1, published.version)
        assertEquals(id, published.listingId)

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(
                mapOf(
                    "title" to "Updated Lens",
                    "description" to "Updated description",
                    "priceHint" to 59.50,
                    "tags" to listOf("updated")
                )
            )
            .put("/listings/$id")
            .then()
            .statusCode(200)
            .body("title", equalTo("Updated Lens"))
            .body("tags", contains("updated"))

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(mapOf("location" to "Berlin", "cameraModel" to "X-T5"))
            .patch("/listings/$id")
            .then()
            .statusCode(200)
            .body("location", equalTo("Berlin"))
            .body("cameraModel", equalTo("X-T5"))

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "PAUSED"))
            .patch("/listings/$id/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("PAUSED"))

        given().get("/listings/$id").then().statusCode(404)
        given().queryParam("q", "Portrait Prime Lens").queryParam("status", "PAUSED")
            .get("/listings")
            .then()
            .statusCode(200)
            .body("id", org.hamcrest.Matchers.not(hasItem(id)))

        given()
            .header("Authorization", "Bearer $token")
            .queryParam("status", "PAUSED")
            .get("/listings/mine")
            .then()
            .statusCode(200)
            .body("id", hasItem(id))

        given().header("Authorization", "Bearer $token")
            .get("/listings/mine/$id")
            .then()
            .statusCode(200)
            .body("status", equalTo("PAUSED"))

        given().header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "SOLD"))
            .patch("/listings/$id/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("SOLD"))

        given().header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "ACTIVE"))
            .patch("/listings/$id/status")
            .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))

        given().header("Authorization", "Bearer $token").delete("/listings/$id").then().statusCode(204)
        given().get("/listings/$id").then()
            .statusCode(404)
            .body("error", equalTo("NOT_FOUND"))
            .body("path", containsString(id))

        dataSource.connection.use { connection ->
            connection.prepareStatement("select count(*) from listings where id = ? and deleted_at is not null").use {
                it.setString(1, id)
                it.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals(1, result.getInt(1))
                }
            }
        }
    }

    private fun relayOutbox() {
        val completed = CompletableFuture<Unit>()
        val context = VertxContext.getOrCreateDuplicatedContext(vertx.delegate)
        VertxContextSafetyToggle.setContextSafe(context, true)
        context.runOnContext {
            outboxRelay.relay().subscribe().with(
                { completed.complete(Unit) },
                { completed.completeExceptionally(it) }
            )
        }
        completed.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun `filters pagination and authorization boundaries use stable errors`() {
        val owner = UUID.randomUUID().toString()
        val ownerToken = testToken(owner)
        val otherToken = testToken(UUID.randomUUID().toString())
        val marker = "filter-${UUID.randomUUID()}"
        val first = createListing(ownerToken, "$marker alpha", listOf(marker, "wide"), "10.00").jsonPath().getString("id")
        createListing(ownerToken, "$marker beta", listOf(marker, "tele"), "20.00")

        given()
            .queryParam("q", marker)
            .queryParam("tag", marker)
            .queryParam("minPrice", 5)
            .queryParam("maxPrice", 25)
            .queryParam("limit", 1)
            .queryParam("offset", 0)
            .get("/listings")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize<Any>(1))

        given().queryParam("q", marker).queryParam("limit", 1).queryParam("offset", 1)
            .get("/listings")
            .then()
            .statusCode(200)
            .body("$", hasSize<Any>(1))

        given().contentType(ContentType.JSON)
            .body(validListingBody("No auth", emptyList(), "1.00"))
            .post("/listings")
            .then()
            .statusCode(401)
            .body("status", equalTo(401))

        given().header("Authorization", "Bearer $otherToken")
            .contentType(ContentType.JSON)
            .body(mapOf("title" to "Forbidden"))
            .patch("/listings/$first")
            .then()
            .statusCode(403)
            .body("error", equalTo("HTTP_ERROR"))
            .body("status", equalTo(403))

        given().queryParam("limit", 0).get("/listings").then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))
            .body("status", equalTo(400))

        given().header("Authorization", "Bearer $ownerToken")
            .contentType(ContentType.JSON)
            .body(validListingBody("Legacy contract", emptyList(), "1.00") + mapOf("imageIds" to listOf("forged")))
            .post("/listings")
            .then()
            .statusCode(400)

        given().get("/listings/${UUID.randomUUID()}").then()
            .statusCode(404)
            .body("status", equalTo(404))
    }

    @Test
    fun `all listing migrations and filter indexes are installed`() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from flyway_schema_history where success").use { result ->
                    assertTrue(result.next())
                    assertTrue(result.getInt(1) >= 3)
                }
                statement.executeQuery(
                    "select count(*) from pg_indexes where tablename = 'listings' " +
                        "and indexname in ('idx_listings_owner_created_at', 'idx_listings_deleted_at')"
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(2, result.getInt(1))
                }
            }
        }
    }

    private fun createListing(token: String, title: String, tags: List<String>, price: String): Response =
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(validListingBody(title, tags, price))
            .post("/listings")
            .then()
            .statusCode(201)
            .extract()
            .response()

    private fun validListingBody(title: String, tags: List<String>, price: String): Map<String, Any> =
        mapOf(
            "title" to title,
            "description" to "A listing covered by the integration suite",
            "priceHint" to price,
            "location" to "Berlin",
            "tags" to tags
        )

    private fun testToken(userId: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val now = Instant.now().epochSecond
        val header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".toByteArray())
        val payload = encoder.encodeToString((
            "{\"iss\":\"lensmarket-auth\",\"aud\":\"lensmarket-services\",\"sub\":\"$userId\"," +
                "\"email\":\"test@example.test\",\"role\":\"CUSTOMER\",\"iat\":$now,\"nbf\":$now," +
                "\"exp\":${now + 3600},\"jti\":\"${UUID.randomUUID()}\"}"
            ).toByteArray()
        )
        val unsigned = "$header.$payload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("test-only-jwt-secret-with-at-least-32-bytes".toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return "$unsigned.${encoder.encodeToString(mac.doFinal(unsigned.toByteArray(StandardCharsets.UTF_8)))}"
    }
}
