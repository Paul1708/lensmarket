package lensmarket.listing.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * Status fuer sichtbare und buchbare Anzeigen.
 */
const val LISTING_STATUS_ACTIVE = "ACTIVE"

/**
 * Status fuer pausierte Anzeigen.
 */
const val LISTING_STATUS_PAUSED = "PAUSED"

/**
 * Status fuer verkaufte Anzeigen.
 */
const val LISTING_STATUS_SOLD = "SOLD"

/**
 * Standardlimit fuer Anzeigen-Suchen.
 */
const val DEFAULT_LISTING_SEARCH_LIMIT = 50

/**
 * Obergrenze fuer Anzeigen-Suchen.
 */
const val MAX_LISTING_SEARCH_LIMIT = 100

/**
 * Alle fachlich erlaubten Anzeigenstatuswerte.
 */
val LISTING_STATUSES = setOf(
    LISTING_STATUS_ACTIVE,
    LISTING_STATUS_PAUSED,
    LISTING_STATUS_SOLD
)

/**
 * Erlaubte echte Statuswechsel. `SOLD` ist terminal; ein idempotenter Request auf
 * den bereits gespeicherten Status wird vor dieser Matrix behandelt.
 */
val LISTING_STATUS_TRANSITIONS = mapOf(
    LISTING_STATUS_ACTIVE to setOf(LISTING_STATUS_PAUSED, LISTING_STATUS_SOLD),
    LISTING_STATUS_PAUSED to setOf(LISTING_STATUS_ACTIVE, LISTING_STATUS_SOLD),
    LISTING_STATUS_SOLD to emptySet()
)

/**
 * Command zum Anlegen einer Anzeige.
 *
 * Das Command ist unabhängig vom HTTP-Request-DTO. Die endgültige Besitzer-ID wird
 * im Use Case aus dem authentifizierten Nutzer gesetzt.
 *
 * @param title Titel der Anzeige.
 * @param description Beschreibung der Anzeige.
 * @param priceHint Optionaler Preisvorschlag.
 * @param location Optionaler Standort.
 * @param cameraModel Optionales Kameramodell.
 * @param lensModel Optionales Objektivmodell.
 * @param tags Schlagwörter der Anzeige.
 */
data class CreateListingCommand(
    val title: String,
    val description: String,
    val priceHint: BigDecimal? = null,
    val location: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Command zum vollständigen Aktualisieren editierbarer Anzeigenfelder.
 *
 * Nicht änderbare Felder wie ID, Besitzer, Status und Bildreferenzen fehlen bewusst.
 *
 * @param title Neuer Titel.
 * @param description Neue Beschreibung.
 * @param priceHint Neuer optionaler Preisvorschlag.
 * @param location Neuer optionaler Standort.
 * @param cameraModel Neues optionales Kameramodell.
 * @param lensModel Neues optionales Objektivmodell.
 * @param tags Neue Schlagwortliste.
 */
data class UpdateListingCommand(
    val title: String,
    val description: String,
    val priceHint: BigDecimal? = null,
    val location: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Command für partielle Anzeigenänderungen.
 *
 * `null` bedeutet, dass das jeweilige Feld unverändert bleibt.
 *
 * @param title Neuer Titel oder `null`.
 * @param description Neue Beschreibung oder `null`.
 * @param priceHint Neuer Preisvorschlag oder `null`.
 * @param location Neuer Standort oder `null`.
 * @param cameraModel Neues Kameramodell oder `null`.
 * @param lensModel Neues Objektivmodell oder `null`.
 * @param tags Neue Schlagwortliste oder `null`.
 */
data class PatchListingCommand(
    val title: String? = null,
    val description: String? = null,
    val priceHint: BigDecimal? = null,
    val location: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val tags: List<String>? = null
)

/**
 * Command für den fachlichen Statuswechsel einer Anzeige.
 *
 * @param status Zielstatus.
 */
data class ChangeListingStatusCommand(
    val status: String
)

/**
 * Fachlicher Suchfilter für Anzeigen.
 *
 * `ownerUserId` ist eine technische Nutzerreferenz und kein sichtbarer Anzeigename.
 *
 * @param query Freitextsuche über Titel, Beschreibung und Tags.
 * @param ownerUserId Optionale Einschränkung auf einen Besitzer.
 * @param status Optionaler Anzeigenstatus.
 * @param tag Optionaler Tag-Filter.
 * @param location Optionaler Standortfilter.
 * @param minPrice Optionale Preisuntergrenze.
 * @param maxPrice Optionale Preisobergrenze.
 * @param limit Maximale Ergebnisanzahl.
 * @param offset Startposition für Pagination.
 */
data class ListingSearchFilter(
    val query: String? = null,
    val ownerUserId: String? = null,
    val status: String? = null,
    val tag: String? = null,
    val location: String? = null,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val limit: Int = DEFAULT_LISTING_SEARCH_LIMIT,
    val offset: Int = 0
)

/**
 * Fachliches Domain-Modell einer Anzeige.
 *
 * Das Modell enthält keine JPA- oder REST-Annotationen. Tags und Bildreferenzen
 * werden hier als Listen geführt.
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
 * @param deletedAt Soft-Delete-Zeitpunkt oder `null`.
 */
data class Listing(
    val id: String,
    val ownerUserId: String,
    val title: String,
    val description: String,
    val priceHint: BigDecimal?,
    val location: String?,
    val cameraModel: String?,
    val lensModel: String?,
    val tags: List<String>,
    val imageIds: List<String>,
    val status: String,
    val createdAt: Instant,
    val deletedAt: Instant? = null
)

/**
 * Ergebnis einer Statusprüfung oder Statusänderung.
 *
 * @param listing Anzeige nach der Prüfung oder Änderung.
 * @param oldStatus Vorheriger Status.
 * @param newStatus Gewünschter oder gespeicherter neuer Status.
 */
data class ChangeListingStatusResult(
    val listing: Listing,
    val oldStatus: String,
    val newStatus: String
) {
    /**
     * Gibt an, ob tatsächlich ein neuer Status gespeichert werden musste.
     */
    val statusChanged: Boolean = oldStatus != newStatus
}
