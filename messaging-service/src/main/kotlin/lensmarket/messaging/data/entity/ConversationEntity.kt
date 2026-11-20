package lensmarket.messaging.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import lensmarket.messaging.domain.model.ConversationStatus
import java.time.Instant

/**
 * JPA-Entity für die Tabelle `conversations`.
 *
 * Cross-Service-Bezüge auf Listings und Nutzer bleiben als technische IDs gespeichert.
 */
@Entity
@Table(name = "conversations")
class ConversationEntity {
    /**
     * Technische Konversations-ID.
     */
    @Id
    @Column(length = 36, nullable = false)
    lateinit var id: String

    /**
     * Listing-ID, zu der die Konversation gehoert.
     */
    @Column(name = "listing_id", length = 36, nullable = false)
    lateinit var listingId: String

    /**
     * Nutzer-ID des Fotografen.
     */
    @Column(name = "photographer_user_id", length = 36, nullable = false)
    lateinit var photographerUserId: String

    /**
     * Nutzer-ID des Kunden.
     */
    @Column(name = "customer_user_id", length = 36, nullable = false)
    lateinit var customerUserId: String

    /**
     * Zeitpunkt der Thread-Anlage.
     *
     * PostgreSQL `TIMESTAMPTZ` wird in Kotlin als `Instant` abgebildet.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    lateinit var createdAt: Instant

    /**
     * Zeitpunkt der letzten Aktivität im Thread.
     *
     * Chatlisten werden nach diesem Wert sortiert.
     */
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    lateinit var updatedAt: Instant

    /**
     * Sichtbarkeits-/Lebenszyklusstatus der Konversation.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ConversationStatus = ConversationStatus.OPEN

    /**
     * Zeitpunkt, zu dem der Fotograf die Konversation entfernt hat.
     */
    @Column(name = "deleted_by_photographer_at", columnDefinition = "TIMESTAMPTZ")
    var deletedByPhotographerAt: Instant? = null

    /**
     * Zeitpunkt, zu dem der Kunde die Konversation entfernt hat.
     */
    @Column(name = "deleted_by_customer_at", columnDefinition = "TIMESTAMPTZ")
    var deletedByCustomerAt: Instant? = null
}
