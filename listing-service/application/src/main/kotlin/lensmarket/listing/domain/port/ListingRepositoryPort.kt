package lensmarket.listing.domain.port

import lensmarket.listing.domain.model.Listing
import java.time.Instant

/** Ergebnis einer atomar deduplizierten Event-Anwendung. */
enum class EventApplyResult { APPLIED, AGGREGATE_NOT_FOUND, DUPLICATE }

/**
 * Persistenz-Port für Anzeigen (Schreibpfad und Detailabruf).
 *
 * Speicher- und Detaillogik wird als Abstraktion in die Use Cases gegeben, damit
 * die Domäne nicht von Panache oder JPA abhängt. Suchzugriffe liegen im
 * ListingSearchPort, damit Use Cases den Lesezugriff getrennt ausdrücken können.
 */
interface ListingRepositoryPort {
    /**
     * Speichert eine neue Anzeige.
     *
     * @param listing Zu persistierende Anzeige.
     * @return Gespeicherte Anzeige.
     */
    suspend fun save(listing: Listing): Listing

    /** Speichert Anzeige und `listing-created`-Outbox-Eintrag atomar. */
    suspend fun saveWithEvent(listing: Listing, eventId: String): Listing

    /**
     * Aktualisiert editierbare Felder einer bestehenden Anzeige.
     *
     * @param listing Anzeige mit aktualisierten Feldern.
     * @return Aktualisierte Anzeige oder `null`, wenn sie nicht existiert oder gelöscht ist.
     */
    suspend fun update(listing: Listing): Listing?

    /**
     * Aktualisiert den Status einer Anzeige.
     *
     * @param listingId Technische Anzeigen-ID.
     * @param status Neuer fachlicher Status.
     * @return Aktualisierte Anzeige oder `null`.
     */
    suspend fun updateStatus(listingId: String, status: String): Listing?

    /** Speichert Statuswechsel und Outbox-Eintrag in derselben DB-Transaktion. */
    suspend fun updateStatusWithEvent(
        listingId: String,
        status: String,
        oldStatus: String,
        eventId: String,
        occurredAt: Instant
    ): Listing?

    /**
     * Markiert eine Anzeige als gelöscht.
     *
     * @param listingId Technische Anzeigen-ID.
     * @param deletedAt Zeitpunkt des Soft-Deletes.
     * @return Gelöschte Anzeige oder `null`.
     */
    suspend fun markDeleted(listingId: String, deletedAt: Instant): Listing?

    /** Speichert Soft-Delete und `listing-deleted`-Outbox-Eintrag atomar. */
    suspend fun markDeletedWithEvent(listingId: String, deletedAt: Instant, eventId: String): Listing?

    /**
     * Sucht eine nicht gelöschte Anzeige anhand der ID.
     *
     * @param id Technische Anzeigen-ID.
     * @return Gefundene Anzeige oder `null`.
     */
    suspend fun findListingById(id: String): Listing?

    /**
     * Hängt eine Bildreferenz an eine Anzeige an.
     *
     * @param listingId Technische Anzeigen-ID.
     * @param imageId Technische Bild-ID aus dem Image Service.
     * @return Aktualisierte Anzeige oder `null`.
     */
    suspend fun appendImageId(listingId: String, imageId: String): Listing?

    /** Wendet ein Consumer-Event samt Event-ID-Marker atomar und höchstens einmal an. */
    suspend fun appendImageIdOnce(
        eventId: String,
        processedAt: Instant,
        listingId: String,
        imageId: String
    ): EventApplyResult
}
