package lensmarket.messaging.composition

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import lensmarket.messaging.domain.port.ClockPort
import lensmarket.messaging.domain.port.ConversationRepositoryPort
import lensmarket.messaging.domain.port.IdGeneratorPort
import lensmarket.messaging.domain.port.ListingLookupPort
import lensmarket.messaging.domain.port.MessageRepositoryPort
import lensmarket.messaging.domain.port.MessagingMetricsPort
import lensmarket.messaging.domain.port.TokenVerifierPort
import lensmarket.messaging.domain.usecase.AuthenticateUserUseCase
import lensmarket.messaging.domain.usecase.CreateConversationUseCase
import lensmarket.messaging.domain.usecase.GetConversationUseCase
import lensmarket.messaging.domain.usecase.GetMessagesUseCase
import lensmarket.messaging.domain.usecase.ListConversationsUseCase
import lensmarket.messaging.domain.usecase.RemoveConversationUseCase
import lensmarket.messaging.domain.usecase.SendMessageUseCase
import java.time.Instant
import java.util.UUID

@ApplicationScoped class SystemClockAdapter : ClockPort { override fun now(): Instant = Instant.now() }
@ApplicationScoped class UuidGeneratorAdapter : IdGeneratorPort { override fun newId(): String = UUID.randomUUID().toString() }

@ApplicationScoped
class MessagingApplicationModule {
    @Produces @Singleton fun authenticate(tokens: TokenVerifierPort) = AuthenticateUserUseCase(tokens)
    @Produces @Singleton fun create(
        conversations: ConversationRepositoryPort, listings: ListingLookupPort, metrics: MessagingMetricsPort,
        clock: ClockPort, ids: IdGeneratorPort
    ) = CreateConversationUseCase(conversations, listings, metrics, clock, ids)
    @Produces @Singleton fun list(conversations: ConversationRepositoryPort) = ListConversationsUseCase(conversations)
    @Produces @Singleton fun get(conversations: ConversationRepositoryPort) = GetConversationUseCase(conversations)
    @Produces @Singleton fun remove(
        conversations: ConversationRepositoryPort, clock: ClockPort, metrics: MessagingMetricsPort
    ) = RemoveConversationUseCase(conversations, clock, metrics)
    @Produces @Singleton fun send(
        conversations: ConversationRepositoryPort, messages: MessageRepositoryPort,
        metrics: MessagingMetricsPort, clock: ClockPort, ids: IdGeneratorPort
    ) = SendMessageUseCase(conversations, messages, metrics, clock, ids)
    @Produces @Singleton fun messages(
        conversations: ConversationRepositoryPort, messages: MessageRepositoryPort
    ) = GetMessagesUseCase(conversations, messages)
}
