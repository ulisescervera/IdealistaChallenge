package com.ulisescervera.uci.data.related

import com.ulisescervera.uci.data.local.dao.PropertyDao
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Simulated recommendation endpoint.
 *
 * It ranks the cached catalogue by naive similarity to the requested property
 * (same operation, then closest price, then closest size) and pays a fixed
 * latency, so the detail screen really does show its skeleton row while the
 * related block "loads".
 *
 * The latency is a constructor parameter with a separate `@Inject` constructor
 * that supplies the production value. Hilt ignores Kotlin default arguments, so
 * a single constructor with a default would have failed to compile as a binding
 * -- the two-constructor shape is what keeps the test seam free.
 *
 * The [PropertyDao] query already excludes [propertyId]; `GetRelatedPropertiesUseCase`
 * filters once more downstream, because a future real endpoint is outside our
 * control.
 */
@Singleton
class FakeRelatedPropertiesService(
    private val propertyDao: PropertyDao,
    private val simulatedLatencyMillis: Long,
) : RelatedPropertiesService {

    @Inject
    constructor(propertyDao: PropertyDao) : this(propertyDao, DEFAULT_LATENCY_MILLIS)

    override suspend fun relatedPropertyIds(propertyId: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        delay(simulatedLatencyMillis)
        val reference = propertyDao.findOne(propertyId)?.property
        // Over-fetch so the ranking has something to sort; the caller still gets
        // at most `limit` items.
        val candidates = propertyDao
            .findRelatedTo(propertyId, limit = limit * OVER_FETCH_FACTOR)
            .map { it.property }
        return candidates
            .sortedWith(similarityTo(reference))
            .take(limit)
            .map(PropertyEntity::propertyCode)
    }

    /**
     * Same operation first (a rental is not a substitute for a purchase), then
     * closest price, then closest size. With no reference property the feed
     * order is kept, which is the backend's own relevance ranking.
     */
    private fun similarityTo(reference: PropertyEntity?): Comparator<PropertyEntity> {
        if (reference == null) return compareBy(PropertyEntity::orderInFeed)
        return compareBy(
            { if (it.operation == reference.operation) 0 else 1 },
            { abs(it.priceAmount - reference.priceAmount) },
            { abs(it.sizeSquareMeters - reference.sizeSquareMeters) },
            PropertyEntity::orderInFeed,
        )
    }

    companion object {
        /** Long enough to see the skeleton, short enough not to annoy. */
        const val DEFAULT_LATENCY_MILLIS = 650L

        /** For tests: no simulated network at all. */
        const val NO_LATENCY_MILLIS = 0L

        private const val OVER_FETCH_FACTOR = 3
    }
}
