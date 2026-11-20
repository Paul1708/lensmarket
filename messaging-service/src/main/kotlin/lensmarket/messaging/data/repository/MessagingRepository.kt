package lensmarket.messaging.data.repository

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.hibernate.reactive.panache.kotlin.Panache
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheRepositoryBase
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.messaging.data.entity.ConversationEntity
import lensmarket.messaging.data.entity.MessageEntity
import lensmarket.messaging.domain.model.Conversation
import lensmarket.messaging.domain.model.ConversationStatus
import lensmarket.messaging.domain.model.Message
import lensmarket.messaging.domain.port.ConversationRepositoryPort
import lensmarket.messaging.domain.port.MessageRepositoryPort
import lensmarket.messaging.domain.usecase.MessagingPersistenceConflictException
import lensmarket.messaging.events.MESSAGE_SENT
import lensmarket.messaging.events.toMessageSentEvent
import java.time.Instant

/**
 * Schlankes Panache-Repository für Konversationen.
 */
@ApplicationScoped
class ConversationPanacheRepository : PanacheRepositoryBase<ConversationEntity, String>

/**
 * Schlankes Panache-Repository für Nachrichten.
 */
@ApplicationScoped
class MessagePanacheRepository : PanacheRepositoryBase<MessageEntity, String>

/**
 * Persistenzadapter für Konversationen und Nachrichten.
 *
 * Der Adapter implementiert getrennte Ports für Conversation- und Message-Use-Cases,
 * obwohl beide Tabellen gemeinsam aktualisiert werden können.
 *
 * @param conversations Panache-Repository für Konversationen.
 * @param messages Panache-Repository für Nachrichten.
 */
@ApplicationScoped
class MessagingRepository(
    private val conversations: ConversationPanacheRepository,
    private val messages: MessagePanacheRepository,
    private val outbox: OutboxRepository,
    private val objectMapper: ObjectMapper
) : ConversationRepositoryPort, MessageRepositoryPort {
    /**
     * Wandelt eine Conversation-Entity in das Domain-Modell um.
     *
     * @param entity Persistenzmodell aus der Datenbank.
     * @return Fachliches Konversations-Modell.
     */
    private fun toConversationDomain(entity: ConversationEntity): Conversation =
        Conversation(
            id = entity.id,
            listingId = entity.listingId,
            photographerUserId = entity.photographerUserId,
            customerUserId = entity.customerUserId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            status = entity.status,
            deletedByPhotographerAt = entity.deletedByPhotographerAt,
            deletedByCustomerAt = entity.deletedByCustomerAt
        )

    /**
     * Wandelt eine Domain-Konversation in eine neue Entity um.
     *
     * @param conversation Fachliches Konversations-Modell.
     * @return Persistenzmodell für Hibernate.
     */
    private fun toConversationEntity(conversation: Conversation): ConversationEntity =
        ConversationEntity().apply {
            id = conversation.id
            listingId = conversation.listingId
            photographerUserId = conversation.photographerUserId
            customerUserId = conversation.customerUserId
            createdAt = conversation.createdAt
            updatedAt = conversation.updatedAt
            status = conversation.status
            deletedByPhotographerAt = conversation.deletedByPhotographerAt
            deletedByCustomerAt = conversation.deletedByCustomerAt
        }

    /**
     * Wandelt eine Message-Entity in das Domain-Modell um.
     *
     * @param entity Persistenzmodell aus der Datenbank.
     * @return Fachliches Nachrichten-Modell.
     */
    private fun toMessageDomain(entity: MessageEntity): Message =
        Message(
            id = entity.id,
            conversationId = entity.conversation.id,
            senderUserId = entity.senderUserId,
            body = entity.body,
            createdAt = entity.createdAt,
            idempotencyKey = entity.idempotencyKey
        )

    /**
     * Wandelt eine Domain-Nachricht in eine neue Entity um.
     *
     * @param message Fachliches Nachrichten-Modell.
     * @param conversation Zugehörige Conversation-Entity.
     * @return Persistenzmodell für Hibernate.
     */
    private fun toMessageEntity(message: Message, conversation: ConversationEntity): MessageEntity =
        MessageEntity().apply {
            id = message.id
            this.conversation = conversation
            senderUserId = message.senderUserId
            body = message.body
            createdAt = message.createdAt
            idempotencyKey = message.idempotencyKey
        }

    /**
     * Speichert eine neue Konversation.
     *
     * @param conversation Zu speichernde Konversation.
     * @return Gespeicherte Konversation.
     */
    override suspend fun saveConversation(conversation: Conversation): Conversation = try {
        Panache.withTransaction {
            conversations.persist(toConversationEntity(conversation)).replaceWith(conversation)
        }.awaitSuspending()
    } catch (failure: Throwable) {
        throw failure.asTypedPersistenceFailure()
    }

    /**
     * Sucht eine Konversation für Anzeige und Kunde.
     *
     * @param listingId Technische Anzeigen-ID.
     * @param customerUserId Technische Kunden-ID.
     * @return Bestehende Konversation oder `null`.
     */
    override suspend fun findConversationForListingAndCustomer(
        listingId: String,
        customerUserId: String
    ): Conversation? = Panache.withSession {
        conversations.find("listingId = ?1 and customerUserId = ?2", listingId, customerUserId)
            .firstResult()
            .onItem().transform { conversation -> conversation?.let { toConversationDomain(it) } }
    }.awaitSuspending()

    /**
     * Listet Konversationen eines Nutzers.
     *
     * @param userId Technische Nutzer-ID.
     * @param listingId Optionale Anzeigen-ID.
     * @param limit Maximale Ergebnisanzahl.
     * @param offset Startposition für Pagination.
     * @return Passende Konversationen.
     */
    override suspend fun findConversationsForUser(
        userId: String,
        listingId: String?,
        limit: Int,
        offset: Int
    ): List<Conversation> = Panache.withSession {
        val query = conversationListQuery(userId, listingId)
        conversations.find(query.text, *query.parameters.toTypedArray())
            .range(offset, offset + limit - 1)
            .list()
            .onItem().transform { results -> results.map { toConversationDomain(it) } }
    }.awaitSuspending()

    /**
     * Baut die dynamische Query für die Konversationsliste.
     *
     * @param userId Technische Nutzer-ID.
     * @param listingId Optionale Anzeigen-ID.
     * @return Query-Text und Parameterliste.
     */
    private fun conversationListQuery(userId: String, listingId: String?): ConversationQuery {
        val clauses = mutableListOf(
            "((photographerUserId = ?1 and deletedByPhotographerAt is null) " +
                "or (customerUserId = ?1 and deletedByCustomerAt is null))"
        )
        val parameters = mutableListOf<Any>(userId)

        listingId?.let { value ->
            parameters += value
            clauses += "listingId = ?${parameters.size}"
        }

        return ConversationQuery(
            text = "${clauses.joinToString(" and ")} order by updatedAt desc",
            parameters = parameters
        )
    }

    /**
     * Repräsentiert eine dynamisch aufgebaute Conversation-Query.
     *
     * @param text Panache-Query-Text.
     * @param parameters Parameter in Bindungsreihenfolge.
     */
    private data class ConversationQuery(
        val text: String,
        val parameters: List<Any>
    )

    /**
     * Sucht eine Konversation anhand ihrer ID.
     *
     * @param conversationId Technische Konversations-ID.
     * @return Gefundene Konversation oder `null`.
     */
    override suspend fun findConversationById(conversationId: String): Conversation? =
        Panache.withSession { conversations.findById(conversationId)
            .onItem().transform { conversation -> conversation?.let { toConversationDomain(it) } }
        }.awaitSuspending()

    /**
     * Entfernt eine Konversation aus der Sicht eines Teilnehmers und schliesst sie,
     * sobald beide Teilnehmer entfernt haben.
     *
     * @param conversationId Technische Konversations-ID.
     * @param userId Technische ID der entfernenden Person.
     * @param deletedAt Zeitpunkt der Entfernung.
     * @return Aktualisierte Konversation.
     */
    override suspend fun markConversationDeletedForUser(
        conversationId: String,
        userId: String,
        deletedAt: Instant
    ): Conversation = Panache.withTransaction {
        conversations.findById(conversationId).onItem().transformToUni { conversation ->
            if (conversation == null) {
                Uni.createFrom().failure(IllegalStateException("Conversation $conversationId not found"))
            } else {
                when (userId) {
                    conversation.photographerUserId -> conversation.deletedByPhotographerAt = deletedAt
                    conversation.customerUserId -> conversation.deletedByCustomerAt = deletedAt
                }
                if (conversation.deletedByPhotographerAt != null && conversation.deletedByCustomerAt != null) {
                    conversation.status = ConversationStatus.CLOSED
                }
                Uni.createFrom().item(toConversationDomain(conversation))
            }
        }
    }.awaitSuspending()

    /**
     * Speichert eine Nachricht und aktualisiert die letzte Thread-Aktivität.
     *
     * @param message Zu speichernde Nachricht.
     * @return Gespeicherte Nachricht.
     */
    override suspend fun saveMessage(message: Message): Message = try {
        Panache.withTransaction { conversations.findById(message.conversationId)
            .onItem().transformToUni { conversation ->
                if (conversation == null) {
                    Uni.createFrom().failure(IllegalStateException("Conversation ${message.conversationId} not found"))
                } else {
                    conversation.updatedAt = message.createdAt
                    messages.persist(toMessageEntity(message, conversation))
                        .replaceWith(message)
                }
            }
        }.awaitSuspending()
    } catch (failure: Throwable) {
        throw failure.asTypedPersistenceFailure()
    }

    override suspend fun saveMessageWithEvent(message: Message, eventId: String): Message = try {
        val event = message.toMessageSentEvent(eventId)
        Panache.withTransaction {
            conversations.findById(message.conversationId).onItem().transformToUni { conversation ->
                if (conversation == null) {
                    Uni.createFrom().failure(IllegalStateException("Conversation ${message.conversationId} not found"))
                } else {
                    conversation.updatedAt = message.createdAt
                    messages.persist(toMessageEntity(message, conversation))
                        .chain { _: MessageEntity ->
                            outbox.persistPending(
                                OutboxEventRecord(
                                    eventId = eventId,
                                    aggregateId = message.id,
                                    eventType = MESSAGE_SENT,
                                    topic = MESSAGE_SENT,
                                    payload = objectMapper.writeValueAsString(event),
                                    occurredAt = message.createdAt,
                                    attempts = 0
                                )
                            )
                        }
                        .replaceWith(message)
                }
            }
        }.awaitSuspending()
    } catch (failure: Throwable) {
        throw failure.asTypedPersistenceFailure()
    }

    /**
     * Sucht eine bereits gesendete Nachricht anhand ihres Idempotency-Keys.
     *
     * @param conversationId Technische Konversations-ID.
     * @param senderUserId Technische ID des Senders.
     * @param idempotencyKey Client-Key aus dem Idempotency-Key-Header.
     * @return Gefundene Nachricht oder `null`, wenn der Key noch nicht verwendet wurde.
     */
    override suspend fun findMessageByIdempotencyKey(
        conversationId: String,
        senderUserId: String,
        idempotencyKey: String
    ): Message? = Panache.withSession { messages.find(
            "conversation.id = ?1 and senderUserId = ?2 and idempotencyKey = ?3",
            conversationId, senderUserId, idempotencyKey
        )
            .firstResult()
            .onItem().transform { message -> message?.let { toMessageDomain(it) } }
    }.awaitSuspending()

    /**
     * Lädt eine paginierte Nachrichtenseite einer Konversation.
     *
     * Die Existenz der Konversation wird im Use Case geprüft; hier wird nur die Seite geladen.
     *
     * @param conversationId Technische Konversations-ID.
     * @param limit Maximale Ergebnisanzahl.
     * @param offset Startposition für Pagination.
     * @return Nachrichten der Konversation.
     */
    override suspend fun findMessages(conversationId: String, limit: Int, offset: Int): List<Message> =
        Panache.withSession { messages.find("conversation.id = ?1 order by createdAt", conversationId)
            .range(offset, offset + limit - 1)
            .list()
            .onItem().transform { results -> results.map { toMessageDomain(it) } }
        }.awaitSuspending()

    private fun Throwable.asTypedPersistenceFailure(): Throwable {
        val conflict = generateSequence(this) { it.cause }.any { cause ->
            val text = "${cause::class.qualifiedName} ${cause.message}".lowercase()
            "duplicate key" in text || "unique constraint" in text || "constraintviolation" in text
        }
        return if (conflict) MessagingPersistenceConflictException("Unique persistence constraint violated", this) else this
    }
}
