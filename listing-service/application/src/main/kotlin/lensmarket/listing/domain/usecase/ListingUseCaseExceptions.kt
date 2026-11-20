package lensmarket.listing.domain.usecase

/**
 * Signalisiert ungültige fachliche Eingaben im Listing Service.
 *
 * Use-Case-Fehler bleiben frei von HTTP-Abhängigkeiten. Die Resource übersetzt sie in
 * passende Statuscodes.
 *
 * @param message Beschreibung der verletzten Validierungsregel.
 */
class ListingUseCaseValidationException(message: String) : RuntimeException(message)

/**
 * Signalisiert, dass eine angeforderte Anzeige nicht existiert oder gelöscht ist.
 *
 * @param message Beschreibung des fehlenden Datensatzes.
 */
class ListingUseCaseNotFoundException(message: String) : RuntimeException(message)

/**
 * Signalisiert, dass der authentifizierte Nutzer die Aktion nicht ausführen darf.
 *
 * @param message Beschreibung der verletzten Berechtigungsregel.
 */
class ListingUseCaseForbiddenException(message: String) : RuntimeException(message)

class ListingUseCaseUnauthorizedException(message: String) : RuntimeException(message)
