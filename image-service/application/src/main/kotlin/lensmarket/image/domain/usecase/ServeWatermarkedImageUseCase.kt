package lensmarket.image.domain.usecase

import lensmarket.image.domain.model.IMAGE_STATUS_WATERMARKED
import lensmarket.image.domain.model.Image
import lensmarket.image.domain.port.ImageRepositoryPort
import lensmarket.image.domain.port.ObjectStoragePort

data class WatermarkedFile(val bytes: ByteArray, val mimeType: String, val fileName: String)

/** Liefert die Wasserzeichenkopie nur für öffentlich sichtbare Bilder aus. */
class ServePublicWatermarkedImageUseCase(
    private val images: ImageRepositoryPort,
    private val storage: ObjectStoragePort
) {
    suspend fun serve(id: String): WatermarkedFile {
        val image = findImage(id).takeIf { it.status == IMAGE_STATUS_WATERMARKED }
            ?: throw ImageUseCaseNotFoundException("Image ${id.trim()} not found")
        return image.toFile(storage)
    }

    private suspend fun findImage(id: String): Image {
        val imageId = requiredServeText(id, "id")
        return images.findImageById(imageId)
            ?: throw ImageUseCaseNotFoundException("Image $imageId not found")
    }
}

/** Liefert auch archivierte Wasserzeichenkopien ausschließlich an deren Besitzer aus. */
class ServeOwnWatermarkedImageUseCase(
    private val images: ImageRepositoryPort,
    private val storage: ObjectStoragePort
) {
    suspend fun serve(id: String, currentUserId: String): WatermarkedFile {
        val imageId = requiredServeText(id, "id")
        val userId = requiredServeText(currentUserId, "currentUserId")
        val image = images.findImageById(imageId)
            ?: throw ImageUseCaseNotFoundException("Image $imageId not found")
        if (image.ownerUserId != userId) {
            throw ImageUseCaseForbiddenException("Only the image owner can view this image")
        }
        return image.toFile(storage)
    }
}

private suspend fun Image.toFile(storage: ObjectStoragePort): WatermarkedFile {
    val key = watermarkedPath
        ?: throw ImageUseCaseValidationException("image $id has no uploaded file — use the metadata endpoint")
    return WatermarkedFile(storage.getObject(key), mimeType, "watermarked_$originalFileName")
}

private fun requiredServeText(value: String, fieldName: String): String = value.trim().also {
    if (it.isEmpty()) throw ImageUseCaseValidationException("$fieldName must not be blank")
}
