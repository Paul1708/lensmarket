package lensmarket.image.domain.usecase

import lensmarket.image.domain.model.ALLOWED_IMAGE_STATUSES
import lensmarket.image.domain.model.IMAGE_STATUS_WATERMARKED
import lensmarket.image.domain.model.Image
import lensmarket.image.domain.model.ImageSearchFilter
import lensmarket.image.domain.port.ImageRepositoryPort

/** Öffentliche Bildsuche; archivierte Bilder können diese Grenze nie passieren. */
class ListPublicImagesUseCase(private val images: ImageRepositoryPort) {
    suspend fun list(filter: ImageSearchFilter = ImageSearchFilter()): List<Image> {
        val validated = validateImageFilter(filter)
        return images.listImages(validated.copy(status = IMAGE_STATUS_WATERMARKED))
    }
}

/** Geschützte Verwaltungssicht auf die eigenen Bilder in allen Status. */
class ListOwnImagesUseCase(private val images: ImageRepositoryPort) {
    suspend fun list(currentUserId: String, filter: ImageSearchFilter = ImageSearchFilter()): List<Image> {
        val ownerUserId = requiredImageText(currentUserId, "currentUserId")
        val validated = validateImageFilter(filter)
        return images.listImages(validated.copy(ownerUserId = ownerUserId))
    }
}

private fun validateImageFilter(filter: ImageSearchFilter): ImageSearchFilter =
    ImageSearchFilter(
        listingId = optionalImageText(filter.listingId),
        ownerUserId = optionalImageText(filter.ownerUserId),
        status = validImageStatus(filter.status)
    )

private fun validImageStatus(value: String?): String? {
    val status = optionalImageText(value)?.uppercase() ?: return null
    if (status !in ALLOWED_IMAGE_STATUSES) {
        throw ImageUseCaseValidationException("status must be one of $ALLOWED_IMAGE_STATUSES")
    }
    return status
}

private fun optionalImageText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

private fun requiredImageText(value: String, fieldName: String): String = value.trim().also {
    if (it.isEmpty()) throw ImageUseCaseValidationException("$fieldName must not be blank")
}
