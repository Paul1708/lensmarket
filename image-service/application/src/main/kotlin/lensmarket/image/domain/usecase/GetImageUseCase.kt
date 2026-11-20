package lensmarket.image.domain.usecase

import lensmarket.image.domain.model.IMAGE_STATUS_WATERMARKED
import lensmarket.image.domain.model.Image
import lensmarket.image.domain.port.ImageRepositoryPort

/** Lädt ausschließlich öffentlich sichtbare, nicht archivierte Bildmetadaten. */
class GetPublicImageUseCase(private val images: ImageRepositoryPort) {
    suspend fun get(id: String): Image {
        val imageId = requiredGetText(id, "id")
        return images.findImageById(imageId)?.takeIf { it.status == IMAGE_STATUS_WATERMARKED }
            ?: throw ImageUseCaseNotFoundException("Image $imageId not found")
    }
}

/** Lädt ein eigenes Bild unabhängig von seinem Status über die Verwaltungssicht. */
class GetOwnImageUseCase(private val images: ImageRepositoryPort) {
    suspend fun get(id: String, currentUserId: String): Image {
        val imageId = requiredGetText(id, "id")
        val userId = requiredGetText(currentUserId, "currentUserId")
        val image = images.findImageById(imageId)
            ?: throw ImageUseCaseNotFoundException("Image $imageId not found")
        if (image.ownerUserId != userId) {
            throw ImageUseCaseForbiddenException("Only the image owner can view this image")
        }
        return image
    }
}

private fun requiredGetText(value: String, fieldName: String): String = value.trim().also {
    if (it.isEmpty()) throw ImageUseCaseValidationException("$fieldName must not be blank")
}
