package lensmarket.user.auth

import lensmarket.user.domain.model.User
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.NumericDate
import org.jose4j.keys.HmacKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

class JwtTokenServiceTest {
    private val secret = "test-jwt-secret-that-is-deliberately-longer-than-sixty-four-bytes-for-all-hmac-tests"
    private val issuer = "lensmarket-auth"
    private val audience = "lensmarket-services"
    private val verifier = JwtTokenVerifier(secret, issuer, audience)

    @Test
    fun `issued token satisfies the complete JWT contract`() {
        val now = Instant.now()
        val user = User(
            id = UUID.randomUUID().toString(),
            email = "jwt@example.test",
            displayName = "JWT Test",
            role = "CUSTOMER",
            bio = null,
            location = null,
            createdAt = now,
            updatedAt = now
        )

        val token = JwtTokenService(secret, 3600, issuer, audience).issueToken(user)
        val authenticated = verifier.verify(token)

        assertEquals(user.id, authenticated?.id)
        assertEquals(user.email, authenticated?.email)
        assertEquals(user.role, authenticated?.role)
        assertTrue(token.startsWith("eyJ"))
    }

    @Test
    fun `algorithm issuer audience times claims and signature are enforced`() {
        val valid = signedToken()

        assertNull(verifier.verify(tamperSignature(valid)))
        assertNull(verifier.verify(signedToken(tokenIssuer = "other-issuer")))
        assertNull(verifier.verify(signedToken(tokenAudience = "other-audience")))
        assertNull(verifier.verify(signedToken(expiresAt = epoch() - 60, issuedAt = epoch() - 3600, notBefore = epoch() - 3600)))
        assertNull(verifier.verify(signedToken(notBefore = epoch() + 3600)))
        assertNull(verifier.verify(signedToken(role = null)))
        assertNull(verifier.verify(signedToken(algorithm = AlgorithmIdentifiers.HMAC_SHA512)))
    }

    private fun signedToken(
        tokenIssuer: String = issuer,
        tokenAudience: String = audience,
        issuedAt: Long = epoch(),
        notBefore: Long = epoch(),
        expiresAt: Long = epoch() + 3600,
        role: String? = "CUSTOMER",
        algorithm: String = AlgorithmIdentifiers.HMAC_SHA256
    ): String {
        val claims = JwtClaims().apply {
            setIssuer(tokenIssuer)
            setAudience(tokenAudience)
            setSubject("user-1")
            setIssuedAt(NumericDate.fromSeconds(issuedAt))
            setNotBefore(NumericDate.fromSeconds(notBefore))
            setExpirationTime(NumericDate.fromSeconds(expiresAt))
            setGeneratedJwtId()
            setStringClaim("email", "jwt@example.test")
            role?.let { setStringClaim("role", it) }
        }
        return JsonWebSignature().apply {
            payload = claims.toJson()
            key = HmacKey(secret.toByteArray(StandardCharsets.UTF_8))
            algorithmHeaderValue = algorithm
            setHeader("typ", "JWT")
        }.compactSerialization
    }

    private fun tamperSignature(token: String): String {
        val parts = token.split('.')
        val signature = parts[2]
        val replacement = if (signature.first() == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.$replacement${signature.drop(1)}"
    }

    private fun epoch(): Long = Instant.now().epochSecond
}
