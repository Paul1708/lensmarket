package lensmarket.image.data.client

import io.smallrye.mutiny.Uni
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.runBlocking
import org.jboss.resteasy.reactive.RestResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import lensmarket.image.domain.usecase.ImageDependencyTimeoutException
import lensmarket.image.domain.usecase.ImageDependencyUnavailableException
import java.util.concurrent.TimeoutException

class ListingServiceClientTest {
    @Test
    fun `ownership maps typed successful and missing responses`() = runBlocking {
        val owner = client(response(Response.Status.OK, ListingOwnershipResponse("owner-1")))
        assertTrue(owner.isListingOwner("listing-1", "owner-1"))
        assertFalse(owner.isListingOwner("listing-1", "other"))

        val missing = client(response(Response.Status.NOT_FOUND))
        assertFalse(missing.isListingOwner("missing", "owner-1"))
    }

    @Test
    fun `ownership rejects server errors malformed contracts and reactive timeouts`() {
        assertThrows<ImageDependencyUnavailableException> {
            runBlocking { client(response(Response.Status.SERVICE_UNAVAILABLE)).isListingOwner("listing-1", "owner-1") }
        }
        assertThrows<ImageDependencyUnavailableException> {
            runBlocking {
                client(response(Response.Status.OK, ListingOwnershipResponse()))
                    .isListingOwner("listing-1", "owner-1")
            }
        }
        assertThrows<ImageDependencyTimeoutException> {
            runBlocking {
                client(Uni.createFrom().failure(TimeoutException("read timeout")))
                    .isListingOwner("listing-1", "owner-1")
            }
        }
    }

    private fun client(response: RestResponse<ListingOwnershipResponse>): ListingServiceClient =
        client(Uni.createFrom().item(response))

    private fun client(response: Uni<RestResponse<ListingOwnershipResponse>>): ListingServiceClient =
        ListingServiceClient(ListingServiceCall {
            response
        })

    private fun response(
        status: Response.Status,
        entity: ListingOwnershipResponse = ListingOwnershipResponse()
    ): RestResponse<ListingOwnershipResponse> =
        RestResponse.ResponseBuilder.create(status, entity).build()
}
