package lensmarket.user.domain.usecase

import kotlinx.coroutines.test.runTest
import lensmarket.user.domain.model.CreateUserCommand
import lensmarket.user.domain.model.LoginUserCommand
import lensmarket.user.domain.model.PatchUserProfileCommand
import lensmarket.user.domain.model.User
import lensmarket.user.domain.port.ClockPort
import lensmarket.user.domain.port.IdGeneratorPort
import lensmarket.user.domain.port.PasswordHashPort
import lensmarket.user.domain.port.TokenIssuerPort
import lensmarket.user.domain.port.UserMetricsPort
import lensmarket.user.domain.port.UserRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.time.Instant

class UserUseCaseTest {
    private val now = Instant.parse("2026-07-13T10:00:00Z")

    @Test
    fun `create normalizes hashes persists and records metrics`() = runTest {
        val fixture = Fixture()
        val created = fixture.create.create(
            CreateUserCommand(" FOTO@EXAMPLE.COM ", " Foto ", "sehr-geheim", "photographer")
        )
        assertEquals("user-1", created.id)
        assertEquals("foto@example.com", created.email)
        assertEquals("PHOTOGRAPHER", created.role)
        assertEquals("hash:sehr-geheim", created.passwordHash)
        assertEquals(listOf("PHOTOGRAPHER"), fixture.metrics.createdRoles)
    }

    @Test
    fun `create rejects invalid and duplicate accounts`() = runTest {
        val fixture = Fixture()
        expectFailure<UserUseCaseValidationException> {
            fixture.create.create(CreateUserCommand("invalid", "Foto", "sehr-geheim"))
        }
        fixture.create.create(CreateUserCommand("foto@example.com", "Foto", "sehr-geheim"))
        expectFailure<UserUseCaseConflictException> {
            fixture.create.create(CreateUserCommand("FOTO@example.com", "Andere", "sehr-geheim"))
        }
    }

    @Test
    fun `create validates password length and role`() = runTest {
        val fixture = Fixture()
        expectFailure<UserUseCaseValidationException> {
            fixture.create.create(CreateUserCommand("foto@example.com", "Foto", "kurz"))
        }
        expectFailure<UserUseCaseValidationException> {
            fixture.create.create(CreateUserCommand("foto@example.com", "Foto", "sehr-geheim", "ADMIN"))
        }
    }

    @Test
    fun `login issues token and hides account existence`() = runTest {
        val fixture = Fixture()
        val user = fixture.create.create(CreateUserCommand("kunde@example.com", "Kunde", "sehr-geheim"))
        val result = fixture.login.login(LoginUserCommand("kunde@example.com", "sehr-geheim"))
        assertEquals("token:${user.id}", result.token)
        expectFailure<UserUseCaseUnauthorizedException> {
            fixture.login.login(LoginUserCommand("unknown@example.com", "sehr-geheim"))
        }
    }

    @Test
    fun `profile patch enforces ownership and records update`() = runTest {
        val fixture = Fixture()
        val user = fixture.create.create(CreateUserCommand("foto@example.com", "Alt", "sehr-geheim"))
        val updated = fixture.patch.patch(user.id, user.id, PatchUserProfileCommand(displayName = "Neu", bio = " Bio "))
        assertEquals("Neu", updated.displayName)
        assertEquals("Bio", updated.bio)
        assertEquals(1, fixture.metrics.updated)
        expectFailure<UserUseCaseForbiddenException> {
            fixture.patch.patch(user.id, "other", PatchUserProfileCommand(displayName = "Fremd"))
        }
    }

    @Test
    fun `profile patch validates fields and missing profiles`() = runTest {
        val fixture = Fixture()
        val user = fixture.create.create(CreateUserCommand("foto@example.com", "Alt", "sehr-geheim"))
        expectFailure<UserUseCaseValidationException> {
            fixture.patch.patch(user.id, user.id, PatchUserProfileCommand(displayName = "   "))
        }
        expectFailure<UserUseCaseNotFoundException> {
            fixture.patch.patch("missing", "missing", PatchUserProfileCommand(displayName = "Neu"))
        }
    }

    @Test
    fun `get and list expose persisted accounts`() = runTest {
        val fixture = Fixture()
        val user = fixture.create.create(CreateUserCommand("foto@example.com", "Foto", "sehr-geheim"))
        assertEquals(user, fixture.get.get(user.id))
        assertEquals(listOf(user), fixture.list.list())
        expectFailure<UserUseCaseNotFoundException> { fixture.get.get("missing") }
    }

    private class Fixture {
        val repository = InMemoryUsers()
        val metrics = RecordingMetrics()
        val hashes = object : PasswordHashPort {
            override fun hash(password: String) = "hash:$password"
            override fun verify(password: String, storedHash: String?) = storedHash == "hash:$password"
        }
        val clock = ClockPort { Instant.parse("2026-07-13T10:00:00Z") }
        val ids = IdGeneratorPort { "user-${repository.size + 1}" }
        val create = CreateUserUseCase(repository, hashes, metrics, clock, ids)
        val login = LoginUserUseCase(repository, hashes, TokenIssuerPort { "token:${it.id}" })
        val patch = PatchUserProfileUseCase(repository, metrics, clock)
        val get = GetUserUseCase(repository)
        val list = ListUsersUseCase(repository)
    }

    private class RecordingMetrics : UserMetricsPort {
        val createdRoles = mutableListOf<String>()
        var updated = 0
        override fun userCreated(role: String) { createdRoles += role }
        override fun userUpdated() { updated++ }
    }

    private class InMemoryUsers : UserRepositoryPort {
        private val users = linkedMapOf<String, User>()
        val size get() = users.size
        override suspend fun save(user: User): User = user.also { users[it.id] = it }
        override suspend fun updateProfile(user: User): User? = user.takeIf { users.containsKey(it.id) }?.also { users[it.id] = it }
        override suspend fun listUsers(): List<User> = users.values.toList()
        override suspend fun findUserById(id: String): User? = users[id]
        override suspend fun findUserByEmail(email: String): User? = users.values.firstOrNull { it.email == email }
    }

    private suspend inline fun <reified T : Throwable> expectFailure(noinline block: suspend () -> Unit) {
        try { block(); fail("Expected ${T::class.simpleName}") } catch (failure: Throwable) {
            assertTrue(failure is T, "Expected ${T::class.simpleName}, got ${failure::class.simpleName}")
        }
    }
}
