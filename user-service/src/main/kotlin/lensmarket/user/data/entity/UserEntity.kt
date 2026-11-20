package lensmarket.user.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * JPA-Entity für die Tabelle `users`.
 *
 * Die Entity bildet das Flyway-Schema ab. Öffentliche und private JSON-Modelle
 * liegen getrennt in der Resource-Schicht.
 */
@Entity
@Table(name = "users")
class UserEntity {
    /**
     * Technische Nutzer-ID.
     */
    @Id
    @Column(length = 36, nullable = false)
    lateinit var id: String

    /**
     * Eindeutige E-Mail-Adresse des Nutzers.
     */
    @Column(length = 255, nullable = false, unique = true)
    lateinit var email: String

    /**
     * Sichtbarer Anzeigename.
     */
    @Column(name = "display_name", length = 120, nullable = false)
    lateinit var displayName: String

    /**
     * Fachliche Nutzerrolle.
     */
    @Column(length = 40, nullable = false)
    lateinit var role: String

    /**
     * Optionaler Profiltext.
     */
    @Column(columnDefinition = "TEXT")
    var bio: String? = null

    /**
     * Optionaler Standort.
     */
    @Column(length = 120)
    var location: String? = null

    /**
     * Persistierter Passwort-Hash des Kontos.
     *
     * Der Wert ist intern und wird nie in API-Antworten ausgegeben.
     */
    @Column(name = "password_hash", columnDefinition = "TEXT")
    var passwordHash: String? = null

    /**
     * Zeitpunkt der Kontoanlage.
     *
     * PostgreSQL `TIMESTAMPTZ` wird in Kotlin als `Instant` abgebildet.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    lateinit var createdAt: Instant

    /**
     * Zeitpunkt der letzten Profiländerung.
     *
     * Logins ändern diesen Wert nicht.
     */
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    lateinit var updatedAt: Instant
}
