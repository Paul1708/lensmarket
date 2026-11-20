package lensmarket.listing.domain.usecase

import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.model.UpdateListingCommand
import lensmarket.listing.domain.port.ListingRepositoryPort
import lensmarket.listing.domain.port.ListingMetricsPort
import java.math.BigDecimal

/**
 * Ersetzt die editierbaren Felder einer bestehenden Anzeige.
 *
 * ID, Besitzer, Status und Bildreferenzen bleiben unverändert.
 *
 * @param listings Persistenz-Port für Anzeigen.
 */
class UpdateListingUseCase(
    private val listings: ListingRepositoryPort,
    private val metrics: ListingMetricsPort
) {
    /**
     * Aktualisiert eine Anzeige per PUT-Semantik.
     *
     * Nur der Besitzer der Anzeige darf die Änderung ausführen.
     *
     * @param id Technische Anzeigen-ID.
     * @param currentUserId ID des authentifizierten Nutzers.
     * @param command Neue editierbare Anzeigendaten.
     * @return Aktualisierte Anzeige.
     * @throws ListingUseCaseValidationException Wenn Eingaben ungültig sind.
     * @throws ListingUseCaseForbiddenException Wenn ein fremder Besitzer ändern möchte.
     * @throws ListingUseCaseNotFoundException Wenn die Anzeige nicht existiert.
     */
    suspend fun update(id: String, currentUserId: String, command: UpdateListingCommand): Listing {
        val listingId = requiredText(id, "id")
        val userId = requiredText(currentUserId, "currentUserId")
        val title = requiredText(command.title, "title")
        val description = requiredText(command.description, "description")
        val priceHint = validPrice(command.priceHint)
        val location = optionalText(command.location)
        val cameraModel = optionalText(command.cameraModel)
        val lensModel = optionalText(command.lensModel)
        val tags = cleanOptionalTexts(command.tags)

        val existing = listings.findListingById(listingId)
            ?: throw ListingUseCaseNotFoundException("Listing $listingId not found")
        if (existing.ownerUserId != userId) {
            throw ListingUseCaseForbiddenException("Only the listing owner can update this listing")
        }
        val updated = listings.update(existing.copy(
            title = title,
            description = description,
            priceHint = priceHint,
            location = location,
            cameraModel = cameraModel,
            lensModel = lensModel,
            tags = tags
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

    private fun optionalText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun cleanOptionalTexts(values: List<String>): List<String> =
        values.mapNotNull { optionalText(it) }

    private fun validPrice(value: BigDecimal?): BigDecimal? {
        if (value != null && value < BigDecimal.ZERO) {
            throw ListingUseCaseValidationException("priceHint must not be negative")
        }
        return value
    }
}
