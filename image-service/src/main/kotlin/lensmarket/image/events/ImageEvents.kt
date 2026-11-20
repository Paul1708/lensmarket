package lensmarket.image.events

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer
import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.image.data.repository.OutboxEventRecord
import lensmarket.image.domain.model.Image
import org.eclipse.microprofile.reactive.messaging.Channel
import java.time.Instant

const val EVENT_VERSION = 1
const val IMAGE_UPLOADED = "image-uploaded"
const val IMAGE_WATERMARKED = "image-watermarked"
const val LISTING_DELETED = "listing-deleted"

data class ImageUploadedEvent(
    val eventId: String,
    val eventType: String,
    val version: Int,
    val occurredAt: Instant,
    val imageId: String,
    val listingId: String,
    val ownerUserId: String,
    val originalFileName: String,
    val mimeType: String
)

data class ImageWatermarkedEvent(
    val eventId: String,
    val eventType: String,
    val version: Int,
    val occurredAt: Instant,
    val imageId: String,
    val listingId: String,
    val ownerUserId: String,
    val watermarkedUrl: String
)

data class ListingDeletedEvent(
    val eventId: String,
    val eventType: String,
    val version: Int,
    val occurredAt: Instant,
    val listingId: String,
    val ownerUserId: String
)

class ListingDeletedEventDeserializer :
    ObjectMapperDeserializer<ListingDeletedEvent>(ListingDeletedEvent::class.java)

/** Publiziert ausschließlich bereits committed Outbox-Nutzlasten. */
@ApplicationScoped
class KafkaOutboxPublisher(
    @param:Channel("image-uploaded-out")
    private val imageUploadedEvents: MutinyEmitter<ImageUploadedEvent>,
    @param:Channel("image-watermarked-out")
    private val imageWatermarkedEvents: MutinyEmitter<ImageWatermarkedEvent>,
    private val objectMapper: ObjectMapper
) : OutboxPublisher {
    override fun publish(event: OutboxEventRecord): Uni<Void> =
        when (event.eventType) {
            IMAGE_UPLOADED -> imageUploadedEvents.send(
                objectMapper.readValue(event.payload, ImageUploadedEvent::class.java)
            )
            IMAGE_WATERMARKED -> imageWatermarkedEvents.send(
                objectMapper.readValue(event.payload, ImageWatermarkedEvent::class.java)
            )
            else -> Uni.createFrom().failure(IllegalArgumentException("Unsupported image outbox event ${event.eventType}"))
        }
}

fun Image.toImageUploadedEvent(eventId: String): ImageUploadedEvent = ImageUploadedEvent(
    eventId = eventId,
    eventType = IMAGE_UPLOADED,
    version = EVENT_VERSION,
    occurredAt = createdAt,
    imageId = id,
    listingId = listingId,
    ownerUserId = ownerUserId,
    originalFileName = originalFileName,
    mimeType = mimeType
)

fun Image.toImageWatermarkedEvent(eventId: String): ImageWatermarkedEvent = ImageWatermarkedEvent(
    eventId = eventId,
    eventType = IMAGE_WATERMARKED,
    version = EVENT_VERSION,
    occurredAt = createdAt,
    imageId = id,
    listingId = listingId,
    ownerUserId = ownerUserId,
    watermarkedUrl = watermarkedUrl
)
