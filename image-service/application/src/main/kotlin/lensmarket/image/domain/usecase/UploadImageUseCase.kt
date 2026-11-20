package lensmarket.image.domain.usecase

import lensmarket.image.domain.model.ALLOWED_MIME_TYPES
import lensmarket.image.domain.model.IMAGE_STATUS_WATERMARKED
import lensmarket.image.domain.model.Image
import lensmarket.image.domain.model.MAX_FILE_SIZE_BYTES
import lensmarket.image.domain.model.UploadImageCommand
import lensmarket.image.domain.port.ClockPort
import lensmarket.image.domain.port.IdGeneratorPort
import lensmarket.image.domain.port.ImageMetricsPort
import lensmarket.image.domain.port.ImageRepositoryPort
import lensmarket.image.domain.port.InvalidImageContentException
import lensmarket.image.domain.port.ListingOwnershipPort
import lensmarket.image.domain.port.ObjectStoragePort
import lensmarket.image.domain.port.WatermarkPort

class UploadImageUseCase(
    private val images: ImageRepositoryPort,
    private val listingOwnership: ListingOwnershipPort,
    private val watermark: WatermarkPort,
    private val storage: ObjectStoragePort,
    private val metrics: ImageMetricsPort,
    private val clock: ClockPort,
    private val ids: IdGeneratorPort
) {
    suspend fun upload(currentUserId: String, command: UploadImageCommand): Image {
        validate(command)
        val ownerUserId = requiredText(currentUserId, "currentUserId")
        if (!listingOwnership.isListingOwner(command.listingId, ownerUserId)) {
            throw ImageUseCaseForbiddenException("Only the listing owner can upload images")
        }
        val id = ids.newId()
        val extension = if (command.mimeType == "image/png") "png" else "jpg"
        val objectKey = "watermarked/$id.$extension"
        val result = try {
            watermark.applyWatermark(command.fileBytes, command.mimeType)
        } catch (exception: InvalidImageContentException) {
            throw ImageUseCaseValidationException(exception.message ?: "file content is not a decodable image")
        }
        storage.putObject(objectKey, result.bytes, command.mimeType)
        val image = Image(
            id = id,
            listingId = command.listingId,
            ownerUserId = ownerUserId,
            originalFileName = command.fileName,
            mimeType = command.mimeType,
            width = result.width,
            height = result.height,
            status = IMAGE_STATUS_WATERMARKED,
            watermarkedUrl = "/images/$id/watermarked",
            originalPath = null,
            watermarkedPath = objectKey,
            createdAt = clock.now()
        )
        val saved = try {
            images.saveWithEvents(image, ids.newId(), ids.newId())
        } catch (failure: Throwable) {
            try {
                storage.deleteObject(objectKey)
            } catch (compensationFailure: Throwable) {
                failure.addSuppressed(compensationFailure)
            }
            throw failure
        }
        metrics.imageRegistered()
        metrics.imageWatermarked()
        return saved
    }

    private fun validate(command: UploadImageCommand) {
        if (command.listingId.isBlank()) throw ImageUseCaseValidationException("listingId must not be blank")
        if (command.fileName.isBlank()) throw ImageUseCaseValidationException("fileName must not be blank")
        if (command.mimeType !in ALLOWED_MIME_TYPES) {
            throw ImageUseCaseValidationException("mimeType must be one of $ALLOWED_MIME_TYPES")
        }
        if (command.fileBytes.isEmpty()) throw ImageUseCaseValidationException("file must not be empty")
        if (command.fileBytes.size > MAX_FILE_SIZE_BYTES) {
            throw ImageUseCaseValidationException("file exceeds maximum size of ${MAX_FILE_SIZE_BYTES / 1024 / 1024} MB")
        }
    }

    private fun requiredText(value: String, fieldName: String): String = value.trim().also {
        if (it.isEmpty()) throw ImageUseCaseValidationException("$fieldName must not be blank")
    }
}
