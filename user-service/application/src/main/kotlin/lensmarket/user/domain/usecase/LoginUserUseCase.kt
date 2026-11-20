package lensmarket.user.domain.usecase

import lensmarket.user.domain.model.LoginUserCommand
import lensmarket.user.domain.model.User
import lensmarket.user.domain.port.UserRepositoryPort
import lensmarket.user.domain.port.PasswordHashPort
import lensmarket.user.domain.port.TokenIssuerPort

/**
 * Ergebnis eines erfolgreichen Logins.
 *
 * @param token Signiertes JWT für geschützte Endpunkte.
 * @param user Authentifizierter Nutzer.
 */
data class LoginUserResult(
    val token: String,
    val user: User
)

/**
 * Authentifiziert Nutzer über E-Mail-Adresse und Passwort.
 *
 * Der Use Case hält Fehlermeldungen bei falschen Login-Daten absichtlich allgemein,
 * damit nicht erkennbar ist, ob E-Mail oder Passwort falsch war.
 *
 * @param users Persistenz-Port für Nutzerkonten.
 * @param passwordHashes Port zur Passwortprüfung.
 * @param tokenIssuer Port zum Ausstellen des Tokens.
 */
class LoginUserUseCase(
    private val users: UserRepositoryPort,
    private val passwordHashes: PasswordHashPort,
    private val tokenIssuer: TokenIssuerPort
) {
    /**
     * Führt den Login aus und erzeugt bei Erfolg ein JWT.
     *
     * @param command Login-Eingabe mit E-Mail-Adresse und Passwort.
     * @return Login-Ergebnis mit Token und Nutzer.
     * @throws UserUseCaseValidationException Wenn Pflichtfelder fehlen.
     * @throws UserUseCaseUnauthorizedException Wenn E-Mail oder Passwort nicht passen.
     */
    suspend fun login(command: LoginUserCommand): LoginUserResult {
        val email = requiredText(command.email, "email").lowercase()
        val password = requiredText(command.password, "password")

        val user = users.findUserByEmail(email)
        if (user == null || !passwordHashes.verify(password, user.passwordHash)) {
            throw UserUseCaseUnauthorizedException("Invalid email or password")
        }
        return LoginUserResult(token = tokenIssuer.issueToken(user), user = user)
    }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw UserUseCaseValidationException("$fieldName must not be blank")
        }
        return trimmed
    }
}
