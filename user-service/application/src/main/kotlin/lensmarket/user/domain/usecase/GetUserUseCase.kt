package lensmarket.user.domain.usecase

import lensmarket.user.domain.model.User
import lensmarket.user.domain.port.UserRepositoryPort

/**
 * Lädt ein Nutzerkonto anhand seiner technischen ID.
 *
 * Der Use Case entscheidet fachlich, dass eine fehlende ID als Nicht-gefunden-Fall
 * behandelt wird.
 *
 * @param users Persistenz-Port für Nutzerkonten.
 */
class GetUserUseCase(
    private val users: UserRepositoryPort
) {
    /**
     * Sucht einen Nutzer anhand der ID.
     *
     * @param id Technische Nutzer-ID.
     * @return Gefundener Nutzer.
     * @throws UserUseCaseValidationException Wenn die ID leer ist.
     * @throws UserUseCaseNotFoundException Wenn kein Nutzer mit dieser ID existiert.
     */
    suspend fun get(id: String): User {
        val userId = requiredText(id, "id")
        return users.findUserById(userId)
            ?: throw UserUseCaseNotFoundException("User $userId not found")
    }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw UserUseCaseValidationException("$fieldName must not be blank")
        }
        return trimmed
    }
}
