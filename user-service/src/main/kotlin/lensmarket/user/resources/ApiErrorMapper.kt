package lensmarket.user.resources

import jakarta.persistence.PersistenceException
import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import lensmarket.user.metrics.UserMetrics
import org.jboss.logging.Logger

/**
 * Einheitliches JSON-Format für Fehlerantworten des User Service.
 *
 * @param error Stabiler technischer Fehlercode.
 * @param message Lesbare Fehlerbeschreibung.
 * @param status HTTP-Statuscode.
 * @param path Aufgerufener Request-Pfad.
 */
data class ApiErrorResponse(
    val error: String,
    val message: String,
    val status: Int,
    val path: String
)

/**
 * HTTP-naher API-Konflikt mit stabilem Fehlercode.
 *
 * Resources nutzen diese Exception, wenn ein fachlicher Konflikt nicht als generischer
 * Datenbankfehler erscheinen soll.
 *
 * @param error Stabiler technischer Fehlercode.
 * @param message Lesbare Fehlerbeschreibung.
 */
class ApiConflictException(
    val error: String,
    message: String
) : RuntimeException(message)

/**
 * Erstellt eine Fehlerantwort aus einem JAX-RS-Status.
 *
 * @param status HTTP-Status als Enum.
 * @param error Stabiler Fehlercode.
 * @param message Lesbare Fehlerbeschreibung.
 * @param uriInfo Request-Kontext für den Pfad.
 * @param metrics Metriken zum Zählen des API-Fehlers.
 * @return Fertige HTTP-Fehlerantwort.
 */
private fun apiError(
    status: Response.Status,
    error: String,
    message: String,
    uriInfo: UriInfo,
    metrics: UserMetrics
): Response =
    apiError(status.statusCode, error, message, uriInfo, metrics)

/**
 * Erstellt eine Fehlerantwort aus einem numerischen HTTP-Status.
 *
 * @param status HTTP-Statuscode.
 * @param error Stabiler Fehlercode.
 * @param message Lesbare Fehlerbeschreibung.
 * @param uriInfo Request-Kontext für den Pfad.
 * @param metrics Metriken zum Zählen des API-Fehlers.
 * @return Fertige HTTP-Fehlerantwort im einheitlichen Format.
 */
private fun apiError(
    status: Int,
    error: String,
    message: String,
    uriInfo: UriInfo,
    metrics: UserMetrics
): Response =
    Response.status(status)
        .entity(ApiErrorResponse(error, message, status, uriInfo.requestPath()))
        .build()
        .also { metrics.apiError(error, status) }

/**
 * Ermittelt den Request-Pfad für eine Fehlerantwort.
 *
 * @return Request-Pfad oder `/`, wenn kein Pfad vorhanden ist.
 */
private fun UriInfo.requestPath(): String =
    requestUri.path.takeIf { it.isNotBlank() } ?: "/"

/**
 * Übersetzt Validierungsfehler in HTTP 400.
 */
@Provider
class BadRequestErrorMapper : ExceptionMapper<BadRequestException> {
    /**
     * Request-Kontext fuer den Pfad der Fehlerantwort.
     */
    @Context
    lateinit var uriInfo: UriInfo

    /**
     * Service-Metriken fuer API-Fehlerzaehler.
     */
    @Inject
    lateinit var metrics: UserMetrics

    /**
     * Erstellt die Fehlerantwort für fehlerhafte Requests.
     *
     * @param exception Ausgelöste BadRequestException.
     * @return Einheitliche HTTP-400-Fehlerantwort.
     */
    override fun toResponse(exception: BadRequestException): Response =
        apiError(
            Response.Status.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.message ?: "Invalid request",
            uriInfo,
            metrics
        )
}

/**
 * Übersetzt nicht gefundene Ressourcen in HTTP 404.
 */
@Provider
class NotFoundErrorMapper : ExceptionMapper<NotFoundException> {
    /**
     * Request-Kontext fuer den Pfad der Fehlerantwort.
     */
    @Context
    lateinit var uriInfo: UriInfo

    /**
     * Service-Metriken fuer API-Fehlerzaehler.
     */
    @Inject
    lateinit var metrics: UserMetrics

    /**
     * Erstellt die Fehlerantwort für nicht gefundene Ressourcen.
     *
     * @param exception Ausgelöste NotFoundException.
     * @return Einheitliche HTTP-404-Fehlerantwort.
     */
    override fun toResponse(exception: NotFoundException): Response =
        apiError(Response.Status.NOT_FOUND, "NOT_FOUND", exception.message ?: "Resource not found", uriInfo, metrics)
}

/**
 * Übersetzt fachliche Konflikte in HTTP 409.
 */
@Provider
class ConflictErrorMapper : ExceptionMapper<ApiConflictException> {
    /**
     * Request-Kontext fuer den Pfad der Fehlerantwort.
     */
    @Context
    lateinit var uriInfo: UriInfo

    /**
     * Service-Metriken fuer API-Fehlerzaehler.
     */
    @Inject
    lateinit var metrics: UserMetrics

    /**
     * Erstellt die Fehlerantwort für fachliche Konflikte.
     *
     * @param exception Ausgelöste Konflikt-Exception mit stabilem Fehlercode.
     * @return Einheitliche HTTP-409-Fehlerantwort.
     */
    override fun toResponse(exception: ApiConflictException): Response =
        apiError(
            Response.Status.CONFLICT,
            exception.error,
            exception.message ?: "Request conflicts with existing data",
            uriInfo,
            metrics
        )
}

/**
 * Übersetzt Persistenzfehler in fachlich passende HTTP-Fehler.
 */
@Provider
class DatabaseErrorMapper : ExceptionMapper<PersistenceException> {
    /**
     * Request-Kontext fuer den Pfad der Fehlerantwort.
     */
    @Context
    lateinit var uriInfo: UriInfo

    /**
     * Service-Metriken fuer API-Fehlerzaehler.
     */
    @Inject
    lateinit var metrics: UserMetrics

    /**
     * Erstellt die Fehlerantwort für Datenbankfehler.
     *
     * Fachliche Konflikte sind bereits im Repository-Adapter typisiert. Rohe
     * Persistenzfehler werden als interner Serverfehler behandelt.
     *
     * @param exception Ausgelöste PersistenceException.
     * @return Einheitliche HTTP-Fehlerantwort.
     */
    override fun toResponse(exception: PersistenceException): Response =
        apiError(
            Response.Status.INTERNAL_SERVER_ERROR,
            "DATABASE_ERROR",
            "Database operation failed",
            uriInfo,
            metrics
        )
}

/**
 * Übersetzt allgemeine WebApplicationExceptions in das Service-Fehlerformat.
 */
@Provider
class WebApplicationErrorMapper : ExceptionMapper<WebApplicationException> {
    /**
     * Request-Kontext fuer den Pfad der Fehlerantwort.
     */
    @Context
    lateinit var uriInfo: UriInfo

    /**
     * Service-Metriken fuer API-Fehlerzaehler.
     */
    @Inject
    lateinit var metrics: UserMetrics

    /**
     * Erstellt die Fehlerantwort für bereits HTTP-nahe Exceptions.
     *
     * @param exception Ausgelöste WebApplicationException.
     * @return Einheitliche HTTP-Fehlerantwort mit dem vorhandenen Statuscode.
     */
    override fun toResponse(exception: WebApplicationException): Response {
        val status = exception.response?.status ?: Response.Status.INTERNAL_SERVER_ERROR.statusCode
        val error = if (status in 400..499) "HTTP_ERROR" else "SERVER_ERROR"
        return apiError(status, error, exception.message ?: "Request failed", uriInfo, metrics)
    }
}

/**
 * Fängt unerwartete Fehler ab und gibt HTTP 500 zurück.
 */
@Provider
class UnexpectedErrorMapper : ExceptionMapper<Throwable> {
    private val logger = Logger.getLogger(UnexpectedErrorMapper::class.java)

    /**
     * Request-Kontext fuer den Pfad der Fehlerantwort.
     */
    @Context
    lateinit var uriInfo: UriInfo

    /**
     * Service-Metriken fuer API-Fehlerzaehler.
     */
    @Inject
    lateinit var metrics: UserMetrics

    /**
     * Erstellt die Fehlerantwort für unerwartete Exceptions.
     *
     * @param exception Nicht speziell behandelter Fehler.
     * @return Einheitliche HTTP-500-Fehlerantwort.
     */
    override fun toResponse(exception: Throwable): Response {
        logger.error("Unexpected request failure", exception)
        return apiError(
            Response.Status.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "Unexpected server error",
            uriInfo,
            metrics
        )
    }
}
