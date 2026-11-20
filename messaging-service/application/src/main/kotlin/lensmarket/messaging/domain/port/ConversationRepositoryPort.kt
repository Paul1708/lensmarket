package lensmarket.messaging.domain.port

import lensmarket.messaging.domain.model.Conversation
import java.time.Instant

/**
 * Persistenz-Port für Konversationen.
 *
 * Der Port bleibt nach dem Interface-Segregation-Prinzip auf Conversation-Operationen
 * beschränkt.
 */
interface ConversationRepositoryPort {
    /**
     * Speichert eine neue Konversation.
     *
     * @param conversation Zu persistierende Konversation.
     * @return Gespeicherte Konversation.
     */
    suspend fun saveConversation(conversation: Conversation): Conversation

    /**
     * Sucht den Thread eines Kunden zu einer Anzeige.
     *
     * @param listingId Technische Anzeigen-ID.
     * @param customerUserId Technische ID des Kunden.
     * @return Bestehende Konversation oder `null`.
     */
    suspend fun findConversationForListingAndCustomer(listingId: String, customerUserId: String): Conversation?

    /**
     * Listet Konversationen eines Nutzers.
     *
     * @param userId Technische Nutzer-ID.
     * @param listingId Optionale Anzeigen-ID.
     * @param limit Maximale Ergebnisanzahl.
     * @param offset Startposition für Pagination.
     * @return Passende Konversationen.
     */
    suspend fun findConversationsForUser(
        userId: String,
        listingId: String?,
        limit: Int,
        offset: Int
    ): List<Conversation>

    /**
     * Sucht eine Konversation anhand ihrer ID.
     *
     * @param conversationId Technische Konversations-ID.
     * @return Gefundene Konversation oder `null`.
     */
    suspend fun findConversationById(conversationId: String): Conversation?

    /**
     * Entfernt eine Konversation aus der Sicht eines Teilnehmers.
     *
     * Sobald beide Teilnehmer entfernt haben, wechselt die Konversation auf CLOSED.
     *
     * @param conversationId Technische Konversations-ID.
     * @param userId Technische ID der entfernenden Person.
     * @param deletedAt Zeitpunkt der Entfernung.
     * @return Aktualisierte Konversation.
     */
    suspend fun markConversationDeletedForUser(
        conversationId: String,
        userId: String,
        deletedAt: Instant
    ): Conversation
}
