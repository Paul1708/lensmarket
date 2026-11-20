package lensmarket.image.domain.usecase

import lensmarket.image.domain.model.ALLOWED_IMAGE_STATUSES
import lensmarket.image.domain.model.ChangeImageStatusCommand
import lensmarket.image.domain.model.Image
import lensmarket.image.domain.port.ImageRepositoryPort

class ChangeImageStatusUseCase(private val images: ImageRepositoryPort) {
    suspend fun changeStatus(imageId: String, currentUserId: String, command: ChangeImageStatusCommand): Image {
        val id = imageId.trim()
        val userId = currentUserId.trim()
        val status = command.status.trim().uppercase()
        if (status !in ALLOWED_IMAGE_STATUSES) {
            throw ImageUseCaseValidationException("status must be one of $ALLOWED_IMAGE_STATUSES")
        }
        val image = images.findImageById(id) ?: throw ImageUseCaseNotFoundException("Image $id not found")
        if (image.ownerUserId != userId) {
            throw ImageUseCaseForbiddenException("Only the image owner can change its status")
        }
        if (image.status == status) return image
        return images.updateStatus(id, status) ?: throw ImageUseCaseNotFoundException("Image $id not found")
    }
}
