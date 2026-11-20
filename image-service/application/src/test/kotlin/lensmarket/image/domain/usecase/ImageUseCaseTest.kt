package lensmarket.image.domain.usecase

import kotlinx.coroutines.test.runTest
import lensmarket.image.domain.model.ChangeImageStatusCommand
import lensmarket.image.domain.model.Image
import lensmarket.image.domain.model.ImageSearchFilter
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
import lensmarket.image.domain.port.WatermarkResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.time.Instant

class ImageUseCaseTest {
    @Test
    fun `upload verifies ownership stores only watermarked copy and publishes`() = runTest {
        val f = Fixture(owner = true)
        val image = f.upload.upload("owner", uploadCommand())
        assertEquals("image-1", image.id)
        assertEquals("WATERMARKED", image.status)
        assertEquals(null, image.originalPath)
        assertEquals(listOf("watermarked/image-1.jpg"), f.storage.putKeys)
        assertEquals(listOf(image.id), f.repository.outboxImages)
        assertEquals(1, f.metrics.registered)
        assertEquals(1, f.metrics.watermarked)
    }

    @Test
    fun `upload validates file and ownership`() = runTest {
        val forbidden = Fixture(owner = false)
        expectFailure<ImageUseCaseForbiddenException> { forbidden.upload.upload("owner", uploadCommand()) }
        val allowed = Fixture(owner = true)
        expectFailure<ImageUseCaseValidationException> {
            allowed.upload.upload("owner", uploadCommand().copy(mimeType = "text/plain"))
        }
    }

    @Test
    fun `upload rejects empty and oversized files`() = runTest {
        val f = Fixture(owner = true)
        expectFailure<ImageUseCaseValidationException> {
            f.upload.upload("owner", uploadCommand().copy(fileBytes = byteArrayOf()))
        }
        expectFailure<ImageUseCaseValidationException> {
            f.upload.upload("owner", uploadCommand().copy(fileBytes = ByteArray(MAX_FILE_SIZE_BYTES + 1)))
        }
    }

    @Test
    fun `upload maps undecodable image content to validation error`() = runTest {
        val f = Fixture(owner = true, watermark = WatermarkPort { _, _ ->
            throw InvalidImageContentException("file content is not a decodable JPEG or PNG image")
        })
        expectFailure<ImageUseCaseValidationException> { f.upload.upload("owner", uploadCommand()) }
    }

    @Test
    fun `status changes only for owner`() = runTest {
        val f = Fixture(owner = true)
        val image = f.upload.upload("owner", uploadCommand())
        val archived = ChangeImageStatusUseCase(f.repository)
            .changeStatus(image.id, "owner", ChangeImageStatusCommand("archived"))
        assertEquals("ARCHIVED", archived.status)
        expectFailure<ImageUseCaseForbiddenException> {
            ChangeImageStatusUseCase(f.repository).changeStatus(image.id, "other", ChangeImageStatusCommand("WATERMARKED"))
        }
    }

    @Test
    fun `status change is idempotent when the value is unchanged`() = runTest {
        val f = Fixture(owner = true)
        val image = f.upload.upload("owner", uploadCommand())
        val unchanged = ChangeImageStatusUseCase(f.repository)
            .changeStatus(image.id, "owner", ChangeImageStatusCommand(" watermarked "))
        assertEquals(image, unchanged)
        assertEquals(0, f.repository.statusUpdates)
    }

    @Test
    fun `public and owner views separate archived images`() = runTest {
        val f = Fixture(owner = true)
        val image = f.upload.upload("owner", uploadCommand())
        assertEquals(listOf(image), ListPublicImagesUseCase(f.repository).list(ImageSearchFilter(listingId = "listing")))
        assertEquals(image, GetPublicImageUseCase(f.repository).get(image.id))
        val file = ServePublicWatermarkedImageUseCase(f.repository, f.storage).serve(image.id)
        assertArrayEquals("stored".toByteArray(), file.bytes)

        ChangeImageStatusUseCase(f.repository).changeStatus(image.id, "owner", ChangeImageStatusCommand("ARCHIVED"))
        assertTrue(ListPublicImagesUseCase(f.repository).list().isEmpty())
        expectFailure<ImageUseCaseNotFoundException> { GetPublicImageUseCase(f.repository).get(image.id) }
        expectFailure<ImageUseCaseNotFoundException> {
            ServePublicWatermarkedImageUseCase(f.repository, f.storage).serve(image.id)
        }
        assertEquals(
            "ARCHIVED",
            GetOwnImageUseCase(f.repository).get(image.id, "owner").status
        )
        assertEquals(
            listOf("ARCHIVED"),
            ListOwnImagesUseCase(f.repository).list("owner").map { it.status }
        )
        assertArrayEquals(
            "stored".toByteArray(),
            ServeOwnWatermarkedImageUseCase(f.repository, f.storage).serve(image.id, "owner").bytes
        )
    }

    @Test
    fun `list normalizes whitespace and empty filters`() = runTest {
        val f = Fixture(owner = true)
        f.upload.upload("owner", uploadCommand())
        ListOwnImagesUseCase(f.repository).list(
            "owner",
            ImageSearchFilter(listingId = " listing ", ownerUserId = " untrusted ", status = " archived ")
        )
        assertEquals(
            ImageSearchFilter(listingId = "listing", ownerUserId = "owner", status = "ARCHIVED"),
            f.repository.lastFilter
        )
    }

    @Test
    fun `listing deletion removes objects and metadata and records consumer result`() = runTest {
        val f = Fixture(owner = true)
        val image = f.upload.upload("owner", uploadCommand())
        val occurredAt = Instant.parse("2026-07-13T10:00:00Z")
        val useCase = DeleteImagesForListingUseCase(f.repository, f.storage, f.metrics)
        val deleted = useCase.deleteForListing("event-delete", occurredAt, "listing")
        assertEquals(1, deleted)
        assertEquals(listOf(image.watermarkedPath), f.storage.deletedKeys)
        assertEquals(null, f.repository.findImageById(image.id))
        assertEquals(listOf("listing-deleted:images_deleted"), f.metrics.consumed)
        assertEquals(0, useCase.deleteForListing("event-delete", occurredAt, "listing"))
        assertEquals("listing-deleted:duplicate", f.metrics.consumed.last())
    }

    @Test
    fun `upload compensates stored object when database transaction fails`() = runTest {
        val f = Fixture(owner = true)
        f.repository.failSave = true
        expectFailure<IllegalStateException> { f.upload.upload("owner", uploadCommand()) }
        assertEquals(listOf("watermarked/image-1.jpg"), f.storage.deletedKeys)
    }

    @Test
    fun `failed storage deletion preserves metadata for replay`() = runTest {
        val f = Fixture(owner = true)
        val image = f.upload.upload("owner", uploadCommand())
        f.storage.failDelete = true
        expectFailure<IllegalStateException> {
            DeleteImagesForListingUseCase(f.repository, f.storage, f.metrics).deleteForListing(
                "event-delete", Instant.parse("2026-07-13T10:00:00Z"), "listing"
            )
        }
        assertEquals(image, f.repository.findImageById(image.id))
        assertTrue(!f.repository.isEventProcessed("event-delete"))
    }

    @Test
    fun `orphan detector reports unreferenced storage keys`() = runTest {
        val f = Fixture(owner = true)
        f.upload.upload("owner", uploadCommand())
        f.storage.externalKeys += "watermarked/orphan.jpg"
        val orphaned = DetectOrphanedObjectsUseCase(f.repository, f.storage, f.metrics).detect()
        assertEquals(setOf("watermarked/orphan.jpg"), orphaned)
        assertEquals(1, f.metrics.orphaned)
    }

    @Test
    fun `missing image is a typed application error`() = runTest {
        val f = Fixture(owner = true)
        expectFailure<ImageUseCaseNotFoundException> { GetPublicImageUseCase(f.repository).get("missing") }
    }

    private class Fixture(
        owner: Boolean,
        watermark: WatermarkPort = WatermarkPort { _, _ -> WatermarkResult("marked".toByteArray(), 640, 480) }
    ) {
        val repository = InMemoryImages()
        val storage = RecordingStorage()
        val metrics = RecordingMetrics()
        private var idIndex = 0
        private val generatedIds = listOf("image-1", "event-uploaded", "event-watermarked")
        val upload = UploadImageUseCase(
            repository,
            ListingOwnershipPort { _, _ -> owner },
            watermark,
            storage,
            metrics,
            ClockPort { Instant.parse("2026-07-13T10:00:00Z") },
            IdGeneratorPort { generatedIds[idIndex++] }
        )
    }

    private class InMemoryImages : ImageRepositoryPort {
        private val images = linkedMapOf<String, Image>()
        private val processedEvents = mutableSetOf<String>()
        val outboxImages = mutableListOf<String>()
        var failSave = false
        var lastFilter: ImageSearchFilter? = null
        var statusUpdates = 0
        override suspend fun save(image: Image): Image = image.also { images[it.id] = it }
        override suspend fun saveWithEvents(
            image: Image, uploadedEventId: String, watermarkedEventId: String
        ): Image {
            if (failSave) throw IllegalStateException("database unavailable")
            return save(image).also { outboxImages += image.id }
        }
        override suspend fun listImages(filter: ImageSearchFilter): List<Image> {
            lastFilter = filter
            return images.values.filter {
                (filter.listingId == null || it.listingId == filter.listingId) &&
                    (filter.ownerUserId == null || it.ownerUserId == filter.ownerUserId) &&
                    (filter.status == null || it.status == filter.status)
            }
        }
        override suspend fun findImageById(id: String): Image? = images[id]
        override suspend fun updateStatus(imageId: String, status: String): Image? =
            images[imageId]?.copy(status = status)?.also { statusUpdates++; images[imageId] = it }
        override suspend fun deleteImage(id: String): Boolean = images.remove(id) != null
        override suspend fun listStoredObjectKeys(): Set<String> = images.values.mapNotNull { it.watermarkedPath }.toSet()
        override suspend fun isEventProcessed(eventId: String): Boolean = eventId in processedEvents
        override suspend fun markListingDeletedEventProcessed(eventId: String, listingId: String, processedAt: Instant) {
            processedEvents += eventId
        }
    }

    private class RecordingStorage : ObjectStoragePort {
        val putKeys = mutableListOf<String>()
        val deletedKeys = mutableListOf<String>()
        val externalKeys = mutableSetOf<String>()
        var failDelete = false
        override suspend fun putObject(key: String, bytes: ByteArray, contentType: String) { putKeys += key }
        override suspend fun getObject(key: String): ByteArray = "stored".toByteArray()
        override suspend fun deleteObject(key: String) {
            if (failDelete) throw IllegalStateException("storage unavailable")
            deletedKeys += key
        }
        override suspend fun listObjectKeys(prefix: String): Set<String> =
            (putKeys + externalKeys).filter { it.startsWith(prefix) }.toSet()
    }

    private class RecordingMetrics : ImageMetricsPort {
        var registered = 0; var watermarked = 0
        var orphaned = 0
        val consumed = mutableListOf<String>()
        override fun imageRegistered() { registered++ }
        override fun imageWatermarked() { watermarked++ }
        override fun imagesDeleted(count: Int) = Unit
        override fun eventConsumed(eventType: String, result: String) { consumed += "$eventType:$result" }
        override fun orphanedObjects(count: Int) { orphaned = count }
    }

    private fun uploadCommand() = UploadImageCommand(
        listingId = "listing",
        fileName = "photo.jpg",
        mimeType = "image/jpeg",
        fileBytes = "raw".toByteArray()
    )

    private suspend inline fun <reified T : Throwable> expectFailure(noinline block: suspend () -> Unit) {
        try { block(); fail("Expected ${T::class.simpleName}") } catch (failure: Throwable) {
            assertTrue(failure is T, "Expected ${T::class.simpleName}, got ${failure::class.simpleName}")
        }
    }
}
