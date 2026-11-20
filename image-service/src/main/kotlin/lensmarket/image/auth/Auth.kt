package lensmarket.image.auth

import jakarta.enterprise.context.ApplicationScoped
import lensmarket.image.domain.port.AuthenticatedUser
import lensmarket.image.domain.port.TokenVerifierPort
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.HmacKey
import java.nio.charset.StandardCharsets

/** Validiert den gemeinsamen JWT-Vertrag mit einer JOSE-Standardbibliothek. */
@ApplicationScoped
class JwtTokenVerifier(
    @param:ConfigProperty(name = "lensmarket.auth.jwt-secret") private val secret: String,
    @param:ConfigProperty(name = "lensmarket.auth.jwt-issuer") private val issuer: String,
    @param:ConfigProperty(name = "lensmarket.auth.jwt-audience") private val audience: String
) : TokenVerifierPort {
    private val consumer by lazy {
        JwtConsumerBuilder()
            .setEnableRequireIntegrity()
            .setRequireExpirationTime()
            .setRequireIssuedAt()
            .setRequireNotBefore()
            .setRequireSubject()
            .setRequireJwtId()
            .setExpectedIssuer(issuer)
            .setExpectedAudience(audience)
            .setExpectedType(true, "JWT")
            .setAllowedClockSkewInSeconds(30)
            .setVerificationKey(HmacKey(secret.toByteArray(StandardCharsets.UTF_8)))
            .setJwsAlgorithmConstraints(ConstraintType.PERMIT, AlgorithmIdentifiers.HMAC_SHA256)
            .build()
    }

    override fun verify(token: String): AuthenticatedUser? = runCatching {
        val claims = consumer.processToClaims(token)
        AuthenticatedUser(
            id = claims.subject.requiredClaim("sub"),
            email = claims.getStringClaimValue("email").requiredClaim("email"),
            role = claims.getStringClaimValue("role").requiredClaim("role")
        )
    }.getOrNull()

    private fun String?.requiredClaim(name: String): String =
        this?.takeIf(String::isNotBlank) ?: throw IllegalArgumentException("Missing $name")
}
