package lensmarket.messaging.events

import io.quarkus.hibernate.reactive.panache.kotlin.Panache
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.vertx.RunOnVertxContext
import io.quarkus.test.vertx.UniAsserter
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import lensmarket.messaging.data.repository.OutboxEventRecord
import lensmarket.messaging.data.repository.OutboxRepository
import lensmarket.messaging.runtime.suspendToUni
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.function.Supplier

@QuarkusTest
class OutboxRelayFailureIntegrationTest {
    @Inject
    lateinit var outbox: OutboxRepository

    @Inject
    lateinit var relay: OutboxRelay

    @InjectMock
    lateinit var publisher: OutboxPublisher

    @Test
    @RunOnVertxContext
    fun `broker failure retains committed event and a later relay publishes it`(asserter: UniAsserter) {
        val now = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MILLIS)
        val record = OutboxEventRecord(
            eventId = UUID.randomUUID().toString(),
            aggregateId = UUID.randomUUID().toString(),
            eventType = MESSAGE_SENT,
            topic = MESSAGE_SENT,
            payload = "{}",
            occurredAt = now,
            attempts = 0
        )
        `when`(publisher.publish(record))
            .thenReturn(Uni.createFrom().failure(IllegalStateException("broker down")))
            .thenReturn(Uni.createFrom().voidItem())

        asserter.execute(Supplier {
            Panache.withTransaction { outbox.persistPending(record) }.replaceWithVoid()
        })
        asserter.execute(Supplier { relay.relay() })
        asserter.assertThat({ Panache.withSession { outbox.findById(record.eventId) } }) { retained ->
            assertEquals(1, retained.attempts)
            assertEquals(null, retained.publishedAt)
        }
        asserter.execute(Supplier {
            suspendToUni {
                outbox.markFailed(
                    record.eventId,
                    IllegalStateException("retry now"),
                    Instant.now().minusSeconds(1)
                )
            }.replaceWithVoid()
        })
        asserter.execute(Supplier { relay.relay() })
        asserter.assertThat({ Panache.withSession { outbox.findById(record.eventId) } }) { published ->
            assertEquals(2, published.attempts)
            assertNotNull(published.publishedAt)
        }
        asserter.execute(Supplier {
            Panache.withTransaction { outbox.deleteById(record.eventId) }.replaceWithVoid()
        })
    }
}
