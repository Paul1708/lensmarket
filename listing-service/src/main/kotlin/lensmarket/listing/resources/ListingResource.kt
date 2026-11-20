package lensmarket.listing.resources

import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.headers.Header
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement
import lensmarket.listing.domain.model.DEFAULT_LISTING_SEARCH_LIMIT
import lensmarket.listing.domain.model.ListingSearchFilter
import lensmarket.listing.domain.usecase.AuthenticateUserUseCase
import lensmarket.listing.domain.usecase.ChangeListingStatusUseCase
import lensmarket.listing.domain.usecase.CreateListingUseCase
import lensmarket.listing.domain.usecase.DeleteListingUseCase
import lensmarket.listing.domain.usecase.GetOwnListingUseCase
import lensmarket.listing.domain.usecase.GetPublicListingUseCase
import lensmarket.listing.domain.usecase.ListingUseCaseForbiddenException
import lensmarket.listing.domain.usecase.ListingUseCaseNotFoundException
import lensmarket.listing.domain.usecase.ListingUseCaseUnauthorizedException
import lensmarket.listing.domain.usecase.ListingUseCaseValidationException
import lensmarket.listing.domain.usecase.PatchListingUseCase
import lensmarket.listing.domain.usecase.SearchOwnListingsUseCase
import lensmarket.listing.domain.usecase.SearchPublicListingsUseCase
import lensmarket.listing.domain.usecase.UpdateListingUseCase
import org.jboss.resteasy.reactive.RestResponse
import java.math.BigDecimal

/** HTTP-Eingangsadapter für Anzeigen und Suche. */
@Path("")
class ListingResource(
    private val authenticateUser: AuthenticateUserUseCase,
    private val createListing: CreateListingUseCase,
    private val searchPublicListings: SearchPublicListingsUseCase,
    private val searchOwnListings: SearchOwnListingsUseCase,
    private val getPublicListing: GetPublicListingUseCase,
    private val getOwnListing: GetOwnListingUseCase,
    private val updateListing: UpdateListingUseCase,
    private val patchListing: PatchListingUseCase,
    private val changeListingStatus: ChangeListingStatusUseCase,
    private val deleteListing: DeleteListingUseCase
) {
    @GET @Path("/health")
    @Operation(summary = "Service-Liveness lesen")
    @APIResponse(responseCode = "200", description = "Service ist erreichbar")
    fun health(): Map<String, String> = mapOf("service" to "listing-service", "status" to "UP")

    @POST
    @Operation(
        summary = "Eigene Anzeige anlegen",
        description = "Owner und Startstatus ACTIVE werden serverseitig gesetzt; Bild-IDs kommen ausschließlich aus dem image-watermarked-Eventflow."
    )
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "201",
        description = "Anzeige angelegt",
        headers = [Header(name = "Location", description = "Öffentlicher Listing-Pfad", schema = Schema(implementation = String::class))],
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ListingResponse::class))]
    )
    @AuthenticatedResponses
    suspend fun create(
        @HeaderParam("Authorization") authorization: String?,
        request: CreateListingRequest
    ): RestResponse<ListingResponse> = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        val saved = createListing.create(current.id, request.toCommand())
        RestResponse.ResponseBuilder.create(Response.Status.CREATED, saved.toResponse())
            .header("Location", "/listings/${saved.id}")
            .build()
    }

    @GET
    @Operation(summary = "Öffentliche ACTIVE-Anzeigen suchen")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Offset/Limit-paginierte Liste ohne Gesamtzähler",
            content = [Content(mediaType = "application/json", schema = Schema(type = SchemaType.ARRAY, implementation = ListingResponse::class))]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler, etwa ungültige Pagination, Preise oder Statusübergänge",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun search(
        @Parameter(description = "Freitext für Titel/Beschreibung", required = false) @QueryParam("q") query: String?,
        @Parameter(required = false) @QueryParam("ownerUserId") ownerUserId: String?,
        @Parameter(required = false) @QueryParam("tag") tag: String?,
        @Parameter(required = false) @QueryParam("location") location: String?,
        @Parameter(required = false) @QueryParam("minPrice") minPrice: BigDecimal?,
        @Parameter(required = false) @QueryParam("maxPrice") maxPrice: BigDecimal?,
        @Parameter(description = "Standard 50", required = false) @QueryParam("limit") limit: Int?,
        @Parameter(required = false) @QueryParam("offset") offset: Int?
    ): List<ListingResponse> = invokeUseCase {
        searchPublicListings.search(filter(query, ownerUserId, null, tag, location, minPrice, maxPrice, limit, offset))
            .map { it.toResponse() }
    }

    @GET @Path("/mine")
    @Operation(summary = "Eigene Anzeigen in allen Status suchen")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Offset/Limit-paginierte Liste ohne Gesamtzähler",
        content = [Content(mediaType = "application/json", schema = Schema(type = SchemaType.ARRAY, implementation = ListingResponse::class))]
    )
    @AuthenticatedResponses
    suspend fun mine(
        @HeaderParam("Authorization") authorization: String?,
        @Parameter(description = "Freitext für Titel/Beschreibung", required = false) @QueryParam("q") query: String?,
        @Parameter(required = false, schema = Schema(enumeration = ["ACTIVE", "PAUSED", "SOLD"])) @QueryParam("status") status: String?,
        @Parameter(required = false) @QueryParam("tag") tag: String?,
        @Parameter(required = false) @QueryParam("location") location: String?,
        @Parameter(required = false) @QueryParam("minPrice") minPrice: BigDecimal?,
        @Parameter(required = false) @QueryParam("maxPrice") maxPrice: BigDecimal?,
        @Parameter(description = "Standard 50", required = false) @QueryParam("limit") limit: Int?,
        @Parameter(required = false) @QueryParam("offset") offset: Int?
    ): List<ListingResponse> = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        searchOwnListings.search(
            current.id,
            filter(query, null, status, tag, location, minPrice, maxPrice, limit, offset)
        )
            .map { it.toResponse() }
    }

    @GET @Path("/mine/{id}")
    @Operation(summary = "Eigene Anzeige unabhängig vom Status lesen")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Anzeige",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ListingResponse::class))]
    )
    @OwnerProtectedResponses
    suspend fun getMine(
        @HeaderParam("Authorization") authorization: String?, @PathParam("id") id: String
    ): ListingResponse = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        getOwnListing.get(id, current.id).toResponse()
    }

    @GET @Path("/{id}")
    @Operation(summary = "Öffentliche ACTIVE-Anzeige lesen")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Anzeige",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ListingResponse::class))]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler, etwa ungültige Pagination, Preise oder Statusübergänge",
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
    suspend fun get(@PathParam("id") id: String): ListingResponse =
        invokeUseCase { getPublicListing.get(id).toResponse() }

    @PUT @Path("/{id}")
    @Operation(summary = "Editierbare Felder einer eigenen Anzeige vollständig ersetzen")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Anzeige",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ListingResponse::class))]
    )
    @OwnerProtectedResponses
    suspend fun update(
        @HeaderParam("Authorization") authorization: String?, @PathParam("id") id: String,
        request: UpdateListingRequest
    ): ListingResponse = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        updateListing.update(id, current.id, request.toCommand()).toResponse()
    }

    @PATCH @Path("/{id}")
    @Operation(summary = "Editierbare Felder einer eigenen Anzeige partiell ändern")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Anzeige",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ListingResponse::class))]
    )
    @OwnerProtectedResponses
    suspend fun patch(
        @HeaderParam("Authorization") authorization: String?, @PathParam("id") id: String,
        request: PatchListingRequest
    ): ListingResponse = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        patchListing.patch(id, current.id, request.toCommand()).toResponse()
    }

    @PATCH @Path("/{id}/status")
    @Operation(
        summary = "Status einer eigenen Anzeige wechseln",
        description = "Erlaubt ACTIVE ↔ PAUSED sowie ACTIVE/PAUSED → SOLD. SOLD ist terminal; derselbe Status ist idempotent."
    )
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Anzeige",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ListingResponse::class))]
    )
    @OwnerProtectedResponses
    suspend fun changeStatus(
        @HeaderParam("Authorization") authorization: String?, @PathParam("id") id: String,
        request: ChangeListingStatusRequest
    ): ListingResponse = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        changeListingStatus.changeStatus(id, current.id, request.toCommand()).listing.toResponse()
    }

    @DELETE @Path("/{id}")
    @Operation(summary = "Eigene Anzeige per Soft-Delete löschen")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(responseCode = "204", description = "Anzeige gelöscht; listing-deleted liegt in der Outbox")
    @OwnerProtectedResponses
    suspend fun delete(
        @HeaderParam("Authorization") authorization: String?, @PathParam("id") id: String
    ): RestResponse<Void> = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        deleteListing.delete(id, current.id)
        RestResponse.noContent()
    }

    private fun filter(
        query: String?, owner: String?, status: String?, tag: String?, location: String?,
        min: BigDecimal?, max: BigDecimal?, limit: Int?, offset: Int?
    ) = ListingSearchFilter(
        query = query, ownerUserId = owner, status = status, tag = tag, location = location,
        minPrice = min, maxPrice = max, limit = limit ?: DEFAULT_LISTING_SEARCH_LIMIT, offset = offset ?: 0
    )

    private suspend fun <T> invokeUseCase(block: suspend () -> T): T = try {
        block()
    } catch (exception: ListingUseCaseValidationException) {
        throw BadRequestException(exception.message)
    } catch (exception: ListingUseCaseNotFoundException) {
        throw NotFoundException(exception.message)
    } catch (exception: ListingUseCaseForbiddenException) {
        throw ForbiddenException(exception.message)
    } catch (exception: ListingUseCaseUnauthorizedException) {
        throw NotAuthorizedException("Bearer")
    }

    private fun String?.bearerToken(): String? =
        this?.trim()?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
}
