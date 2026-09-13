package com.ulisescervera.uci.domain.fake

import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.repository.RelatedPropertiesRepository

/**
 * Returns whatever it is given, *including* the requested property.
 *
 * That is the point: `GetRelatedPropertiesUseCase` promises never to return the
 * property itself, and the only way to prove the use case enforces it -- rather
 * than relying on a well-behaved data source -- is to hand it a misbehaving one.
 */
class FakeRelatedPropertiesRepository(
    private var response: UciResult<List<Property>> = UciResult.Success(emptyList()),
) : RelatedPropertiesRepository {

    var lastRequestedId: String? = null
        private set
    var lastRequestedLimit: Int? = null
        private set

    fun returns(items: List<Property>) {
        response = UciResult.Success(items)
    }

    fun fails(error: UciError) {
        response = UciResult.Failure(error)
    }

    override suspend fun relatedTo(propertyId: String, limit: Int): UciResult<List<Property>> {
        lastRequestedId = propertyId
        lastRequestedLimit = limit
        return response
    }
}
