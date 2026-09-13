package com.ulisescervera.uci.domain.fake

import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.repository.PropertyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [PropertyRepository].
 *
 * A hand-written fake rather than a MockK stub: the flag use cases assert on
 * *behaviour over time* (toggle twice, end up where you started), and a mock
 * that returns a canned value for `flagOf` cannot express that. The fake also
 * enforces the real invariant -- one flag per property, `NONE` stored as the
 * absence of a row -- so a use case that tried to break it would fail here
 * exactly as it would in production.
 *
 * The read side is driven by [emitVisible] / [emitFavourites] / [emitDiscarded]
 * rather than derived from [flags], because the tests that care about reads and
 * the tests that care about writes are different tests. Wiring them together
 * would make every flag assertion depend on a query this fake does not have.
 */
class FakePropertyRepository(
    initialFlags: Map<String, PropertyFlag> = emptyMap(),
) : PropertyRepository {

    private val visible = MutableStateFlow<List<Property>>(emptyList())
    private val favourites = MutableStateFlow<List<Property>>(emptyList())
    private val discarded = MutableStateFlow<List<DiscardedProperty>>(emptyList())
    private val details = MutableStateFlow<Map<String, PropertyDetail>>(emptyMap())
    private val flags = MutableStateFlow(initialFlags)

    /** Set to make the next write fail, to exercise the error path. */
    var failWith: UciError? = null

    /** Every `setFlag` call, in order, for assertions about what was written. */
    val writes = mutableListOf<Pair<String, PropertyFlag>>()

    var refreshPropertiesResult: UciResult<Unit> = UciResult.Success(Unit)

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

    fun currentFlags(): Map<String, PropertyFlag> = flags.value

    // ------------------------------------------------------------------ reads

    override fun observeVisibleProperties(): Flow<List<Property>> = visible

    override fun observeFavouriteProperties(): Flow<List<Property>> = favourites

    override fun observeDiscardedProperties(): Flow<List<DiscardedProperty>> = discarded

    override fun observeProperty(propertyId: String): Flow<Property?> =
        visible.map { list -> list.firstOrNull { it.id == propertyId } }

    override fun observePropertyDetail(propertyId: String): Flow<PropertyDetail?> =
        details.map { it[propertyId] }

    override suspend fun flagOf(propertyId: String): PropertyFlag =
        flags.value[propertyId] ?: PropertyFlag.NONE

    // ----------------------------------------------------------------- writes

    override suspend fun refreshProperties(): UciResult<Unit> = refreshPropertiesResult

    override suspend fun refreshPropertyDetail(propertyId: String): UciResult<PropertyDetail> =
        details.value[propertyId]
            ?.let { UciResult.Success(it) }
            ?: UciResult.Failure(UciError.PropertyNotFound(propertyId))

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
        val discardedFlags = flags.value.filterValues { it == PropertyFlag.DISCARDED }
        flags.value = flags.value - discardedFlags.keys
        return UciResult.Success(discardedFlags.size)
    }
}
