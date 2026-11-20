package lensmarket.image.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * JPA-Entity für die Tabelle `images`.
 *
 * Die Entity beschreibt Bild-Metadaten. Die eigentlichen Bilddateien liegen im
 * Object Storage und werden nur über Pfade referenziert.
 */
@Entity
@Table(name = "images")
class ImageEntity {
    /**
     * Technische Bild-ID.
     */
    @Id
    @Column(length = 36, nullable = false)
    lateinit var id: String

    /**
     * Listing-ID, zu der das Bild gehoert.
     */
    @Column(name = "listing_id", length = 36, nullable = false)
    lateinit var listingId: String

    /**
     * Nutzer-ID des Bildbesitzers.
     */
    @Column(name = "owner_user_id", length = 36, nullable = false)
    lateinit var ownerUserId: String

    /**
     * Urspruenglicher Dateiname des Uploads.
     */
    @Column(name = "original_file_name", length = 255, nullable = false)
    lateinit var originalFileName: String

    /**
     * MIME-Type der hochgeladenen Bilddatei.
     */
    @Column(name = "mime_type", length = 120, nullable = false)
    lateinit var mimeType: String

    /**
     * Optionale Bildbreite.
     *
     * Breite und Höhe sind optional, weil sie erst nach echtem Upload sicher bekannt sind.
     */
    @Column
    var width: Int? = null

    /**
     * Optionale Bildhöhe.
     */
    @Column
    var height: Int? = null

    /**
     * Fachlicher Verarbeitungsstatus des Bildes.
     */
    @Column(length = 40, nullable = false)
    lateinit var status: String

    /**
     * Oeffentliche URL der Wasserzeichenkopie.
     */
    @Column(name = "watermarked_url", length = 255, nullable = false)
    lateinit var watermarkedUrl: String

    /**
     * Interner Pfad des Originalobjekts, sofern eines gespeichert ist.
     */
    @Column(name = "original_path", length = 512)
    var originalPath: String? = null

    /**
     * Interner Pfad der Wasserzeichenkopie im Object Storage.
     */
    @Column(name = "watermarked_path", length = 512)
    var watermarkedPath: String? = null

    /**
     * Zeitpunkt der Bildregistrierung.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    lateinit var createdAt: Instant
}
