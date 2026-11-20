package lensmarket.user.domain.usecase

import lensmarket.user.domain.model.User
import lensmarket.user.domain.port.UserRepositoryPort

/**
 * Listet alle gespeicherten Nutzerkonten.
 *
 * Die Resource greift dadurch nicht direkt auf Persistenz zu, auch wenn dieser
 * lesende Use Case bewusst schlank bleibt.
 *
 * @param users Persistenz-Port für Nutzerkonten.
 */
class ListUsersUseCase(
    private val users: UserRepositoryPort
) {
    /**
     * Lädt die Nutzerliste.
     *
     * @return Liste der gespeicherten Nutzer.
     */
    suspend fun list(): List<User> = users.listUsers()
}
