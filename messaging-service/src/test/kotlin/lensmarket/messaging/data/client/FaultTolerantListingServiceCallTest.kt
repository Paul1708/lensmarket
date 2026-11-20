package lensmarket.messaging.data.client

import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.runBlocking
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.resteasy.reactive.RestResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@QuarkusTest
class FaultTolerantListingServiceCallTest {
    @InjectMock
    @field:RestClient
    lateinit var api: ListingServiceApi

    @Inject
    lateinit var call: FaultTolerantListingServiceCall

    @Test
    fun `safe listing GET retries two temporary failures`() = runBlocking {
        val failure = Uni.createFrom().failure<RestResponse<ListingLookupResponse>>(
            MessagingDependencyUnavailableForTest()
        )
        val success = Uni.createFrom().item(
            RestResponse.ResponseBuilder.create(
                Response.Status.OK,
                ListingLookupResponse("listing-1", "owner-1", "ACTIVE")
            ).build()
        )
        `when`(api.getListing("listing-1")).thenReturn(failure, failure, success)

        val response = call.getListing("listing-1").awaitSuspending()

        assertEquals(200, response.status)
        verify(api, times(3)).getListing("listing-1")
        Unit
    }

    private class MessagingDependencyUnavailableForTest : RuntimeException("temporary listing outage")
}
