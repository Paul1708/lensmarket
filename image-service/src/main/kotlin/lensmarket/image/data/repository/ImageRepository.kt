package lensmarket.image.data.repository

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.hibernate.reactive.panache.kotlin.Panache
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheRepositoryBase
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.image.data.entity.ImageEntity
import lensmarket.image.domain.model.Image
import lensmarket.image.domain.model.ImageSearchFilter
import lensmarket.image.domain.port.ImageRepositoryPort
import lensmarket.image.domain.usecase.ImageUseCaseNotFoundException
import lensmarket.image.events.IMAGE_UPLOADED
import lensmarket.image.events.IMAGE_WATERMARKED
import lensmarket.image.events.toImageUploadedEvent
import lensmarket.image.events.toImageWatermarkedEvent
import java.time.Instant

/**
 * Panache-basierter Persistenzadapter für Bild-Metadaten.
 *
 * Das Repository kapselt Hibernate Reactive mit Panache und erfüllt den
 * ImageRepositoryPort der Domänenschicht.
 */
@ApplicationScoped
class ImageRepository(
    private val outbox: OutboxRepository,
    private val processedEvents: ProcessedEventRepository,
    private val deletedListings: DeletedListingRepository,
    private val objectMapper: ObjectMapper
) : PanacheRepositoryBase<ImageEntity, String>, ImageRepositoryPort {
    /**
     * Wandelt eine JPA-Entity in das Domain-Modell um.
     *
     * @param entity Persistenzmodell aus der Datenbank.
     * @return Fachliches Bild-Modell.
     */
    private fun toDomain(entity: ImageEntity): Image =
        Image(
            id = entity.id,
            listingId = entity.listingId,
            ownerUserId = entity.ownerUserId,
            originalFileName = entity.originalFileName,
            mimeType = entity.mimeType,
            width = entity.width,
            height = entity.height,
            status = entity.status,
            watermarkedUrl = entity.watermarkedUrl,
            originalPath = entity.originalPath,
            watermarkedPath = entity.watermarkedPath,
            createdAt = entity.createdAt
        )

    /**
     * Wandelt ein Domain-Modell in eine neue JPA-Entity um.
     *
     * @param image Fachliches Bild-Modell.
     * @return Persistenzmodell für Hibernate.
     */
    private fun toEntity(image: Image): ImageEntity =
        ImageEntity().apply {
            id = image.id
            listingId = image.listingId
            ownerUserId = image.ownerUserId
            originalFileName = image.originalFileName
            mimeType = image.mimeType
            width = image.width
            height = image.height
            status = image.status
            watermarkedUrl = image.watermarkedUrl
            originalPath = image.originalPath
            watermarkedPath = image.watermarkedPath
            createdAt = image.createdAt
        }

    /**
     * Speichert neue Bild-Metadaten.
     *
     * @param image Zu speicherndes Bild.
     * @return Gespeichertes Bild.
     */
    override suspend fun save(image: Image): Image =
        Panache.withTransaction { persist(toEntity(image)).replaceWith(image) }.awaitSuspending()

    override suspend fun saveWithEvents(
        image: Image,
        uploadedEventId: String,
        watermarkedEventId: String
    ): Image {
        val uploaded = image.toImageUploadedEvent(uploadedEventId)
        val watermarked = image.toImageWatermarkedEvent(watermarkedEventId)
        return Panache.withTransaction {
            deletedListings.findById(image.listingId).onItem().transformToUni { tombstone ->
                if (tombstone != null) {
                    io.smallrye.mutiny.Uni.createFrom().failure(
                        ImageUseCaseNotFoundException("Listing ${image.listingId} was deleted during upload")
                    )
                } else {
                    persist(toEntity(image))
                        .chain { _: ImageEntity ->
                            outbox.persistPending(
                                eventRecord(uploadedEventId, image.id, IMAGE_UPLOADED, image.createdAt, uploaded)
                            )
                        }
                        .chain { _ ->
                            outbox.persistPending(
                                eventRecord(watermarkedEventId, image.id, IMAGE_WATERMARKED, image.createdAt, watermarked)
                            )
                        }
                        .replaceWith(image)
                }
            }
        }.awaitSuspending()
    }

    /**
     * Listet Bild-Metadaten anhand optionaler Filter.
     *
     * @param filter Suchfilter für Listing- oder Besitzer-ID.
     * @return Passende Bild-Metadaten.
     */
    override suspend fun listImages(filter: ImageSearchFilter): List<Image> = Panache.withSession {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        filter.listingId?.let { listingId ->
            conditions += "listingId = :listingId"
            params["listingId"] = listingId
        }
        filter.ownerUserId?.let { ownerUserId ->
            conditions += "ownerUserId = :ownerUserId"
            params["ownerUserId"] = ownerUserId
        }
        filter.status?.let { status ->
            conditions += "status = :status"
            params["status"] = status
        }
        val query = if (conditions.isEmpty()) "order by createdAt desc"
        else conditions.joinToString(" and ") + " order by createdAt desc"
        list(query, params)
            .onItem().transform { images -> images.map { toDomain(it) } }
    }.awaitSuspending()

    /**
     * Sucht Bild-Metadaten anhand der technischen ID.
     *
     * `null` signalisiert „nicht gefunden“; die Resource-Schicht erzeugt daraus HTTP 404.
     *
     * @param id Technische Bild-ID.
     * @return Gefundenes Bild oder `null`.
     */
    override suspend fun findImageById(id: String): Image? =
        Panache.withSession { findById(id)
            .onItem().transform { image -> image?.let { toDomain(it) } }
        }.awaitSuspending()

    /**
     * Aktualisiert den fachlichen Status eines Bildes.
     *
     * @param imageId Technische Bild-ID.
     * @param status Neuer Bildstatus.
     * @return Aktualisierte Bild-Metadaten oder `null`.
     */
    override suspend fun updateStatus(imageId: String, status: String): Image? =
        Panache.withTransaction { findById(imageId)
            .onItem().transform { entity ->
                if (entity == null) null else {
                    entity.status = status
                    toDomain(entity)
                }
            }
        }.awaitSuspending()

    /**
     * Löscht Bild-Metadaten unwiderruflich anhand der technischen ID.
     *
     * @param id Technische Bild-ID.
     * @return `true`, wenn ein Datensatz gelöscht wurde.
     */
    override suspend fun deleteImage(id: String): Boolean =
        Panache.withTransaction { deleteById(id) }.awaitSuspending()

    override suspend fun listStoredObjectKeys(): Set<String> = Panache.withSession {
        list("watermarkedPath is not null").map { rows ->
            rows.mapNotNull { it.watermarkedPath }.toSet()
        }
    }.awaitSuspending()

    override suspend fun isEventProcessed(eventId: String): Boolean = Panache.withSession {
        processedEvents.findById(eventId).map { it != null }
    }.awaitSuspending()

    override suspend fun markListingDeletedEventProcessed(eventId: String, listingId: String, processedAt: Instant) {
        Panache.withTransaction {
            processedEvents.findById(eventId).chain { existing ->
                if (existing == null) {
                    processedEvents.persistProcessed(eventId, "listing-deleted", processedAt)
                        .chain { _: lensmarket.image.data.entity.ProcessedEventEntity ->
                            deletedListings.persistTombstone(listingId, eventId, processedAt)
                        }
                        .replaceWithVoid()
                } else {
                    io.smallrye.mutiny.Uni.createFrom().voidItem()
                }
            }
        }.awaitSuspending()
    }

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
