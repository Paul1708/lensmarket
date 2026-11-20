package lensmarket.messaging.domain.usecase

import lensmarket.messaging.domain.model.Conversation
import lensmarket.messaging.domain.port.ClockPort
import lensmarket.messaging.domain.port.ConversationRepositoryPort
import lensmarket.messaging.domain.port.MessagingMetricsPort

/** Entfernt eine Konversation aus der Sicht eines Teilnehmers. */
class RemoveConversationUseCase(
    private val conversations: ConversationRepositoryPort,
    private val clock: ClockPort,
    private val metrics: MessagingMetricsPort
) {
    suspend fun remove(currentUserId: String, conversationId: String): Conversation {
        val userId = requiredText(currentUserId, "currentUserId")
        val cleanedConversationId = requiredText(conversationId, "conversationId")
        val conversation = conversations.findConversationById(cleanedConversationId)
            ?: throw MessagingUseCaseNotFoundException("Conversation $cleanedConversationId not found")

        if (userId != conversation.photographerUserId && userId != conversation.customerUserId) {
            throw MessagingUseCaseForbiddenException("Only conversation participants can remove this thread")
        }

        return conversations.markConversationDeletedForUser(cleanedConversationId, userId, clock.now()).also {
            metrics.conversationRemoved()
        }
    }

    private fun requiredText(value: String, field: String): String = value.trim().also {
        if (it.isEmpty()) throw MessagingUseCaseValidationException("$field must not be blank")
    }
}
