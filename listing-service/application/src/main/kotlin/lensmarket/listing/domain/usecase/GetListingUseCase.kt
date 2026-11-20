package lensmarket.listing.domain.usecase

import lensmarket.listing.domain.model.LISTING_STATUS_ACTIVE
import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.port.ListingRepositoryPort

/** Lädt ausschließlich öffentlich sichtbare, aktive Anzeigen. */
class GetPublicListingUseCase(
    private val listings: ListingRepositoryPort
) {
    suspend fun get(id: String): Listing {
        val listingId = requiredListingText(id, "id")
        return listings.findListingById(listingId)?.takeIf { it.status == LISTING_STATUS_ACTIVE }
            ?: throw ListingUseCaseNotFoundException("Listing $listingId not found")
    }
}

/** Lädt eine eigene Anzeige unabhängig von ihrem Status über eine geschützte Grenze. */
class GetOwnListingUseCase(
    private val listings: ListingRepositoryPort
) {
    suspend fun get(id: String, currentUserId: String): Listing {
        val listingId = requiredListingText(id, "id")
        val userId = requiredListingText(currentUserId, "currentUserId")
        val listing = listings.findListingById(listingId)
            ?: throw ListingUseCaseNotFoundException("Listing $listingId not found")
        if (listing.ownerUserId != userId) {
            throw ListingUseCaseForbiddenException("Only the listing owner can view this listing")
        }
        return listing
    }
}

private fun requiredListingText(value: String, fieldName: String): String = value.trim().also {
    if (it.isEmpty()) throw ListingUseCaseValidationException("$fieldName must not be blank")
}
