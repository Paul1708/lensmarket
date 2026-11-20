package lensmarket.image.data.client

import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import lensmarket.image.domain.port.ListingOwnershipPort
import lensmarket.image.domain.usecase.ImageDependencyTimeoutException
import lensmarket.image.domain.usecase.ImageDependencyUnavailableException
import org.eclipse.microprofile.faulttolerance.CircuitBreaker
import org.eclipse.microprofile.faulttolerance.Retry
import org.eclipse.microprofile.faulttolerance.Timeout
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.resteasy.reactive.RestResponse
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

data class ListingOwnershipResponse(val ownerUserId: String? = null)

@RegisterRestClient(configKey = "listing-service")
interface ListingServiceApi {
    @GET
    @Path("/{listingId}")
    fun getListing(@PathParam("listingId") listingId: String): Uni<RestResponse<ListingOwnershipResponse>>
}

fun interface ListingServiceCall {
    fun getListing(listingId: String): Uni<RestResponse<ListingOwnershipResponse>>
}

/** Retry und Circuit Breaker liegen auf einem separaten CDI-Bean, damit Interception wirksam ist. */
@ApplicationScoped
class FaultTolerantListingServiceCall(
    @param:RestClient private val api: ListingServiceApi
) : ListingServiceCall {
    @Retry(maxRetries = 2, delay = 100, jitter = 50, maxDuration = 5000)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    @Timeout(2500)
    override fun getListing(listingId: String): Uni<RestResponse<ListingOwnershipResponse>> =
        api.getListing(listingId).onItem().transform { response ->
            when {
                response.status == 504 -> throw ImageDependencyTimeoutException("Listing service timed out")
                response.status >= 500 -> throw ImageDependencyUnavailableException("Listing service is unavailable")
                else -> response
            }
        }
}

@ApplicationScoped
class ListingServiceClient(
    private val call: ListingServiceCall
) : ListingOwnershipPort {
    override suspend fun isListingOwner(listingId: String, userId: String): Boolean {
        val response = try {
            call.getListing(listingId).awaitSuspending()
        } catch (failure: Throwable) {
            throw failure.asDependencyFailure()
        }
        return when {
            response.status == 404 -> false
            response.status in 200..299 -> {
                val owner = response.entity?.ownerUserId
                    ?: throw ImageDependencyUnavailableException("Listing service returned an invalid contract")
                owner == userId
            }
            response.status == 504 -> throw ImageDependencyTimeoutException("Listing service timed out")
            else -> throw ImageDependencyUnavailableException("Listing service is unavailable")
        }
    }

    private fun Throwable.asDependencyFailure(): RuntimeException = when {
        this is ImageDependencyTimeoutException -> this
        this is ImageDependencyUnavailableException -> this
        generateSequence(this) { it.cause }.any {
            it is TimeoutException || it is SocketTimeoutException || it::class.simpleName == "TimeoutException"
        } -> ImageDependencyTimeoutException("Listing service timed out", this)
        else -> ImageDependencyUnavailableException("Listing service is unavailable", this)
    }
}
