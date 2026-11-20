package lensmarket.messaging.domain.usecase

import lensmarket.messaging.domain.model.ConversationStatus
import lensmarket.messaging.domain.model.MAX_IDEMPOTENCY_KEY_LENGTH
import lensmarket.messaging.domain.model.MAX_MESSAGE_BODY_LENGTH
import lensmarket.messaging.domain.model.Message
import lensmarket.messaging.domain.model.SendMessageCommand
import lensmarket.messaging.domain.model.SendMessageResult
import lensmarket.messaging.domain.port.ClockPort
import lensmarket.messaging.domain.port.ConversationRepositoryPort
import lensmarket.messaging.domain.port.IdGeneratorPort
import lensmarket.messaging.domain.port.MessageRepositoryPort
import lensmarket.messaging.domain.port.MessagingMetricsPort

class SendMessageUseCase(
    private val conversations: ConversationRepositoryPort,
    private val messages: MessageRepositoryPort,
    private val metrics: MessagingMetricsPort,
    private val clock: ClockPort,
    private val ids: IdGeneratorPort
) {
    suspend fun send(currentUserId: String, command: SendMessageCommand): SendMessageResult {
        val userId = requiredText(currentUserId, "currentUserId")
        val conversationId = requiredText(command.conversationId, "conversationId")
        val body = validBody(command.body)
        val key = validIdempotencyKey(command.idempotencyKey)
        val conversation = conversations.findConversationById(conversationId)
            ?: throw MessagingUseCaseNotFoundException("Conversation $conversationId not found")
        if (userId != conversation.photographerUserId && userId != conversation.customerUserId) {
            throw MessagingUseCaseForbiddenException("Only conversation participants can send messages")
        }
        if (conversation.status == ConversationStatus.CLOSED) {
            throw MessagingUseCaseForbiddenException("Closed conversations cannot receive messages")
        }
        if (key != null) {
            messages.findMessageByIdempotencyKey(conversationId, userId, key)?.let {
                return SendMessageResult(it, created = false)
            }
        }
        val candidate = Message(ids.newId(), conversationId, userId, body, clock.now(), key)
        val result = try {
            SendMessageResult(messages.saveMessageWithEvent(candidate, ids.newId()), created = true)
        } catch (conflict: MessagingPersistenceConflictException) {
            if (key == null) throw conflict
            val winner = messages.findMessageByIdempotencyKey(conversationId, userId, key) ?: throw conflict
            SendMessageResult(winner, created = false)
        }
        if (result.created) {
            metrics.messageSent()
        }
        return result
    }

    private fun requiredText(value: String, field: String): String = value.trim().also {
        if (it.isEmpty()) throw MessagingUseCaseValidationException("$field must not be blank")
    }
    private fun validBody(value: String): String = requiredText(value, "body").also {
        if (it.length > MAX_MESSAGE_BODY_LENGTH) {
            throw MessagingUseCaseValidationException("body must not be longer than $MAX_MESSAGE_BODY_LENGTH characters")
        }
    }
    private fun validIdempotencyKey(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
        if (it.length > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw MessagingUseCaseValidationException(
                "Idempotency-Key must not be longer than $MAX_IDEMPOTENCY_KEY_LENGTH characters"
            )
        }
    }
}
