package lensmarket.listing.domain.usecase

import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.model.PatchListingCommand
import lensmarket.listing.domain.port.ListingRepositoryPort
import lensmarket.listing.domain.port.ListingMetricsPort
import java.math.BigDecimal

/**
 * Aktualisiert gesetzte Felder einer Anzeige partiell.
 *
 * Nicht gesetzte Felder bleiben unverändert. ID, Besitzer, Status und Bildreferenzen
 * werden nicht über PATCH geändert.
 *
 * @param listings Persistenz-Port für Anzeigen.
 */
class PatchListingUseCase(
    private val listings: ListingRepositoryPort,
    private val metrics: ListingMetricsPort
) {
    /**
     * Wendet eine partielle Anzeigenänderung an.
     *
     * Nur der Besitzer der Anzeige darf die Änderung ausführen.
     *
     * @param id Technische Anzeigen-ID.
     * @param currentUserId ID des authentifizierten Nutzers.
     * @param command Partielle Änderungen.
     * @return Aktualisierte Anzeige.
     * @throws ListingUseCaseValidationException Wenn Eingaben ungültig sind.
     * @throws ListingUseCaseForbiddenException Wenn ein fremder Besitzer ändern möchte.
     * @throws ListingUseCaseNotFoundException Wenn die Anzeige nicht existiert.
     */
    suspend fun patch(id: String, currentUserId: String, command: PatchListingCommand): Listing {
        val listingId = requiredText(id, "id")
        val userId = requiredText(currentUserId, "currentUserId")

        val existing = listings.findListingById(listingId)
            ?: throw ListingUseCaseNotFoundException("Listing $listingId not found")
        if (existing.ownerUserId != userId) {
            throw ListingUseCaseForbiddenException("Only the listing owner can update this listing")
        }
        val updated = listings.update(existing.copy(
            title = command.title?.let { requiredText(it, "title") } ?: existing.title,
            description = command.description?.let { requiredText(it, "description") } ?: existing.description,
            priceHint = command.priceHint?.let { validPrice(it) } ?: existing.priceHint,
            location = if (command.location != null) optionalText(command.location) else existing.location,
            cameraModel = if (command.cameraModel != null) optionalText(command.cameraModel) else existing.cameraModel,
            lensModel = if (command.lensModel != null) optionalText(command.lensModel) else existing.lensModel,
            tags = command.tags?.let { cleanOptionalTexts(it) } ?: existing.tags
        )) ?: throw ListingUseCaseNotFoundException("Listing $listingId not found")
        metrics.listingUpdated()
        return updated
    }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw ListingUseCaseValidationException("$fieldName must not be blank")
        }
        return trimmed
    }

    private fun optionalText(value: String): String? =
        value.trim().takeIf { it.isNotEmpty() }

    private fun cleanOptionalTexts(values: List<String>): List<String> =
        values.mapNotNull { optionalText(it) }

    private fun validPrice(value: BigDecimal): BigDecimal {
        if (value < BigDecimal.ZERO) {
            throw ListingUseCaseValidationException("priceHint must not be negative")
        }
        return value
    }
}
