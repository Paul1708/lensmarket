package lensmarket.messaging.events

import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.messaging.data.repository.OutboxEventRecord
import lensmarket.messaging.domain.model.Message
import org.eclipse.microprofile.reactive.messaging.Channel
import java.time.Instant

const val EVENT_VERSION = 1
const val MESSAGE_SENT = "message-sent"

data class MessageSentEvent(
    val eventId: String,
    val eventType: String,
    val version: Int,
    val occurredAt: Instant,
    val messageId: String,
    val conversationId: String,
    val senderUserId: String
)

@ApplicationScoped
class KafkaOutboxPublisher(
    @param:Channel("message-sent-out")
    private val messageSentEvents: MutinyEmitter<MessageSentEvent>,
    private val objectMapper: ObjectMapper
) : OutboxPublisher {
    override fun publish(event: OutboxEventRecord): Uni<Void> =
        if (event.eventType == MESSAGE_SENT) {
            messageSentEvents.send(objectMapper.readValue(event.payload, MessageSentEvent::class.java))
        } else {
            Uni.createFrom().failure(IllegalArgumentException("Unsupported messaging outbox event ${event.eventType}"))
        }
}

fun Message.toMessageSentEvent(eventId: String): MessageSentEvent = MessageSentEvent(
    eventId = eventId,
    eventType = MESSAGE_SENT,
    version = EVENT_VERSION,
    occurredAt = createdAt,
    messageId = id,
    conversationId = conversationId,
    senderUserId = senderUserId
)
