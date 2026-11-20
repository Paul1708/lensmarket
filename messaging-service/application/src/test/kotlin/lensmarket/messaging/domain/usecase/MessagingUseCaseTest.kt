package lensmarket.messaging.domain.usecase

import kotlinx.coroutines.test.runTest
import lensmarket.messaging.domain.model.Conversation
import lensmarket.messaging.domain.model.ConversationStatus
import lensmarket.messaging.domain.model.CreateConversationCommand
import lensmarket.messaging.domain.model.ListingReference
import lensmarket.messaging.domain.model.Message
import lensmarket.messaging.domain.model.SendMessageCommand
import lensmarket.messaging.domain.port.ClockPort
import lensmarket.messaging.domain.port.ConversationRepositoryPort
import lensmarket.messaging.domain.port.IdGeneratorPort
import lensmarket.messaging.domain.port.ListingLookupPort
import lensmarket.messaging.domain.port.MessageRepositoryPort
import lensmarket.messaging.domain.port.MessagingMetricsPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.time.Instant

class MessagingUseCaseTest {
    @Test
    fun `conversation creation is idempotent and measured once`() = runTest {
        val f = Fixture()
        val first = f.create.create("customer", CreateConversationCommand("listing"))
        val second = f.create.create("customer", CreateConversationCommand("listing"))
        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.conversation.id, second.conversation.id)
        assertEquals(1, f.metrics.conversations)
    }

    @Test
    fun `conversation rejects inactive listings and self contact`() = runTest {
        val inactive = Fixture(ListingReference("listing", "owner", "PAUSED"))
        expectFailure<MessagingUseCaseValidationException> {
            inactive.create.create("customer", CreateConversationCommand("listing"))
        }
        val self = Fixture()
        expectFailure<MessagingUseCaseForbiddenException> {
            self.create.create("owner", CreateConversationCommand("listing"))
        }
    }

    @Test
    fun `message send is participant-only idempotent and publishes once`() = runTest {
        val f = Fixture()
        val conversation = f.create.create("customer", CreateConversationCommand("listing")).conversation
        val first = f.send.send("customer", SendMessageCommand(conversation.id, " Hallo ", "retry"))
        val retry = f.send.send("customer", SendMessageCommand(conversation.id, "Anderer Text", "retry"))
        assertTrue(first.created)
        assertFalse(retry.created)
        assertEquals(first.message.id, retry.message.id)
        assertEquals(listOf(first.message.id), f.repository.outboxMessages)
        assertEquals(1, f.metrics.messages)
        expectFailure<MessagingUseCaseForbiddenException> {
            f.send.send("stranger", SendMessageCommand(conversation.id, "Nein", null))
        }
    }

    @Test
    fun `thread and message reads enforce participants`() = runTest {
        val f = Fixture()
        val conversation = f.create.create("customer", CreateConversationCommand("listing")).conversation
        f.send.send("customer", SendMessageCommand(conversation.id, "Hallo", null))
        assertEquals(conversation, GetConversationUseCase(f.repository).get("owner", conversation.id))
        assertEquals(1, GetMessagesUseCase(f.repository, f.repository).get("customer", conversation.id, 10, 0).size)
        expectFailure<MessagingUseCaseForbiddenException> {
            GetConversationUseCase(f.repository).get("other", conversation.id)
        }
    }

    @Test
    fun `message bodies are trimmed and reads are paginated`() = runTest {
        val f = Fixture()
        val conversation = f.create.create("customer", CreateConversationCommand("listing")).conversation
        f.send.send("customer", SendMessageCommand(conversation.id, " first ", null))
        val second = f.send.send("owner", SendMessageCommand(conversation.id, " second ", null)).message
        val page = GetMessagesUseCase(f.repository, f.repository).get("customer", conversation.id, 1, 1)
        assertEquals(listOf(second), page)
        assertEquals("second", page.single().body)
    }

    @Test
    fun `conversation lists are filtered and paginated`() = runTest {
        val f = Fixture()
        f.create.create("customer", CreateConversationCommand("listing"))
        val list = ListConversationsUseCase(f.repository).list("customer", "listing", 10, 0)
        assertEquals(1, list.size)
        expectFailure<MessagingUseCaseValidationException> {
            ListConversationsUseCase(f.repository).list("customer", null, 0, 0)
        }
    }

    @Test
    fun `idempotency key length is validated`() = runTest {
        val f = Fixture()
        val conversation = f.create.create("customer", CreateConversationCommand("listing")).conversation
        expectFailure<MessagingUseCaseValidationException> {
            f.send.send("customer", SendMessageCommand(conversation.id, "Hallo", "x".repeat(65)))
        }
    }

    @Test
    fun `conversation removal only hides the caller and closes after both participants remove it`() = runTest {
        val f = Fixture()
        val conversation = f.create.create("customer", CreateConversationCommand("listing")).conversation

        val afterCustomerRemoves = f.remove.remove("customer", conversation.id)
        assertEquals(ConversationStatus.OPEN, afterCustomerRemoves.status)
        assertTrue(ListConversationsUseCase(f.repository).list("owner", null, 10, 0).any { it.id == conversation.id })
        assertTrue(ListConversationsUseCase(f.repository).list("customer", null, 10, 0).none { it.id == conversation.id })
        assertEquals(1, f.metrics.removed)

        val afterOwnerRemoves = f.remove.remove("owner", conversation.id)
        assertEquals(ConversationStatus.CLOSED, afterOwnerRemoves.status)
        assertEquals(2, f.metrics.removed)

        expectFailure<MessagingUseCaseForbiddenException> {
            f.send.send("customer", SendMessageCommand(conversation.id, "Hallo", null))
        }
        expectFailure<MessagingUseCaseForbiddenException> {
            f.remove.remove("stranger", conversation.id)
        }
    }

    private class Fixture(listing: ListingReference = ListingReference("listing", "owner", "ACTIVE")) {
        val repository = InMemoryMessaging()
        val metrics = RecordingMetrics()
        private var id = 0
        private val ids = IdGeneratorPort { "id-${++id}" }
        private val clock = ClockPort { Instant.parse("2026-07-13T10:00:00Z") }
        val create = CreateConversationUseCase(repository, ListingLookupPort { listing }, metrics, clock, ids)
        val send = SendMessageUseCase(repository, repository, metrics, clock, ids)
        val remove = RemoveConversationUseCase(repository, clock, metrics)
    }

    private class RecordingMetrics : MessagingMetricsPort {
        var conversations = 0; var messages = 0; var removed = 0
        override fun conversationCreated() { conversations++ }
        override fun conversationRemoved() { removed++ }
        override fun messageSent() { messages++ }
    }

    private class InMemoryMessaging : ConversationRepositoryPort, MessageRepositoryPort {
        private val conversations = linkedMapOf<String, Conversation>()
        private val messages = linkedMapOf<String, Message>()
        val outboxMessages = mutableListOf<String>()
        override suspend fun saveConversation(conversation: Conversation): Conversation =
            conversation.also { conversations[it.id] = it }
        override suspend fun findConversationForListingAndCustomer(listingId: String, customerUserId: String) =
            conversations.values.firstOrNull { it.listingId == listingId && it.customerUserId == customerUserId }
        override suspend fun findConversationsForUser(userId: String, listingId: String?, limit: Int, offset: Int) =
            conversations.values.filter {
                userId in setOf(it.photographerUserId, it.customerUserId) && (listingId == null || it.listingId == listingId) &&
                    !(it.photographerUserId == userId && it.deletedByPhotographerAt != null) &&
                    !(it.customerUserId == userId && it.deletedByCustomerAt != null)
            }.drop(offset).take(limit)
        override suspend fun findConversationById(conversationId: String) = conversations[conversationId]
        override suspend fun markConversationDeletedForUser(
            conversationId: String, userId: String, deletedAt: Instant
        ): Conversation {
            val conversation = conversations[conversationId]
                ?: throw IllegalStateException("Conversation $conversationId not found")
            val updated = when (userId) {
                conversation.photographerUserId -> conversation.copy(deletedByPhotographerAt = deletedAt)
                conversation.customerUserId -> conversation.copy(deletedByCustomerAt = deletedAt)
                else -> conversation
            }
            val closed = if (updated.deletedByPhotographerAt != null && updated.deletedByCustomerAt != null) {
                updated.copy(status = ConversationStatus.CLOSED)
            } else updated
            conversations[conversationId] = closed
            return closed
        }
        override suspend fun saveMessage(message: Message): Message = message.also { messages[it.id] = it }
        override suspend fun saveMessageWithEvent(message: Message, eventId: String): Message =
            saveMessage(message).also { outboxMessages += message.id }
        override suspend fun findMessages(conversationId: String, limit: Int, offset: Int) =
            messages.values.filter { it.conversationId == conversationId }.drop(offset).take(limit)
        override suspend fun findMessageByIdempotencyKey(
            conversationId: String, senderUserId: String, idempotencyKey: String
        ) = messages.values.firstOrNull {
            it.conversationId == conversationId && it.senderUserId == senderUserId && it.idempotencyKey == idempotencyKey
        }
    }

    private suspend inline fun <reified T : Throwable> expectFailure(noinline block: suspend () -> Unit) {
        try { block(); fail("Expected ${T::class.simpleName}") } catch (failure: Throwable) {
            assertTrue(failure is T, "Expected ${T::class.simpleName}, got ${failure::class.simpleName}")
        }
    }
}
