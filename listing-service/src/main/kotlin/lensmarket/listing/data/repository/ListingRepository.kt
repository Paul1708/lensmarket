package lensmarket.listing.data.repository

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.hibernate.reactive.panache.kotlin.Panache
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheRepositoryBase
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.listing.data.entity.ListingEntity
import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.model.ListingSearchFilter
import lensmarket.listing.domain.port.EventApplyResult
import lensmarket.listing.domain.port.ListingRepositoryPort
import lensmarket.listing.domain.port.ListingSearchPort
import lensmarket.listing.events.LISTING_CREATED
import lensmarket.listing.events.LISTING_DELETED
import lensmarket.listing.events.LISTING_STATUS_CHANGED
import lensmarket.listing.events.toListingCreatedEvent
import lensmarket.listing.events.toListingDeletedEvent
import lensmarket.listing.events.toListingStatusChangedEvent
import java.time.Instant

/** Panache-Ausgangsadapter für die Listing-Persistenz. */
@ApplicationScoped
class ListingRepository(
    private val mapper: ListingEntityMapper,
    private val outbox: OutboxRepository,
    private val processedEvents: ProcessedEventRepository,
    private val objectMapper: ObjectMapper
) : PanacheRepositoryBase<ListingEntity, String>, ListingRepositoryPort, ListingSearchPort {
    override suspend fun save(listing: Listing): Listing =
        Panache.withTransaction { persist(mapper.toEntity(listing)).replaceWith(listing) }.awaitSuspending()

    override suspend fun saveWithEvent(listing: Listing, eventId: String): Listing {
        val event = listing.toListingCreatedEvent(eventId)
        return Panache.withTransaction {
            persist(mapper.toEntity(listing))
                .chain { _: ListingEntity ->
                    outbox.persistPending(eventRecord(eventId, listing.id, LISTING_CREATED, event.occurredAt, event))
                }
                .replaceWith(listing)
        }.awaitSuspending()
    }

    override suspend fun update(listing: Listing): Listing? =
        Panache.withTransaction {
            findById(listing.id).onItem().transform { entity ->
                if (entity == null || entity.deletedAt != null) null else {
                    entity.title = listing.title
                    entity.description = listing.description
                    entity.priceHint = listing.priceHint
                    entity.location = listing.location
                    entity.cameraModel = listing.cameraModel
                    entity.lensModel = listing.lensModel
                    entity.tags = mapper.toJson(listing.tags)
                    mapper.toDomain(entity)
                }
            }
        }.awaitSuspending()

    override suspend fun updateStatus(listingId: String, status: String): Listing? =
        Panache.withTransaction {
            findById(listingId).onItem().transform { entity ->
                if (entity == null || entity.deletedAt != null) null else {
                    entity.status = status
                    mapper.toDomain(entity)
                }
            }
        }.awaitSuspending()

    override suspend fun updateStatusWithEvent(
        listingId: String,
        status: String,
        oldStatus: String,
        eventId: String,
        occurredAt: Instant
    ): Listing? = Panache.withTransaction {
        findById(listingId).onItem().transformToUni { entity ->
            if (entity == null || entity.deletedAt != null) {
                Uni.createFrom().nullItem()
            } else {
                entity.status = status
                val updated = mapper.toDomain(entity)
                val event = updated.toListingStatusChangedEvent(eventId, occurredAt, oldStatus, status)
                outbox.persistPending(
                    eventRecord(eventId, listingId, LISTING_STATUS_CHANGED, occurredAt, event)
                ).replaceWith(updated)
            }
        }
    }.awaitSuspending()

    override suspend fun appendImageId(listingId: String, imageId: String): Listing? =
        Panache.withTransaction {
            findById(listingId).onItem().transform { entity ->
                if (entity == null || entity.deletedAt != null) null else {
                    val current = mapper.fromJson(entity.imageIds)
                    entity.imageIds = mapper.toJson(if (imageId in current) current else current + imageId)
                    mapper.toDomain(entity)
                }
            }
        }.awaitSuspending()

    override suspend fun appendImageIdOnce(
        eventId: String,
        processedAt: Instant,
        listingId: String,
        imageId: String
    ): EventApplyResult = Panache.withTransaction {
        processedEvents.findById(eventId).onItem().transformToUni { processed ->
            if (processed != null) {
                Uni.createFrom().item(EventApplyResult.DUPLICATE)
            } else {
                findById(listingId).onItem().transformToUni { entity ->
                    val result = if (entity == null || entity.deletedAt != null) {
                        EventApplyResult.AGGREGATE_NOT_FOUND
                    } else {
                        val current = mapper.fromJson(entity.imageIds)
                        entity.imageIds = mapper.toJson(if (imageId in current) current else current + imageId)
                        EventApplyResult.APPLIED
                    }
                    processedEvents.persistProcessed(eventId, "image-watermarked", processedAt)
                        .replaceWith(result)
                }
            }
        }
    }.awaitSuspending()

    override suspend fun markDeleted(listingId: String, deletedAt: Instant): Listing? =
        Panache.withTransaction {
            findById(listingId).onItem().transform { entity ->
                if (entity == null || entity.deletedAt != null) null else {
                    entity.deletedAt = deletedAt
                    mapper.toDomain(entity)
                }
            }
        }.awaitSuspending()

    override suspend fun markDeletedWithEvent(
        listingId: String,
        deletedAt: Instant,
        eventId: String
    ): Listing? = Panache.withTransaction {
        findById(listingId).onItem().transformToUni { entity ->
            if (entity == null || entity.deletedAt != null) {
                Uni.createFrom().nullItem()
            } else {
                entity.deletedAt = deletedAt
                val deleted = mapper.toDomain(entity)
                val event = deleted.toListingDeletedEvent(eventId)
                outbox.persistPending(eventRecord(eventId, listingId, LISTING_DELETED, deletedAt, event))
                    .replaceWith(deleted)
            }
        }
    }.awaitSuspending()

    override suspend fun search(filter: ListingSearchFilter): List<Listing> =
        Panache.withSession {
            val query = buildListingSearchQuery(filter)
            val panacheQuery = if (query.parameters.isEmpty()) find(query.text)
                else find(query.text, *query.parameters.toTypedArray())
            panacheQuery.range(filter.offset, filter.offset + filter.limit - 1).list()
                .onItem().transform { mapper.toDomains(it) }
        }.awaitSuspending()

    override suspend fun findListingById(id: String): Listing? =
        Panache.withSession {
            find("id = ?1 and deletedAt is null", id).firstResult()
                .onItem().transform { it?.let(mapper::toDomain) }
        }.awaitSuspending()

    private fun eventRecord(
        eventId: String,
        aggregateId: String,
        eventType: String,
        occurredAt: Instant,
        event: Any
    ) = OutboxEventRecord(
        eventId = eventId,
        aggregateId = aggregateId,
        eventType = eventType,
        topic = eventType,
        payload = objectMapper.writeValueAsString(event),
        occurredAt = occurredAt,
        attempts = 0
    )
}
