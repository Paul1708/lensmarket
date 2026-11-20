package lensmarket.listing.composition

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import lensmarket.listing.domain.port.ClockPort
import lensmarket.listing.domain.port.IdGeneratorPort
import lensmarket.listing.domain.port.ListingMetricsPort
import lensmarket.listing.domain.port.ListingRepositoryPort
import lensmarket.listing.domain.port.ListingSearchPort
import lensmarket.listing.domain.port.TokenVerifierPort
import lensmarket.listing.domain.usecase.AttachImageToListingUseCase
import lensmarket.listing.domain.usecase.AuthenticateUserUseCase
import lensmarket.listing.domain.usecase.ChangeListingStatusUseCase
import lensmarket.listing.domain.usecase.CreateListingUseCase
import lensmarket.listing.domain.usecase.DeleteListingUseCase
import lensmarket.listing.domain.usecase.GetOwnListingUseCase
import lensmarket.listing.domain.usecase.GetPublicListingUseCase
import lensmarket.listing.domain.usecase.PatchListingUseCase
import lensmarket.listing.domain.usecase.SearchOwnListingsUseCase
import lensmarket.listing.domain.usecase.SearchPublicListingsUseCase
import lensmarket.listing.domain.usecase.UpdateListingUseCase
import java.time.Instant
import java.util.UUID

@ApplicationScoped class SystemClockAdapter : ClockPort { override fun now(): Instant = Instant.now() }
@ApplicationScoped class UuidGeneratorAdapter : IdGeneratorPort { override fun newId(): String = UUID.randomUUID().toString() }

@ApplicationScoped
class ListingApplicationModule {
    @Produces @Singleton fun authenticate(tokens: TokenVerifierPort) = AuthenticateUserUseCase(tokens)
    @Produces @Singleton fun create(
        listings: ListingRepositoryPort, metrics: ListingMetricsPort,
        clock: ClockPort, ids: IdGeneratorPort
    ) = CreateListingUseCase(listings, metrics, clock, ids)
    @Produces @Singleton fun searchPublic(listings: ListingSearchPort) = SearchPublicListingsUseCase(listings)
    @Produces @Singleton fun searchOwn(listings: ListingSearchPort) = SearchOwnListingsUseCase(listings)
    @Produces @Singleton fun getPublic(listings: ListingRepositoryPort) = GetPublicListingUseCase(listings)
    @Produces @Singleton fun getOwn(listings: ListingRepositoryPort) = GetOwnListingUseCase(listings)
    @Produces @Singleton fun update(listings: ListingRepositoryPort, metrics: ListingMetricsPort) =
        UpdateListingUseCase(listings, metrics)
    @Produces @Singleton fun patch(listings: ListingRepositoryPort, metrics: ListingMetricsPort) =
        PatchListingUseCase(listings, metrics)
    @Produces @Singleton fun status(
        listings: ListingRepositoryPort, metrics: ListingMetricsPort, clock: ClockPort, ids: IdGeneratorPort
    ) = ChangeListingStatusUseCase(listings, metrics, clock, ids)
    @Produces @Singleton fun delete(
        listings: ListingRepositoryPort, metrics: ListingMetricsPort, clock: ClockPort, ids: IdGeneratorPort
    ) = DeleteListingUseCase(listings, metrics, clock, ids)
    @Produces @Singleton fun attach(listings: ListingRepositoryPort, metrics: ListingMetricsPort) =
        AttachImageToListingUseCase(listings, metrics)
}
