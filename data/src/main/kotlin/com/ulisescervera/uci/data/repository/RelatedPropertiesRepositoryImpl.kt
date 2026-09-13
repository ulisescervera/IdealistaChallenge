package com.ulisescervera.uci.data.repository

import com.ulisescervera.uci.data.local.dao.PropertyDao
import com.ulisescervera.uci.data.mapper.PropertyEntityMapper
import com.ulisescervera.uci.data.network.ErrorMapper
import com.ulisescervera.uci.data.related.RelatedPropertiesService
import com.ulisescervera.uci.domain.common.DispatcherProvider
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.repository.RelatedPropertiesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Asks the (simulated) recommender for ids and hydrates them from the cache.
 *
 * Hydrating locally rather than trusting a payload means related items arrive
 * with their favourite/discarded state already joined in, so the related block
 * at the bottom of the detail screen cannot disagree with the list above it.
 *
 * The service returns ids in relevance order; SQLite would return them in
 * primary-key order, so the result is re-sorted to match the ranking.
 */
@Singleton
class RelatedPropertiesRepositoryImpl @Inject constructor(
    private val service: RelatedPropertiesService,
    private val propertyDao: PropertyDao,
    private val entityMapper: PropertyEntityMapper,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) : RelatedPropertiesRepository {

    override suspend fun relatedTo(propertyId: String, limit: Int): UciResult<List<Property>> =
        withContext(dispatchers.io) {
            errorMapper.runCatchingUci {
                val rankedIds = service.relatedPropertyIds(propertyId, limit)
                val byId = rankedIds
                    .mapNotNull { id -> propertyDao.findOne(id) }
                    .associateBy { it.property.propertyCode }
                rankedIds.mapNotNull { id -> byId[id]?.let(entityMapper::toDomain) }
            }
        }
}
