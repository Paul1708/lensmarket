package lensmarket.listing.domain.port

import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.model.ListingSearchFilter

/**
 * Lese-Port für die Anzeigensuche.
 */
interface ListingSearchPort {
    /**
     * Sucht Anzeigen anhand eines fachlichen Filters.
     *
     * @param filter Validierter Suchfilter.
     * @return Passende Anzeigen.
     */
    suspend fun search(filter: ListingSearchFilter): List<Listing>
}
