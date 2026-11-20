package lensmarket.user.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.user.domain.port.UserMetricsPort

private const val SERVICE = "user-service"

/**
 * Kapselt fachliche Micrometer-Zähler des User Service.
 *
 * Die Metriken werden von Prometheus als Counter exportiert und im Grafana-Dashboard
 * für Nutzeraktionen und API-Fehler verwendet.
 *
 * @param meterRegistry Registry zum Registrieren und Aktualisieren der Counter.
 */
@ApplicationScoped
class UserMetrics(private val meterRegistry: MeterRegistry) : UserMetricsPort {

    private val updated = Counter.builder("lensmarket.users.updated")
        .description("Updated user profiles")
        .register(meterRegistry)

    /**
     * Zählt ein neu angelegtes Nutzerkonto.
     *
     * @param role Rolle des neuen Nutzers.
     */
    override fun userCreated(role: String) {
        counter("lensmarket.users.created", "Created users", "role", role.normalizedRole()).increment()
    }

    /**
     * Zählt eine erfolgreiche Profilaktualisierung.
     */
    override fun userUpdated() {
        updated.increment()
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

    /**
     * Normalisiert Rollenwerte für ein begrenztes Metrik-Label.
     *
     * @return `CUSTOMER`, `PHOTOGRAPHER` oder `OTHER`.
     */
    private fun String.normalizedRole(): String =
        uppercase().takeIf { it == "CUSTOMER" || it == "PHOTOGRAPHER" } ?: "OTHER"
}
