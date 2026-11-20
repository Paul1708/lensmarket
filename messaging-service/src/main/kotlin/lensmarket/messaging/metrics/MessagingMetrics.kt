package lensmarket.messaging.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Gauge
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.messaging.domain.port.MessagingMetricsPort
import java.util.concurrent.atomic.AtomicLong

private const val SERVICE = "messaging-service"

/**
 * Kapselt fachliche Micrometer-Zähler des Messaging Service.
 *
 * Die Counter erfassen Konversationen, Nachrichten, Domain Events und zentrale
 * API-Fehler.
 *
 * @param meterRegistry Registry zum Registrieren und Aktualisieren der Counter.
 */
@ApplicationScoped
class MessagingMetrics(private val meterRegistry: MeterRegistry) : MessagingMetricsPort {

    private val outboxPending = AtomicLong()
    private val outboxOldestSeconds = AtomicLong()

    private val conversationsCreated = Counter.builder("lensmarket.conversations.created").description("Created conversations").register(meterRegistry)
    private val conversationsRemoved = Counter.builder("lensmarket.conversations.removed").description("Conversations removed by a participant").register(meterRegistry)
    private val messagesSent = Counter.builder("lensmarket.messages.sent").description("Sent messages").register(meterRegistry)

    init {
        Gauge.builder("lensmarket.outbox.pending", outboxPending) { it.get().toDouble() }
            .description("Unpublished outbox events").tag("service", SERVICE).register(meterRegistry)
        Gauge.builder("lensmarket.outbox.oldest.seconds", outboxOldestSeconds) { it.get().toDouble() }
            .description("Age of oldest unpublished outbox event").tag("service", SERVICE).register(meterRegistry)
    }

    /**
     * Zählt eine neu angelegte Konversation.
     */
    override fun conversationCreated() { conversationsCreated.increment() }

    /**
     * Zählt eine von einer Person entfernte Konversation.
     */
    override fun conversationRemoved() { conversationsRemoved.increment() }

    /**
     * Zählt eine gespeicherte Nachricht.
     */
    override fun messageSent() { messagesSent.increment() }

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
            "lensmarket.outbox.publish.failures", "Failed outbox publish attempts",
            "service", SERVICE, "event_type", eventType
        ).increment()
    }

    fun outboxState(pending: Long, oldestAgeSeconds: Long) {
        outboxPending.set(pending)
        outboxOldestSeconds.set(oldestAgeSeconds)
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
