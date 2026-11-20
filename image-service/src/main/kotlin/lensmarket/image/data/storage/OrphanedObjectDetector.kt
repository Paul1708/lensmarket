package lensmarket.image.data.storage

import io.quarkus.scheduler.Scheduled
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.image.domain.usecase.DetectOrphanedObjectsUseCase
import lensmarket.image.runtime.suspendToUni
import org.jboss.logging.Logger

/** Periodischer, nicht-destruktiver Abgleich von MinIO und Bildmetadaten. */
@ApplicationScoped
class OrphanedObjectDetector(
    private val detectOrphanedObjects: DetectOrphanedObjectsUseCase
) {
    private val logger = Logger.getLogger(OrphanedObjectDetector::class.java)

    @Scheduled(
        every = "{lensmarket.storage.orphan-scan.interval:1m}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    fun scan(): Uni<Void> = suspendToUni {
        val orphaned = detectOrphanedObjects.detect()
        if (orphaned.isNotEmpty()) {
            logger.warn("Detected ${orphaned.size} orphaned image objects: ${orphaned.sorted().joinToString()}")
        }
    }.replaceWithVoid()
}
