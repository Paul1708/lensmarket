package lensmarket.listing.domain.usecase

import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.port.ListingRepositoryPort
import lensmarket.listing.domain.port.ClockPort
import lensmarket.listing.domain.port.IdGeneratorPort
import lensmarket.listing.domain.port.ListingMetricsPort

/**
 * Löscht Anzeigen per Soft-Delete.
 *
 * Der Datensatz bleibt erhalten, damit referenzierende Bilder, Nachrichten und Events
 * fachlich nachvollziehbar bleiben.
 *
 * @param listings Persistenz-Port für Anzeigen.
 */
class DeleteListingUseCase(
    private val listings: ListingRepositoryPort,
    private val metrics: ListingMetricsPort,
    private val clock: ClockPort,
    private val ids: IdGeneratorPort
) {
    /**
     * Markiert eine Anzeige als gelöscht.
     *
     * Nur der Besitzer der Anzeige darf diese Aktion ausführen.
     *
     * @param id Technische Anzeigen-ID.
     * @param currentUserId ID des authentifizierten Nutzers.
     * @return Gelöschte Anzeige.
     * @throws ListingUseCaseValidationException Wenn IDs ungültig sind.
     * @throws ListingUseCaseForbiddenException Wenn ein fremder Besitzer löschen möchte.
     * @throws ListingUseCaseNotFoundException Wenn die Anzeige nicht existiert.
     */
    suspend fun delete(id: String, currentUserId: String): Listing {
        val listingId = requiredText(id, "id")
        val userId = requiredText(currentUserId, "currentUserId")

        val existing = listings.findListingById(listingId)
            ?: throw ListingUseCaseNotFoundException("Listing $listingId not found")
        if (existing.ownerUserId != userId) {
            throw ListingUseCaseForbiddenException("Only the listing owner can delete this listing")
        }
        val deleted = listings.markDeletedWithEvent(listingId, clock.now(), ids.newId())
            ?: throw ListingUseCaseNotFoundException("Listing $listingId not found")
        metrics.listingDeleted()
        return deleted
    }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw ListingUseCaseValidationException("$fieldName must not be blank")
        }
        return trimmed
    }
}
