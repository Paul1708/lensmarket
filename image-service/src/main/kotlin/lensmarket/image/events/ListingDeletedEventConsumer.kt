package lensmarket.image.events

import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.image.domain.usecase.DeleteImagesForListingUseCase
import lensmarket.image.domain.port.ImageMetricsPort
import lensmarket.image.runtime.suspendToUni
import org.eclipse.microprofile.reactive.messaging.Incoming

/**
 * Konsumiert `listing-deleted`-Events aus dem Listing Service.
 *
 * Der Consumer entfernt alle Bilder der gelöschten Anzeige unwiderruflich aus Object
 * Storage und Datenbank, damit sie über die Bild-Endpunkte nicht mehr abrufbar sind.
 *
 * @param deleteImagesForListing Use Case zum Entfernen aller Bilder eines Listings.
 */
@ApplicationScoped
class ListingDeletedEventConsumer(
    private val deleteImagesForListing: DeleteImagesForListingUseCase,
    private val metrics: ImageMetricsPort
) {
    /**
     * Verarbeitet eine gelöschte Anzeige.
     *
     * @param event Eingehendes `listing-deleted`-Event.
     * @return Abschluss der Event-Verarbeitung.
     */
    @Incoming("listing-deleted-in")
    fun onListingDeleted(event: ListingDeletedEvent): Uni<Unit> = suspendToUni {
        try {
            deleteImagesForListing.deleteForListing(event.eventId, event.occurredAt, event.listingId)
            Unit
        } catch (failure: Throwable) {
            metrics.eventConsumed("listing-deleted", "failed")
            throw failure
        }
    }
}
