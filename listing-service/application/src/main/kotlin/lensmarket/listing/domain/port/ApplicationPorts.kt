package lensmarket.listing.domain.port

import lensmarket.listing.domain.model.Listing
import java.time.Instant

data class AuthenticatedUser(val id: String, val email: String, val role: String)

interface TokenVerifierPort {
    fun verify(token: String): AuthenticatedUser?
}

interface ListingMetricsPort {
    fun listingCreated()
    fun listingUpdated()
    fun listingDeleted()
    fun listingStatusChanged(oldStatus: String, newStatus: String)
    fun eventConsumed(eventType: String, result: String)
}

fun interface ClockPort { fun now(): Instant }
fun interface IdGeneratorPort { fun newId(): String }
