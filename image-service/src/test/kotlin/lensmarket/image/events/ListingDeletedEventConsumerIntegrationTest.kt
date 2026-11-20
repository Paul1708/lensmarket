package lensmarket.image.events

import io.quarkus.kafka.client.serialization.ObjectMapperSerializer
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.vertx.RunOnVertxContext
import io.quarkus.test.vertx.UniAsserter
import jakarta.inject.Inject
import lensmarket.image.data.repository.ImageRepository
import lensmarket.image.domain.model.Image
import lensmarket.image.domain.port.ObjectStoragePort
import lensmarket.image.domain.usecase.ImageUseCaseNotFoundException
import lensmarket.image.runtime.suspendToUni
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import java.util.function.Supplier

@QuarkusTest
class ListingDeletedEventConsumerIntegrationTest {
    @Inject
    lateinit var repository: ImageRepository

    @Inject
    lateinit var consumer: ListingDeletedEventConsumer

    @InjectMock
    lateinit var storage: ObjectStoragePort

    @Test
    @RunOnVertxContext
    fun `listing-deleted contract round-trips and duplicate deliveries are harmless`(asserter: UniAsserter) {
        val listingId = UUID.randomUUID().toString()
        val path = "watermarked/${UUID.randomUUID()}.jpg"
        val image = Image(
            id = UUID.randomUUID().toString(),
            listingId = listingId,
            ownerUserId = UUID.randomUUID().toString(),
            originalFileName = "event.jpg",
            mimeType = "image/jpeg",
            width = 100,
            height = 100,
            status = "WATERMARKED",
            watermarkedUrl = "/images/event/watermarked",
            originalPath = null,
            watermarkedPath = path,
            createdAt = Instant.now()
        )
        val event = ListingDeletedEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = LISTING_DELETED,
            version = 1,
            occurredAt = Instant.now(),
            listingId = listingId,
            ownerUserId = image.ownerUserId
        )
        val bytes = ObjectMapperSerializer<ListingDeletedEvent>().serialize("listing-deleted", event)
        val decoded = ListingDeletedEventDeserializer().deserialize("listing-deleted", bytes)
        assertEquals(event, decoded)
        asserter.execute(Supplier { suspendToUni { repository.save(image); Unit }.replaceWithVoid() })
        asserter.execute(Supplier { consumer.onListingDeleted(decoded).replaceWithVoid() })
        asserter.execute(Supplier { consumer.onListingDeleted(decoded).replaceWithVoid() })
        asserter.assertThat({ suspendToUni { repository.findImageById(image.id) } }) { stored -> assertNull(stored) }
        asserter.execute { runBlocking { verify(storage).deleteObject(path) } }
    }

    @Test
    @RunOnVertxContext
    fun `listing tombstone rejects a delayed upload completion`(asserter: UniAsserter) {
        val listingId = UUID.randomUUID().toString()
        val event = ListingDeletedEvent(
            eventId = UUID.randomUUID().toString(), eventType = LISTING_DELETED, version = 1,
            occurredAt = Instant.now(), listingId = listingId, ownerUserId = UUID.randomUUID().toString()
        )
        val delayed = Image(
            id = UUID.randomUUID().toString(), listingId = listingId, ownerUserId = event.ownerUserId,
            originalFileName = "late.jpg", mimeType = "image/jpeg", width = 100, height = 100,
            status = "WATERMARKED", watermarkedUrl = "/images/late/watermarked", originalPath = null,
            watermarkedPath = "watermarked/late.jpg", createdAt = Instant.now()
        )
        asserter.execute(Supplier { consumer.onListingDeleted(event).replaceWithVoid() })
        asserter.assertFailedWith(
            { suspendToUni { repository.saveWithEvents(delayed, UUID.randomUUID().toString(), UUID.randomUUID().toString()) } },
            ImageUseCaseNotFoundException::class.java
        )
    }
}
