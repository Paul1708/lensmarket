package lensmarket.image.resources

import org.eclipse.microprofile.openapi.annotations.headers.Header
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses

/** Fehler-Antwortset fuer besitzergeschuetzte Operationen mit Bild-ID (400/401/403/404/500). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@APIResponses(
    APIResponse(
        responseCode = "400",
        description = "Validierungsfehler, ungültiger Bildinhalt, MIME-Type oder Status",
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
        description = "Nutzer:in besitzt Bild beziehungsweise Anzeige nicht",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    ),
    APIResponse(
        responseCode = "404",
        description = "Bild nicht gefunden oder im öffentlichen Pfad ARCHIVED",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    ),
    APIResponse(
        responseCode = "500",
        description = "Unerwarteter interner Fehler",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
    )
)
annotation class OwnerProtectedResponses
