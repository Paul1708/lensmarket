package lensmarket.messaging.domain.model

import java.time.Instant

/**
 * Listing-Status, bei dem neue Konversationen erlaubt sind.
 */
const val LISTING_STATUS_ACTIVE = "ACTIVE"

/**
 * Standardlimit fuer Konversationslisten.
 */
const val DEFAULT_CONVERSATION_LIST_LIMIT = 50

/**
 * Obergrenze fuer Konversationslisten.
 */
const val MAX_CONVERSATION_LIST_LIMIT = 100

/**
 * Standardlimit fuer Nachrichtenlisten.
 */
const val DEFAULT_MESSAGE_LIST_LIMIT = 100

/**
 * Obergrenze fuer Nachrichtenlisten.
 */
const val MAX_MESSAGE_LIST_LIMIT = 200

/**
 * Maximale Laenge eines Nachrichtentextes.
 */
const val MAX_MESSAGE_BODY_LENGTH = 4000

/**
 * Maximale Laenge eines Idempotency-Keys.
 */
const val MAX_IDEMPOTENCY_KEY_LENGTH = 64

/**
 * Command zum Erstellen oder Finden einer Konversation.
 *
 * Das Command ist unabhängig vom HTTP-Request-DTO.
 *
 * @param listingId Technische ID der Anzeige.
 */
data class CreateConversationCommand(
    val listingId: String
)

/**
 * Command zum Senden einer Nachricht.
 *
 * Die Konversations-ID wird an der Resource-Grenze aus dem Pfad ergänzt. Der optionale
 * idempotencyKey stammt aus dem Idempotency-Key-Header und macht Retries gefahrlos.
 *
 * @param conversationId Technische Konversations-ID.
 * @param body Nachrichtentext.
 * @param idempotencyKey Optionaler Client-Key gegen doppelte Zustellung bei Retries.
 */
data class SendMessageCommand(
    val conversationId: String,
    val body: String,
    val idempotencyKey: String? = null
)

/**
 * Minimale Anzeige-Referenz aus dem Listing Service.
 *
 * Der Messaging Service speichert keine Listing-Details und nutzt diese Referenz nur
 * für Chat-Regeln.
 *
 * @param id Technische Anzeigen-ID.
 * @param ownerUserId Technische ID des Anzeigenbesitzers.
 * @param status Fachlicher Anzeigenstatus.
 */
data class ListingReference(
    val id: String,
    val ownerUserId: String,
    val status: String
)

/**
 * Sichtbarkeits-/Lebenszyklusstatus einer Konversation.
 */
enum class ConversationStatus {
    OPEN,
    CLOSED
}

/**
 * Domain-Modell einer Konversation zu genau einer Anzeige.
 *
 * @param id Technische Konversations-ID.
 * @param listingId Technische Anzeigen-ID.
 * @param photographerUserId Technische ID des Anzeigenbesitzers.
 * @param customerUserId Technische ID des Kunden.
 * @param createdAt Anlagezeitpunkt.
 * @param updatedAt Zeitpunkt der letzten Aktivität.
 * @param status OPEN solange mindestens eine Seite die Konversation noch sieht, sonst CLOSED.
 * @param deletedByPhotographerAt Zeitpunkt, zu dem der Fotograf die Konversation entfernt hat, oder `null`.
 * @param deletedByCustomerAt Zeitpunkt, zu dem der Kunde die Konversation entfernt hat, oder `null`.
 */
data class Conversation(
    val id: String,
    val listingId: String,
    val photographerUserId: String,
    val customerUserId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: ConversationStatus = ConversationStatus.OPEN,
    val deletedByPhotographerAt: Instant? = null,
    val deletedByCustomerAt: Instant? = null
)

/**
 * Ergebnis der idempotenten Konversationserstellung.
 *
 * @param conversation Neue oder bereits bestehende Konversation.
 * @param created `true`, wenn die Konversation neu gespeichert wurde.
 */
data class CreateConversationResult(
    val conversation: Conversation,
    val created: Boolean
)

/**
 * Domain-Modell einer Nachricht innerhalb einer Konversation.
 *
 * @param id Technische Nachrichten-ID.
 * @param conversationId Technische Konversations-ID.
 * @param senderUserId Technische ID des Senders.
 * @param body Nachrichtentext.
 * @param createdAt Sendezeitpunkt.
 * @param idempotencyKey Optionaler Client-Key gegen doppelte Nachrichten bei Retries.
 */
data class Message(
    val id: String,
    val conversationId: String,
    val senderUserId: String,
    val body: String,
    val createdAt: Instant,
    val idempotencyKey: String? = null
)

/**
 * Ergebnis des idempotenten Nachrichtenversands.
 *
 * `created = false` bedeutet, dass ein Retry mit demselben Idempotency-Key die
 * bereits gespeicherte Nachricht zurueckbekommt.
 *
 * @param message Neue oder bereits vorhandene Nachricht.
 * @param created `true`, wenn die Nachricht neu gespeichert wurde.
 */
data class SendMessageResult(
    val message: Message,
    val created: Boolean
)
