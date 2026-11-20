package lensmarket.messaging.domain.usecase

import lensmarket.messaging.domain.model.DEFAULT_MESSAGE_LIST_LIMIT
import lensmarket.messaging.domain.model.MAX_MESSAGE_LIST_LIMIT
import lensmarket.messaging.domain.model.Message
import lensmarket.messaging.domain.port.ConversationRepositoryPort
import lensmarket.messaging.domain.port.MessageRepositoryPort

/**
 * Lädt Nachrichten einer Konversation mit Teilnehmerprüfung.
 *
 * Erst wird der Thread autorisiert, danach wird eine paginierte Nachrichtenseite
 * aus dem MessageRepositoryPort geladen.
 *
 * @param conversations Persistenz-Port für Konversationen.
 * @param messages Persistenz-Port für Nachrichten.
 */
class GetMessagesUseCase(
    private val conversations: ConversationRepositoryPort,
    private val messages: MessageRepositoryPort
) {
    /**
     * Lädt eine paginierte Nachrichtenseite.
     *
     * @param currentUserId ID des authentifizierten Nutzers.
     * @param conversationId Technische Konversations-ID.
     * @param limit Maximale Anzahl zurückgegebener Nachrichten.
     * @param offset Startposition für Pagination.
     * @return Nachrichten der Konversation.
     * @throws MessagingUseCaseValidationException Wenn IDs oder Pagination ungültig sind.
     * @throws MessagingUseCaseForbiddenException Wenn der Nutzer kein Teilnehmer ist.
     * @throws MessagingUseCaseNotFoundException Wenn die Konversation nicht existiert.
     */
    suspend fun get(currentUserId: String, conversationId: String, limit: Int?, offset: Int?): List<Message> {
        val userId = requiredText(currentUserId, "currentUserId")
        val cleanedConversationId = requiredText(conversationId, "conversationId")

        val conversation = conversations.findConversationById(cleanedConversationId)
            ?: throw MessagingUseCaseNotFoundException("Conversation $cleanedConversationId not found")
        if (userId != conversation.photographerUserId && userId != conversation.customerUserId) {
            throw MessagingUseCaseForbiddenException("Only conversation participants can read messages")
        }
        return messages.findMessages(
            conversationId = cleanedConversationId,
            limit = validLimit(limit ?: DEFAULT_MESSAGE_LIST_LIMIT),
            offset = validOffset(offset ?: 0)
        )
    }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw MessagingUseCaseValidationException("$fieldName must not be blank")
        }
        return trimmed
    }

    private fun validLimit(value: Int): Int {
        if (value <= 0) {
            throw MessagingUseCaseValidationException("limit must be greater than zero")
        }
        if (value > MAX_MESSAGE_LIST_LIMIT) {
            throw MessagingUseCaseValidationException("limit must not be greater than $MAX_MESSAGE_LIST_LIMIT")
        }
        return value
    }

    private fun validOffset(value: Int): Int {
        if (value < 0) {
            throw MessagingUseCaseValidationException("offset must not be negative")
        }
        return value
    }
}
