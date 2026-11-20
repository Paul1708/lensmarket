package lensmarket.image.resources

import io.smallrye.common.annotation.NonBlocking
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.mutiny.core.Vertx
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.BeanParam
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.headers.Header
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Encoding
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement
import lensmarket.image.domain.model.ImageSearchFilter
import lensmarket.image.domain.usecase.AuthenticateUserUseCase
import lensmarket.image.domain.usecase.ChangeImageStatusUseCase
import lensmarket.image.domain.usecase.GetOwnImageUseCase
import lensmarket.image.domain.usecase.GetPublicImageUseCase
import lensmarket.image.domain.usecase.ImageDependencyTimeoutException
import lensmarket.image.domain.usecase.ImageDependencyUnavailableException
import lensmarket.image.domain.usecase.ImageUseCaseForbiddenException
import lensmarket.image.domain.usecase.ImageUseCaseNotFoundException
import lensmarket.image.domain.usecase.ImageUseCaseUnauthorizedException
import lensmarket.image.domain.usecase.ImageUseCaseValidationException
import lensmarket.image.domain.usecase.ListOwnImagesUseCase
import lensmarket.image.domain.usecase.ListPublicImagesUseCase
import lensmarket.image.domain.usecase.ServeOwnWatermarkedImageUseCase
import lensmarket.image.domain.usecase.ServePublicWatermarkedImageUseCase
import lensmarket.image.domain.usecase.UploadImageUseCase
import org.jboss.resteasy.reactive.RestResponse
import java.util.concurrent.Callable

/** HTTP-Eingangsadapter für Bildmetadaten, Upload und Auslieferung. */
@Path("")
class ImageResource(
    private val authenticateUser: AuthenticateUserUseCase,
    private val uploadImage: UploadImageUseCase,
    private val listPublicImages: ListPublicImagesUseCase,
    private val listOwnImages: ListOwnImagesUseCase,
    private val getPublicImage: GetPublicImageUseCase,
    private val getOwnImage: GetOwnImageUseCase,
    private val changeImageStatus: ChangeImageStatusUseCase,
    private val servePublicWatermarkedImage: ServePublicWatermarkedImageUseCase,
    private val serveOwnWatermarkedImage: ServeOwnWatermarkedImageUseCase,
    private val vertx: Vertx
) {
    @GET @Path("/health")
    @Operation(summary = "Service-Liveness lesen")
    @APIResponse(responseCode = "200", description = "Service ist erreichbar")
    fun health(): Map<String, String> = mapOf("service" to "image-service", "status" to "UP")

    @POST @Path("/upload") @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(
        summary = "Bild zu einer eigenen ACTIVE-Anzeige hochladen",
        description = "Akzeptiert JPEG/PNG bis 10 MiB, validiert dekodierbaren Inhalt, verwirft das Original und speichert nur die Wasserzeichenkopie. Der Listing-Eventflow ist nach dem HTTP-Erfolg eventual consistent."
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequestBody(
        required = true,
        content = [Content(
            mediaType = "multipart/form-data",
            schema = Schema(
                type = SchemaType.OBJECT,
                requiredProperties = ["listingId", "file"],
                properties = [
                    SchemaProperty(name = "listingId", type = SchemaType.STRING, minLength = 1),
                    SchemaProperty(
                        name = "file", type = SchemaType.STRING, format = "binary",
                        description = "Dekodierbares image/jpeg oder image/png, maximal 10 MiB"
                    )
                ]
            ),
            encoding = [Encoding(name = "file", contentType = "image/jpeg, image/png")]
        )]
    )
    @APIResponses(
        APIResponse(
            responseCode = "201",
            description = "Wasserzeichenbild und Outbox-Ereignisse gespeichert",
            headers = [Header(name = "Location", description = "Öffentlicher Bildmetadaten-Pfad", schema = Schema(implementation = String::class))],
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ImageResponse::class))]
        ),
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
            responseCode = "503",
            description = "Listing Service nicht erreichbar oder Vertrag ungültig; error ist DEPENDENCY_UNAVAILABLE",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "504",
            description = "Listing-Service-Aufruf hat Zeitgrenze überschritten; error ist DEPENDENCY_TIMEOUT",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun upload(
        @HeaderParam("Authorization") authorization: String?,
        @BeanParam request: UploadImageRequest
    ): RestResponse<ImageResponse> = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        val command = vertx.executeBlocking(Callable { request.toCommand() }).awaitSuspending()
        val saved = uploadImage.upload(current.id, command)
        RestResponse.ResponseBuilder.create(Response.Status.CREATED, saved.toResponse())
            .header("Location", "/images/${saved.id}")
            .build()
    }

    @GET
    @Operation(summary = "Öffentliche WATERMARKED-Bilder filtern")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Bildmetadatenliste",
            content = [Content(mediaType = "application/json", schema = Schema(type = SchemaType.ARRAY, implementation = ImageResponse::class))]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler, ungültiger Bildinhalt, MIME-Type oder Status",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun list(
        @Parameter(required = false) @QueryParam("listingId") listingId: String?,
        @Parameter(required = false) @QueryParam("ownerUserId") ownerUserId: String?
    ): List<ImageResponse> = invokeUseCase {
        listPublicImages.list(ImageSearchFilter(listingId = listingId, ownerUserId = ownerUserId))
            .map { it.toResponse() }
    }

    @GET @Path("/mine")
    @Operation(summary = "Eigene Bilder einschließlich ARCHIVED filtern")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Bildmetadatenliste",
            content = [Content(mediaType = "application/json", schema = Schema(type = SchemaType.ARRAY, implementation = ImageResponse::class))]
        ),
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
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun mine(
        @HeaderParam("Authorization") authorization: String?,
        @Parameter(required = false) @QueryParam("listingId") listingId: String?,
        @Parameter(required = false, schema = Schema(enumeration = ["WATERMARKED", "ARCHIVED"])) @QueryParam("status") status: String?
    ): List<ImageResponse> = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        listOwnImages.list(current.id, ImageSearchFilter(listingId = listingId, status = status))
            .map { it.toResponse() }
    }

    @GET @Path("/mine/{id}")
    @Operation(summary = "Eigene Bildmetadaten unabhängig vom Status lesen")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Bildmetadaten",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ImageResponse::class))]
    )
    @OwnerProtectedResponses
    suspend fun getMine(
        @HeaderParam("Authorization") authorization: String?, @PathParam("id") id: String
    ): ImageResponse = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        getOwnImage.get(id, current.id).toResponse()
    }

    @GET @Path("/{id}")
    @Operation(summary = "Öffentliche WATERMARKED-Bildmetadaten lesen")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Bildmetadaten",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ImageResponse::class))]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler, ungültiger Bildinhalt, MIME-Type oder Status",
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
    suspend fun get(@PathParam("id") id: String): ImageResponse =
        invokeUseCase { getPublicImage.get(id).toResponse() }

    @PATCH @NonBlocking @Path("/{id}/status")
    @Operation(summary = "Status eines eigenen Bildes ändern")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Bildmetadaten",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ImageResponse::class))]
    )
    @OwnerProtectedResponses
    suspend fun changeStatus(
        @HeaderParam("Authorization") authorization: String?, @PathParam("id") id: String,
        request: ChangeImageStatusRequest
    ): ImageResponse = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        changeImageStatus.changeStatus(id, current.id, request.toCommand()).toResponse()
    }

    @GET @Path("/{id}/watermarked")
    @Operation(summary = "Öffentliches WATERMARKED-Bild ausliefern")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Binäre Wasserzeichenkopie",
            headers = [Header(name = "Content-Disposition", description = "inline mit Dateiname", schema = Schema(implementation = String::class))],
            content = [
                Content(mediaType = "image/jpeg", schema = Schema(type = SchemaType.STRING, format = "binary")),
                Content(mediaType = "image/png", schema = Schema(type = SchemaType.STRING, format = "binary"))
            ]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler, ungültiger Bildinhalt, MIME-Type oder Status",
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
    suspend fun getWatermarked(@PathParam("id") id: String): Response = invokeUseCase {
        val file = servePublicWatermarkedImage.serve(id)
        Response.ok(file.bytes).type(file.mimeType)
            .header("Content-Disposition", "inline; filename=\"${file.fileName}\"").build()
    }

    @GET @Path("/mine/{id}/watermarked")
    @Operation(summary = "Eigenes Wasserzeichenbild unabhängig vom Status ausliefern")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Binäre Wasserzeichenkopie",
        headers = [Header(name = "Content-Disposition", description = "inline mit Dateiname", schema = Schema(implementation = String::class))],
        content = [
            Content(mediaType = "image/jpeg", schema = Schema(type = SchemaType.STRING, format = "binary")),
            Content(mediaType = "image/png", schema = Schema(type = SchemaType.STRING, format = "binary"))
        ]
    )
    @OwnerProtectedResponses
    suspend fun getMineWatermarked(
        @HeaderParam("Authorization") authorization: String?, @PathParam("id") id: String
    ): Response = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        val file = serveOwnWatermarkedImage.serve(id, current.id)
        Response.ok(file.bytes).type(file.mimeType)
            .header("Content-Disposition", "inline; filename=\"${file.fileName}\"").build()
    }

    private suspend fun <T> invokeUseCase(block: suspend () -> T): T = try {
        block()
    } catch (exception: ImageUseCaseValidationException) {
        throw BadRequestException(exception.message)
    } catch (exception: ImageUseCaseNotFoundException) {
        throw NotFoundException(exception.message)
    } catch (exception: ImageUseCaseForbiddenException) {
        throw ForbiddenException(exception.message)
    } catch (exception: ImageUseCaseUnauthorizedException) {
        throw NotAuthorizedException("Bearer")
    } catch (exception: ImageDependencyTimeoutException) {
        throw WebApplicationException(exception.message, Response.Status.GATEWAY_TIMEOUT)
    } catch (exception: ImageDependencyUnavailableException) {
        throw WebApplicationException(exception.message, Response.Status.SERVICE_UNAVAILABLE)
    }

    private fun String?.bearerToken(): String? =
        this?.trim()?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
}
