package lensmarket.messaging.data.client

import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import lensmarket.messaging.domain.model.ListingReference
import lensmarket.messaging.domain.port.ListingLookupPort
import lensmarket.messaging.domain.usecase.MessagingDependencyTimeoutException
import lensmarket.messaging.domain.usecase.MessagingDependencyUnavailableException
import org.eclipse.microprofile.faulttolerance.CircuitBreaker
import org.eclipse.microprofile.faulttolerance.Retry
import org.eclipse.microprofile.faulttolerance.Timeout
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.resteasy.reactive.RestResponse
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

data class ListingLookupResponse(
    val id: String? = null,
    val ownerUserId: String? = null,
    val status: String? = null
)

@RegisterRestClient(configKey = "listing-service")
interface ListingServiceApi {
    @GET
    @Path("/{listingId}")
    fun getListing(@PathParam("listingId") listingId: String): Uni<RestResponse<ListingLookupResponse>>
}

fun interface ListingServiceCall {
    fun getListing(listingId: String): Uni<RestResponse<ListingLookupResponse>>
}

@ApplicationScoped
class FaultTolerantListingServiceCall(
    @param:RestClient private val api: ListingServiceApi
) : ListingServiceCall {
    @Retry(maxRetries = 2, delay = 100, jitter = 50, maxDuration = 5000)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    @Timeout(2500)
    override fun getListing(listingId: String): Uni<RestResponse<ListingLookupResponse>> =
        api.getListing(listingId).onItem().transform { response ->
            when {
                response.status == 504 -> throw MessagingDependencyTimeoutException("Listing service timed out")
                response.status >= 500 -> throw MessagingDependencyUnavailableException("Listing service is unavailable")
                else -> response
            }
        }
}

@ApplicationScoped
class ListingServiceClient(
    private val call: ListingServiceCall
) : ListingLookupPort {
    override suspend fun findListing(listingId: String): ListingReference? {
        val response = try {
            call.getListing(listingId).awaitSuspending()
        } catch (failure: Throwable) {
            throw failure.asDependencyFailure()
        }
        return when {
            response.status == 404 -> null
            response.status in 200..299 -> {
                val listing = response.entity
                    ?: throw MessagingDependencyUnavailableException("Listing service returned an invalid contract")
                ListingReference(
                    id = listing.id ?: listingId,
                    ownerUserId = listing.ownerUserId
                        ?: throw MessagingDependencyUnavailableException("Listing service returned an invalid contract"),
                    status = listing.status
                        ?: throw MessagingDependencyUnavailableException("Listing service returned an invalid contract")
                )
            }
            response.status == 504 -> throw MessagingDependencyTimeoutException("Listing service timed out")
            else -> throw MessagingDependencyUnavailableException("Listing service is unavailable")
        }
    }

    private fun Throwable.asDependencyFailure(): RuntimeException = when {
        this is MessagingDependencyTimeoutException -> this
        this is MessagingDependencyUnavailableException -> this
        generateSequence(this) { it.cause }.any {
            it is TimeoutException || it is SocketTimeoutException || it::class.simpleName == "TimeoutException"
        } -> MessagingDependencyTimeoutException("Listing service timed out", this)
        else -> MessagingDependencyUnavailableException("Listing service is unavailable", this)
    }
}
