package lensmarket.user.auth

import jakarta.enterprise.context.ApplicationScoped
import lensmarket.user.domain.port.PasswordHashPort
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
private const val HASH_ITERATIONS = 120_000
private const val SALT_BYTES = 16
private const val HASH_BITS = 256

/**
 * Erzeugt und prüft Passwort-Hashes für lokale Nutzerkonten.
 *
 * Passwörter werden mit PBKDF2, zufälligem Salt und einer festen Iterationszahl
 * verarbeitet. Klartext-Passwörter werden nicht gespeichert.
 */
@ApplicationScoped
class PasswordHasher : PasswordHashPort {
    private val random = SecureRandom()

    /**
     * Erstellt einen speicherbaren Hash für ein Klartext-Passwort.
     *
     * Das Ergebnis enthält Algorithmus, Iterationszahl, Salt und Hashwert in einem
     * durch `$` getrennten Format.
     *
     * @param password Klartext-Passwort aus dem Registrierungs- oder Änderungsprozess.
     * @return Persistierbarer Passwort-Hash.
     */
    override fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)
        val hash = pbkdf2(password, salt, HASH_ITERATIONS)
        return listOf(
            "pbkdf2_sha256",
            HASH_ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash)
        ).joinToString("\$")
    }

    /**
     * Prüft ein Klartext-Passwort gegen einen gespeicherten Hash.
     *
     * Der berechnete Hash wird zeitkonstant mit dem gespeicherten Wert verglichen,
     * damit keine einfachen Timing-Informationen nach außen gelangen.
     *
     * @param password Klartext-Passwort aus dem Login-Request.
     * @param storedHash Persistierter Passwort-Hash oder `null`.
     * @return `true`, wenn Passwort und Hash zusammenpassen.
     */
    override fun verify(password: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        val parts = storedHash.split("\$")
        if (parts.size != 4 || parts[0] != "pbkdf2_sha256") return false

        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = Base64.getDecoder().decode(parts[2])
        val expected = Base64.getDecoder().decode(parts[3])
        val actual = pbkdf2(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    /**
     * Führt die PBKDF2-Ableitung für Passwort, Salt und Iterationszahl aus.
     *
     * @param password Klartext-Passwort.
     * @param salt Zufälliger Salt des gespeicherten Hashs.
     * @param iterations Anzahl der PBKDF2-Iterationen.
     * @return Abgeleiteter Hashwert als Byte-Array.
     */
    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS)
        return SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(spec).encoded
    }
}
