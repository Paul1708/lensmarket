package lensmarket.user.domain.port

import lensmarket.user.domain.model.User
import java.time.Instant

data class AuthenticatedUser(
    val id: String,
    val email: String,
    val role: String
)

interface PasswordHashPort {
    fun hash(password: String): String
    fun verify(password: String, storedHash: String?): Boolean
}

fun interface TokenIssuerPort {
    fun issueToken(user: User): String
}

interface TokenVerifierPort {
    fun verify(token: String): AuthenticatedUser?
}

interface UserMetricsPort {
    fun userCreated(role: String)
    fun userUpdated()
}

fun interface ClockPort {
    fun now(): Instant
}

fun interface IdGeneratorPort {
    fun newId(): String
}
