package lensmarket.messaging.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity {
    @Id @Column(name = "event_id", length = 36, nullable = false)
    lateinit var eventId: String
    @Column(name = "aggregate_id", length = 36, nullable = false)
    lateinit var aggregateId: String
    @Column(name = "event_type", length = 80, nullable = false)
    lateinit var eventType: String
    @Column(length = 120, nullable = false)
    lateinit var topic: String
    @Column(nullable = false, columnDefinition = "TEXT")
    lateinit var payload: String
    @Column(name = "occurred_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    lateinit var occurredAt: Instant
    @Column(nullable = false)
    var attempts: Int = 0
    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    lateinit var nextAttemptAt: Instant
    @Column(name = "published_at", columnDefinition = "TIMESTAMPTZ")
    var publishedAt: Instant? = null
    @Column(name = "last_error", length = 1000)
    var lastError: String? = null
}
