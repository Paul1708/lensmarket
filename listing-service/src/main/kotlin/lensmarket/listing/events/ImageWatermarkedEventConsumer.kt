package lensmarket.listing.events

import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.listing.domain.usecase.AttachImageResult
import lensmarket.listing.domain.usecase.AttachImageToListingUseCase
import lensmarket.listing.domain.port.ListingMetricsPort
import lensmarket.listing.runtime.suspendToUni
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger

/**
 * Konsumiert `image-watermarked`-Events aus dem Image Service.
 *
 * Der Consumer hängt die Bild-ID idempotent an die betroffene Anzeige an und erfasst,
 * ob das Event fachlich verarbeitet oder wegen fehlender Anzeige ignoriert wurde.
 *
 * @param attachImage Use Case für die idempotente Aktualisierung der Listing-Projektion.
 */
@ApplicationScoped
class ImageWatermarkedEventConsumer(
    private val attachImage: AttachImageToListingUseCase,
    private val metrics: ListingMetricsPort
) {
    private val logger = Logger.getLogger(ImageWatermarkedEventConsumer::class.java)

    /**
     * Verarbeitet ein fertig wassergezeichnetes Bild.
     *
     * @param event Eingehendes `image-watermarked`-Event.
     * @return Abschluss der Event-Verarbeitung.
     */
    @Incoming("image-watermarked-in")
    fun onImageWatermarked(event: ImageWatermarkedEvent): Uni<Unit> = suspendToUni {
        try {
            if (attachImage.attach(event.eventId, event.occurredAt, event.listingId, event.imageId) ==
                AttachImageResult.LISTING_NOT_FOUND
            ) {
                logger.warn("Ignored image-watermarked event ${event.eventId}: listing ${event.listingId} not found")
            }
        } catch (failure: Throwable) {
            metrics.eventConsumed("image-watermarked", "failed")
            throw failure
        }
    }
}
