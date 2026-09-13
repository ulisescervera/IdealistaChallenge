package com.ulisescervera.uci.domain.repository

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.Property

/**
 * "More like this" at the bottom of the detail screen.
 *
 * The challenge has no endpoint for it, so `:data` fakes one (latency included)
 * while honouring the contract the brief describes: given a property id, return
 * *other* properties, never the one asked about.
 */
interface RelatedPropertiesRepository {

    suspend fun relatedTo(propertyId: String, limit: Int = DEFAULT_LIMIT): UciResult<List<Property>>

    companion object {
        const val DEFAULT_LIMIT: Int = 10
    }
}
