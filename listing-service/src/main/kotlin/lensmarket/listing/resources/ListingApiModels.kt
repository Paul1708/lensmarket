package lensmarket.listing.resources

import org.eclipse.microprofile.openapi.annotations.media.Schema
import lensmarket.listing.domain.model.ChangeListingStatusCommand
import lensmarket.listing.domain.model.CreateListingCommand
import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.model.PatchListingCommand
import lensmarket.listing.domain.model.UpdateListingCommand
import java.math.BigDecimal
import java.time.Instant

/**
 * Request-Body zum Anlegen einer Anzeige.
 *
 * Die Besitzer-ID wird serverseitig aus dem Token gesetzt. Bildreferenzen werden
 * ausschließlich durch den validierten `image-watermarked`-Eventflow ergänzt.
 *
 * @param title Titel der Anzeige.
 * @param description Beschreibung der Anzeige.
 * @param priceHint Optionaler Preisvorschlag.
 * @param location Optionaler Standort.
 * @param cameraModel Optionales Kameramodell.
 * @param lensModel Optionales Objektivmodell.
 * @param tags Optionale Schlagwörter.
 */
data class CreateListingRequest(
    @field:Schema(minLength = 1) val title: String,
    @field:Schema(minLength = 1) val description: String,
    @field:Schema(minimum = "0") val priceHint: BigDecimal? = null,
    val location: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val tags: List<String> = emptyList()
) {
    /**
     * Wandelt den HTTP-Request in ein Domain-Command um.
     *
     * @return Command für den CreateListingUseCase.
     */
    fun toCommand(): CreateListingCommand =
        CreateListingCommand(
            title = title,
            description = description,
            priceHint = priceHint,
            location = location,
            cameraModel = cameraModel,
            lensModel = lensModel,
            tags = tags
        )
    }

/**
 * Request-Body zum vollständigen Aktualisieren editierbarer Anzeigenfelder.
 *
 * Bild-IDs, Status und Besitzer werden nicht über PUT geändert.
 *
 * @param title Neuer Titel.
 * @param description Neue Beschreibung.
 * @param priceHint Neuer optionaler Preisvorschlag.
 * @param location Neuer optionaler Standort.
 * @param cameraModel Neues optionales Kameramodell.
 * @param lensModel Neues optionales Objektivmodell.
 * @param tags Neue Schlagwortliste.
 */
data class UpdateListingRequest(
    @field:Schema(minLength = 1) val title: String,
    @field:Schema(minLength = 1) val description: String,
    @field:Schema(minimum = "0") val priceHint: BigDecimal? = null,
    val location: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val tags: List<String> = emptyList()
) {
    /**
     * Wandelt den PUT-Request in ein Domain-Command um.
     *
     * @return Command für den UpdateListingUseCase.
     */
    fun toCommand(): UpdateListingCommand =
        UpdateListingCommand(
            title = title,
            description = description,
            priceHint = priceHint,
            location = location,
            cameraModel = cameraModel,
            lensModel = lensModel,
            tags = tags
        )
}

/**
 * Request-Body für einen Anzeigenstatuswechsel.
 *
 * @param status Zielstatus der Anzeige.
 */
data class ChangeListingStatusRequest(
    @field:Schema(enumeration = ["ACTIVE", "PAUSED", "SOLD"]) val status: String
) {
    /**
     * Wandelt den Status-Request in ein Domain-Command um.
     *
     * @return Command für den ChangeListingStatusUseCase.
     */
    fun toCommand(): ChangeListingStatusCommand =
        ChangeListingStatusCommand(status = status)
}

/**
 * Request-Body für partielle Anzeigenänderungen.
 *
 * Nicht gesetzte Felder bleiben unverändert.
 *
 * @param title Neuer Titel oder `null`.
 * @param description Neue Beschreibung oder `null`.
 * @param priceHint Neuer Preisvorschlag oder `null`.
 * @param location Neuer Standort oder `null`.
 * @param cameraModel Neues Kameramodell oder `null`.
 * @param lensModel Neues Objektivmodell oder `null`.
 * @param tags Neue Schlagwortliste oder `null`.
 */
@Schema(minProperties = 1, description = "Mindestens eines der Felder muss vorhanden sein.")
data class PatchListingRequest(
    @field:Schema(minLength = 1) val title: String? = null,
    @field:Schema(minLength = 1) val description: String? = null,
    @field:Schema(minimum = "0") val priceHint: BigDecimal? = null,
    val location: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val tags: List<String>? = null
) {
    /**
     * Wandelt den PATCH-Request in ein Domain-Command um.
     *
     * @return Command für den PatchListingUseCase.
     */
    fun toCommand(): PatchListingCommand =
        PatchListingCommand(
            title = title,
            description = description,
            priceHint = priceHint,
            location = location,
            cameraModel = cameraModel,
            lensModel = lensModel,
            tags = tags
        )
}

/**
 * API-Antwort für eine Anzeige.
 *
 * Listen werden für Clients als normale JSON-Arrays ausgeliefert, auch wenn sie in
 * der Datenbank als JSON-Text gespeichert sind.
 *
 * @param id Technische Anzeigen-ID.
 * @param ownerUserId Technische ID des Anzeigenbesitzers.
 * @param title Titel der Anzeige.
 * @param description Beschreibung der Anzeige.
 * @param priceHint Optionaler Preisvorschlag.
 * @param location Optionaler Standort.
 * @param cameraModel Optionales Kameramodell.
 * @param lensModel Optionales Objektivmodell.
 * @param tags Schlagwörter der Anzeige.
 * @param imageIds Referenzen auf Bilder im Image Service.
 * @param status Fachlicher Anzeigenstatus.
 * @param createdAt Anlagezeitpunkt.
 */
data class ListingResponse(
    val id: String,
    val ownerUserId: String,
    val title: String,
    val description: String,
    @field:Schema(minimum = "0") val priceHint: BigDecimal?,
    val location: String?,
    val cameraModel: String?,
    val lensModel: String?,
    val tags: List<String>,
    @field:Schema(readOnly = true) val imageIds: List<String>,
    @field:Schema(enumeration = ["ACTIVE", "PAUSED", "SOLD"]) val status: String,
    val createdAt: Instant
)

/**
 * Wandelt ein Domain-Listing in die HTTP-Antwort um.
 *
 * @return API-Modell für Clients.
 */
fun Listing.toResponse(): ListingResponse =
    ListingResponse(
        id = id,
        ownerUserId = ownerUserId,
        title = title,
        description = description,
        priceHint = priceHint,
        location = location,
        cameraModel = cameraModel,
        lensModel = lensModel,
        tags = tags,
        imageIds = imageIds,
        status = status,
        createdAt = createdAt
    )
