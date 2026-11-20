package lensmarket.user.resources

import org.eclipse.microprofile.openapi.annotations.media.Schema
import lensmarket.user.domain.model.CreateUserCommand
import lensmarket.user.domain.model.DEFAULT_USER_ROLE
import lensmarket.user.domain.model.LoginUserCommand
import lensmarket.user.domain.model.PatchUserProfileCommand
import lensmarket.user.domain.model.User
import lensmarket.user.domain.usecase.LoginUserResult
import java.time.Instant

/**
 * Request-Body zum Anlegen eines neuen Nutzerkontos.
 *
 * @param email E-Mail-Adresse des neuen Nutzers.
 * @param displayName Sichtbarer Anzeigename.
 * @param password Klartext-Passwort, das nur zur Hash-Erzeugung verwendet wird.
 * @param role Gewünschte Nutzerrolle oder Standardrolle.
 * @param bio Optionale Profilbeschreibung.
 * @param location Optionaler Standort.
 */
data class CreateUserRequest(
    @field:Schema(format = "email") val email: String,
    val displayName: String,
    @field:Schema(minLength = 8, writeOnly = true) val password: String,
    @field:Schema(enumeration = ["CUSTOMER", "PHOTOGRAPHER"], defaultValue = "CUSTOMER") val role: String = DEFAULT_USER_ROLE,
    val bio: String? = null,
    val location: String? = null
) {
    /**
     * Wandelt den HTTP-Request in ein Domain-Command um.
     *
     * @return Command für den CreateUserUseCase.
     */
    fun toCommand(): CreateUserCommand =
        CreateUserCommand(
            email = email,
            displayName = displayName,
            password = password,
            role = role,
            bio = bio,
            location = location
        )
}

/**
 * Request-Body für den Login eines bestehenden Nutzers.
 *
 * @param email E-Mail-Adresse des Nutzerkontos.
 * @param password Klartext-Passwort aus dem Login-Formular.
 */
data class LoginUserRequest(
    @field:Schema(format = "email") val email: String,
    @field:Schema(minLength = 1, writeOnly = true) val password: String
) {
    /**
     * Wandelt den Login-Request in ein Domain-Command um.
     *
     * @return Command für den LoginUserUseCase.
     */
    fun toCommand(): LoginUserCommand =
        LoginUserCommand(email = email, password = password)
}

/**
 * Request-Body für partielle Profiländerungen.
 *
 * Nicht gesetzte Felder bleiben unverändert.
 *
 * @param displayName Neuer Anzeigename oder `null`.
 * @param bio Neue Profilbeschreibung oder `null`.
 * @param location Neuer Standort oder `null`.
 * @param role Neue Nutzerrolle oder `null`.
 */
@Schema(minProperties = 1, description = "Mindestens eines der vier Felder muss vorhanden sein.")
data class PatchUserProfileRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val location: String? = null,
    @field:Schema(enumeration = ["CUSTOMER", "PHOTOGRAPHER"]) val role: String? = null
) {
    /**
     * Wandelt den PATCH-Request in ein Domain-Command um.
     *
     * @return Command für den PatchUserProfileUseCase.
     */
    fun toCommand(): PatchUserProfileCommand =
        PatchUserProfileCommand(
            displayName = displayName,
            bio = bio,
            location = location,
            role = role
        )
}

/**
 * Antwort nach erfolgreichem Login.
 *
 * @param token HMAC-signiertes JWT für geschützte Endpunkte.
 * @param user Private Accountdaten des eingeloggten Nutzers.
 */
data class LoginUserResponse(
    val token: String,
    val user: PrivateUserResponse
)

/**
 * Öffentlich sichtbares Nutzerprofil.
 *
 * Diese Antwort enthält bewusst keine E-Mail-Adresse und keine internen Authentifizierungsdaten.
 *
 * @param id Technische Nutzer-ID.
 * @param displayName Sichtbarer Anzeigename.
 * @param role Rolle des Nutzers.
 * @param bio Optionale Profilbeschreibung.
 * @param location Optionaler Standort.
 * @param createdAt Zeitpunkt der Kontoanlage.
 * @param updatedAt Zeitpunkt der letzten Profiländerung.
 */
data class PublicUserResponse(
    val id: String,
    val displayName: String,
    val role: String,
    val bio: String?,
    val location: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Private Accountantwort für den angemeldeten Nutzer.
 *
 * Diese Antwort wird nur nach Registrierung, Login, `/me` oder eigenen Profiländerungen
 * verwendet.
 *
 * @param id Technische Nutzer-ID.
 * @param email E-Mail-Adresse des Kontos.
 * @param displayName Sichtbarer Anzeigename.
 * @param role Rolle des Nutzers.
 * @param bio Optionale Profilbeschreibung.
 * @param location Optionaler Standort.
 * @param createdAt Zeitpunkt der Kontoanlage.
 * @param updatedAt Zeitpunkt der letzten Profiländerung.
 */
data class PrivateUserResponse(
    val id: String,
    @field:Schema(format = "email") val email: String,
    val displayName: String,
    val role: String,
    val bio: String?,
    val location: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Erstellt aus einem Domain-Nutzer die öffentliche API-Antwort.
 *
 * @return Öffentliches Nutzerprofil ohne private Accountdaten.
 */
fun User.toPublicResponse(): PublicUserResponse =
    PublicUserResponse(
        id = id,
        displayName = displayName,
        role = role,
        bio = bio,
        location = location,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

/**
 * Erstellt aus einem Domain-Nutzer die private API-Antwort.
 *
 * @return Private Accountdaten für den angemeldeten Nutzer.
 */
fun User.toPrivateResponse(): PrivateUserResponse =
    PrivateUserResponse(
        id = id,
        email = email,
        displayName = displayName,
        role = role,
        bio = bio,
        location = location,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

/**
 * Wandelt das Login-Ergebnis der Domäne in die HTTP-Antwort um.
 *
 * @return Login-Antwort mit Token und privatem Nutzerprofil.
 */
fun LoginUserResult.toResponse(): LoginUserResponse =
    LoginUserResponse(
        token = token,
        user = user.toPrivateResponse()
    )
