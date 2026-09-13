package com.ulisescervera.uci.data.repository

import com.ulisescervera.uci.data.local.dao.PropertyFlagDao
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity
import com.ulisescervera.uci.data.network.ErrorMapper
import com.ulisescervera.uci.domain.common.DispatcherProvider
import com.ulisescervera.uci.domain.common.UciClock
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.PropertyFlag
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * The only writer of `property_flags`.
 *
 * ### Why this is a class and not a repository
 * It used to implement a `PropertyFlagRepository` interface in `:domain`. That
 * interface is gone: splitting the contract meant "favourite" was read from one
 * repository and written to another, which is a seam in the wrong place.
 *
 * What survives is the *implementation* split, and it earns its keep: flags are
 * the only data in UCI the user actually created, they are written with a clock
 * and a time zone, and none of that has anything to do with HTTP or with the
 * property cache. Keeping it here stops [PropertyRepositoryImpl] from growing a
 * ten-argument constructor and keeps the flag rules in one readable file.
 *
 * ### Why it is public, given that it is an implementation detail
 * It was `internal` for one commit. That does not compile: [PropertyRepositoryImpl]
 * is public -- `:app` names it in `UciBindingsModule` -- and a public constructor
 * cannot take a parameter of a less visible type (`EXPOSED_PARAMETER_TYPE`).
 *
 * Of the three ways out, this is the cheapest. Making the constructor `internal`
 * relies on Dagger tolerating a mangled visibility it does not document;
 * making the whole impl `internal` would move the composition root into `:data`
 * and contradict ADR 0001's reasoning about where wiring decisions belong.
 *
 * And the guarantee being given up was mostly theatre: [PropertyFlagDao] is
 * public and `@Provides`-exposed in `DatabaseModule`, so anything in `:app`
 * already had a *shorter* path to this table than going through here. The rule
 * "only [PropertyRepositoryImpl] writes flags" is a convention enforced by
 * review, not by the compiler -- which is what it always was.
 *
 * Two things are load-bearing:
 *
 * 1. **Clearing deletes the row.** `PropertyFlag.NONE` is the absence of an
 *    opinion, so it is represented by the absence of a row. That keeps the
 *    `LEFT JOIN ... IS NULL` in `PropertyDao` honest and the table small.
 * 2. **The time zone is captured at write time**, not read time. The favourite
 *    date shown in the detail is the wall clock of the moment the user tapped,
 *    in the zone they were in -- see `FavouriteMark.atOriginalZone`.
 */
@Singleton
class PropertyFlagStore @Inject constructor(
    private val flagDao: PropertyFlagDao,
    private val clock: UciClock,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun setFlag(propertyId: String, flag: PropertyFlag): UciResult<Unit> =
        withContext(dispatchers.io) {
            errorMapper.runCatchingUci {
                if (flag == PropertyFlag.NONE) {
                    flagDao.delete(propertyId)
                } else {
                    flagDao.upsert(
                        PropertyFlagEntity(
                            propertyCode = propertyId,
                            flag = flag,
                            updatedAtEpochMillis = clock.now().toEpochMilli(),
                            timeZoneId = clock.zone().id,
                        ),
                    )
                }
            }
        }

    /** No row means "the user has no opinion", which is [PropertyFlag.NONE]. */
    suspend fun flagOf(propertyId: String): PropertyFlag = withContext(dispatchers.io) {
        flagDao.findRow(propertyId)?.flag ?: PropertyFlag.NONE
    }

    suspend fun restoreAllDiscarded(): UciResult<Int> = withContext(dispatchers.io) {
        errorMapper.runCatchingUci { flagDao.deleteAllDiscarded() }
    }
}
