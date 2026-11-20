package lensmarket.listing.domain.usecase

import kotlinx.coroutines.test.runTest
import lensmarket.listing.domain.model.ChangeListingStatusCommand
import lensmarket.listing.domain.model.CreateListingCommand
import lensmarket.listing.domain.model.Listing
import lensmarket.listing.domain.model.ListingSearchFilter
import lensmarket.listing.domain.model.PatchListingCommand
import lensmarket.listing.domain.model.UpdateListingCommand
import lensmarket.listing.domain.port.ClockPort
import lensmarket.listing.domain.port.EventApplyResult
import lensmarket.listing.domain.port.IdGeneratorPort
import lensmarket.listing.domain.port.ListingMetricsPort
import lensmarket.listing.domain.port.ListingRepositoryPort
import lensmarket.listing.domain.port.ListingSearchPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ListingUseCaseTest {
    @Test
    fun `create normalizes persists publishes and measures`() = runTest {
        val f = Fixture()
        val listing = f.create.create("owner", CreateListingCommand(title = " Titel ", description = " Text ", priceHint = BigDecimal.TEN))
        assertEquals("listing-1", listing.id)
        assertEquals("ACTIVE", listing.status)
        assertTrue(listing.imageIds.isEmpty())
        assertEquals(listOf(listing.id), f.repository.createdOutbox)
        assertEquals(1, f.metrics.created)
    }

    @Test
    fun `create validates required fields and nonnegative prices`() = runTest {
        val f = Fixture()
        expectFailure<ListingUseCaseValidationException> {
            f.create.create("owner", CreateListingCommand(title = " ", description = "Text"))
        }
        expectFailure<ListingUseCaseValidationException> {
            f.create.create(
                "owner",
                CreateListingCommand(title = "Titel", description = "Text", priceHint = BigDecimal("-0.01"))
            )
        }
    }

    @Test
    fun `update and patch enforce ownership and measure changes`() = runTest {
        val f = Fixture()
        val listing = f.create.create("owner", CreateListingCommand(title = "Titel", description = "Text"))
        val updated = f.update.update(listing.id, "owner", UpdateListingCommand("Neu", "Beschreibung", null))
        assertEquals("Neu", updated.title)
        val patched = f.patch.patch(listing.id, "owner", PatchListingCommand(tags = listOf(" eins ", "")))
        assertEquals(listOf("eins"), patched.tags)
        assertEquals(2, f.metrics.updated)
        expectFailure<ListingUseCaseForbiddenException> {
            f.update.update(listing.id, "other", UpdateListingCommand("X", "Y", null))
        }
    }

    @Test
    fun `status publishes only real transitions`() = runTest {
        val f = Fixture()
        val listing = f.create.create("owner", CreateListingCommand(title = "Titel", description = "Text"))
        val changed = f.status.changeStatus(listing.id, "owner", ChangeListingStatusCommand("paused"))
        assertTrue(changed.statusChanged)
        val unchanged = f.status.changeStatus(listing.id, "owner", ChangeListingStatusCommand("PAUSED"))
        assertFalse(unchanged.statusChanged)
        assertEquals(listOf("ACTIVE->PAUSED"), f.repository.statusOutbox)
    }

    @Test
    fun `status rejects invalid values and foreign owners`() = runTest {
        val f = Fixture()
        val listing = f.create.create("owner", CreateListingCommand(title = "Titel", description = "Text"))
        expectFailure<ListingUseCaseValidationException> {
            f.status.changeStatus(listing.id, "owner", ChangeListingStatusCommand("INVALID"))
        }
        expectFailure<ListingUseCaseForbiddenException> {
            f.status.changeStatus(listing.id, "other", ChangeListingStatusCommand("PAUSED"))
        }
    }

    @Test
    fun `sold is terminal while active and paused can transition`() = runTest {
        val f = Fixture()
        val listing = f.create.create("owner", CreateListingCommand(title = "Titel", description = "Text"))
        f.status.changeStatus(listing.id, "owner", ChangeListingStatusCommand("PAUSED"))
        f.status.changeStatus(listing.id, "owner", ChangeListingStatusCommand("ACTIVE"))
        f.status.changeStatus(listing.id, "owner", ChangeListingStatusCommand("SOLD"))

        expectFailure<ListingUseCaseValidationException> {
            f.status.changeStatus(listing.id, "owner", ChangeListingStatusCommand("ACTIVE"))
        }
        val idempotent = f.status.changeStatus(listing.id, "owner", ChangeListingStatusCommand("SOLD"))
        assertFalse(idempotent.statusChanged)
    }

    @Test
    fun `delete soft deletes and publishes`() = runTest {
        val f = Fixture()
        val listing = f.create.create("owner", CreateListingCommand(title = "Titel", description = "Text"))
        f.delete.delete(listing.id, "owner")
        assertEquals(null, f.repository.findListingById(listing.id))
        assertEquals(listOf(listing.id), f.repository.deletedOutbox)
        assertEquals(1, f.metrics.deleted)
    }

    @Test
    fun `public and owner searches enforce separate visibility rules`() = runTest {
        val f = Fixture()
        f.create.create("owner", CreateListingCommand(title = "Titel", description = "Text"))
        f.repository.save(sample("paused", "owner", status = "PAUSED"))
        val public = f.publicSearch.search(ListingSearchFilter(status = "PAUSED"))
        assertEquals(listOf("listing-1"), public.map { it.id })
        val owner = f.ownSearch.search("owner", ListingSearchFilter(ownerUserId = "untrusted"))
        assertEquals(2, owner.size)
        expectFailure<ListingUseCaseValidationException> {
            f.publicSearch.search(ListingSearchFilter(limit = 0))
        }
    }

    @Test
    fun `public detail hides non-active listings while owner detail exposes only own`() = runTest {
        val f = Fixture()
        f.repository.save(sample("paused", "owner", status = "PAUSED"))
        expectFailure<ListingUseCaseNotFoundException> { f.publicGet.get("paused") }
        assertEquals("paused", f.ownGet.get("paused", "owner").id)
        expectFailure<ListingUseCaseForbiddenException> { f.ownGet.get("paused", "other") }
    }

    @Test
    fun `search normalizes all filters and validates ranges`() = runTest {
        val f = Fixture()
        f.ownSearch.search(
            "owner",
            ListingSearchFilter(
                query = " kamera ", ownerUserId = " untrusted ", status = " paused ", tag = " portrait ",
                location = " berlin ", minPrice = BigDecimal.ONE, maxPrice = BigDecimal.TEN, limit = 25, offset = 2
            )
        )
        assertEquals(
            ListingSearchFilter(
                query = "kamera", ownerUserId = "owner", status = "PAUSED", tag = "portrait",
                location = "berlin", minPrice = BigDecimal.ONE, maxPrice = BigDecimal.TEN, limit = 25, offset = 2
            ),
            f.repository.lastFilter
        )
        expectFailure<ListingUseCaseValidationException> {
            f.ownSearch.search("owner", ListingSearchFilter(minPrice = BigDecimal.TEN, maxPrice = BigDecimal.ONE))
        }
        expectFailure<ListingUseCaseValidationException> {
            f.ownSearch.search("owner", ListingSearchFilter(status = "INVALID"))
        }
    }

    @Test
    fun `image attachment is idempotent and records consumer result`() = runTest {
        val f = Fixture()
        val listing = f.create.create("owner", CreateListingCommand(title = "Titel", description = "Text"))
        val occurredAt = Instant.parse("2026-07-13T10:00:00Z")
        assertEquals(AttachImageResult.ATTACHED, f.attach.attach("event-1", occurredAt, listing.id, "image-1"))
        assertEquals(AttachImageResult.DUPLICATE, f.attach.attach("event-1", occurredAt, listing.id, "image-1"))
        assertEquals(listOf("image-1"), f.repository.findListingById(listing.id)?.imageIds)
        assertEquals(
            AttachImageResult.LISTING_NOT_FOUND,
            f.attach.attach("event-2", occurredAt, "missing", "image-2")
        )
    }

    private class Fixture {
        val repository = InMemoryListings()
        val metrics = RecordingMetrics()
        val clock = ClockPort { Instant.parse("2026-07-13T10:00:00Z") }
        val ids = IdGeneratorPort { "listing-${repository.all.size + 1}" }
        val create = CreateListingUseCase(repository, metrics, clock, ids)
        val update = UpdateListingUseCase(repository, metrics)
        val patch = PatchListingUseCase(repository, metrics)
        val status = ChangeListingStatusUseCase(repository, metrics, clock, ids)
        val delete = DeleteListingUseCase(repository, metrics, clock, ids)
        val publicSearch = SearchPublicListingsUseCase(repository)
        val ownSearch = SearchOwnListingsUseCase(repository)
        val publicGet = GetPublicListingUseCase(repository)
        val ownGet = GetOwnListingUseCase(repository)
        val attach = AttachImageToListingUseCase(repository, metrics)
    }

    private class RecordingMetrics : ListingMetricsPort {
        var created = 0; var updated = 0; var deleted = 0
        val consumed = mutableListOf<String>()
        override fun listingCreated() { created++ }
        override fun listingUpdated() { updated++ }
        override fun listingDeleted() { deleted++ }
        override fun listingStatusChanged(oldStatus: String, newStatus: String) = Unit
        override fun eventConsumed(eventType: String, result: String) { consumed += "$eventType:$result" }
    }

    private class InMemoryListings : ListingRepositoryPort, ListingSearchPort {
        val all = linkedMapOf<String, Listing>()
        val createdOutbox = mutableListOf<String>()
        val statusOutbox = mutableListOf<String>()
        val deletedOutbox = mutableListOf<String>()
        var lastFilter: ListingSearchFilter? = null
        private val deleted = mutableSetOf<String>()
        private val processedEvents = mutableSetOf<String>()
        override suspend fun save(listing: Listing): Listing = listing.also { all[it.id] = it }
        override suspend fun saveWithEvent(listing: Listing, eventId: String): Listing =
            save(listing).also { createdOutbox += listing.id }
        override suspend fun update(listing: Listing): Listing? = listing.takeIf { it.id in all && it.id !in deleted }?.also { all[it.id] = it }
        override suspend fun updateStatus(listingId: String, status: String): Listing? =
            all[listingId]?.takeIf { listingId !in deleted }?.copy(status = status)?.also { all[listingId] = it }
        override suspend fun updateStatusWithEvent(
            listingId: String, status: String, oldStatus: String, eventId: String, occurredAt: Instant
        ): Listing? = updateStatus(listingId, status)?.also { statusOutbox += "$oldStatus->$status" }
        override suspend fun markDeleted(listingId: String, deletedAt: Instant): Listing? =
            all[listingId]?.takeIf { deleted.add(listingId) }?.copy(deletedAt = deletedAt)?.also { all[listingId] = it }
        override suspend fun markDeletedWithEvent(listingId: String, deletedAt: Instant, eventId: String): Listing? =
            markDeleted(listingId, deletedAt)?.also { deletedOutbox += listingId }
        override suspend fun findListingById(id: String): Listing? = all[id]?.takeIf { id !in deleted }
        override suspend fun appendImageId(listingId: String, imageId: String): Listing? =
            findListingById(listingId)?.let { current ->
                current.copy(imageIds = (current.imageIds + imageId).distinct()).also { all[listingId] = it }
            }
        override suspend fun appendImageIdOnce(
            eventId: String, processedAt: Instant, listingId: String, imageId: String
        ): EventApplyResult {
            if (!processedEvents.add(eventId)) return EventApplyResult.DUPLICATE
            return if (appendImageId(listingId, imageId) == null) {
                EventApplyResult.AGGREGATE_NOT_FOUND
            } else {
                EventApplyResult.APPLIED
            }
        }
        override suspend fun search(filter: ListingSearchFilter): List<Listing> {
            lastFilter = filter
            return all.values.filter {
                it.id !in deleted && (filter.ownerUserId == null || it.ownerUserId == filter.ownerUserId) &&
                    (filter.status == null || it.status == filter.status)
            }.drop(filter.offset).take(filter.limit)
        }
    }

    private fun sample(id: String, owner: String, status: String = "ACTIVE") = Listing(
        id, owner, "Titel", "Text", null, null, null, null, emptyList(), emptyList(), status,
        Instant.parse("2026-07-13T10:00:00Z")
    )

    private suspend inline fun <reified T : Throwable> expectFailure(noinline block: suspend () -> Unit) {
        try { block(); fail("Expected ${T::class.simpleName}") } catch (failure: Throwable) {
            assertTrue(failure is T, "Expected ${T::class.simpleName}, got ${failure::class.simpleName}")
        }
    }
}
