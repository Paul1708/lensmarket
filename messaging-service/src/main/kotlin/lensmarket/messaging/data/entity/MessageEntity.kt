package lensmarket.messaging.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * JPA-Entity für die Tabelle `messages`.
 *
 * Jede Nachricht gehört per Fremdschlüssel zu genau einer Konversation.
 */
@Entity
@Table(name = "messages")
class MessageEntity {
    /**
     * Technische Nachrichten-ID.
     */
    @Id
    @Column(length = 36, nullable = false)
    lateinit var id: String

    /**
     * Zugehörige Konversation der Nachricht.
     *
     * Die Beziehung entspricht der FK-Spalte `conversation_id` aus der Flyway-Migration.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    lateinit var conversation: ConversationEntity

    /**
     * Nutzer-ID des Senders.
     */
    @Column(name = "sender_user_id", length = 36, nullable = false)
    lateinit var senderUserId: String

    /**
     * Nachrichtentext.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    lateinit var body: String

    /**
     * Sendezeitpunkt der Nachricht.
     *
     * PostgreSQL `TIMESTAMPTZ` wird in Kotlin als `Instant` abgebildet.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    lateinit var createdAt: Instant

    /**
     * Optionaler Client-Key zur Retry-sicheren Nachrichtenerstellung.
     *
     * Der partielle Unique Index verhindert doppelte Nachrichten bei wiederholten
     * Requests mit demselben Key.
     */
    @Column(name = "idempotency_key", length = 64)
    var idempotencyKey: String? = null
}
