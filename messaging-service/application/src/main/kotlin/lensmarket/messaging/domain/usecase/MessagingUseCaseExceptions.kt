package lensmarket.messaging.domain.usecase

/**
 * Signalisiert ungültige fachliche Eingaben im Messaging Service.
 *
 * Use-Case-Fehler bleiben frei von HTTP-Abhängigkeiten. Die Resource übersetzt sie in
 * passende Statuscodes.
 *
 * @param message Beschreibung der verletzten Validierungsregel.
 */
class MessagingUseCaseValidationException(message: String) : RuntimeException(message)

/**
 * Signalisiert, dass eine angeforderte Konversation oder Anzeige nicht existiert.
 *
 * @param message Beschreibung des fehlenden Datensatzes.
 */
class MessagingUseCaseNotFoundException(message: String) : RuntimeException(message)

/**
 * Signalisiert, dass der authentifizierte Nutzer die Aktion nicht ausführen darf.
 *
 * @param message Beschreibung der verletzten Berechtigungsregel.
 */
class MessagingUseCaseForbiddenException(message: String) : RuntimeException(message)

class MessagingUseCaseUnauthorizedException(message: String) : RuntimeException(message)

/** Adapter-neutraler Konflikt, den Persistenzadapter aus Unique-Constraints ableiten. */
class MessagingPersistenceConflictException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Temporärer Ausfall eines synchron benötigten Fremdservices. */
class MessagingDependencyUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Zeitüberschreitung eines synchron benötigten Fremdservices. */
class MessagingDependencyTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
