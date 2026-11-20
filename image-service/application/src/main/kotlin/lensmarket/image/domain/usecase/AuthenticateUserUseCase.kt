package lensmarket.image.domain.usecase

import lensmarket.image.domain.port.AuthenticatedUser
import lensmarket.image.domain.port.TokenVerifierPort

class AuthenticateUserUseCase(private val tokens: TokenVerifierPort) {
    fun authenticate(token: String?): AuthenticatedUser {
        if (token.isNullOrBlank()) throw ImageUseCaseUnauthorizedException("A valid bearer token is required")
        return tokens.verify(token) ?: throw ImageUseCaseUnauthorizedException("A valid bearer token is required")
    }
}
