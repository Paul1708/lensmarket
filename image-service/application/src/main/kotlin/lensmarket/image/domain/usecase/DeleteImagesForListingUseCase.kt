package lensmarket.image.domain.usecase

import lensmarket.image.domain.model.Image
import lensmarket.image.domain.model.ImageSearchFilter
import lensmarket.image.domain.port.ImageMetricsPort
import lensmarket.image.domain.port.ImageRepositoryPort
import lensmarket.image.domain.port.ObjectStoragePort
import java.time.Instant

class DeleteImagesForListingUseCase(
    private val images: ImageRepositoryPort,
    private val storage: ObjectStoragePort,
    private val metrics: ImageMetricsPort
) {
    suspend fun deleteForListing(eventId: String, occurredAt: Instant, listingId: String): Int {
        val id = listingId.trim()
        if (id.isEmpty()) throw ImageUseCaseValidationException("listingId must not be blank")
        if (images.isEventProcessed(eventId)) {
            metrics.eventConsumed("listing-deleted", "duplicate")
            return 0
        }
        val matches = images.listImages(ImageSearchFilter(listingId = id))
        matches.forEach { deleteOne(it) }
        images.markListingDeletedEventProcessed(eventId, id, occurredAt)
        metrics.imagesDeleted(matches.size)
        metrics.eventConsumed("listing-deleted", if (matches.isEmpty()) "no_images_found" else "images_deleted")
        return matches.size
    }

    private suspend fun deleteOne(image: Image) {
        image.watermarkedPath?.let { storage.deleteObject(it) }
        images.deleteImage(image.id)
    }
}
