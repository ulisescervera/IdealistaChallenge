package com.ulisescervera.uci.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ulisescervera.uci.data.local.converter.UciTypeConverters
import com.ulisescervera.uci.data.local.dao.PropertyDao
import com.ulisescervera.uci.data.local.dao.PropertyDetailDao
import com.ulisescervera.uci.data.local.dao.PropertyFlagDao
import com.ulisescervera.uci.data.local.entity.PropertyDetailEntity
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity

/**
 * Three tables, three responsibilities:
 *
 * | table              | owner    | survives a cache wipe |
 * |--------------------|----------|-----------------------|
 * | `properties`       | backend  | no                    |
 * | `property_details` | backend  | no                    |
 * | `property_flags`   | the user | **yes**               |
 *
 * The schema is exported to `data/schemas/` (see the Room Gradle plugin
 * configuration) so that any future migration can be validated against the
 * committed JSON instead of against someone's memory.
 */
@Database(
    entities = [
        PropertyEntity::class,
        PropertyDetailEntity::class,
        PropertyFlagEntity::class,
    ],
    version = UciDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(UciTypeConverters::class)
abstract class UciDatabase : RoomDatabase() {

    abstract fun propertyDao(): PropertyDao

    abstract fun propertyDetailDao(): PropertyDetailDao

    abstract fun propertyFlagDao(): PropertyFlagDao

    companion object {
        const val VERSION = 1
        const val NAME = "uci.db"
    }
}
