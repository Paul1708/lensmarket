package lensmarket.messaging.domain.usecase

import lensmarket.messaging.domain.model.Conversation
import lensmarket.messaging.domain.port.ConversationRepositoryPort

/**
 * Lädt eine einzelne Konversation mit Teilnehmerprüfung.
 *
 * Thread-Details sind nur für die beiden gespeicherten Teilnehmer sichtbar.
 *
 * @param conversations Persistenz-Port für Konversationen.
 */
class GetConversationUseCase(
    private val conversations: ConversationRepositoryPort
) {
    /**
     * Sucht eine Konversation anhand der ID.
     *
     * @param currentUserId ID des authentifizierten Nutzers.
     * @param conversationId Technische Konversations-ID.
     * @return Gefundene Konversation.
     * @throws MessagingUseCaseValidationException Wenn IDs leer sind.
     * @throws MessagingUseCaseForbiddenException Wenn der Nutzer kein Teilnehmer ist.
     * @throws MessagingUseCaseNotFoundException Wenn die Konversation nicht existiert.
     */
    suspend fun get(currentUserId: String, conversationId: String): Conversation {
        val userId = requiredText(currentUserId, "currentUserId")
        val cleanedConversationId = requiredText(conversationId, "conversationId")

        val conversation = conversations.findConversationById(cleanedConversationId)
            ?: throw MessagingUseCaseNotFoundException("Conversation $cleanedConversationId not found")
        if (userId !in setOf(conversation.photographerUserId, conversation.customerUserId)) {
            throw MessagingUseCaseForbiddenException("Only conversation participants can read this thread")
        }
        return conversation
    }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw MessagingUseCaseValidationException("$fieldName must not be blank")
        }
        return trimmed
    }
}
