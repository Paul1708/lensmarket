package lensmarket.messaging.data.client

import io.smallrye.mutiny.Uni
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.runBlocking
import org.jboss.resteasy.reactive.RestResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import lensmarket.messaging.domain.usecase.MessagingDependencyTimeoutException
import lensmarket.messaging.domain.usecase.MessagingDependencyUnavailableException
import java.util.concurrent.TimeoutException

class ListingServiceClientTest {
    @Test
    fun `listing lookup maps typed successful and missing responses`() = runBlocking {
        val client = client(
            response(Response.Status.OK, ListingLookupResponse("listing-1", "owner-1", "ACTIVE"))
        )
        val listing = client.findListing("listing-1")
        assertEquals("listing-1", listing?.id)
        assertEquals("owner-1", listing?.ownerUserId)
        assertEquals("ACTIVE", listing?.status)

        assertNull(client(response(Response.Status.NOT_FOUND)).findListing("missing"))
    }

    @Test
    fun `listing lookup rejects server errors malformed contracts and reactive timeouts`() {
        assertThrows<MessagingDependencyUnavailableException> {
            runBlocking { client(response(Response.Status.INTERNAL_SERVER_ERROR)).findListing("listing-1") }
        }
        assertThrows<MessagingDependencyUnavailableException> {
            runBlocking {
                client(response(Response.Status.OK, ListingLookupResponse(id = "listing-1", status = "ACTIVE")))
                    .findListing("listing-1")
            }
        }
        assertThrows<MessagingDependencyTimeoutException> {
            runBlocking {
                client(Uni.createFrom().failure(TimeoutException("read timeout"))).findListing("listing-1")
            }
        }
    }

    private fun client(response: RestResponse<ListingLookupResponse>): ListingServiceClient =
        client(Uni.createFrom().item(response))

    private fun client(response: Uni<RestResponse<ListingLookupResponse>>): ListingServiceClient =
        ListingServiceClient(ListingServiceCall { response })

    private fun response(
        status: Response.Status,
        entity: ListingLookupResponse = ListingLookupResponse()
    ): RestResponse<ListingLookupResponse> =
        RestResponse.ResponseBuilder.create(status, entity).build()
}
