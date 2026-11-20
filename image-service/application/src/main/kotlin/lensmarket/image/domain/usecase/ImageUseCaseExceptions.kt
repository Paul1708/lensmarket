package lensmarket.image.domain.usecase

/**
 * Signalisiert ungültige fachliche Eingaben im Image Service.
 *
 * Use-Case-Fehler bleiben frei von HTTP-Abhängigkeiten. Die Resource übersetzt sie in
 * passende Statuscodes.
 *
 * @param message Beschreibung der verletzten Validierungsregel.
 */
class ImageUseCaseValidationException(message: String) : RuntimeException(message)

/**
 * Signalisiert, dass ein angefordertes Bild nicht existiert.
 *
 * @param message Beschreibung des fehlenden Datensatzes.
 */
class ImageUseCaseNotFoundException(message: String) : RuntimeException(message)

/**
 * Signalisiert, dass der authentifizierte Nutzer die Aktion nicht ausführen darf.
 *
 * @param message Beschreibung der verletzten Berechtigungsregel.
 */
class ImageUseCaseForbiddenException(message: String) : RuntimeException(message)

class ImageUseCaseUnauthorizedException(message: String) : RuntimeException(message)

/** Temporärer Ausfall eines synchron benötigten Fremdservices. */
class ImageDependencyUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Zeitüberschreitung eines synchron benötigten Fremdservices. */
class ImageDependencyTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
