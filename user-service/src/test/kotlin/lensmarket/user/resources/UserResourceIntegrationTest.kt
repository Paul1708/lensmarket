package lensmarket.user.resources

import io.agroal.api.AgroalDataSource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class UserResourceIntegrationTest {
    @Inject
    lateinit var dataSource: AgroalDataSource

    @Test
    fun `registration login and authenticated profile flow expose the HTTP contract`() {
        val email = "integration-${UUID.randomUUID()}@example.test"
        val created = register(email, "Integration Test User")

        created.then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .header("Location", matchesPattern("/users/[0-9a-f-]{36}"))
            .body("email", equalTo(email))
            .body("displayName", equalTo("Integration Test User"))
            .body("passwordHash", nullValue())

        val id = created.jsonPath().getString("id")
        val login = given()
            .contentType(ContentType.JSON)
            .body(mapOf("email" to email, "password" to "correct-horse-battery-staple"))
            .post("/users/login")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("user.id", equalTo(id))
            .extract()

        val token = login.jsonPath().getString("token")
        given()
            .header("Authorization", "Bearer $token")
            .get("/users/me")
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("email", equalTo(email))

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(mapOf("bio" to "Covered by the integration test", "location" to "Berlin"))
            .patch("/users/$id")
            .then()
            .statusCode(200)
            .body("bio", equalTo("Covered by the integration test"))
            .body("location", equalTo("Berlin"))

        given().get("/users/$id").then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("email", nullValue())
    }

    @Test
    fun `validation duplicate and authentication failures use the common error shape`() {
        val email = "errors-${UUID.randomUUID()}@example.test"
        register(email, "First").then().statusCode(201)

        register(email.uppercase(), "Duplicate").then()
            .statusCode(409)
            .contentType(ContentType.JSON)
            .body("error", equalTo("USER_ALREADY_EXISTS"))
            .body("status", equalTo(409))
            .body("path", containsString("/users"))

        given()
            .contentType(ContentType.JSON)
            .body(mapOf("email" to "invalid", "displayName" to "", "password" to "short"))
            .post("/users")
            .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))
            .body("status", equalTo(400))

        given()
            .contentType(ContentType.JSON)
            .body(mapOf("email" to email, "password" to "wrong-password"))
            .post("/users/login")
            .then()
            .statusCode(401)
            .body("status", equalTo(401))
            .body("error", equalTo("HTTP_ERROR"))

        given().get("/users/me").then()
            .statusCode(401)
            .body("status", equalTo(401))
    }

    @Test
    fun `tampered token forbidden profile and missing user are rejected`() {
        val first = register("first-${UUID.randomUUID()}@example.test", "First").jsonPath()
        val second = register("second-${UUID.randomUUID()}@example.test", "Second").jsonPath()
        val firstId = first.getString("id")
        val secondId = second.getString("id")
        val email = first.getString("email")
        val token = login(email)
        val tokenParts = token.split(".")
        val signature = tokenParts[2]
        val tamperedSignature = (if (signature.first() == 'A') 'B' else 'A') + signature.drop(1)
        val tamperedToken = "${tokenParts[0]}.${tokenParts[1]}.$tamperedSignature"

        given()
            .header("Authorization", "Bearer $tamperedToken")
            .get("/users/me")
            .then()
            .statusCode(401)

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(mapOf("displayName" to "Forbidden"))
            .patch("/users/$secondId")
            .then()
            .statusCode(403)
            .body("status", equalTo(403))
            .body("error", equalTo("HTTP_ERROR"))

        given().get("/users/${UUID.randomUUID()}").then()
            .statusCode(404)
            .body("status", equalTo(404))
            .body("error", equalTo("NOT_FOUND"))

        given().get("/users").then()
            .statusCode(200)
            .body("id", not(emptyList<String>()))
            .body("find { it.id == '$firstId' }.email", nullValue())
    }

    @Test
    fun `flyway migrations and PostgreSQL constraints are active`() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from flyway_schema_history where success").use { result ->
                    assertTrue(result.next())
                    assertTrue(result.getInt(1) >= 3)
                }
                statement.executeQuery(
                    "select count(*) from information_schema.columns " +
                        "where table_name = 'users' and column_name in ('password_hash', 'updated_at')"
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(2, result.getInt(1))
                }
                statement.executeQuery(
                    "select count(*) from pg_indexes where tablename = 'users' and indexdef ilike '%unique%'"
                ).use { result ->
                    assertTrue(result.next())
                    assertTrue(result.getInt(1) >= 1)
                }
            }
        }
    }

    private fun register(email: String, displayName: String): Response =
        given()
            .contentType(ContentType.JSON)
            .body(
                mapOf(
                    "email" to email,
                    "displayName" to displayName,
                    "password" to "correct-horse-battery-staple"
                )
            )
            .post("/users")

    private fun login(email: String): String =
        given()
            .contentType(ContentType.JSON)
            .body(mapOf("email" to email, "password" to "correct-horse-battery-staple"))
            .post("/users/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("token")
}
