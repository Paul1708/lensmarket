package lensmarket.user.domain.port

import lensmarket.user.domain.model.User

/**
 * Persistenz-Port für Nutzerkonten.
 *
 * Use Cases hängen an dieser Abstraktion und nicht direkt an Panache oder JPA.
 */
interface UserRepositoryPort {
    /**
     * Speichert ein neues Nutzerkonto.
     *
     * @param user Zu persistierendes Domain-Modell.
     * @return Gespeicherter Nutzer.
     */
    suspend fun save(user: User): User

    /**
     * Aktualisiert die änderbaren Profilfelder eines Nutzers.
     *
     * @param user Domain-Modell mit den neuen Profilwerten.
     * @return Aktualisierter Nutzer oder `null`, wenn die ID nicht existiert.
     */
    suspend fun updateProfile(user: User): User?

    /**
     * Listet alle Nutzerkonten.
     *
     * @return Liste der gespeicherten Nutzer.
     */
    suspend fun listUsers(): List<User>

    /**
     * Sucht einen Nutzer anhand seiner technischen ID.
     *
     * @param id Technische Nutzer-ID.
     * @return Gefundener Nutzer oder `null`.
     */
    suspend fun findUserById(id: String): User?

    /**
     * Sucht einen Nutzer anhand seiner E-Mail-Adresse.
     *
     * @param email Normalisierte E-Mail-Adresse.
     * @return Gefundener Nutzer oder `null`.
     */
    suspend fun findUserByEmail(email: String): User?
}
