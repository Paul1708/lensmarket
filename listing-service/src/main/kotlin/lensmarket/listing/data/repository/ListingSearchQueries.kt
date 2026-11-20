package lensmarket.listing.data.repository

import lensmarket.listing.domain.model.ListingSearchFilter

/**
 * Repräsentiert eine dynamisch aufgebaute Listing-Query.
 *
 * @param text Query-Text ab der WHERE-Bedingung inkl. Sortierung (ohne `from`-Teil).
 * @param parameters Parameter in Bindungsreihenfolge (positional, 1-basiert).
 */
internal data class ListingQuery(
    val text: String,
    val parameters: List<Any>
)

/**
 * Baut die dynamische Suchquery für Anzeigen.
 *
 * @param filter Validierter Suchfilter.
 * @return Query-Text und Parameterliste.
 */
internal fun buildListingSearchQuery(filter: ListingSearchFilter): ListingQuery {
    val clauses = mutableListOf("deletedAt is null")
    val parameters = mutableListOf<Any>()

    fun addClause(condition: (Int) -> String, value: Any) {
        parameters += value
        clauses += condition(parameters.size)
    }

    filter.query?.let { value ->
        addClause(
            { index -> "(lower(title) like ?$index or lower(description) like ?$index or lower(tags) like ?$index)" },
            "%${value.lowercase()}%"
        )
    }
    filter.ownerUserId?.let { value -> addClause({ index -> "ownerUserId = ?$index" }, value) }
    filter.status?.let { value -> addClause({ index -> "status = ?$index" }, value) }
    filter.tag?.let { value -> addClause({ index -> "lower(tags) like ?$index" }, "%${value.lowercase()}%") }
    filter.location?.let { value -> addClause({ index -> "lower(location) like ?$index" }, "%${value.lowercase()}%") }
    filter.minPrice?.let { value -> addClause({ index -> "priceHint >= ?$index" }, value) }
    filter.maxPrice?.let { value -> addClause({ index -> "priceHint <= ?$index" }, value) }

    val where = clauses.joinToString(" and ")
    return ListingQuery("$where order by createdAt desc", parameters)
}
