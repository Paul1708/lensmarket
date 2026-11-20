package lensmarket.messaging.resources

import org.eclipse.microprofile.openapi.annotations.media.Schema
import lensmarket.messaging.domain.model.Conversation
import lensmarket.messaging.domain.model.CreateConversationCommand
import lensmarket.messaging.domain.model.Message
import lensmarket.messaging.domain.model.SendMessageCommand
import java.time.Instant

/**
 * Request-Body zum Erstellen einer Konversation.
 *
 * @param listingId Technische ID der Anzeige, zu der geschrieben werden soll.
 */
data class CreateConversationRequest(
    @field:Schema(minLength = 1) val listingId: String
) {
    /**
     * Wandelt den HTTP-Request in ein Domain-Command um.
     *
     * @return Command für den CreateConversationUseCase.
     */
    fun toCommand(): CreateConversationCommand =
        CreateConversationCommand(listingId = listingId)
}

/**
 * Request-Body zum Senden einer Nachricht.
 *
 * @param body Nachrichtentext.
 */
data class SendMessageRequest(
    @field:Schema(minLength = 1, maxLength = 4000) val body: String
) {
    /**
     * Wandelt Pfad-ID, Idempotency-Key-Header und Request-Body in ein Domain-Command um.
     *
     * @param conversationId Technische Konversations-ID aus dem Pfad.
     * @param idempotencyKey Optionaler Client-Key aus dem `Idempotency-Key`-Header.
     * @return Command für den SendMessageUseCase.
     */
    fun toCommand(conversationId: String, idempotencyKey: String?): SendMessageCommand =
        SendMessageCommand(
            conversationId = conversationId,
            body = body,
            idempotencyKey = idempotencyKey
        )
}

/**
 * API-Antwort für eine Konversation zu genau einer Anzeige.
 *
 * @param id Technische Konversations-ID.
 * @param listingId Technische Anzeigen-ID.
 * @param photographerUserId Technische ID des Anzeigenbesitzers.
 * @param customerUserId Technische ID des anfragenden Kunden.
 * @param createdAt Anlagezeitpunkt.
 * @param updatedAt Zeitpunkt der letzten Aktivität.
 * @param status OPEN solange mindestens eine Seite die Konversation noch sieht, sonst CLOSED.
 */
data class ConversationResponse(
    val id: String,
    val listingId: String,
    val photographerUserId: String,
    val customerUserId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    @field:Schema(enumeration = ["OPEN", "CLOSED"]) val status: String
)

/**
 * API-Antwort für eine Nachricht innerhalb einer Konversation.
 *
 * @param id Technische Nachrichten-ID.
 * @param conversationId Technische Konversations-ID.
 * @param senderUserId Technische ID des Senders.
 * @param body Nachrichtentext.
 * @param createdAt Sendezeitpunkt.
 */
data class MessageResponse(
    val id: String,
    val conversationId: String,
    val senderUserId: String,
    val body: String,
    val createdAt: Instant
)

/**
 * Wandelt eine Domain-Konversation in die HTTP-Antwort um.
 *
 * @return API-Modell für Clients.
 */
fun Conversation.toResponse(): ConversationResponse =
    ConversationResponse(
        id = id,
        listingId = listingId,
        photographerUserId = photographerUserId,
        customerUserId = customerUserId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status.name
    )

/**
 * Wandelt eine Domain-Nachricht in die HTTP-Antwort um.
 *
 * @return API-Modell für Clients.
 */
fun Message.toResponse(): MessageResponse =
    MessageResponse(
        id = id,
        conversationId = conversationId,
        senderUserId = senderUserId,
        body = body,
        createdAt = createdAt
    )
