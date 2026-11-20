package lensmarket.listing.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * JPA-Entity für die Tabelle `listings`.
 *
 * Die Entity bleibt nah am Flyway-Schema. Das Domain- und API-Modell nutzt dagegen
 * echte Listen für `tags` und `imageIds`.
 */
@Entity
@Table(name = "listings")
class ListingEntity {
    /**
     * Technische Anzeigen-ID.
     */
    @Id
    @Column(length = 36, nullable = false)
    lateinit var id: String

    /**
     * Nutzer-ID des Anzeigenbesitzers.
     */
    @Column(name = "owner_user_id", length = 36, nullable = false)
    lateinit var ownerUserId: String

    /**
     * Titel der Anzeige.
     */
    @Column(length = 160, nullable = false)
    lateinit var title: String

    /**
     * Beschreibungstext der Anzeige.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    lateinit var description: String

    /**
     * Optionaler Preisvorschlag.
     */
    @Column(name = "price_hint", precision = 10, scale = 2)
    var priceHint: BigDecimal? = null

    /**
     * Optionaler Standort.
     */
    @Column(length = 120)
    var location: String? = null

    /**
     * Optionales Kameramodell.
     */
    @Column(name = "camera_model", length = 120)
    var cameraModel: String? = null

    /**
     * Optionales Objektivmodell.
     */
    @Column(name = "lens_model", length = 120)
    var lensModel: String? = null

    /**
     * JSON-Text mit den Tags der Anzeige.
     *
     * Für das MVP wird keine zusätzliche Tag-Tabelle benötigt.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    lateinit var tags: String

    /**
     * JSON-Text mit Bildreferenzen aus dem Image Service.
     *
     * Der Listing Service speichert nur die IDs und nicht die Bilddateien.
     */
    @Column(name = "image_ids", nullable = false, columnDefinition = "TEXT")
    lateinit var imageIds: String

    /**
     * Fachlicher Anzeigenstatus.
     */
    @Column(length = 40, nullable = false)
    lateinit var status: String

    /**
     * Zeitpunkt der Anzeigeanlage.
     *
     * PostgreSQL `TIMESTAMPTZ` wird in Kotlin als `Instant` abgebildet.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    lateinit var createdAt: Instant

    /**
     * Zeitpunkt des Soft-Deletes oder `null`.
     */
    @Column(name = "deleted_at", columnDefinition = "TIMESTAMPTZ")
    var deletedAt: Instant? = null
}
