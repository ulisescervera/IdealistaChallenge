package com.ulisescervera.uci.data.local.di

import android.content.Context
import androidx.room.Room
import com.ulisescervera.uci.data.local.UciDatabase
import com.ulisescervera.uci.data.local.dao.PropertyDao
import com.ulisescervera.uci.data.local.dao.PropertyDetailDao
import com.ulisescervera.uci.data.local.dao.PropertyFlagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): UciDatabase =
        Room.databaseBuilder(context, UciDatabase::class.java, UciDatabase.NAME)
            // The schema is at version 1 and exported to data/schemas/. There is
            // deliberately no `fallbackToDestructiveMigration()`: it would throw
            // away the user's favourites on the first schema bump, which is the
            // one table in this app that is not a cache. A real migration is
            // cheap to write and this is the reminder to write it.
            .build()

    @Provides
    fun propertyDao(database: UciDatabase): PropertyDao = database.propertyDao()

    @Provides
    fun propertyDetailDao(database: UciDatabase): PropertyDetailDao = database.propertyDetailDao()

    @Provides
    fun propertyFlagDao(database: UciDatabase): PropertyFlagDao = database.propertyFlagDao()
}
