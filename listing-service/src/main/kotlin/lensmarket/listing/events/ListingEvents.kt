package lensmarket.listing.events

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer
import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.listing.data.repository.OutboxEventRecord
import lensmarket.listing.domain.model.Listing
import org.eclipse.microprofile.reactive.messaging.Channel
import java.time.Instant

const val EVENT_VERSION = 1
const val LISTING_CREATED = "listing-created"
const val LISTING_STATUS_CHANGED = "listing-status-changed"
const val LISTING_DELETED = "listing-deleted"

data class ListingCreatedEvent(
    val eventId: String,
    val eventType: String,
    val version: Int,
    val occurredAt: Instant,
    val listingId: String,
    val ownerUserId: String,
    val status: String
)

data class ListingStatusChangedEvent(
    val eventId: String,
    val eventType: String,
    val version: Int,
    val occurredAt: Instant,
    val listingId: String,
    val ownerUserId: String,
    val oldStatus: String,
    val newStatus: String
)

data class ListingDeletedEvent(
    val eventId: String,
    val eventType: String,
    val version: Int,
    val occurredAt: Instant,
    val listingId: String,
    val ownerUserId: String
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

class ImageWatermarkedEventDeserializer :
    ObjectMapperDeserializer<ImageWatermarkedEvent>(ImageWatermarkedEvent::class.java)

/** Publiziert ausschließlich bereits committed Outbox-Nutzlasten. */
@ApplicationScoped
class KafkaOutboxPublisher(
    @param:Channel("listing-created-out")
    private val listingCreatedEvents: MutinyEmitter<ListingCreatedEvent>,
    @param:Channel("listing-status-changed-out")
    private val listingStatusChangedEvents: MutinyEmitter<ListingStatusChangedEvent>,
    @param:Channel("listing-deleted-out")
    private val listingDeletedEvents: MutinyEmitter<ListingDeletedEvent>,
    private val objectMapper: ObjectMapper
) : OutboxPublisher {
    override fun publish(event: OutboxEventRecord): Uni<Void> =
        when (event.eventType) {
            LISTING_CREATED -> listingCreatedEvents.send(
                objectMapper.readValue(event.payload, ListingCreatedEvent::class.java)
            )
            LISTING_STATUS_CHANGED -> listingStatusChangedEvents.send(
                objectMapper.readValue(event.payload, ListingStatusChangedEvent::class.java)
            )
            LISTING_DELETED -> listingDeletedEvents.send(
                objectMapper.readValue(event.payload, ListingDeletedEvent::class.java)
            )
            else -> Uni.createFrom().failure(IllegalArgumentException("Unsupported listing outbox event ${event.eventType}"))
        }
}

fun Listing.toListingCreatedEvent(eventId: String): ListingCreatedEvent = ListingCreatedEvent(
    eventId = eventId,
    eventType = LISTING_CREATED,
    version = EVENT_VERSION,
    occurredAt = createdAt,
    listingId = id,
    ownerUserId = ownerUserId,
    status = status
)

fun Listing.toListingStatusChangedEvent(
    eventId: String,
    occurredAt: Instant,
    oldStatus: String,
    newStatus: String
): ListingStatusChangedEvent = ListingStatusChangedEvent(
    eventId = eventId,
    eventType = LISTING_STATUS_CHANGED,
    version = EVENT_VERSION,
    occurredAt = occurredAt,
    listingId = id,
    ownerUserId = ownerUserId,
    oldStatus = oldStatus,
    newStatus = newStatus
)

fun Listing.toListingDeletedEvent(eventId: String): ListingDeletedEvent = ListingDeletedEvent(
    eventId = eventId,
    eventType = LISTING_DELETED,
    version = EVENT_VERSION,
    occurredAt = deletedAt ?: error("Deleted listing $id has no deletedAt"),
    listingId = id,
    ownerUserId = ownerUserId
)
