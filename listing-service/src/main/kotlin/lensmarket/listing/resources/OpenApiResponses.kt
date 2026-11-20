package lensmarket.listing.resources

import org.eclipse.microprofile.openapi.annotations.headers.Header
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses

/** Fehler-Antwortset fuer besitzergeschuetzte Operationen mit Pfad-ID (400/401/403/404/500). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@APIResponses(
    APIResponse(
        responseCode = "400",
        description = "Validierungsfehler, etwa ungültige Pagination, Preise oder Statusübergänge",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    ),
    APIResponse(
        responseCode = "401",
        description = "Bearer-Token fehlt oder ist ungültig",
        headers = [Header(name = "WWW-Authenticate", schema = Schema(implementation = String::class))],
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    ),
    APIResponse(
        responseCode = "403",
        description = "Nutzer:in besitzt die Anzeige nicht",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    ),
    APIResponse(
        responseCode = "404",
        description = "Anzeige nicht gefunden oder im öffentlichen Pfad nicht ACTIVE",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    ),
    APIResponse(
        responseCode = "500",
        description = "Unerwarteter interner Fehler",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    )
)
annotation class OwnerProtectedResponses

/** Fehler-Antwortset fuer authentifizierte Operationen ohne Pfad-ID (400/401/500). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@APIResponses(
    APIResponse(
        responseCode = "400",
        description = "Validierungsfehler, etwa ungültige Pagination, Preise oder Statusübergänge",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    ),
    APIResponse(
        responseCode = "401",
        description = "Bearer-Token fehlt oder ist ungültig",
        headers = [Header(name = "WWW-Authenticate", schema = Schema(implementation = String::class))],
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    ),
    APIResponse(
        responseCode = "500",
        description = "Unerwarteter interner Fehler",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    )
)
annotation class AuthenticatedResponses
