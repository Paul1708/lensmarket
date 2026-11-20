package lensmarket.messaging.domain.usecase

import lensmarket.messaging.domain.model.DEFAULT_CONVERSATION_LIST_LIMIT
import lensmarket.messaging.domain.model.Conversation
import lensmarket.messaging.domain.model.MAX_CONVERSATION_LIST_LIMIT
import lensmarket.messaging.domain.port.ConversationRepositoryPort

/**
 * Listet Konversationen des angemeldeten Nutzers.
 *
 * Nutzer sehen nur eigene Konversationen. Eine optionale Listing-ID schränkt diese
 * Liste weiter ein.
 *
 * @param conversations Persistenz-Port für Konversationen.
 */
class ListConversationsUseCase(
    private val conversations: ConversationRepositoryPort
) {
    /**
     * Lädt eine paginierte Konversationsliste.
     *
     * @param currentUserId ID des authentifizierten Nutzers.
     * @param listingId Optionale Anzeigen-ID.
     * @param limit Maximale Anzahl zurückgegebener Konversationen.
     * @param offset Startposition für Pagination.
     * @return Liste eigener Konversationen.
     * @throws MessagingUseCaseValidationException Wenn Nutzer-ID oder Pagination ungültig sind.
     */
    suspend fun list(currentUserId: String, listingId: String?, limit: Int?, offset: Int?): List<Conversation> {
        val userId = requiredText(currentUserId, "currentUserId")
        return conversations.findConversationsForUser(
            userId = userId,
            listingId = optionalText(listingId),
            limit = validLimit(limit ?: DEFAULT_CONVERSATION_LIST_LIMIT),
            offset = validOffset(offset ?: 0)
        )
    }

    private fun optionalText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

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
        if (value > MAX_CONVERSATION_LIST_LIMIT) {
            throw MessagingUseCaseValidationException("limit must not be greater than $MAX_CONVERSATION_LIST_LIMIT")
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
