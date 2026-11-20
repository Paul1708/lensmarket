package lensmarket.listing.domain.usecase

import lensmarket.listing.domain.model.ChangeListingStatusCommand
import lensmarket.listing.domain.model.ChangeListingStatusResult
import lensmarket.listing.domain.model.LISTING_STATUSES
import lensmarket.listing.domain.model.LISTING_STATUS_TRANSITIONS
import lensmarket.listing.domain.port.ClockPort
import lensmarket.listing.domain.port.IdGeneratorPort
import lensmarket.listing.domain.port.ListingRepositoryPort
import lensmarket.listing.domain.port.ListingMetricsPort

/**
 * Ändert den fachlichen Status einer Anzeige.
 *
 * Erlaubte Zielstatus sind die Werte aus `LISTING_STATUSES`. Ein Request auf den
 * bereits gespeicherten Status gilt als erfolgreich, erzeugt aber keinen echten Wechsel.
 *
 * @param listings Persistenz-Port für Anzeigen.
 */
class ChangeListingStatusUseCase(
    private val listings: ListingRepositoryPort,
    private val metrics: ListingMetricsPort,
    private val clock: ClockPort,
    private val ids: IdGeneratorPort
) {
    /**
     * Prüft und speichert einen Statuswechsel.
     *
     * Nur der Besitzer der Anzeige darf den Status ändern.
     *
     * @param id Technische Anzeigen-ID.
     * @param currentUserId ID des authentifizierten Nutzers.
     * @param command Zielstatus.
     * @return Ergebnis mit Anzeige, altem Status, neuem Status und Wechselkennzeichen.
     * @throws ListingUseCaseValidationException Wenn ID oder Status ungültig sind.
     * @throws ListingUseCaseForbiddenException Wenn ein fremder Besitzer ändern möchte.
     * @throws ListingUseCaseNotFoundException Wenn die Anzeige nicht existiert.
     */
    suspend fun changeStatus(
        id: String,
        currentUserId: String,
        command: ChangeListingStatusCommand
    ): ChangeListingStatusResult {
        val listingId = requiredText(id, "id")
        val userId = requiredText(currentUserId, "currentUserId")
        val newStatus = validStatus(command.status)

        val existing = listings.findListingById(listingId)
            ?: throw ListingUseCaseNotFoundException("Listing $listingId not found")
        if (existing.ownerUserId != userId) {
            throw ListingUseCaseForbiddenException("Only the listing owner can change this listing status")
        }
        if (existing.status == newStatus) {
            return ChangeListingStatusResult(existing, existing.status, newStatus)
        }
        if (newStatus !in LISTING_STATUS_TRANSITIONS.getValue(existing.status)) {
            throw ListingUseCaseValidationException(
                "listing status cannot change from ${existing.status} to $newStatus"
            )
        }
        val updated = listings.updateStatusWithEvent(
            listingId,
            newStatus,
            existing.status,
            ids.newId(),
            clock.now()
        )
            ?: throw ListingUseCaseNotFoundException("Listing $listingId not found")
        val result = ChangeListingStatusResult(updated, existing.status, newStatus)
        metrics.listingStatusChanged(result.oldStatus, result.newStatus)
        return result
    }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw ListingUseCaseValidationException("$fieldName must not be blank")
        }
        return trimmed
    }

    private fun validStatus(value: String): String {
        val status = requiredText(value, "status").uppercase()
        if (status !in LISTING_STATUSES) {
            throw ListingUseCaseValidationException("status must be one of ${LISTING_STATUSES.joinToString()}")
        }
        return status
    }
}
