package com.ulisescervera.uci.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ulisescervera.uci.data.local.entity.PropertyDetailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyDetailDao {

    @Query("SELECT * FROM property_details WHERE property_code = :propertyCode LIMIT 1")
    fun observe(propertyCode: String): Flow<PropertyDetailEntity?>

    @Query("SELECT * FROM property_details WHERE property_code = :propertyCode LIMIT 1")
    suspend fun find(propertyCode: String): PropertyDetailEntity?

    @Upsert
    suspend fun upsert(entity: PropertyDetailEntity)
}
