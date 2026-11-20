package lensmarket.user.domain.usecase

import lensmarket.user.domain.port.AuthenticatedUser
import lensmarket.user.domain.port.TokenVerifierPort

class AuthenticateUserUseCase(
    private val tokens: TokenVerifierPort
) {
    fun authenticate(token: String?): AuthenticatedUser {
        if (token.isNullOrBlank()) {
            throw UserUseCaseUnauthorizedException("A valid bearer token is required")
        }
        return tokens.verify(token)
            ?: throw UserUseCaseUnauthorizedException("A valid bearer token is required")
    }
}
