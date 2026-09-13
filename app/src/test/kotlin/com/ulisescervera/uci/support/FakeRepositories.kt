package com.ulisescervera.uci.support

import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.repository.PropertyRepository
import com.ulisescervera.uci.domain.repository.RelatedPropertiesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory repositories for the ViewModel tests.
 *
 * ### Why fakes and not mocks
 * The ViewModels are tested through *real* use cases sitting on top of these
 * fakes, which means the tests cover the intent -> use case -> repository path
 * the app actually takes. A MockK stub of a use case would let a test pass
 * while the ViewModel called the wrong one.
 *
 * The fakes also reproduce the behaviour that matters most: writes are visible
 * through the observable flows, exactly as Room's are. That is what makes
 * "toggling a favourite makes the list re-emit" assertable without a database.
 */
class FakePropertyRepository(
    initialFlags: Map<String, PropertyFlag> = emptyMap(),
) : PropertyRepository {

    private val visible = MutableStateFlow<List<Property>>(emptyList())
    private val favourites = MutableStateFlow<List<Property>>(emptyList())
    private val discarded = MutableStateFlow<List<DiscardedProperty>>(emptyList())
    private val details = MutableStateFlow<Map<String, PropertyDetail>>(emptyMap())
    private val flags = MutableStateFlow(initialFlags)

    var refreshListResult: UciResult<Unit> = UciResult.Success(Unit)
    var refreshDetailResult: (String) -> UciResult<PropertyDetail> = { id ->
        details.value[id]
            ?.let { UciResult.Success(it) }
            ?: UciResult.Failure(UciError.PropertyNotFound(id))
    }

    var refreshListCallCount = 0
        private set
    var refreshDetailCallCount = 0
        private set

    /** Set to make the next flag write fail, to exercise the error path. */
    var failWith: UciError? = null

    /** Every `setFlag` call, in order. */
    val writes = mutableListOf<Pair<String, PropertyFlag>>()

    /** Emitted by [refreshProperties] on success -- what the network "returned". */
    var onRefreshEmit: List<Property>? = null

    fun emitVisible(properties: List<Property>) {
        visible.value = properties
    }

    fun emitFavourites(properties: List<Property>) {
        favourites.value = properties
    }

    fun emitDiscarded(items: List<DiscardedProperty>) {
        discarded.value = items
    }

    fun emitDetail(detail: PropertyDetail) {
        details.value = details.value + (detail.id to detail)
    }

    override fun observeVisibleProperties(): Flow<List<Property>> = visible

    override fun observeFavouriteProperties(): Flow<List<Property>> = favourites

    override fun observeDiscardedProperties(): Flow<List<DiscardedProperty>> = discarded

    override fun observeProperty(propertyId: String): Flow<Property?> =
        visible.map { list -> list.firstOrNull { it.id == propertyId } }

    override fun observePropertyDetail(propertyId: String): Flow<PropertyDetail?> =
        details.map { it[propertyId] }

    override suspend fun refreshProperties(): UciResult<Unit> {
        refreshListCallCount++
        if (refreshListResult is UciResult.Success) onRefreshEmit?.let(::emitVisible)
        return refreshListResult
    }

    override suspend fun refreshPropertyDetail(propertyId: String): UciResult<PropertyDetail> {
        refreshDetailCallCount++
        return refreshDetailResult(propertyId)
    }

    // ------------------------------------------------------------------ flags

    override suspend fun flagOf(propertyId: String): PropertyFlag =
        flags.value[propertyId] ?: PropertyFlag.NONE

    override suspend fun setFlag(propertyId: String, flag: PropertyFlag): UciResult<Unit> {
        failWith?.let { return UciResult.Failure(it) }
        writes += propertyId to flag
        flags.value = flags.value.toMutableMap().apply {
            if (flag == PropertyFlag.NONE) remove(propertyId) else put(propertyId, flag)
        }
        return UciResult.Success(Unit)
    }

    override suspend fun restoreAllDiscarded(): UciResult<Int> {
        failWith?.let { return UciResult.Failure(it) }
        val toRestore = flags.value.filterValues { it == PropertyFlag.DISCARDED }
        flags.value = flags.value - toRestore.keys
        return UciResult.Success(toRestore.size)
    }

    fun currentFlags(): Map<String, PropertyFlag> = flags.value
}

class FakeRelatedRepository : RelatedPropertiesRepository {

    var result: UciResult<List<Property>> = UciResult.Success(emptyList())
    var callCount = 0
        private set

    override suspend fun relatedTo(propertyId: String, limit: Int): UciResult<List<Property>> {
        callCount++
        return result
    }
}
