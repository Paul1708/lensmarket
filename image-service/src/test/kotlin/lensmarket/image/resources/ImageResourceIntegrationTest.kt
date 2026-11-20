package lensmarket.image.resources

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
import lensmarket.image.domain.port.ListingOwnershipPort
import lensmarket.image.domain.port.ObjectStoragePort
import lensmarket.image.domain.usecase.ImageDependencyTimeoutException
import lensmarket.image.domain.usecase.ImageDependencyUnavailableException
import lensmarket.image.events.ImageUploadedEvent
import lensmarket.image.events.ImageWatermarkedEvent
import lensmarket.image.events.OutboxRelay
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.reset
import org.mockito.Mockito.`when`
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.imageio.ImageIO

@QuarkusTest
class ImageResourceIntegrationTest {
    @InjectMock
    lateinit var listingOwnership: ListingOwnershipPort

    @InjectMock
    lateinit var storage: ObjectStoragePort

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
    fun resetExternalPorts() = runBlocking {
        reset(listingOwnership, storage)
        `when`(listingOwnership.isListingOwner(anyString(), anyString())).thenReturn(false)
        `when`(storage.getObject(anyString())).thenReturn("watermarked-image".toByteArray())
        Unit
    }

    @Test
    fun `multipart upload archive and watermarked delivery expose the full HTTP contract`() = runBlocking {
        val listingId = UUID.randomUUID().toString()
        val owner = UUID.randomUUID().toString()
        val token = testToken(owner)
        `when`(listingOwnership.isListingOwner(listingId, owner)).thenReturn(true)

        val uploaded = given()
            .header("Authorization", "Bearer $token")
            .multiPart("listingId", listingId)
            .multiPart("file", "sample.jpg", solidJpeg(), "image/jpeg")
            .post("/images/upload")
            .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .header("Location", matchesPattern("/images/[0-9a-f-]{36}"))
            .body("listingId", equalTo(listingId))
            .body("ownerUserId", equalTo(owner))
            .body("status", equalTo("WATERMARKED"))
            .body("hasFile", equalTo(true))
            .body("width", greaterThan(0))
            .body("height", greaterThan(0))
            .extract()

        val imageId = uploaded.jsonPath().getString("id")
        relayOutbox()
        val uploadEvent = connector.sink<ImageUploadedEvent>("image-uploaded-out").received()
            .map { it.payload }
            .last { it.imageId == imageId }
        val watermarkEvent = connector.sink<ImageWatermarkedEvent>("image-watermarked-out").received()
            .map { it.payload }
            .last { it.imageId == imageId }
        assertEquals("image-uploaded", uploadEvent.eventType)
        assertEquals("image-watermarked", watermarkEvent.eventType)
        assertEquals(1, watermarkEvent.version)
        assertEquals(listingId, watermarkEvent.listingId)

        given().queryParam("listingId", listingId).get("/images").then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", hasItem(imageId))

        given().get("/images/$imageId/watermarked").then()
            .statusCode(200)
            .contentType("image/jpeg")
            .header("Content-Disposition", containsString("watermarked_sample.jpg"))
            .body(equalTo("watermarked-image"))

        given().header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "ARCHIVED"))
            .patch("/images/$imageId/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("ARCHIVED"))

        given().queryParam("listingId", listingId).get("/images").then()
            .statusCode(200)
            .body("id", org.hamcrest.Matchers.not(hasItem(imageId)))
        given().get("/images/$imageId").then().statusCode(404)
        given().get("/images/$imageId/watermarked").then().statusCode(404)

        given().get("/images/mine").then().statusCode(401)
        given().header("Authorization", "Bearer $token")
            .queryParam("status", "ARCHIVED")
            .get("/images/mine")
            .then()
            .statusCode(200)
            .body("id", hasItem(imageId))
        given().header("Authorization", "Bearer $token")
            .get("/images/mine/$imageId")
            .then()
            .statusCode(200)
            .body("status", equalTo("ARCHIVED"))
        given().header("Authorization", "Bearer $token")
            .get("/images/mine/$imageId/watermarked")
            .then()
            .statusCode(200)
            .contentType("image/jpeg")

        given().header("Authorization", "Bearer ${testToken(UUID.randomUUID().toString())}")
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "WATERMARKED"))
            .patch("/images/$imageId/status")
            .then()
            .statusCode(403)
            .body("status", equalTo(403))
            .body("error", equalTo("HTTP_ERROR"))
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
    fun `upload ownership authentication validation and missing image use stable errors`() = runBlocking {
        val listingId = UUID.randomUUID().toString()
        val user = UUID.randomUUID().toString()

        given()
            .multiPart("listingId", listingId)
            .multiPart("file", "no-auth.jpg", solidJpeg(), "image/jpeg")
            .post("/images/upload")
            .then()
            .statusCode(401)
            .body("status", equalTo(401))

        given()
            .header("Authorization", "Bearer ${testToken(user)}")
            .multiPart("listingId", listingId)
            .multiPart("file", "forbidden.jpg", solidJpeg(), "image/jpeg")
            .post("/images/upload")
            .then()
            .statusCode(403)
            .body("status", equalTo(403))

        given()
            .header("Authorization", "Bearer ${testToken(user)}")
            .multiPart("listingId", listingId)
            .multiPart("file", "invalid.txt", "not an image".toByteArray(), "text/plain")
            .post("/images/upload")
            .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))
            .body("path", containsString("/images/upload"))

        `when`(listingOwnership.isListingOwner(listingId, user)).thenReturn(true)
        given()
            .header("Authorization", "Bearer ${testToken(user)}")
            .multiPart("listingId", listingId)
            .multiPart("file", "fake.jpg", "not an image".toByteArray(), "image/jpeg")
            .post("/images/upload")
            .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))

        val missing = UUID.randomUUID()
        given().get("/images/$missing").then()
            .statusCode(404)
            .body("error", equalTo("NOT_FOUND"))

        given().header("Authorization", "Bearer ${testToken(user)}")
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "INVALID"))
            .patch("/images/$missing/status")
            .then()
            .statusCode(400)
            .body("status", equalTo(400))
        Unit
    }

    @Test
    fun `listing dependency outage maps to stable 503 and timeout to 504`() = runBlocking {
        val listingId = UUID.randomUUID().toString()
        val user = UUID.randomUUID().toString()

        `when`(listingOwnership.isListingOwner(listingId, user))
            .thenThrow(ImageDependencyUnavailableException("Listing service is unavailable"))
        given().header("Authorization", "Bearer ${testToken(user)}")
            .multiPart("listingId", listingId)
            .multiPart("file", "unavailable.jpg", solidJpeg(), "image/jpeg")
            .post("/images/upload").then()
            .statusCode(503)
            .body("error", equalTo("DEPENDENCY_UNAVAILABLE"))

        reset(listingOwnership)
        `when`(listingOwnership.isListingOwner(listingId, user))
            .thenThrow(ImageDependencyTimeoutException("Listing service timed out"))
        given().header("Authorization", "Bearer ${testToken(user)}")
            .multiPart("listingId", listingId)
            .multiPart("file", "timeout.jpg", solidJpeg(), "image/jpeg")
            .post("/images/upload").then()
            .statusCode(504)
            .body("error", equalTo("DEPENDENCY_TIMEOUT"))
        Unit
    }

    @Test
    fun `all image migrations and storage path columns are installed`() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from flyway_schema_history where success").use { result ->
                    assertTrue(result.next())
                    assertTrue(result.getInt(1) >= 2)
                }
                statement.executeQuery(
                    "select count(*) from information_schema.columns where table_name = 'images' " +
                        "and column_name in ('original_path', 'watermarked_path')"
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(2, result.getInt(1))
                }
            }
        }
    }

    private fun solidJpeg(): ByteArray {
        val image = BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color(30, 50, 70)
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.dispose()
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "jpeg", output)
            output.toByteArray()
        }
    }

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
