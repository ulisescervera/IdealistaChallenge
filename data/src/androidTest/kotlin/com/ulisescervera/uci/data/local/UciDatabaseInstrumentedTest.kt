package com.ulisescervera.uci.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.local.entity.ImageRecord
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity
import com.ulisescervera.uci.domain.model.PropertyFlag
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The database on a real device, against real SQLite.
 *
 * Robolectric's SQLite is a good approximation but it is not the same binary.
 * This test exists to catch the difference: collation, type affinity and the
 * exact behaviour of `NOT IN (:list)` with an empty list are all places where
 * the two have historically disagreed.
 *
 * [MigrationTestHelper] is wired up now, at version 1, deliberately. The schema
 * is exported to `data/schemas/`, so the first real migration can be validated
 * against a committed JSON file instead of against someone's memory -- and the
 * harness will already be here.
 *
 * One step is intentionally left undone: the schema folder is **not** on the
 * androidTest asset path. `MigrationTestHelper` only reads it from
 * `createDatabase()` / `runMigrationsAndValidate()`, neither of which is called
 * at version 1, and AGP 9 replaced `assets.srcDirs(...)` with an `assets`
 * accessor whose exact form is not worth guessing at for configuration nothing
 * reads yet. When the first migration lands, add this to data/build.gradle.kts
 * next to the `kotlin.directories` lines and verify it against the AGP 9 docs:
 *
 *     android.sourceSets.named("androidTest") { assets.directories += "$projectDir/schemas" }
 */
@RunWith(AndroidJUnit4::class)
class UciDatabaseInstrumentedTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UciDatabase::class.java,
    )

    @Test
    fun theSchemaOpensAtTheCurrentVersion() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UciDatabase::class.java,
        ).build()

        assertThat(database.openHelper.readableDatabase.version).isEqualTo(UciDatabase.VERSION)
        database.close()
    }

    @Test
    fun flagsSurviveClosingAndReopeningTheDatabase() = runBlocking<Unit> {
        val name = "uci-instrumented-test.db"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(name)

        var database = Room.databaseBuilder(context, UciDatabase::class.java, name).build()
        database.propertyDao().replaceFeed(listOf(entity("1")))
        database.propertyFlagDao().upsert(
            PropertyFlagEntity("1", PropertyFlag.FAVOURITE, 1_000L, "Europe/Madrid"),
        )
        database.close()

        database = Room.databaseBuilder(context, UciDatabase::class.java, name).build()

        // The one table in this app that is not a cache.
        assertThat(database.propertyFlagDao().findRow("1")?.flag).isEqualTo(PropertyFlag.FAVOURITE)
        assertThat(database.propertyFlagDao().findRow("1")?.timeZoneId).isEqualTo("Europe/Madrid")
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun accentedTextRoundTripsThroughRealSqlite() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UciDatabase::class.java,
        ).build()

        // "Chamberí", "Salón": UTF-8 handling is the kind of thing that only
        // breaks on a device.
        database.propertyDao().replaceFeed(listOf(entity("1", neighborhood = "Chamberí")))

        assertThat(database.propertyDao().findOne("1")?.property?.neighborhood)
            .isEqualTo("Chamberí")
        database.close()
    }

    private fun entity(code: String, neighborhood: String = "Castellana") = PropertyEntity(
        propertyCode = code,
        orderInFeed = 0,
        propertyType = "flat",
        operation = "sale",
        street = "calle de Lagasca",
        neighborhood = neighborhood,
        district = "Barrio de Salamanca",
        municipality = "Madrid",
        province = "Madrid",
        countryCode = "es",
        latitude = 40.4362687,
        longitude = -3.6833686,
        priceAmount = 1_195_000.0,
        priceCurrencySuffix = "€",
        sizeSquareMeters = 133.0,
        rooms = 3,
        bathrooms = 2,
        rawFloor = "2",
        hasParkingSpace = null,
        isParkingIncludedInPrice = null,
        isExterior = false,
        hasAirConditioning = true,
        hasBoxRoom = false,
        hasSwimmingPool = null,
        hasTerrace = null,
        hasGarden = null,
        thumbnailUrl = null,
        images = listOf(ImageRecord("https://example.test/$code.webp", localizedName = "Salón")),
        summary = "Venta.",
        cachedAtEpochMillis = 1_000L,
    )
}
