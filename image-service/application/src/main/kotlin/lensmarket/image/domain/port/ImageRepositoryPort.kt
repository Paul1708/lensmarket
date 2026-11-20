package lensmarket.image.domain.port

import lensmarket.image.domain.model.Image
import lensmarket.image.domain.model.ImageSearchFilter
import java.time.Instant

/**
 * Persistenz-Port für Bild-Metadaten.
 *
 * Use Cases hängen an dieser Abstraktion und nicht direkt an Panache oder JPA.
 */
interface ImageRepositoryPort {
    /**
     * Speichert neue Bild-Metadaten.
     *
     * @param image Zu persistierendes Domain-Modell.
     * @return Gespeicherte Bild-Metadaten.
     */
    suspend fun save(image: Image): Image

    /** Speichert Bildmetadaten und beide Upload-Outbox-Events atomar. */
    suspend fun saveWithEvents(image: Image, uploadedEventId: String, watermarkedEventId: String): Image

    /**
     * Listet Bild-Metadaten anhand eines Filters.
     *
     * @param filter Suchfilter für Listing- oder Besitzer-ID.
     * @return Passende Bild-Metadaten.
     */
    suspend fun listImages(filter: ImageSearchFilter): List<Image>

    /**
     * Sucht Bild-Metadaten anhand der technischen ID.
     *
     * @param id Technische Bild-ID.
     * @return Gefundene Bild-Metadaten oder `null`.
     */
    suspend fun findImageById(id: String): Image?

    /**
     * Aktualisiert den fachlichen Status eines Bildes.
     *
     * @param imageId Technische Bild-ID.
     * @param status Neuer Bildstatus.
     * @return Aktualisierte Bild-Metadaten oder `null`.
     */
    suspend fun updateStatus(imageId: String, status: String): Image?

    /**
     * Löscht Bild-Metadaten unwiderruflich anhand der technischen ID.
     *
     * @param id Technische Bild-ID.
     * @return `true`, wenn ein Datensatz gelöscht wurde.
     */
    suspend fun deleteImage(id: String): Boolean

    /** Liefert alle in der DB referenzierten Object-Storage-Schlüssel. */
    suspend fun listStoredObjectKeys(): Set<String>

    /** Prüft die explizite Consumer-Deduplizierung anhand der stabilen Event-ID. */
    suspend fun isEventProcessed(eventId: String): Boolean

    /** Markiert Listing-Delete und Tombstone atomar als erfolgreich verarbeitet. */
    suspend fun markListingDeletedEventProcessed(eventId: String, listingId: String, processedAt: Instant)
}
