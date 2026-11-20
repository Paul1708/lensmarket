package lensmarket.user.domain.usecase

/**
 * Signalisiert ungültige fachliche Eingaben im User Service.
 *
 * Use-Case-Fehler bleiben frei von HTTP-Abhängigkeiten. Die Resource übersetzt sie in
 * passende Statuscodes.
 *
 * @param message Beschreibung der verletzten Validierungsregel.
 */
class UserUseCaseValidationException(message: String) : RuntimeException(message)

/**
 * Signalisiert, dass ein angefordertes Nutzerkonto nicht existiert.
 *
 * @param message Beschreibung des fehlenden Datensatzes.
 */
class UserUseCaseNotFoundException(message: String) : RuntimeException(message)

/**
 * Signalisiert einen fachlichen Datenkonflikt im User Service.
 *
 * Zum Beispiel darf eine E-Mail-Adresse nur einem Konto zugeordnet sein.
 *
 * @param message Beschreibung des Konflikts.
 */
class UserUseCaseConflictException(message: String) : RuntimeException(message)

/**
 * Signalisiert, dass der authentifizierte Nutzer die Aktion nicht ausführen darf.
 *
 * @param message Beschreibung der verletzten Berechtigungsregel.
 */
class UserUseCaseForbiddenException(message: String) : RuntimeException(message)

/**
 * Signalisiert fehlgeschlagene Authentifizierung im User Service.
 *
 * @param message Beschreibung des Authentifizierungsfehlers.
 */
class UserUseCaseUnauthorizedException(message: String) : RuntimeException(message)
