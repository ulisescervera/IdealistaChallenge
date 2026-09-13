package com.ulisescervera.uci.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity
import kotlinx.coroutines.flow.Flow

/**
 * The user's flags.
 *
 * ### Why these queries return rows and not `PropertyFlag?`
 * A `SELECT flag` returning a nullable enum would need a `String?` -> `PropertyFlag?`
 * type converter, while writing [PropertyFlagEntity.flag] needs a non-null one.
 * Two converters over the same pair of types is ambiguous for Room, and the
 * resolution it picks is not something worth depending on. Returning the whole
 * row keeps a single, non-null conversion, and mapping "no row" to
 * `PropertyFlag.NONE` happens once in `PropertyFlagStore`.
 *
 * ### Why `PropertyFlag.NONE` is never stored
 * Clearing a flag deletes the row. That keeps the table proportional to the
 * number of decisions the user has actually made, and it makes the
 * `LEFT JOIN … flag IS NULL` in [PropertyDao] mean exactly "the user has no
 * opinion about this property".
 */
@Dao
interface PropertyFlagDao {

    @Upsert
    suspend fun upsert(entity: PropertyFlagEntity)

    @Query("DELETE FROM property_flags WHERE property_code = :propertyCode")
    suspend fun delete(propertyCode: String)

    @Query("SELECT * FROM property_flags WHERE property_code = :propertyCode LIMIT 1")
    suspend fun findRow(propertyCode: String): PropertyFlagEntity?

    @Query("SELECT * FROM property_flags WHERE property_code = :propertyCode LIMIT 1")
    fun observeRow(propertyCode: String): Flow<PropertyFlagEntity?>

    /**
     * Returns the number of rows restored, which the "restore all" confirmation
     * copy uses. The `'discarded'` literal is the token produced by
     * `UciTypeConverters.flagToToken`; Room needs compile-time constant SQL, so
     * it is duplicated here and `UciTypeConverters.Flags` documents the pairing.
     */
    @Query("DELETE FROM property_flags WHERE flag = 'discarded'")
    suspend fun deleteAllDiscarded(): Int
}
