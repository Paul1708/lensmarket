package lensmarket.listing.domain.usecase

import lensmarket.listing.domain.port.EventApplyResult
import lensmarket.listing.domain.port.ListingMetricsPort
import lensmarket.listing.domain.port.ListingRepositoryPort
import java.time.Instant

enum class AttachImageResult { ATTACHED, LISTING_NOT_FOUND, DUPLICATE }

class AttachImageToListingUseCase(
    private val listings: ListingRepositoryPort,
    private val metrics: ListingMetricsPort
) {
    suspend fun attach(eventId: String, occurredAt: Instant, listingId: String, imageId: String): AttachImageResult {
        val result = when (listings.appendImageIdOnce(eventId, occurredAt, listingId, imageId)) {
            EventApplyResult.APPLIED -> AttachImageResult.ATTACHED
            EventApplyResult.AGGREGATE_NOT_FOUND -> AttachImageResult.LISTING_NOT_FOUND
            EventApplyResult.DUPLICATE -> AttachImageResult.DUPLICATE
        }
        metrics.eventConsumed(
            "image-watermarked",
            when (result) {
                AttachImageResult.ATTACHED -> "attached"
                AttachImageResult.LISTING_NOT_FOUND -> "listing_not_found"
                AttachImageResult.DUPLICATE -> "duplicate"
            }
        )
        return result
    }
}
