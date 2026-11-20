package lensmarket.messaging.domain.usecase

import lensmarket.messaging.domain.model.Conversation
import lensmarket.messaging.domain.model.CreateConversationCommand
import lensmarket.messaging.domain.model.CreateConversationResult
import lensmarket.messaging.domain.model.LISTING_STATUS_ACTIVE
import lensmarket.messaging.domain.port.ClockPort
import lensmarket.messaging.domain.port.ConversationRepositoryPort
import lensmarket.messaging.domain.port.IdGeneratorPort
import lensmarket.messaging.domain.port.ListingLookupPort
import lensmarket.messaging.domain.port.MessagingMetricsPort

class CreateConversationUseCase(
    private val conversations: ConversationRepositoryPort,
    private val listings: ListingLookupPort,
    private val metrics: MessagingMetricsPort,
    private val clock: ClockPort,
    private val ids: IdGeneratorPort
) {
    suspend fun create(currentUserId: String, command: CreateConversationCommand): CreateConversationResult {
        val customerId = requiredText(currentUserId, "currentUserId")
        val listingId = requiredText(command.listingId, "listingId")
        val listing = listings.findListing(listingId)
            ?: throw MessagingUseCaseNotFoundException("Listing $listingId not found")
        if (listing.status != LISTING_STATUS_ACTIVE) {
            throw MessagingUseCaseValidationException("Only active listings can receive new conversations")
        }
        if (listing.ownerUserId == customerId) {
            throw MessagingUseCaseForbiddenException("Listing owners cannot start conversations with themselves")
        }
        conversations.findConversationForListingAndCustomer(listingId, customerId)?.let {
            return CreateConversationResult(it, created = false)
        }
        val now = clock.now()
        val candidate = Conversation(ids.newId(), listingId, listing.ownerUserId, customerId, now, now)
        val result = try {
            CreateConversationResult(conversations.saveConversation(candidate), created = true)
        } catch (conflict: MessagingPersistenceConflictException) {
            val winner = conversations.findConversationForListingAndCustomer(listingId, customerId) ?: throw conflict
            CreateConversationResult(winner, created = false)
        }
        if (result.created) metrics.conversationCreated()
        return result
    }

    private fun requiredText(value: String, field: String): String = value.trim().also {
        if (it.isEmpty()) throw MessagingUseCaseValidationException("$field must not be blank")
    }
}
