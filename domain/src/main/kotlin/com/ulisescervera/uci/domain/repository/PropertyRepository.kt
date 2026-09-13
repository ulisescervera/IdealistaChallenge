package com.ulisescervera.uci.domain.repository

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.model.PropertyFlag
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app knows about properties: the cached catalogue, the network
 * refreshes, and the user's own favourite/discarded decisions.
 *
 * ### Why one interface and not two
 * This used to be split into `PropertyRepository` (reads) and
 * `PropertyFlagRepository` (flag writes), on the theory that backend-owned data
 * and user-owned data deserve separate contracts. The split did not survive
 * contact with the code: "favourite" ended up on **both** sides of it -- read
 * through [observeFavouriteProperties], written through [setFlag] -- and a
 * concept spread across two interfaces is a seam in the wrong place.
 *
 * The ownership distinction is real, but it belongs where it actually applies:
 * to the *tables* (`properties` is a replaceable cache, `property_flags` is
 * user data with no foreign key to it) and to the implementation, which keeps
 * a separate internal collaborator for flags. Cohesion at the boundary,
 * separation behind it. See `docs/adr/0002-un-repositorio-por-agregado.md`.
 *
 * ### The shape of the contract
 * ```
 * refresh* / setFlag / restoreAll  -> write, return an outcome
 * observe*                         -> read, never touch the network
 * ```
 * The `observe*` functions are the single source of truth: they emit from the
 * database, so a favourite toggled on the detail screen shows up in the list
 * and in the Compose favourites tab without any cross-screen messaging.
 */
interface PropertyRepository {

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    /** Everything the user has not discarded, in backend order. */
    fun observeVisibleProperties(): Flow<List<Property>>

    /** Only the favourites, most recently marked first. */
    fun observeFavouriteProperties(): Flow<List<Property>>

    /** Only the discarded ones, most recently discarded first. */
    fun observeDiscardedProperties(): Flow<List<DiscardedProperty>>

    /** One property from the cache. Emits `null` while it is not cached yet. */
    fun observeProperty(propertyId: String): Flow<Property?>

    /**
     * The cached detail joined with its summary. Emits `null` until the detail
     * endpoint has answered at least once, which is what lets the detail screen
     * paint its header from [observeProperty] in the meantime.
     */
    fun observePropertyDetail(propertyId: String): Flow<PropertyDetail?>

    /** Current flag, or [PropertyFlag.NONE] if the property has never been flagged. */
    suspend fun flagOf(propertyId: String): PropertyFlag

    // -----------------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------------

    /** Hits `list.json` and replaces the cache. Flags are never touched. */
    suspend fun refreshProperties(): UciResult<Unit>

    /** Hits `detail.json` for one property and caches the result. */
    suspend fun refreshPropertyDetail(propertyId: String): UciResult<PropertyDetail>

    /**
     * Sets the user's flag, overwriting whatever was there.
     *
     * Because [PropertyFlag] is a single column, marking a discarded property as
     * favourite un-discards it in the same transaction -- the exclusivity is
     * enforced by the schema, not by application code.
     *
     * When [flag] is [PropertyFlag.FAVOURITE] the current instant *and time
     * zone* are stored alongside it, so the detail screen can render the date in
     * the zone the user was in when they tapped.
     */
    suspend fun setFlag(propertyId: String, flag: PropertyFlag): UciResult<Unit>

    /** Clears every discarded flag at once. Returns how many rows were restored. */
    suspend fun restoreAllDiscarded(): UciResult<Int>
}
