package lensmarket.messaging.resources

import io.agroal.api.AgroalDataSource
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.smallrye.reactive.messaging.memory.InMemoryConnector
import io.vertx.mutiny.core.Vertx
import io.smallrye.common.vertx.VertxContext
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle
import jakarta.enterprise.inject.Any as AnyQualifier
import jakarta.inject.Inject
import lensmarket.messaging.domain.model.ListingReference
import lensmarket.messaging.domain.port.ListingLookupPort
import lensmarket.messaging.domain.usecase.MessagingDependencyTimeoutException
import lensmarket.messaging.domain.usecase.MessagingDependencyUnavailableException
import lensmarket.messaging.events.MessageSentEvent
import lensmarket.messaging.events.OutboxRelay
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.reset
import org.mockito.Mockito.`when`
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@QuarkusTest
class MessagingResourceIntegrationTest {
    @InjectMock
    lateinit var listingLookup: ListingLookupPort

    @Inject
    @field:AnyQualifier
    lateinit var connector: InMemoryConnector

    @Inject
    lateinit var outboxRelay: OutboxRelay

    @Inject
    lateinit var vertx: Vertx

    @Inject
    lateinit var dataSource: AgroalDataSource

    @BeforeEach
    fun resetListingLookup() = runBlocking {
        reset(listingLookup)
        `when`(listingLookup.findListing(anyString())).thenReturn(null)
        Unit
    }

    @Test
    fun `conversation and message retries preserve participants and idempotency`() = runBlocking {
        val listingId = UUID.randomUUID().toString()
        val photographer = UUID.randomUUID().toString()
        val customer = UUID.randomUUID().toString()
        val stranger = UUID.randomUUID().toString()
        `when`(listingLookup.findListing(listingId)).thenReturn(ListingReference(listingId, photographer, "ACTIVE"))

        val createBody = mapOf("listingId" to listingId)
        val created = given()
            .header("Authorization", "Bearer ${testToken(customer)}")
            .contentType(ContentType.JSON)
            .body(createBody)
            .post("/messages/conversations")
            .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .header("Location", matchesPattern("/messages/conversations/[0-9a-f-]{36}"))
            .body("photographerUserId", equalTo(photographer))
            .body("customerUserId", equalTo(customer))
            .extract()

        val conversationId = created.jsonPath().getString("id")
        given()
            .header("Authorization", "Bearer ${testToken(customer)}")
            .contentType(ContentType.JSON)
            .body(createBody)
            .post("/messages/conversations")
            .then()
            .statusCode(200)
            .body("id", equalTo(conversationId))

        val key = "retry-${UUID.randomUUID()}"
        val firstMessage = given()
            .header("Authorization", "Bearer ${testToken(customer)}")
            .header("Idempotency-Key", key)
            .contentType(ContentType.JSON)
            .body(mapOf("body" to "Is the lens still available?"))
            .post("/messages/conversations/$conversationId/messages")
            .then()
            .statusCode(201)
            .header(
                "Location",
                matchesPattern("/messages/conversations/[0-9a-f-]{36}/messages/[0-9a-f-]{36}")
            )
            .body("senderUserId", equalTo(customer))
            .extract()

        val messageId = firstMessage.jsonPath().getString("id")
        given()
            .header("Authorization", "Bearer ${testToken(customer)}")
            .header("Idempotency-Key", key)
            .contentType(ContentType.JSON)
            .body(mapOf("body" to "A retried body must not create a second message"))
            .post("/messages/conversations/$conversationId/messages")
            .then()
            .statusCode(200)
            .body("id", equalTo(messageId))
            .body("body", equalTo("Is the lens still available?"))

        relayOutbox()
        val events = connector.sink<MessageSentEvent>("message-sent-out").received()
            .map { it.payload }
            .filter { it.messageId == messageId }
        assertEquals(1, events.size)
        assertEquals("message-sent", events.single().eventType)
        assertEquals(1, events.single().version)

        given()
            .header("Authorization", "Bearer ${testToken(photographer)}")
            .get("/messages/conversations/$conversationId")
            .then()
            .statusCode(200)
            .body("id", equalTo(conversationId))

        given()
            .header("Authorization", "Bearer ${testToken(customer)}")
            .queryParam("limit", 1)
            .queryParam("offset", 0)
            .get("/messages/conversations/$conversationId/messages")
            .then()
            .statusCode(200)
            .body("$", hasSize<kotlin.Any>(1))
            .body("id", hasItem(messageId))

        given()
            .header("Authorization", "Bearer ${testToken(stranger)}")
            .get("/messages/conversations/$conversationId/messages")
            .then()
            .statusCode(403)
            .body("error", equalTo("HTTP_ERROR"))
            .body("status", equalTo(403))

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select count(*) from messages where conversation_id = ? and idempotency_key = ?"
            ).use {
                it.setString(1, conversationId)
                it.setString(2, key)
                it.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals(1, result.getInt(1))
                }
            }
        }
        Unit
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
    fun `listing state authentication validation and missing resources map to stable errors`() = runBlocking {
        val owner = UUID.randomUUID().toString()
        val customer = UUID.randomUUID().toString()
        val activeId = UUID.randomUUID().toString()
        val pausedId = UUID.randomUUID().toString()
        `when`(listingLookup.findListing(activeId)).thenReturn(ListingReference(activeId, owner, "ACTIVE"))
        `when`(listingLookup.findListing(pausedId)).thenReturn(ListingReference(pausedId, owner, "PAUSED"))

        given().contentType(ContentType.JSON)
            .body(mapOf("listingId" to activeId))
            .post("/messages/conversations")
            .then()
            .statusCode(401)
            .body("status", equalTo(401))

        given().header("Authorization", "Bearer ${testToken(owner)}")
            .contentType(ContentType.JSON)
            .body(mapOf("listingId" to activeId))
            .post("/messages/conversations")
            .then()
            .statusCode(403)
            .body("status", equalTo(403))

        given().header("Authorization", "Bearer ${testToken(customer)}")
            .contentType(ContentType.JSON)
            .body(mapOf("listingId" to pausedId))
            .post("/messages/conversations")
            .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))

        given().header("Authorization", "Bearer ${testToken(customer)}")
            .contentType(ContentType.JSON)
            .body(mapOf("listingId" to UUID.randomUUID().toString()))
            .post("/messages/conversations")
            .then()
            .statusCode(404)
            .body("error", equalTo("NOT_FOUND"))
            .body("path", containsString("/messages/conversations"))

        given().header("Authorization", "Bearer ${testToken(customer)}")
            .queryParam("limit", 0)
            .get("/messages/conversations")
            .then()
            .statusCode(400)
            .body("status", equalTo(400))

        given().header("Authorization", "Bearer ${testToken(customer)}")
            .get("/messages/conversations/${UUID.randomUUID()}")
            .then()
            .statusCode(404)
            .body("status", equalTo(404))
        Unit
    }

    @Test
    fun `listing dependency outage maps to stable 503 and timeout to 504`() = runBlocking {
        val customer = UUID.randomUUID().toString()
        val listingId = UUID.randomUUID().toString()
        val request = mapOf("listingId" to listingId)

        `when`(listingLookup.findListing(listingId))
            .thenThrow(MessagingDependencyUnavailableException("Listing service is unavailable"))
        given().header("Authorization", "Bearer ${testToken(customer)}")
            .contentType(ContentType.JSON).body(request)
            .post("/messages/conversations").then()
            .statusCode(503)
            .body("error", equalTo("DEPENDENCY_UNAVAILABLE"))

        reset(listingLookup)
        `when`(listingLookup.findListing(listingId))
            .thenThrow(MessagingDependencyTimeoutException("Listing service timed out"))
        given().header("Authorization", "Bearer ${testToken(customer)}")
            .contentType(ContentType.JSON).body(request)
            .post("/messages/conversations").then()
            .statusCode(504)
            .body("error", equalTo("DEPENDENCY_TIMEOUT"))
        Unit
    }

    @Test
    fun `conversation filter pagination and idempotency header validation are enforced`() = runBlocking {
        val photographer = UUID.randomUUID().toString()
        val customer = UUID.randomUUID().toString()
        val firstListing = UUID.randomUUID().toString()
        val secondListing = UUID.randomUUID().toString()
        `when`(listingLookup.findListing(firstListing)).thenReturn(ListingReference(firstListing, photographer, "ACTIVE"))
        `when`(listingLookup.findListing(secondListing)).thenReturn(ListingReference(secondListing, photographer, "ACTIVE"))
        val token = testToken(customer)
        val firstConversation = createConversation(token, firstListing)
        createConversation(token, secondListing)

        given().header("Authorization", "Bearer $token")
            .queryParam("listingId", firstListing)
            .queryParam("limit", 1)
            .queryParam("offset", 0)
            .get("/messages/conversations")
            .then()
            .statusCode(200)
            .body("$", hasSize<kotlin.Any>(1))
            .body("id", hasItem(firstConversation))

        given().header("Authorization", "Bearer $token")
            .header("Idempotency-Key", "x".repeat(65))
            .contentType(ContentType.JSON)
            .body(mapOf("body" to "Too long key"))
            .post("/messages/conversations/$firstConversation/messages")
            .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))
        Unit
    }

    @Test
    fun `conversation removal is participant-local and closes after both sides remove it`() = runBlocking {
        val photographer = UUID.randomUUID().toString()
        val customer = UUID.randomUUID().toString()
        val listingId = UUID.randomUUID().toString()
        `when`(listingLookup.findListing(listingId)).thenReturn(ListingReference(listingId, photographer, "ACTIVE"))

        val customerToken = testToken(customer)
        val photographerToken = testToken(photographer)
        val conversationId = createConversation(customerToken, listingId)

        given()
            .header("Authorization", "Bearer $customerToken")
            .delete("/messages/conversations/$conversationId")
            .then()
            .statusCode(200)
            .body("status", equalTo("OPEN"))

        given()
            .header("Authorization", "Bearer $customerToken")
            .get("/messages/conversations")
            .then()
            .statusCode(200)
            .body("id", not(hasItem(conversationId)))

        given()
            .header("Authorization", "Bearer $photographerToken")
            .get("/messages/conversations")
            .then()
            .statusCode(200)
            .body("id", hasItem(conversationId))

        given()
            .header("Authorization", "Bearer $photographerToken")
            .delete("/messages/conversations/$conversationId")
            .then()
            .statusCode(200)
            .body("status", equalTo("CLOSED"))

        given()
            .header("Authorization", "Bearer $customerToken")
            .contentType(ContentType.JSON)
            .body(mapOf("body" to "Ist das noch verfuegbar?"))
            .post("/messages/conversations/$conversationId/messages")
            .then()
            .statusCode(403)
        Unit
    }

    @Test
    fun `all messaging migrations and unique indexes are installed`() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from flyway_schema_history where success").use { result ->
                    assertTrue(result.next())
                    assertTrue(result.getInt(1) >= 3)
                }
                statement.executeQuery(
                    "select count(*) from pg_indexes where indexname in " +
                        "('idx_conversations_listing_customer_unique', 'uq_messages_idempotency')"
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(2, result.getInt(1))
                }
            }
        }
    }

    private fun createConversation(token: String, listingId: String): String =
        given().header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(mapOf("listingId" to listingId))
            .post("/messages/conversations")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("id")

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
