package lensmarket.user.resources

import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.headers.Header
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement
import lensmarket.user.domain.usecase.AuthenticateUserUseCase
import lensmarket.user.domain.usecase.CreateUserUseCase
import lensmarket.user.domain.usecase.GetUserUseCase
import lensmarket.user.domain.usecase.ListUsersUseCase
import lensmarket.user.domain.usecase.LoginUserUseCase
import lensmarket.user.domain.usecase.PatchUserProfileUseCase
import lensmarket.user.domain.usecase.UserUseCaseConflictException
import lensmarket.user.domain.usecase.UserUseCaseForbiddenException
import lensmarket.user.domain.usecase.UserUseCaseNotFoundException
import lensmarket.user.domain.usecase.UserUseCaseUnauthorizedException
import lensmarket.user.domain.usecase.UserUseCaseValidationException
import org.jboss.resteasy.reactive.RestResponse

/** HTTP-Eingangsadapter für Nutzerkonten, Login und Profile. */
@Path("")
class UserResource(
    private val authenticateUser: AuthenticateUserUseCase,
    private val createUser: CreateUserUseCase,
    private val loginUser: LoginUserUseCase,
    private val listUsers: ListUsersUseCase,
    private val getUser: GetUserUseCase,
    private val patchUserProfile: PatchUserProfileUseCase
) {
    @GET
    @Path("/health")
    @Operation(summary = "Service-Liveness lesen")
    @APIResponse(responseCode = "200", description = "Service ist erreichbar")
    fun health(): Map<String, String> = mapOf("service" to "user-service", "status" to "UP")

    @POST
    @Operation(summary = "Nutzerkonto registrieren")
    @APIResponses(
        APIResponse(
            responseCode = "201",
            description = "Konto angelegt",
            headers = [Header(
                name = "Location",
                description = "Pfad des öffentlichen Profils",
                schema = Schema(implementation = String::class)
            )],
            content = [Content(mediaType = "application/json", schema = Schema(implementation = PrivateUserResponse::class))]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "409",
            description = "E-Mail-Adresse existiert bereits; error ist USER_ALREADY_EXISTS",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun create(request: CreateUserRequest): RestResponse<PrivateUserResponse> =
        invokeUseCase {
            val saved = createUser.create(request.toCommand())
            RestResponse.ResponseBuilder.create(Response.Status.CREATED, saved.toPrivateResponse())
                .header("Location", "/users/${saved.id}")
                .build()
        }

    @POST
    @Path("/login")
    @Operation(summary = "Anmelden und JWT ausstellen")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Login erfolgreich",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = LoginUserResponse::class))]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "401",
            description = "Anmeldedaten ungültig",
            headers = [Header(name = "WWW-Authenticate", schema = Schema(implementation = String::class))],
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun login(request: LoginUserRequest): LoginUserResponse =
        invokeUseCase { loginUser.login(request.toCommand()).toResponse() }

    @POST
    @Path("/logout")
    @Operation(
        summary = "JWT validieren und clientseitigen Logout bestätigen",
        description = "Es existiert keine serverseitige Session oder Token-Blacklist; der Client verwirft den Token."
    )
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses(
        APIResponse(responseCode = "204", description = "Token war gültig; keine Antwortnutzlast"),
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
    suspend fun logout(@HeaderParam("Authorization") authorization: String?): Response =
        invokeUseCase {
            authenticateUser.authenticate(authorization.bearerToken())
            Response.noContent().build()
        }

    @GET
    @Operation(summary = "Öffentliche Profile auflisten")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Öffentliche Profile",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY, implementation = PublicUserResponse::class)
            )]
        ),
        APIResponse(
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun list(): List<PublicUserResponse> =
        invokeUseCase { listUsers.list().map { it.toPublicResponse() } }

    @GET
    @Path("/me")
    @Operation(summary = "Eigenes privates Profil lesen")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Privates Profil",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = PrivateUserResponse::class))]
        ),
        APIResponse(
            responseCode = "401",
            description = "Bearer-Token fehlt oder ist ungültig",
            headers = [Header(name = "WWW-Authenticate", schema = Schema(implementation = String::class))],
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "404",
            description = "Ressource nicht gefunden",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun me(@HeaderParam("Authorization") authorization: String?): PrivateUserResponse =
        invokeUseCase {
            val currentUser = authenticateUser.authenticate(authorization.bearerToken())
            getUser.get(currentUser.id).toPrivateResponse()
        }

    @GET
    @Path("/{id}")
    @Operation(summary = "Öffentliches Profil lesen")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Öffentliches Profil ohne E-Mail-Adresse",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = PublicUserResponse::class))]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "404",
            description = "Ressource nicht gefunden",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun get(@PathParam("id") id: String): PublicUserResponse =
        invokeUseCase { getUser.get(id).toPublicResponse() }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Eigenes Profil partiell ändern")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Aktualisiertes privates Profil",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = PrivateUserResponse::class))]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler",
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
            description = "Identität darf die Ressource nicht ändern",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "404",
            description = "Ressource nicht gefunden",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        APIResponse(
            responseCode = "500",
            description = "Unerwarteter interner Fehler",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))]
        )
    )
    suspend fun patch(
        @HeaderParam("Authorization") authorization: String?,
        @PathParam("id") id: String,
        request: PatchUserProfileRequest
    ): PrivateUserResponse = invokeUseCase {
        val currentUser = authenticateUser.authenticate(authorization.bearerToken())
        patchUserProfile.patch(id, currentUser.id, request.toCommand()).toPrivateResponse()
    }

    private suspend fun <T> invokeUseCase(block: suspend () -> T): T = try {
        block()
    } catch (exception: UserUseCaseValidationException) {
        throw BadRequestException(exception.message)
    } catch (exception: UserUseCaseConflictException) {
        throw ApiConflictException(
            "USER_ALREADY_EXISTS",
            exception.message ?: "A user with this email already exists"
        )
    } catch (exception: UserUseCaseNotFoundException) {
        throw NotFoundException(exception.message)
    } catch (exception: UserUseCaseForbiddenException) {
        throw ForbiddenException(exception.message)
    } catch (exception: UserUseCaseUnauthorizedException) {
        throw NotAuthorizedException(exception.message ?: "Unauthorized", "Bearer")
    }

    private fun String?.bearerToken(): String? =
        this?.trim()?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
}
