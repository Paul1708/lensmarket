package lensmarket.user.composition

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import lensmarket.user.domain.port.ClockPort
import lensmarket.user.domain.port.IdGeneratorPort
import lensmarket.user.domain.port.PasswordHashPort
import lensmarket.user.domain.port.TokenIssuerPort
import lensmarket.user.domain.port.TokenVerifierPort
import lensmarket.user.domain.port.UserMetricsPort
import lensmarket.user.domain.port.UserRepositoryPort
import lensmarket.user.domain.usecase.AuthenticateUserUseCase
import lensmarket.user.domain.usecase.CreateUserUseCase
import lensmarket.user.domain.usecase.GetUserUseCase
import lensmarket.user.domain.usecase.ListUsersUseCase
import lensmarket.user.domain.usecase.LoginUserUseCase
import lensmarket.user.domain.usecase.PatchUserProfileUseCase
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class SystemClockAdapter : ClockPort {
    override fun now(): Instant = Instant.now()
}

@ApplicationScoped
class UuidGeneratorAdapter : IdGeneratorPort {
    override fun newId(): String = UUID.randomUUID().toString()
}

@ApplicationScoped
class UserApplicationModule {
    @Produces @Singleton
    fun authenticate(tokens: TokenVerifierPort) = AuthenticateUserUseCase(tokens)

    @Produces @Singleton
    fun createUser(
        users: UserRepositoryPort,
        hashes: PasswordHashPort,
        metrics: UserMetricsPort,
        clock: ClockPort,
        ids: IdGeneratorPort
    ) = CreateUserUseCase(users, hashes, metrics, clock, ids)

    @Produces @Singleton
    fun loginUser(users: UserRepositoryPort, hashes: PasswordHashPort, tokens: TokenIssuerPort) =
        LoginUserUseCase(users, hashes, tokens)

    @Produces @Singleton
    fun listUsers(users: UserRepositoryPort) = ListUsersUseCase(users)

    @Produces @Singleton
    fun getUser(users: UserRepositoryPort) = GetUserUseCase(users)

    @Produces @Singleton
    fun patchUser(users: UserRepositoryPort, metrics: UserMetricsPort, clock: ClockPort) =
        PatchUserProfileUseCase(users, metrics, clock)
}
