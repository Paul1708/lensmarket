package lensmarket.user.auth

import jakarta.enterprise.context.ApplicationScoped
import lensmarket.user.domain.model.User
import lensmarket.user.domain.port.TokenIssuerPort
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.NumericDate
import org.jose4j.keys.HmacKey
import java.nio.charset.StandardCharsets

/** Stellt HS256-JWTs nach dem gemeinsamen LensMarket-Claimvertrag aus. */
@ApplicationScoped
class JwtTokenService(
    @param:ConfigProperty(name = "lensmarket.auth.jwt-secret") private val secret: String,
    @param:ConfigProperty(name = "lensmarket.auth.token-ttl-seconds", defaultValue = "7200")
    private val tokenTtlSeconds: Long,
    @param:ConfigProperty(name = "lensmarket.auth.jwt-issuer") private val issuer: String,
    @param:ConfigProperty(name = "lensmarket.auth.jwt-audience") private val audience: String
) : TokenIssuerPort {
    override fun issueToken(user: User): String {
        require(tokenTtlSeconds > 0) { "Token TTL must be positive" }
        val now = NumericDate.now()
        val claims = JwtClaims().apply {
            setIssuer(this@JwtTokenService.issuer)
            setAudience(this@JwtTokenService.audience)
            setSubject(user.id)
            setIssuedAt(now)
            setNotBefore(now)
            setExpirationTime(NumericDate.fromSeconds(now.value + tokenTtlSeconds))
            setGeneratedJwtId()
            setStringClaim("email", user.email)
            setStringClaim("role", user.role)
        }
        return JsonWebSignature().apply {
            payload = claims.toJson()
            key = HmacKey(secret.toByteArray(StandardCharsets.UTF_8))
            algorithmHeaderValue = AlgorithmIdentifiers.HMAC_SHA256
            setHeader("typ", "JWT")
        }.compactSerialization
    }
}
