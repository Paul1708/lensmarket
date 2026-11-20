package lensmarket.listing.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Gauge
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.listing.domain.port.ListingMetricsPort
import java.util.concurrent.atomic.AtomicLong

private const val SERVICE = "listing-service"

/**
 * Kapselt fachliche Micrometer-Zähler des Listing Service.
 *
 * Die Counter erfassen Anzeigenaktionen, Domain Events und zentrale API-Fehler.
 *
 * @param meterRegistry Registry zum Registrieren und Aktualisieren der Counter.
 */
@ApplicationScoped
class ListingMetrics(private val meterRegistry: MeterRegistry) : ListingMetricsPort {

    private val outboxPending = AtomicLong()
    private val outboxOldestSeconds = AtomicLong()

    private val created = Counter.builder("lensmarket.listings.created").description("Created listings").register(meterRegistry)
    private val updated = Counter.builder("lensmarket.listings.updated").description("Updated listings").register(meterRegistry)
    private val deleted = Counter.builder("lensmarket.listings.deleted").description("Soft-deleted listings").register(meterRegistry)

    init {
        Gauge.builder("lensmarket.outbox.pending", outboxPending) { it.get().toDouble() }
            .description("Unpublished outbox events").tag("service", SERVICE).register(meterRegistry)
        Gauge.builder("lensmarket.outbox.oldest.seconds", outboxOldestSeconds) { it.get().toDouble() }
            .description("Age of oldest unpublished outbox event").tag("service", SERVICE).register(meterRegistry)
    }

    /**
     * Zählt eine neu angelegte Anzeige.
     */
    override fun listingCreated() { created.increment() }

    /**
     * Zählt eine aktualisierte Anzeige.
     */
    override fun listingUpdated() { updated.increment() }

    /**
     * Zählt eine per Soft-Delete gelöschte Anzeige.
     */
    override fun listingDeleted() { deleted.increment() }

    /**
     * Zählt einen echten Anzeigenstatuswechsel.
     *
     * @param oldStatus Vorheriger Status.
     * @param newStatus Neuer Status.
     */
    override fun listingStatusChanged(oldStatus: String, newStatus: String) {
        counter("lensmarket.listing.status.changed", "Listing status changes", "from_status", oldStatus, "to_status", newStatus).increment()
    }

    /**
     * Zählt ein erfolgreich veröffentlichtes Domain Event.
     *
     * @param eventType Event-Typ.
     */
    fun eventPublished(eventType: String) {
        counter("lensmarket.events.published", "Published domain events", "service", SERVICE, "event_type", eventType).increment()
    }

    fun outboxPublishFailed(eventType: String) {
        counter(
            "lensmarket.outbox.publish.failures",
            "Failed outbox publish attempts",
            "service", SERVICE,
            "event_type", eventType
        ).increment()
    }

    fun outboxState(pending: Long, oldestAgeSeconds: Long) {
        outboxPending.set(pending)
        outboxOldestSeconds.set(oldestAgeSeconds)
    }

    /**
     * Zählt ein konsumiertes Domain Event mit fachlichem Ergebnis.
     *
     * @param eventType Event-Typ.
     * @param result Ergebnis der Verarbeitung.
     */
    override fun eventConsumed(eventType: String, result: String) {
        counter("lensmarket.events.consumed", "Consumed domain events", "service", SERVICE, "event_type", eventType, "result", result).increment()
    }

    /**
     * Zählt eine zentrale API-Fehlerantwort.
     *
     * @param error Stabiler Fehlercode.
     * @param status HTTP-Statuscode.
     */
    fun apiError(error: String, status: Int) {
        counter("lensmarket.api.errors", "HTTP API errors", "service", SERVICE, "error", error, "status", status.toString()).increment()
        if (status == 401) {
            counter("lensmarket.auth.failures", "Rejected authentication attempts", "service", SERVICE, "reason", "unauthorized").increment()
        }
    }

    /**
     * Erstellt oder lädt einen Counter mit den angegebenen Tags.
     *
     * @param name Metrikname in Micrometer-Schreibweise.
     * @param description Beschreibung der Metrik.
     * @param tags Tag-Liste als Schlüssel-Wert-Paare.
     * @return Registrierter Counter.
     */
    private fun counter(name: String, description: String, vararg tags: String): Counter =
        Counter.builder(name).description(description).tags(*tags).register(meterRegistry)
}
