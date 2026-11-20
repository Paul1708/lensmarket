package lensmarket.listing.domain.usecase

import lensmarket.listing.domain.model.LISTING_STATUSES
import lensmarket.listing.domain.model.LISTING_STATUS_ACTIVE
import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.model.ListingSearchFilter
import lensmarket.listing.domain.model.MAX_LISTING_SEARCH_LIMIT
import lensmarket.listing.domain.port.ListingSearchPort
import java.math.BigDecimal

/**
 * Öffentliche Suche. Unabhängig von den übergebenen Filtern können nur aktive
 * Anzeigen die öffentliche API-Grenze passieren.
 */
class SearchPublicListingsUseCase(
    private val listings: ListingSearchPort
) {
    suspend fun search(filter: ListingSearchFilter): List<Listing> {
        val validated = validateFilter(filter)
        return listings.search(validated.copy(status = LISTING_STATUS_ACTIVE))
    }
}

/** Geschützte Suche für die eigenen, nicht gelöschten Anzeigen in allen Status. */
class SearchOwnListingsUseCase(
    private val listings: ListingSearchPort
) {
    suspend fun search(currentUserId: String, filter: ListingSearchFilter): List<Listing> {
        val ownerUserId = requiredText(currentUserId, "currentUserId")
        val validated = validateFilter(filter)
        return listings.search(validated.copy(ownerUserId = ownerUserId))
    }
}

private fun validateFilter(filter: ListingSearchFilter): ListingSearchFilter {
    val minPrice = validPrice(filter.minPrice, "minPrice")
    val maxPrice = validPrice(filter.maxPrice, "maxPrice")
    if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
        throw ListingUseCaseValidationException("minPrice must not be greater than maxPrice")
    }

    return filter.copy(
        query = optionalText(filter.query),
        ownerUserId = optionalText(filter.ownerUserId),
        status = validStatus(filter.status),
        tag = optionalText(filter.tag),
        location = optionalText(filter.location),
        minPrice = minPrice,
        maxPrice = maxPrice,
        limit = validLimit(filter.limit),
        offset = validOffset(filter.offset)
    )
}

private fun requiredText(value: String, fieldName: String): String =
    value.trim().also {
        if (it.isEmpty()) throw ListingUseCaseValidationException("$fieldName must not be blank")
    }

private fun optionalText(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }

private fun validStatus(value: String?): String? {
    val status = optionalText(value)?.uppercase() ?: return null
    if (status !in LISTING_STATUSES) {
        throw ListingUseCaseValidationException("status must be one of ${LISTING_STATUSES.joinToString()}")
    }
    return status
}

private fun validPrice(value: BigDecimal?, fieldName: String): BigDecimal? {
    if (value != null && value < BigDecimal.ZERO) {
        throw ListingUseCaseValidationException("$fieldName must not be negative")
    }
    return value
}

private fun validLimit(value: Int): Int {
    if (value <= 0) {
        throw ListingUseCaseValidationException("limit must be greater than zero")
    }
    if (value > MAX_LISTING_SEARCH_LIMIT) {
        throw ListingUseCaseValidationException("limit must not be greater than $MAX_LISTING_SEARCH_LIMIT")
    }
    return value
}

private fun validOffset(value: Int): Int {
    if (value < 0) {
        throw ListingUseCaseValidationException("offset must not be negative")
    }
    return value
}
