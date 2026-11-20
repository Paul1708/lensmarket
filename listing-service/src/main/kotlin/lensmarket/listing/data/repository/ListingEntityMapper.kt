package lensmarket.listing.data.repository

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.listing.data.entity.ListingEntity
import lensmarket.listing.domain.model.Listing

/**
 * Wandelt zwischen ListingEntity und Domain-Modell um.
 *
 * @param objectMapper JSON-Mapper für Listenfelder, die in Postgres als Text gespeichert sind.
 */
@ApplicationScoped
class ListingEntityMapper(
    private val objectMapper: ObjectMapper
) {
    /**
     * Jackson-Typinformation für JSON-Listen aus `tags` und `imageIds`.
     */
    private val stringListType = object : TypeReference<List<String>>() {}

    /**
     * Wandelt eine JPA-Entity in das Domain-Modell um.
     *
     * @param entity Persistenzmodell aus der Datenbank.
     * @return Fachliches Anzeigen-Modell.
     */
    fun toDomain(entity: ListingEntity): Listing =
        Listing(
            id = entity.id,
            ownerUserId = entity.ownerUserId,
            title = entity.title,
            description = entity.description,
            priceHint = entity.priceHint,
            location = entity.location,
            cameraModel = entity.cameraModel,
            lensModel = entity.lensModel,
            tags = objectMapper.readValue(entity.tags, stringListType),
            imageIds = objectMapper.readValue(entity.imageIds, stringListType),
            status = entity.status,
            createdAt = entity.createdAt,
            deletedAt = entity.deletedAt
        )

    /**
     * Wandelt mehrere JPA-Entities in Domain-Modelle um.
     *
     * @param entities Persistenzmodelle aus der Datenbank.
     * @return Fachliche Anzeigen-Modelle.
     */
    fun toDomains(entities: List<ListingEntity>): List<Listing> =
        entities.map { entity -> toDomain(entity) }

    /**
     * Wandelt ein Domain-Modell in eine neue JPA-Entity um.
     *
     * @param listing Fachliches Anzeigen-Modell.
     * @return Persistenzmodell für Hibernate.
     */
    fun toEntity(listing: Listing): ListingEntity =
        ListingEntity().apply {
            id = listing.id
            ownerUserId = listing.ownerUserId
            title = listing.title
            description = listing.description
            priceHint = listing.priceHint
            location = listing.location
            cameraModel = listing.cameraModel
            lensModel = listing.lensModel
            tags = objectMapper.writeValueAsString(listing.tags)
            imageIds = objectMapper.writeValueAsString(listing.imageIds)
            status = listing.status
            createdAt = listing.createdAt
            deletedAt = listing.deletedAt
        }

    /**
     * Serialisiert eine Stringliste als JSON-Text für die Datenbank.
     *
     * @param values Fachliche Werte, z.B. Tags oder Bild-IDs.
     * @return JSON-Text für die Text-Spalte.
     */
    fun toJson(values: List<String>): String =
        objectMapper.writeValueAsString(values)

    /**
     * Liest eine JSON-Text-Spalte als Stringliste.
     *
     * @param json JSON-Text aus der Datenbank.
     * @return Fachliche Werte.
     */
    fun fromJson(json: String): List<String> =
        objectMapper.readValue(json, stringListType)
}
