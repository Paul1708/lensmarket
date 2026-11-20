package lensmarket.image.domain.port


/**
 * Port zur Besitzprüfung einer Anzeige im Listing Service.
 *
 * Der Image Service besitzt Listings nicht selbst und fragt deshalb nur die fachliche
 * Ownership extern ab.
 */
fun interface ListingOwnershipPort {
    /**
     * Prüft, ob ein Nutzer Besitzer einer Anzeige ist.
     *
     * @param listingId Technische Anzeigen-ID.
     * @param userId Technische Nutzer-ID.
     * @return `true`, wenn der Nutzer Besitzer der Anzeige ist.
     */
    suspend fun isListingOwner(listingId: String, userId: String): Boolean
}
