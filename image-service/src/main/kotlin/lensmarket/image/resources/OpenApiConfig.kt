package lensmarket.image.resources

import jakarta.ws.rs.core.Application
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType
import org.eclipse.microprofile.openapi.annotations.info.Info
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme
import org.eclipse.microprofile.openapi.annotations.servers.Server

/**
 * Traeger fuer globale OpenAPI-Metadaten (Info, Server, SecurityScheme).
 * Ohne eine JAX-RS-Application-Subklasse werden @OpenAPIDefinition/@SecurityScheme
 * von SmallRye OpenAPI nicht zuverlaessig aufgegriffen.
 */
@OpenAPIDefinition(
    info = Info(
        title = "LensMarket Image Service API",
        version = "0.1.0",
        description = "Wasserzeichen-Bildupload, Metadaten und Auslieferung. Öffentliche Lesewege liefern ausschließlich WATERMARKED-Bilder."
    ),
    servers = [Server(url = "http://localhost:8081", description = "Lokaler Traefik-Gateway")]
)
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
class OpenApiConfig : Application()
