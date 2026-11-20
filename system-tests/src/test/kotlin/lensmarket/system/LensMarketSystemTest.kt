package lensmarket.system

import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

class LensMarketSystemTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun configureBaseUrl() {
            RestAssured.baseURI = System.getProperty("lensmarket.base-url", "http://localhost:8081")
        }
    }

    @Test
    fun `critical marketplace flow works through the Linux-portable HTTP boundary`() {
        val marker = UUID.randomUUID().toString().take(8)
        val sellerEmail = "system-seller-$marker@lensmarket.test"
        val buyerEmail = "system-buyer-$marker@lensmarket.test"
        val seller = register(sellerEmail, "System Seller")
        val buyer = register(buyerEmail, "System Buyer")
        val sellerToken = login(sellerEmail)
        val buyerToken = login(buyerEmail)

        val listing = given()
            .authBearer(sellerToken)
            .contentType(ContentType.JSON)
            .body(
                mapOf(
                    "title" to "System Test Lens $marker",
                    "description" to "Portable full-stack test",
                    "priceHint" to 149.99,
                    "location" to "Berlin",
                    "tags" to listOf("system", marker)
                )
            )
            .post("/listings")
            .then()
            .statusCode(201)
            .extract()
            .response()
        val listingId = listing.jsonPath().getString("id")
        assertEquals(seller, listing.jsonPath().getString("ownerUserId"))

        given()
            .authBearer(sellerToken)
            .contentType(ContentType.JSON)
            .body(
                mapOf(
                    "title" to "Forged projection $marker",
                    "description" to "Must be rejected",
                    "imageIds" to listOf("forged-image")
                )
            )
            .post("/listings")
            .then()
            .statusCode(400)

        val image = given()
            .authBearer(sellerToken)
            .multiPart("listingId", listingId)
            .multiPart("file", "system.jpg", jpeg(), "image/jpeg")
            .post("/images/upload")
            .then()
            .statusCode(201)
            .extract()
            .response()
        val imageId = image.jsonPath().getString("id")
        assertEquals("WATERMARKED", image.jsonPath().getString("status"))
        assertTrue(eventually {
            given().get("/listings/$listingId").jsonPath().getList<String>("imageIds").contains(imageId)
        })

        given()
            .authBearer(sellerToken)
            .multiPart("listingId", listingId)
            .multiPart("file", "fake.jpg", "not an image".toByteArray(), "image/jpeg")
            .post("/images/upload")
            .then()
            .statusCode(400)

        given().authBearer(sellerToken)
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "ARCHIVED"))
            .patch("/images/$imageId/status")
            .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("ARCHIVED"))

        given().get("/images/$imageId").then().statusCode(404)
        given().get("/images/$imageId/watermarked").then().statusCode(404)
        val ownImages = given().authBearer(sellerToken).queryParam("status", "ARCHIVED")
            .get("/images/mine")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList<String>("id")
        assertTrue(imageId in ownImages)
        given().authBearer(sellerToken).get("/images/mine/$imageId/watermarked").then().statusCode(200)

        val conversation = given()
            .authBearer(buyerToken)
            .contentType(ContentType.JSON)
            .body(mapOf("listingId" to listingId))
            .post("/messages/conversations")
            .then()
            .statusCode(201)
            .extract()
            .response()
        val conversationId = conversation.jsonPath().getString("id")
        assertEquals(buyer, conversation.jsonPath().getString("customerUserId"))

        val cors = given()
            .header("Origin", "http://localhost:8081")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Idempotency-Key")
            .options("/messages/conversations/$conversationId/messages")
        assertTrue(cors.statusCode in listOf(200, 204))
        assertTrue(cors.getHeader("Access-Control-Allow-Headers").contains("Idempotency-Key", ignoreCase = true))

        val key = "system-$marker"
        val message = sendMessage(buyerToken, conversationId, key, "Is this available?", 201)
        val retried = sendMessage(buyerToken, conversationId, key, "Retry body", 200)
        assertEquals(message.jsonPath().getString("id"), retried.jsonPath().getString("id"))
        assertEquals("Is this available?", retried.jsonPath().getString("body"))

        given().contentType(ContentType.JSON)
            .body(mapOf("title" to "Unauthorized", "description" to "Must fail"))
            .post("/listings")
            .then()
            .statusCode(401)

        given().authBearer(sellerToken)
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "PAUSED"))
            .patch("/listings/$listingId/status")
            .then()
            .statusCode(200)
        given().get("/listings/$listingId").then().statusCode(404)
        given().authBearer(sellerToken).get("/listings/mine/$listingId").then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("PAUSED"))
        val publicIds = given().queryParam("ownerUserId", seller).queryParam("status", "PAUSED")
            .get("/listings")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList<String>("id")
        assertTrue(listingId !in publicIds)

        given().authBearer(sellerToken)
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "SOLD"))
            .patch("/listings/$listingId/status")
            .then()
            .statusCode(200)
        given().authBearer(sellerToken)
            .contentType(ContentType.JSON)
            .body(mapOf("status" to "ACTIVE"))
            .patch("/listings/$listingId/status")
            .then()
            .statusCode(400)

        given().authBearer(sellerToken).delete("/listings/$listingId").then().statusCode(204)
        assertTrue(eventually { given().authBearer(sellerToken).get("/images/mine/$imageId").statusCode == 404 })
    }

    private fun register(email: String, displayName: String): String {
        val response = given()
            .contentType(ContentType.JSON)
            .body(mapOf("email" to email, "displayName" to displayName, "password" to "Test1234!"))
            .post("/users")
            .then()
            .statusCode(201)
            .extract()
            .response()
        return response.jsonPath().getString("id").also { assertNotNull(it) }
    }

    private fun login(email: String): String =
        given()
            .contentType(ContentType.JSON)
            .body(mapOf("email" to email, "password" to "Test1234!"))
            .post("/users/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("token")

    private fun sendMessage(token: String, conversationId: String, key: String, body: String, status: Int): Response =
        given().authBearer(token)
            .header("Idempotency-Key", key)
            .contentType(ContentType.JSON)
            .body(mapOf("body" to body))
            .post("/messages/conversations/$conversationId/messages")
            .then()
            .statusCode(status)
            .extract()
            .response()

    private fun io.restassured.specification.RequestSpecification.authBearer(token: String) =
        header("Authorization", "Bearer $token")

    private fun eventually(assertion: () -> Boolean): Boolean {
        repeat(20) {
            if (runCatching(assertion).getOrDefault(false)) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun jpeg(): ByteArray {
        val image = BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color(80, 140, 190)
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.dispose()
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "jpeg", output)
            output.toByteArray()
        }
    }
}
