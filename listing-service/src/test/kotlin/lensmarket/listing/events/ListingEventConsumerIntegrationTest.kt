package lensmarket.listing.events

import io.quarkus.kafka.client.serialization.ObjectMapperSerializer
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.vertx.RunOnVertxContext
import io.quarkus.test.vertx.UniAsserter
import jakarta.inject.Inject
import lensmarket.listing.data.repository.ListingRepository
import lensmarket.listing.domain.model.Listing
import lensmarket.listing.runtime.suspendToUni
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.function.Supplier

@QuarkusTest
class ListingEventConsumerIntegrationTest {
    @Inject
    lateinit var repository: ListingRepository

    @Inject
    lateinit var consumer: ImageWatermarkedEventConsumer

    @Test
    @RunOnVertxContext
    fun `event contract round-trips and duplicate deliveries append the image once`(asserter: UniAsserter) {
        val listing = Listing(
            id = UUID.randomUUID().toString(),
            ownerUserId = UUID.randomUUID().toString(),
            title = "Event listing",
            description = "Consumer idempotence",
            priceHint = BigDecimal("25.00"),
            location = null,
            cameraModel = null,
            lensModel = null,
            tags = listOf("event"),
            imageIds = emptyList(),
            status = "ACTIVE",
            createdAt = Instant.now()
        )
        val event = ImageWatermarkedEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = "image-watermarked",
            version = 1,
            occurredAt = Instant.now(),
            imageId = UUID.randomUUID().toString(),
            listingId = listing.id,
            ownerUserId = listing.ownerUserId,
            watermarkedUrl = "/images/watermarked"
        )
        val bytes = ObjectMapperSerializer<ImageWatermarkedEvent>().serialize("image-watermarked", event)
        val decoded = ImageWatermarkedEventDeserializer().deserialize("image-watermarked", bytes)
        assertEquals(event, decoded)

        asserter.execute(Supplier { suspendToUni { repository.save(listing); Unit }.replaceWithVoid() })
        asserter.execute(Supplier { consumer.onImageWatermarked(decoded).replaceWithVoid() })
        asserter.execute(Supplier { consumer.onImageWatermarked(decoded).replaceWithVoid() })
        asserter.assertThat({ suspendToUni { repository.findListingById(listing.id) } }) { stored ->
            assertEquals(listOf(event.imageId), stored?.imageIds)
        }
    }

    @Test
    @RunOnVertxContext
    fun `late image event cannot resurrect a soft deleted listing`(asserter: UniAsserter) {
        val listing = Listing(
            id = UUID.randomUUID().toString(), ownerUserId = UUID.randomUUID().toString(),
            title = "Deleted", description = "Late event", priceHint = null, location = null,
            cameraModel = null, lensModel = null, tags = emptyList(), imageIds = emptyList(),
            status = "ACTIVE", createdAt = Instant.now()
        )
        val event = ImageWatermarkedEvent(
            eventId = UUID.randomUUID().toString(), eventType = "image-watermarked", version = 1,
            occurredAt = Instant.now(), imageId = UUID.randomUUID().toString(), listingId = listing.id,
            ownerUserId = listing.ownerUserId, watermarkedUrl = "/images/late/watermarked"
        )
        asserter.execute(Supplier { suspendToUni { repository.save(listing); Unit }.replaceWithVoid() })
        asserter.execute(Supplier {
            suspendToUni { repository.markDeleted(listing.id, Instant.now()); Unit }.replaceWithVoid()
        })
        asserter.execute(Supplier { consumer.onImageWatermarked(event).replaceWithVoid() })
        asserter.execute(Supplier { consumer.onImageWatermarked(event).replaceWithVoid() })
        asserter.assertThat({ suspendToUni { repository.findListingById(listing.id) } }) { stored ->
            assertEquals(null, stored)
        }
    }
}
