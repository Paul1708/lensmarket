package lensmarket.listing.domain.usecase

import lensmarket.listing.domain.port.AuthenticatedUser
import lensmarket.listing.domain.port.TokenVerifierPort

class AuthenticateUserUseCase(private val tokens: TokenVerifierPort) {
    fun authenticate(token: String?): AuthenticatedUser {
        if (token.isNullOrBlank()) throw ListingUseCaseUnauthorizedException("A valid bearer token is required")
        return tokens.verify(token)
            ?: throw ListingUseCaseUnauthorizedException("A valid bearer token is required")
    }
}
