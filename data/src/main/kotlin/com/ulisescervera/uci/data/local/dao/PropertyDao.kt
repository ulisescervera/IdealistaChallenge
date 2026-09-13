package com.ulisescervera.uci.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import com.ulisescervera.uci.data.local.pojo.DiscardedPropertyRow
import com.ulisescervera.uci.data.local.pojo.PropertyWithLocalState
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes of the property cache.
 *
 * The `'discarded'` / `'favourite'` literals below are the tokens produced by
 * `UciTypeConverters.flagToToken`. They are duplicated here because Room needs
 * compile-time constant SQL; `UciTypeConverters.Flags` documents the pairing.
 */
@Dao
interface PropertyDao {

    /**
     * Everything the user has not discarded, in backend order.
     *
     * `LEFT JOIN` plus the `IS NULL` branch matters: most properties have no row
     * in `property_flags` at all, and an `INNER JOIN` would silently return an
     * empty list on a fresh install.
     */
    @Transaction
    @Query(
        """
        SELECT properties.* FROM properties
        LEFT JOIN property_flags ON properties.property_code = property_flags.property_code
        WHERE property_flags.flag IS NULL OR property_flags.flag != 'discarded'
        ORDER BY properties.order_in_feed ASC
        """,
    )
    fun observeVisible(): Flow<List<PropertyWithLocalState>>

    /** Favourites, most recently marked first. */
    @Transaction
    @Query(
        """
        SELECT properties.* FROM properties
        INNER JOIN property_flags ON properties.property_code = property_flags.property_code
        WHERE property_flags.flag = 'favourite'
        ORDER BY property_flags.updated_at DESC
        """,
    )
    fun observeFavourites(): Flow<List<PropertyWithLocalState>>

    @Transaction
    @Query("SELECT * FROM properties WHERE property_code = :propertyCode LIMIT 1")
    fun observeOne(propertyCode: String): Flow<PropertyWithLocalState?>

    @Transaction
    @Query("SELECT * FROM properties WHERE property_code = :propertyCode LIMIT 1")
    suspend fun findOne(propertyCode: String): PropertyWithLocalState?

    /** Narrow projection for the undo sheet, most recent dismissal first. */
    @Query(
        """
        SELECT
            properties.property_code            AS property_code,
            properties.property_type            AS property_type,
            properties.neighborhood             AS neighborhood,
            properties.district                 AS district,
            properties.municipality             AS municipality,
            properties.province                 AS province,
            properties.price_amount             AS price_amount,
            properties.price_currency_suffix    AS price_currency_suffix,
            properties.thumbnail_url            AS thumbnail_url,
            property_flags.updated_at           AS discarded_at
        FROM properties
        INNER JOIN property_flags ON properties.property_code = property_flags.property_code
        WHERE property_flags.flag = 'discarded'
        ORDER BY property_flags.updated_at DESC
        """,
    )
    fun observeDiscarded(): Flow<List<DiscardedPropertyRow>>

    /**
     * Other properties, for the "related" block at the bottom of the detail.
     * The exclusion is done in SQL so the caller cannot forget it.
     */
    @Transaction
    @Query(
        """
        SELECT properties.* FROM properties
        LEFT JOIN property_flags ON properties.property_code = property_flags.property_code
        WHERE properties.property_code != :propertyCode
          AND (property_flags.flag IS NULL OR property_flags.flag != 'discarded')
        ORDER BY properties.order_in_feed ASC
        LIMIT :limit
        """,
    )
    suspend fun findRelatedTo(propertyCode: String, limit: Int): List<PropertyWithLocalState>

    @Query("SELECT COUNT(*) FROM properties")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(entities: List<PropertyEntity>)

    @Query("DELETE FROM properties WHERE property_code NOT IN (:keptCodes)")
    suspend fun deleteNotIn(keptCodes: List<String>)

    @Query("DELETE FROM property_details WHERE property_code NOT IN (:keptCodes)")
    suspend fun deleteOrphanDetails(keptCodes: List<String>)

    /**
     * Replaces the feed without destroying anything the user owns.
     *
     * An upsert (rather than `DELETE FROM properties` + insert) keeps the cached
     * details -- and therefore the known lift values -- for properties that are
     * still in the feed. Properties that dropped out are removed explicitly,
     * along with their now-orphaned details. Flags are never touched here: a
     * favourite survives even a property temporarily leaving the feed.
     */
    @Transaction
    suspend fun replaceFeed(entities: List<PropertyEntity>) {
        if (entities.isEmpty()) return
        val codes = entities.map(PropertyEntity::propertyCode)
        upsertAll(entities)
        deleteNotIn(codes)
        deleteOrphanDetails(codes)
    }
}
