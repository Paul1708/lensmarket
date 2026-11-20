package lensmarket.image.composition

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import lensmarket.image.domain.port.ClockPort
import lensmarket.image.domain.port.IdGeneratorPort
import lensmarket.image.domain.port.ImageMetricsPort
import lensmarket.image.domain.port.ImageRepositoryPort
import lensmarket.image.domain.port.ListingOwnershipPort
import lensmarket.image.domain.port.ObjectStoragePort
import lensmarket.image.domain.port.TokenVerifierPort
import lensmarket.image.domain.port.WatermarkPort
import lensmarket.image.domain.usecase.AuthenticateUserUseCase
import lensmarket.image.domain.usecase.ChangeImageStatusUseCase
import lensmarket.image.domain.usecase.DeleteImagesForListingUseCase
import lensmarket.image.domain.usecase.DetectOrphanedObjectsUseCase
import lensmarket.image.domain.usecase.GetOwnImageUseCase
import lensmarket.image.domain.usecase.GetPublicImageUseCase
import lensmarket.image.domain.usecase.ListOwnImagesUseCase
import lensmarket.image.domain.usecase.ListPublicImagesUseCase
import lensmarket.image.domain.usecase.ServeOwnWatermarkedImageUseCase
import lensmarket.image.domain.usecase.ServePublicWatermarkedImageUseCase
import lensmarket.image.domain.usecase.UploadImageUseCase
import java.time.Instant
import java.util.UUID

@ApplicationScoped class SystemClockAdapter : ClockPort { override fun now(): Instant = Instant.now() }
@ApplicationScoped class UuidGeneratorAdapter : IdGeneratorPort { override fun newId(): String = UUID.randomUUID().toString() }

@ApplicationScoped
class ImageApplicationModule {
    @Produces @Singleton fun authenticate(tokens: TokenVerifierPort) = AuthenticateUserUseCase(tokens)
    @Produces @Singleton fun upload(
        images: ImageRepositoryPort, ownership: ListingOwnershipPort, watermark: WatermarkPort,
        storage: ObjectStoragePort, metrics: ImageMetricsPort,
        clock: ClockPort, ids: IdGeneratorPort
    ) = UploadImageUseCase(images, ownership, watermark, storage, metrics, clock, ids)
    @Produces @Singleton fun listPublic(images: ImageRepositoryPort) = ListPublicImagesUseCase(images)
    @Produces @Singleton fun listOwn(images: ImageRepositoryPort) = ListOwnImagesUseCase(images)
    @Produces @Singleton fun getPublic(images: ImageRepositoryPort) = GetPublicImageUseCase(images)
    @Produces @Singleton fun getOwn(images: ImageRepositoryPort) = GetOwnImageUseCase(images)
    @Produces @Singleton fun status(images: ImageRepositoryPort) = ChangeImageStatusUseCase(images)
    @Produces @Singleton fun servePublic(images: ImageRepositoryPort, storage: ObjectStoragePort) =
        ServePublicWatermarkedImageUseCase(images, storage)
    @Produces @Singleton fun serveOwn(images: ImageRepositoryPort, storage: ObjectStoragePort) =
        ServeOwnWatermarkedImageUseCase(images, storage)
    @Produces @Singleton fun delete(
        images: ImageRepositoryPort, storage: ObjectStoragePort, metrics: ImageMetricsPort
    ) = DeleteImagesForListingUseCase(images, storage, metrics)
    @Produces @Singleton fun detectOrphans(
        images: ImageRepositoryPort, storage: ObjectStoragePort, metrics: ImageMetricsPort
    ) = DetectOrphanedObjectsUseCase(images, storage, metrics)
}
