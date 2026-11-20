package lensmarket.user.domain.usecase

import lensmarket.user.domain.model.CreateUserCommand
import lensmarket.user.domain.model.DEFAULT_USER_ROLE
import lensmarket.user.domain.model.User
import lensmarket.user.domain.model.USER_ROLES
import lensmarket.user.domain.port.UserRepositoryPort
import lensmarket.user.domain.port.ClockPort
import lensmarket.user.domain.port.IdGeneratorPort
import lensmarket.user.domain.port.PasswordHashPort
import lensmarket.user.domain.port.UserMetricsPort

private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

/**
 * Legt neue Nutzerkonten mit validierten Eingaben an.
 *
 * Der Use Case bereinigt Eingaben, validiert E-Mail, Passwort und Rolle, setzt
 * technische Systemfelder und speichert den Nutzer über den Repository-Port.
 *
 * @param users Persistenz-Port für Nutzerkonten.
 * @param passwordHashes Port zum Erzeugen des Passwort-Hashs.
 * @param metrics Port für fachliche Nutzermetriken.
 * @param clock Quelle für Systemzeitpunkte.
 * @param ids Generator technischer IDs.
 */
class CreateUserUseCase(
    private val users: UserRepositoryPort,
    private val passwordHashes: PasswordHashPort,
    private val metrics: UserMetricsPort,
    private val clock: ClockPort,
    private val ids: IdGeneratorPort
) {
    /**
     * Erstellt ein neues Nutzerkonto.
     *
     * @param command Fachliche Eingabe zum Anlegen des Nutzers.
     * @return Gespeicherter Nutzer mit technischer ID und Zeitstempeln.
     * @throws UserUseCaseValidationException Wenn Pflichtfelder, E-Mail, Passwort oder Rolle ungültig sind.
     * @throws UserUseCaseConflictException Wenn bereits ein Konto mit derselben E-Mail-Adresse existiert.
     */
    suspend fun create(command: CreateUserCommand): User {
        val email = validEmail(command.email)
        val displayName = requiredText(command.displayName, "displayName")
        val password = validPassword(command.password)
        val role = validRole(command.role)
        val now = clock.now()

        val user = User(
            id = ids.newId(),
            email = email,
            displayName = displayName,
            role = role,
            bio = optionalText(command.bio),
            location = optionalText(command.location),
            createdAt = now,
            updatedAt = now,
            passwordHash = passwordHashes.hash(password)
        )

        if (users.findUserByEmail(email) != null) {
            throw UserUseCaseConflictException("A user with this email already exists")
        }
        return users.save(user).also { metrics.userCreated(it.role) }
    }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw UserUseCaseValidationException("$fieldName must not be blank")
        }
        return trimmed
    }

    private fun optionalText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun validEmail(value: String): String {
        val email = requiredText(value, "email")
        if (!emailPattern.matches(email)) {
            throw UserUseCaseValidationException("email must be a valid email address")
        }
        return email.lowercase()
    }

    private fun validPassword(value: String): String {
        val password = requiredText(value, "password")
        if (password.length < 8) {
            throw UserUseCaseValidationException("password must contain at least 8 characters")
        }
        return password
    }

    private fun validRole(value: String): String {
        val role = value.trim().ifBlank { DEFAULT_USER_ROLE }.uppercase()
        if (role !in USER_ROLES) {
            throw UserUseCaseValidationException("role must be one of ${USER_ROLES.joinToString()}")
        }
        return role
    }
}
