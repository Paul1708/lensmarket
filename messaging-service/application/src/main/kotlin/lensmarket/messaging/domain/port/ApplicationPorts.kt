package lensmarket.messaging.domain.port

import java.time.Instant

data class AuthenticatedUser(val id: String, val email: String, val role: String)
interface TokenVerifierPort { fun verify(token: String): AuthenticatedUser? }
interface MessagingMetricsPort {
    fun conversationCreated()
    fun conversationRemoved()
    fun messageSent()
}
fun interface ClockPort { fun now(): Instant }
fun interface IdGeneratorPort { fun newId(): String }
