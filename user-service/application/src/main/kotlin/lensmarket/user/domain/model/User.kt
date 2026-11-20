package lensmarket.user.domain.model

import java.time.Instant

/**
 * Standardrolle fuer neu angelegte Nutzerkonten.
 */
const val DEFAULT_USER_ROLE = "CUSTOMER"

/**
 * Rolle fuer Kundinnen und Kunden.
 */
const val USER_ROLE_CUSTOMER = "CUSTOMER"

/**
 * Rolle fuer Fotografenprofile.
 */
const val USER_ROLE_PHOTOGRAPHER = "PHOTOGRAPHER"

/**
 * Alle fachlich erlaubten Nutzerrollen.
 */
val USER_ROLES = setOf(USER_ROLE_CUSTOMER, USER_ROLE_PHOTOGRAPHER)

/**
 * Command zum Anlegen eines Nutzerkontos.
 *
 * Das Command ist unabhängig vom HTTP-Request-DTO und bildet die Eingabe für den
 * CreateUserUseCase.
 *
 * @param email E-Mail-Adresse des neuen Kontos.
 * @param displayName Sichtbarer Anzeigename.
 * @param password Klartext-Passwort vor dem Hashing.
 * @param role Gewünschte Nutzerrolle.
 * @param bio Optionale Profilbeschreibung.
 * @param location Optionaler Standort.
 */
data class CreateUserCommand(
    val email: String,
    val displayName: String,
    val password: String,
    val role: String = DEFAULT_USER_ROLE,
    val bio: String? = null,
    val location: String? = null
)

/**
 * Command für die Anmeldung eines bestehenden Nutzers.
 *
 * @param email E-Mail-Adresse des Kontos.
 * @param password Klartext-Passwort aus dem Login.
 */
data class LoginUserCommand(
    val email: String,
    val password: String
)

/**
 * Command für partielle Profiländerungen.
 *
 * `null` bedeutet, dass das jeweilige Feld unverändert bleiben soll.
 *
 * @param displayName Neuer Anzeigename oder `null`.
 * @param bio Neue Profilbeschreibung oder `null`.
 * @param location Neuer Standort oder `null`.
 * @param role Neue Nutzerrolle oder `null`.
 */
data class PatchUserProfileCommand(
    val displayName: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val role: String? = null
)

/**
 * Fachliches Domain-Modell eines Nutzerkontos.
 *
 * Das Modell enthält keine JPA- oder REST-Annotationen und kann deshalb unabhängig
 * von Persistenz und HTTP-Schicht verwendet werden.
 *
 * @param id Technische Nutzer-ID.
 * @param email E-Mail-Adresse des Kontos.
 * @param displayName Sichtbarer Anzeigename.
 * @param role Fachliche Nutzerrolle.
 * @param bio Optionale Profilbeschreibung.
 * @param location Optionaler Standort.
 * @param createdAt Zeitpunkt der Kontoanlage.
 * @param updatedAt Zeitpunkt der letzten Profiländerung.
 * @param passwordHash Persistierter Passwort-Hash oder `null`.
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val bio: String?,
    val location: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val passwordHash: String? = null
)
