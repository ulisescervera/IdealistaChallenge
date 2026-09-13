package com.ulisescervera.uci.data.repository

import com.ulisescervera.uci.data.local.dao.PropertyDao
import com.ulisescervera.uci.data.local.dao.PropertyDetailDao
import com.ulisescervera.uci.data.mapper.PropertyDetailMapper
import com.ulisescervera.uci.data.mapper.PropertyDtoMapper
import com.ulisescervera.uci.data.mapper.PropertyEntityMapper
import com.ulisescervera.uci.data.network.ErrorMapper
import com.ulisescervera.uci.data.network.UciPropertyApi
import com.ulisescervera.uci.domain.common.DispatcherProvider
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.common.asFailure
import com.ulisescervera.uci.domain.common.asSuccess
import com.ulisescervera.uci.domain.common.flatMap
import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first, single-source-of-truth repository.
 *
 * The shape is deliberate and worth stating once:
 *
 * ```
 * refresh*  : network -> mappers -> Room     (write only, returns an outcome)
 * observe*  : Room    -> mappers -> :domain  (read only, never touches network)
 * ```
 *
 * Nothing reads through the network. That is what makes the app behave sanely
 * offline, what makes a favourite toggled on the detail screen appear in the
 * list without any event bus, and what makes the ViewModels testable with a
 * fake DAO and no HTTP at all.
 *
 * `flowOn(io)` is applied at the repository edge rather than inside Room:
 * Room already dispatches its queries off the main thread, but the *mapping*
 * would otherwise run on the collector's dispatcher, which is the main thread.
 *
 * The user's flags are part of this contract but not of this class: they are
 * delegated verbatim to [PropertyFlagStore]. One boundary for the caller, two
 * collaborators behind it -- which is what keeps this constructor from growing
 * a clock and a fourth DAO it would otherwise only pass through.
 */
@Singleton
class PropertyRepositoryImpl @Inject constructor(
    private val api: UciPropertyApi,
    private val propertyDao: PropertyDao,
    private val detailDao: PropertyDetailDao,
    private val flagStore: PropertyFlagStore,
    private val dtoMapper: PropertyDtoMapper,
    private val entityMapper: PropertyEntityMapper,
    private val detailMapper: PropertyDetailMapper,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) : PropertyRepository {

    override fun observeVisibleProperties(): Flow<List<Property>> =
        propertyDao.observeVisible()
            .map(entityMapper::toDomainList)
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    override fun observeFavouriteProperties(): Flow<List<Property>> =
        propertyDao.observeFavourites()
            .map(entityMapper::toDomainList)
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    override fun observeDiscardedProperties(): Flow<List<DiscardedProperty>> =
        propertyDao.observeDiscarded()
            .map(entityMapper::toDiscardedList)
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    override fun observeProperty(propertyId: String): Flow<Property?> =
        propertyDao.observeOne(propertyId)
            .map { row -> row?.let(entityMapper::toDomain) }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    /**
     * Emits `null` until the detail endpoint has answered once for this id.
     *
     * The summary is combined in rather than embedded in the detail row, so a
     * favourite toggle -- which only writes to `property_flags` -- re-emits the
     * detail with the new flag without re-fetching anything.
     */
    override fun observePropertyDetail(propertyId: String): Flow<PropertyDetail?> =
        combine(
            detailDao.observe(propertyId),
            propertyDao.observeOne(propertyId),
        ) { detailEntity, summaryRow ->
            val summary = summaryRow?.let(entityMapper::toDomain)
            if (detailEntity == null || summary == null) null else detailMapper.toDomain(detailEntity, summary)
        }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    override suspend fun refreshProperties(): UciResult<Unit> = withContext(dispatchers.io) {
        errorMapper.runCatchingUci {
            val entities = dtoMapper.toEntities(api.properties())
            propertyDao.replaceFeed(entities)
        }
    }

    /**
     * A missing summary is [UciError.PropertyNotFound], not a generic failure:
     * the detail screen can only be reached for a property that is cached, so
     * this means the cache was wiped mid-navigation (or a deep link arrived for
     * an id the feed does not contain) and the UI should offer "back to list"
     * rather than "retry".
     */
    override suspend fun refreshPropertyDetail(propertyId: String): UciResult<PropertyDetail> =
        withContext(dispatchers.io) {
            errorMapper
                .runCatchingUci {
                    val entity = detailMapper.toEntity(propertyId, api.propertyDetail())
                    detailDao.upsert(entity)
                    entity
                }
                .flatMap { entity ->
                    errorMapper.runCatchingUci { propertyDao.findOne(propertyId) }
                        .flatMap { summaryRow ->
                            when (summaryRow) {
                                null -> UciError.PropertyNotFound(propertyId).asFailure()
                                else -> detailMapper
                                    .toDomain(entity, entityMapper.toDomain(summaryRow))
                                    .asSuccess()
                            }
                        }
                }
        }

    // -----------------------------------------------------------------------
    // Flags -- delegated wholesale. No wrapping here on purpose: the store
    // already applies `withContext(io)` and `runCatchingUci`, and doing it
    // twice would bury a real error inside a second, meaningless try/catch.
    // -----------------------------------------------------------------------

    override suspend fun flagOf(propertyId: String): PropertyFlag =
        flagStore.flagOf(propertyId)

    override suspend fun setFlag(propertyId: String, flag: PropertyFlag): UciResult<Unit> =
        flagStore.setFlag(propertyId, flag)

    override suspend fun restoreAllDiscarded(): UciResult<Int> =
        flagStore.restoreAllDiscarded()
}
