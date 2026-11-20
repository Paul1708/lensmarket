package lensmarket.listing.events

import io.quarkus.scheduler.Scheduled
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.listing.data.repository.OutboxEventRecord
import lensmarket.listing.data.repository.OutboxRepository
import lensmarket.listing.metrics.ListingMetrics
import lensmarket.listing.runtime.suspendToUni
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.time.Instant
import kotlin.math.min

interface OutboxPublisher {
    fun publish(event: OutboxEventRecord): Uni<Void>
}

/** Beobachtbares at-least-once Relay für committed Outbox-Einträge. */
@ApplicationScoped
class OutboxRelay(
    private val outbox: OutboxRepository,
    private val publisher: OutboxPublisher,
    private val metrics: ListingMetrics,
    @param:ConfigProperty(name = "lensmarket.outbox.relay.batch-size", defaultValue = "50")
    private val batchSize: Int
) {
    private val logger = Logger.getLogger(OutboxRelay::class.java)

    @Scheduled(
        every = "{lensmarket.outbox.relay.interval:1s}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    fun relay(): Uni<Void> = suspendToUni {
        val now = Instant.now()
        outbox.pending(now, batchSize).forEach { event ->
            try {
                publisher.publish(event).awaitSuspending()
                outbox.markPublished(event.eventId, Instant.now())
                metrics.eventPublished(event.eventType)
            } catch (failure: Throwable) {
                val delaySeconds = 1L shl min(event.attempts, 6)
                outbox.markFailed(event.eventId, failure, Instant.now().plusSeconds(delaySeconds))
                metrics.outboxPublishFailed(event.eventType)
                logger.warn("Outbox publish failed for ${event.eventId}; retry in ${delaySeconds}s", failure)
            }
        }
        val stats = outbox.stats(Instant.now())
        metrics.outboxState(stats.pending, stats.oldestAgeSeconds)
    }.replaceWithVoid()
}
