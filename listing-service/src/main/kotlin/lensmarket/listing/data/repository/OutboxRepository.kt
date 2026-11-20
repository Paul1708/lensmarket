package lensmarket.listing.data.repository

import io.quarkus.hibernate.reactive.panache.kotlin.Panache
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheRepositoryBase
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.listing.data.entity.OutboxEventEntity
import lensmarket.listing.data.entity.ProcessedEventEntity
import java.time.Duration
import java.time.Instant

data class OutboxEventRecord(
    val eventId: String,
    val aggregateId: String,
    val eventType: String,
    val topic: String,
    val payload: String,
    val occurredAt: Instant,
    val attempts: Int
)

data class OutboxStats(val pending: Long, val oldestAgeSeconds: Long)

@ApplicationScoped
class OutboxRepository : PanacheRepositoryBase<OutboxEventEntity, String> {
    fun persistPending(record: OutboxEventRecord): Uni<OutboxEventEntity> =
        persist(OutboxEventEntity().apply {
            eventId = record.eventId
            aggregateId = record.aggregateId
            eventType = record.eventType
            topic = record.topic
            payload = record.payload
            occurredAt = record.occurredAt
            attempts = 0
            nextAttemptAt = record.occurredAt
        })

    suspend fun pending(now: Instant, limit: Int): List<OutboxEventRecord> = Panache.withSession {
        find("publishedAt is null and nextAttemptAt <= ?1 order by occurredAt", now)
            .range(0, limit - 1).list()
            .onItem().transform { rows -> rows.map { it.toRecord() } }
    }.awaitSuspending()

    suspend fun markPublished(eventId: String, publishedAt: Instant) {
        Panache.withTransaction {
            findById(eventId).onItem().invoke { row ->
                row?.publishedAt = publishedAt
                row?.lastError = null
            }
        }.awaitSuspending()
    }

    suspend fun markFailed(eventId: String, failure: Throwable, nextAttemptAt: Instant) {
        Panache.withTransaction {
            findById(eventId).onItem().invoke { row ->
                if (row != null) {
                    row.attempts += 1
                    row.nextAttemptAt = nextAttemptAt
                    row.lastError = (failure.message ?: failure::class.simpleName ?: "publish failed").take(1000)
                }
            }
        }.awaitSuspending()
    }

    suspend fun stats(now: Instant): OutboxStats = Panache.withSession {
        count("publishedAt is null").chain { pending ->
            find("publishedAt is null order by occurredAt").firstResult()
                .map { oldest ->
                    val age = oldest?.let { Duration.between(it.occurredAt, now).seconds.coerceAtLeast(0) } ?: 0
                    OutboxStats(pending, age)
                }
        }
    }.awaitSuspending()

    private fun OutboxEventEntity.toRecord() = OutboxEventRecord(
        eventId, aggregateId, eventType, topic, payload, occurredAt, attempts
    )
}

@ApplicationScoped
class ProcessedEventRepository : PanacheRepositoryBase<ProcessedEventEntity, String> {
    fun persistProcessed(eventId: String, eventType: String, processedAt: Instant): Uni<ProcessedEventEntity> =
        persist(ProcessedEventEntity().apply {
            this.eventId = eventId
            this.eventType = eventType
            this.processedAt = processedAt
        })
}
