package lensmarket.messaging.domain.usecase

import lensmarket.messaging.domain.port.AuthenticatedUser
import lensmarket.messaging.domain.port.TokenVerifierPort

class AuthenticateUserUseCase(private val tokens: TokenVerifierPort) {
    fun authenticate(token: String?): AuthenticatedUser {
        if (token.isNullOrBlank()) throw MessagingUseCaseUnauthorizedException("A valid bearer token is required")
        return tokens.verify(token) ?: throw MessagingUseCaseUnauthorizedException("A valid bearer token is required")
    }
}
