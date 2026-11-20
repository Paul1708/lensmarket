package lensmarket.messaging.resources

import io.smallrye.common.annotation.NonBlocking
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.headers.Header
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement
import lensmarket.messaging.domain.usecase.AuthenticateUserUseCase
import lensmarket.messaging.domain.usecase.CreateConversationUseCase
import lensmarket.messaging.domain.usecase.GetConversationUseCase
import lensmarket.messaging.domain.usecase.GetMessagesUseCase
import lensmarket.messaging.domain.usecase.ListConversationsUseCase
import lensmarket.messaging.domain.usecase.MessagingDependencyTimeoutException
import lensmarket.messaging.domain.usecase.MessagingDependencyUnavailableException
import lensmarket.messaging.domain.usecase.MessagingUseCaseForbiddenException
import lensmarket.messaging.domain.usecase.MessagingUseCaseNotFoundException
import lensmarket.messaging.domain.usecase.MessagingUseCaseUnauthorizedException
import lensmarket.messaging.domain.usecase.MessagingUseCaseValidationException
import lensmarket.messaging.domain.usecase.RemoveConversationUseCase
import lensmarket.messaging.domain.usecase.SendMessageUseCase
import org.jboss.resteasy.reactive.RestResponse

/** HTTP-Eingangsadapter für Konversationen und Nachrichten. */
@Path("")
class MessagingResource(
    private val authenticateUser: AuthenticateUserUseCase,
    private val createConversation: CreateConversationUseCase,
    private val listConversations: ListConversationsUseCase,
    private val getConversation: GetConversationUseCase,
    private val removeConversation: RemoveConversationUseCase,
    private val sendMessage: SendMessageUseCase,
    private val getMessages: GetMessagesUseCase
) {
    @GET @Path("/health")
    @Operation(summary = "Service-Liveness lesen")
    @APIResponse(responseCode = "200", description = "Service ist erreichbar")
    fun health(): Map<String, String> = mapOf("service" to "messaging-service", "status" to "UP")

    @POST @NonBlocking @Path("/conversations")
    @Operation(
        summary = "Konversation zu einer ACTIVE-Anzeige erstellen oder wiederverwenden",
        description = "Pro Interessent:in und Anzeige existiert höchstens eine Konversation. Die Anzeigenbesitzer:in kann keine Konversation mit sich selbst eröffnen."
    )
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses(
        APIResponse(
            responseCode = "201",
            description = "Neue Konversation angelegt",
            headers = [Header(name = "Location", description = "Konversationspfad", schema = Schema(implementation = String::class))],
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ConversationResponse::class))]
        ),
        APIResponse(
            responseCode = "200",
            description = "Bereits vorhandene Konversation",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ConversationResponse::class))]
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
        )
    )
    @ParticipantProtectedResponses
    suspend fun createConversation(
        @HeaderParam("Authorization") authorization: String?, request: CreateConversationRequest
    ): RestResponse<ConversationResponse> = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        val result = createConversation.create(current.id, request.toCommand())
        val status = if (result.created) Response.Status.CREATED else Response.Status.OK
        val response = RestResponse.ResponseBuilder.create(status, result.conversation.toResponse())
        if (result.created) response.header("Location", "/messages/conversations/${result.conversation.id}")
        response.build()
    }

    @GET @Path("/conversations")
    @Operation(
        summary = "Eigene Konversationen auflisten",
        description = "Nach letzter Aktivität sortierte Offset/Limit-Pagination ohne Gesamtzähler."
    )
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses(
        APIResponse(
            responseCode = "200",
            description = "Offset/Limit-paginierte Konversationsliste ohne Gesamtzähler",
            content = [Content(mediaType = "application/json", schema = Schema(type = SchemaType.ARRAY, implementation = ConversationResponse::class))]
        ),
        APIResponse(
            responseCode = "400",
            description = "Validierungsfehler, ungültige Pagination, inaktive Anzeige oder zu langer Key/Text",
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
    suspend fun listConversations(
        @HeaderParam("Authorization") authorization: String?,
        @Parameter(required = false) @QueryParam("listingId") listingId: String?,
        @Parameter(description = "Standard 50", required = false) @QueryParam("limit") limit: Int?,
        @Parameter(required = false) @QueryParam("offset") offset: Int?
    ): List<ConversationResponse> = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        listConversations.list(current.id, listingId, limit, offset).map { it.toResponse() }
    }

    @GET @Path("/conversations/{conversationId}")
    @Operation(summary = "Eigene Konversation lesen")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Konversation",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ConversationResponse::class))]
    )
    @ParticipantProtectedResponses
    suspend fun getConversation(
        @HeaderParam("Authorization") authorization: String?,
        @PathParam("conversationId") conversationId: String
    ): ConversationResponse = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        getConversation.get(current.id, conversationId).toResponse()
    }

    @DELETE @Path("/conversations/{conversationId}")
    @Operation(
        summary = "Konversation aus der eigenen Ansicht entfernen",
        description = "Blendet den Verlauf nur für die aufrufende Person aus. Nachdem beide Teilnehmenden ihn entfernt haben, ist die Konversation geschlossen."
    )
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Konversation entfernt (aus eigener Sicht); status ist OPEN oder CLOSED, wenn beide Seiten entfernt haben",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ConversationResponse::class))]
    )
    @ParticipantProtectedResponses
    suspend fun removeConversation(
        @HeaderParam("Authorization") authorization: String?,
        @PathParam("conversationId") conversationId: String
    ): ConversationResponse = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        removeConversation.remove(current.id, conversationId).toResponse()
    }

    @POST @Path("/conversations/{conversationId}/messages")
    @Operation(
        summary = "Nachricht als Konversationsteilnehmer:in senden",
        description = "Ein Retry mit demselben Idempotency-Key, derselben Konversation und demselben Sender liefert die ursprüngliche Nachricht und erzeugt weder neue Nachricht noch neues Event."
    )
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses(
        APIResponse(
            responseCode = "201",
            description = "Neue Nachricht und message-sent-Outbox-Eintrag gespeichert",
            headers = [Header(name = "Location", description = "Logischer Nachrichtenpfad", schema = Schema(implementation = String::class))],
            content = [Content(mediaType = "application/json", schema = Schema(implementation = MessageResponse::class))]
        ),
        APIResponse(
            responseCode = "200",
            description = "Idempotenter Retry; ursprüngliche Nachricht",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = MessageResponse::class))]
        )
    )
    @ParticipantProtectedResponses
    suspend fun sendMessage(
        @HeaderParam("Authorization") authorization: String?,
        @Parameter(
            name = "Idempotency-Key", `in` = ParameterIn.HEADER, required = false,
            description = "Optionaler, vom Client stabil wiederverwendeter Schlüssel. Gültigkeitsbereich: Konversation + Sender.",
            schema = Schema(minLength = 1, maxLength = 64)
        )
        @HeaderParam("Idempotency-Key") idempotencyKey: String?,
        @PathParam("conversationId") conversationId: String,
        request: SendMessageRequest
    ): RestResponse<MessageResponse> = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        val result = sendMessage.send(current.id, request.toCommand(conversationId, idempotencyKey))
        val status = if (result.created) Response.Status.CREATED else Response.Status.OK
        val response = RestResponse.ResponseBuilder.create(status, result.message.toResponse())
        if (result.created) {
            response.header(
                "Location", "/messages/conversations/${result.message.conversationId}/messages/${result.message.id}"
            )
        }
        response.build()
    }

    @GET @Path("/conversations/{conversationId}/messages")
    @Operation(summary = "Nachrichten einer eigenen Konversation lesen")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponse(
        responseCode = "200",
        description = "Offset/Limit-paginierte Nachrichtenliste ohne Gesamtzähler",
        content = [Content(mediaType = "application/json", schema = Schema(type = SchemaType.ARRAY, implementation = MessageResponse::class))]
    )
    @ParticipantProtectedResponses
    suspend fun getMessages(
        @HeaderParam("Authorization") authorization: String?,
        @PathParam("conversationId") conversationId: String,
        @Parameter(description = "Standard 100", required = false) @QueryParam("limit") limit: Int?,
        @Parameter(required = false) @QueryParam("offset") offset: Int?
    ): List<MessageResponse> = invokeUseCase {
        val current = authenticateUser.authenticate(authorization.bearerToken())
        getMessages.get(current.id, conversationId, limit, offset).map { it.toResponse() }
    }

    private suspend fun <T> invokeUseCase(block: suspend () -> T): T = try {
        block()
    } catch (exception: MessagingUseCaseValidationException) {
        throw BadRequestException(exception.message)
    } catch (exception: MessagingUseCaseNotFoundException) {
        throw NotFoundException(exception.message)
    } catch (exception: MessagingUseCaseForbiddenException) {
        throw ForbiddenException(exception.message)
    } catch (exception: MessagingUseCaseUnauthorizedException) {
        throw NotAuthorizedException("Bearer")
    } catch (exception: MessagingDependencyTimeoutException) {
        throw WebApplicationException(exception.message, Response.Status.GATEWAY_TIMEOUT)
    } catch (exception: MessagingDependencyUnavailableException) {
        throw WebApplicationException(exception.message, Response.Status.SERVICE_UNAVAILABLE)
    }

    private fun String?.bearerToken(): String? =
        this?.trim()?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
}
