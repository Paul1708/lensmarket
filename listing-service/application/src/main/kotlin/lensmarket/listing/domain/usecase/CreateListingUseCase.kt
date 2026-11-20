package lensmarket.listing.domain.usecase

import lensmarket.listing.domain.model.CreateListingCommand
import lensmarket.listing.domain.model.LISTING_STATUS_ACTIVE
import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.port.ListingRepositoryPort
import lensmarket.listing.domain.port.ClockPort
import lensmarket.listing.domain.port.IdGeneratorPort
import lensmarket.listing.domain.port.ListingMetricsPort
import java.math.BigDecimal

/**
 * Legt neue Anzeigen für authentifizierte Nutzer an.
 *
 * Der Use Case bereinigt Texte, validiert Preisangaben und setzt Standardwerte wie
 * den Startstatus `ACTIVE`.
 *
 * @param listings Persistenz-Port für Anzeigen.
 */
class CreateListingUseCase(
    private val listings: ListingRepositoryPort,
    private val metrics: ListingMetricsPort,
    private val clock: ClockPort,
    private val ids: IdGeneratorPort
) {
    /**
     * Erstellt eine neue Anzeige.
     *
     * Die Besitzer-ID kommt aus dem geprüften Token und nicht aus dem Request-Body.
     *
     * @param currentUserId ID des authentifizierten Nutzers.
     * @param command Fachliche Eingabe zum Anlegen der Anzeige.
     * @return Gespeicherte Anzeige.
     * @throws ListingUseCaseValidationException Wenn Pflichtfelder oder Preisangaben ungültig sind.
     */
    suspend fun create(currentUserId: String, command: CreateListingCommand): Listing {
        val priceHint = validPrice(command.priceHint)

        val listing = Listing(
            id = ids.newId(),
            ownerUserId = requiredText(currentUserId, "currentUserId"),
            title = requiredText(command.title, "title"),
            description = requiredText(command.description, "description"),
            priceHint = priceHint,
            location = optionalText(command.location),
            cameraModel = optionalText(command.cameraModel),
            lensModel = optionalText(command.lensModel),
            tags = cleanOptionalTexts(command.tags),
            imageIds = emptyList(),
            status = LISTING_STATUS_ACTIVE,
            createdAt = clock.now()
        )

        val saved = listings.saveWithEvent(listing, ids.newId())
        metrics.listingCreated()
        return saved
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
