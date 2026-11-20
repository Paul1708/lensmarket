package lensmarket.messaging.domain.port

import lensmarket.messaging.domain.model.ListingReference

/**
 * Port zum Laden minimaler Listing-Daten für Chat-Regeln.
 *
 * Der Messaging Service fragt nur die Daten ab, die für Konversationserstellung und
 * Berechtigungslogik nötig sind.
 */
fun interface ListingLookupPort {
    /**
     * Sucht eine Anzeige im Listing Service.
     *
     * @param listingId Technische Anzeigen-ID.
     * @return Minimale Listing-Referenz oder `null`.
     */
    suspend fun findListing(listingId: String): ListingReference?
}
