package lensmarket.user.data.repository

import io.quarkus.hibernate.reactive.panache.kotlin.Panache
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheRepositoryBase
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.user.data.entity.UserEntity
import lensmarket.user.domain.model.User
import lensmarket.user.domain.port.UserRepositoryPort
import lensmarket.user.domain.usecase.UserUseCaseConflictException

/**
 * Panache-basierter Persistenzadapter für Nutzerkonten.
 *
 * Das Repository kapselt Hibernate Reactive mit Panache und erfüllt den
 * UserRepositoryPort der Domänenschicht.
 */
@ApplicationScoped
class UserRepository : PanacheRepositoryBase<UserEntity, String>, UserRepositoryPort {
    /**
     * Wandelt eine JPA-Entity in das Domain-Modell um.
     *
     * @param entity Persistenzmodell aus der Datenbank.
     * @return Fachliches Nutzer-Modell.
     */
    private fun toDomain(entity: UserEntity): User =
        User(
            id = entity.id,
            email = entity.email,
            displayName = entity.displayName,
            role = entity.role,
            bio = entity.bio,
            location = entity.location,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            passwordHash = entity.passwordHash
        )

    /**
     * Wandelt ein Domain-Modell in eine neue JPA-Entity um.
     *
     * @param user Fachliches Nutzer-Modell.
     * @return Persistenzmodell für Hibernate.
     */
    private fun toEntity(user: User): UserEntity =
        UserEntity().apply {
            id = user.id
            email = user.email
            displayName = user.displayName
            role = user.role
            bio = user.bio
            location = user.location
            passwordHash = user.passwordHash
            createdAt = user.createdAt
            updatedAt = user.updatedAt
        }

    /**
     * Speichert ein neues Nutzerkonto.
     *
     * @param user Zu speichernder Nutzer.
     * @return Gespeicherter Nutzer.
     */
    override suspend fun save(user: User): User = try {
        Panache.withTransaction { persist(toEntity(user)).replaceWith(user) }.awaitSuspending()
    } catch (failure: Throwable) {
        if (failure.isUniqueConstraintViolation()) {
            throw UserUseCaseConflictException("A user with this email already exists")
        }
        throw failure
    }

    /**
     * Aktualisiert die editierbaren Profilfelder eines Nutzers.
     *
     * E-Mail-Adresse, Passwort-Hash und Anlagezeitpunkt bleiben dabei unverändert.
     *
     * @param user Domain-Modell mit neuen Profilwerten.
     * @return Aktualisierter Nutzer oder `null`, wenn kein Datensatz existiert.
     */
    override suspend fun updateProfile(user: User): User? =
        Panache.withTransaction {
            findById(user.id)
            .onItem().transform { entity ->
                if (entity == null) {
                    null
                } else {
                    entity.displayName = user.displayName
                    entity.bio = user.bio
                    entity.location = user.location
                    entity.role = user.role
                    entity.updatedAt = user.updatedAt
                    toDomain(entity)
                }
            }
        }.awaitSuspending()

    /**
     * Lädt alle Nutzer sortiert nach Anlagezeitpunkt.
     *
     * @return Liste der gespeicherten Nutzer als Domain-Modelle.
     */
    override suspend fun listUsers(): List<User> =
        Panache.withSession { list("order by createdAt")
            .onItem().transform { users -> users.map { toDomain(it) } }
        }.awaitSuspending()

    /**
     * Sucht einen Nutzer anhand der technischen ID.
     *
     * `null` bedeutet hier bewusst „nicht gefunden“ und wird in der Resource-Schicht
     * in HTTP 404 übersetzt.
     *
     * @param id Technische Nutzer-ID.
     * @return Gefundener Nutzer oder `null`.
     */
    override suspend fun findUserById(id: String): User? =
        Panache.withSession { findById(id)
            .onItem().transform { user -> user?.let { toDomain(it) } }
        }.awaitSuspending()

    /**
     * Sucht einen Nutzer anhand seiner E-Mail-Adresse.
     *
     * @param email Normalisierte E-Mail-Adresse.
     * @return Gefundener Nutzer oder `null`.
     */
    override suspend fun findUserByEmail(email: String): User? =
        Panache.withSession { find("email", email)
            .firstResult()
            .onItem().transform { user -> user?.let { toDomain(it) } }
        }.awaitSuspending()

    private fun Throwable.isUniqueConstraintViolation(): Boolean =
        generateSequence(this) { it.cause }.any { cause ->
            val text = "${cause::class.qualifiedName} ${cause.message}".lowercase()
            "duplicate key" in text || "unique constraint" in text || "constraintviolation" in text
        }
}
