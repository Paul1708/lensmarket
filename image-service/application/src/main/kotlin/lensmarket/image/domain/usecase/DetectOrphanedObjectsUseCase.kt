package lensmarket.image.domain.usecase

import lensmarket.image.domain.port.ImageMetricsPort
import lensmarket.image.domain.port.ImageRepositoryPort
import lensmarket.image.domain.port.ObjectStoragePort

/** Erkennt Storage-Objekte, die von keinem Bilddatensatz mehr referenziert werden. */
class DetectOrphanedObjectsUseCase(
    private val images: ImageRepositoryPort,
    private val storage: ObjectStoragePort,
    private val metrics: ImageMetricsPort
) {
    suspend fun detect(): Set<String> {
        val orphaned = storage.listObjectKeys("watermarked/") - images.listStoredObjectKeys()
        metrics.orphanedObjects(orphaned.size)
        return orphaned
    }
}
