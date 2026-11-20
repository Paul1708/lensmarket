package lensmarket.user.domain.usecase

import lensmarket.user.domain.model.PatchUserProfileCommand
import lensmarket.user.domain.model.USER_ROLES
import lensmarket.user.domain.model.User
import lensmarket.user.domain.port.UserRepositoryPort
import lensmarket.user.domain.port.ClockPort
import lensmarket.user.domain.port.UserMetricsPort

/**
 * Aktualisiert editierbare Profilfelder eines Nutzerkontos.
 *
 * Nutzer dürfen nur ihr eigenes Profil ändern. Admin- oder Fremdbearbeitung ist
 * nicht Teil des MVP.
 *
 * @param users Persistenz-Port für Nutzerkonten.
 */
class PatchUserProfileUseCase(
    private val users: UserRepositoryPort,
    private val metrics: UserMetricsPort,
    private val clock: ClockPort
) {
    /**
     * Wendet eine partielle Profiländerung an.
     *
     * Nicht gesetzte Felder bleiben unverändert. Der Zeitstempel `updatedAt` wird bei
     * erfolgreicher Änderung erneuert.
     *
     * @param userId Ziel-ID des zu ändernden Profils.
     * @param currentUserId ID des authentifizierten Nutzers.
     * @param command Gewünschte Profiländerungen.
     * @return Aktualisiertes Nutzerkonto.
     * @throws UserUseCaseValidationException Wenn IDs oder Änderungsdaten ungültig sind.
     * @throws UserUseCaseForbiddenException Wenn ein fremdes Profil geändert werden soll.
     * @throws UserUseCaseNotFoundException Wenn das Zielprofil nicht existiert.
     */
    suspend fun patch(userId: String, currentUserId: String, command: PatchUserProfileCommand): User {
        val targetUserId = requiredText(userId, "id")
        val authenticatedUserId = requiredText(currentUserId, "currentUserId")
        if (targetUserId != authenticatedUserId) {
            throw UserUseCaseForbiddenException("Only the account owner can update this profile")
        }
        if (!command.hasChanges()) {
            throw UserUseCaseValidationException("At least one profile field must be provided")
        }

        val existing = users.findUserById(targetUserId)
            ?: throw UserUseCaseNotFoundException("User $targetUserId not found")
        val updated = existing.copy(
            displayName = command.displayName?.let { requiredText(it, "displayName") } ?: existing.displayName,
            bio = if (command.bio != null) optionalText(command.bio) else existing.bio,
            location = if (command.location != null) optionalText(command.location) else existing.location,
            role = command.role?.let { validRole(it) } ?: existing.role,
            updatedAt = clock.now()
        )
        return (users.updateProfile(updated)
            ?: throw UserUseCaseNotFoundException("User $targetUserId not found"))
            .also { metrics.userUpdated() }
    }

    private fun PatchUserProfileCommand.hasChanges(): Boolean =
        displayName != null || bio != null || location != null || role != null

    private fun optionalText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw UserUseCaseValidationException("$fieldName must not be blank")
        }
        return trimmed
    }

    private fun validRole(value: String): String {
        val role = requiredText(value, "role").uppercase()
        if (role !in USER_ROLES) {
            throw UserUseCaseValidationException("role must be one of ${USER_ROLES.joinToString()}")
        }
        return role
    }
}
