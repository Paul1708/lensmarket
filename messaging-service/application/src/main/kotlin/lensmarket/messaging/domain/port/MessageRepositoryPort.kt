package lensmarket.messaging.domain.port

import lensmarket.messaging.domain.model.Message

/**
 * Persistenz-Port für Nachrichten.
 *
 * Der Port bleibt nach dem Interface-Segregation-Prinzip auf Message-Operationen
 * beschränkt.
 */
interface MessageRepositoryPort {
    /**
     * Speichert eine neue Nachricht.
     *
     * @param message Zu persistierende Nachricht.
     * @return Gespeicherte Nachricht.
     */
    suspend fun saveMessage(message: Message): Message

    /** Speichert Nachricht, Conversation-Aktivität und Outbox-Event atomar. */
    suspend fun saveMessageWithEvent(message: Message, eventId: String): Message

    /**
     * Lädt eine paginierte Nachrichtenseite einer Konversation.
     *
     * @param conversationId Technische Konversations-ID.
     * @param limit Maximale Ergebnisanzahl.
     * @param offset Startposition für Pagination.
     * @return Nachrichten der Konversation.
     */
    suspend fun findMessages(conversationId: String, limit: Int, offset: Int): List<Message>

    /**
     * Sucht eine Nachricht anhand ihres Idempotency-Keys.
     *
     * @param conversationId Technische Konversations-ID.
     * @param senderUserId Technische ID des Senders.
     * @param idempotencyKey Client-Key aus dem Request.
     * @return Bereits gespeicherte Nachricht oder `null`.
     */
    suspend fun findMessageByIdempotencyKey(
        conversationId: String,
        senderUserId: String,
        idempotencyKey: String
    ): Message?
}
