package lensmarket.image.domain.port

import lensmarket.image.domain.model.Image
import java.time.Instant

data class AuthenticatedUser(val id: String, val email: String, val role: String)
data class WatermarkResult(val bytes: ByteArray, val width: Int, val height: Int)

/** Signalisiert, dass Uploadbytes nicht als unterstütztes Bild dekodiert werden können. */
class InvalidImageContentException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface TokenVerifierPort { fun verify(token: String): AuthenticatedUser? }
fun interface WatermarkPort { suspend fun applyWatermark(imageBytes: ByteArray, mimeType: String): WatermarkResult }
interface ImageMetricsPort {
    fun imageRegistered()
    fun imageWatermarked()
    fun imagesDeleted(count: Int)
    fun eventConsumed(eventType: String, result: String)
    fun orphanedObjects(count: Int)
}
fun interface ClockPort { fun now(): Instant }
fun interface IdGeneratorPort { fun newId(): String }
