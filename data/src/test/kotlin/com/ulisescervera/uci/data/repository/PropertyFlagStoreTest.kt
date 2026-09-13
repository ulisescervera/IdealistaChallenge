package com.ulisescervera.uci.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.local.UciDatabase
import com.ulisescervera.uci.data.network.ErrorMapper
import com.ulisescervera.uci.data.support.FixedUciClock
import com.ulisescervera.uci.data.support.TestDispatcherProvider
import com.ulisescervera.uci.domain.common.getOrNull
import com.ulisescervera.uci.domain.model.FavouriteMark
import com.ulisescervera.uci.domain.model.PropertyFlag
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric because the subject is a real Room database: the point of these
 * tests is the SQL and the schema, and an in-memory fake DAO would assert that
 * this file's own assumptions agree with themselves.
 */
@RunWith(RobolectricTestRunner::class)
class PropertyFlagStoreTest {

    private lateinit var database: UciDatabase
    private lateinit var store: PropertyFlagStore
    private val clock = FixedUciClock()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UciDatabase::class.java,
        ).allowMainThreadQueries().build()

        store = PropertyFlagStore(
            flagDao = database.propertyFlagDao(),
            clock = clock,
            errorMapper = ErrorMapper(),
            dispatchers = TestDispatcherProvider(),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `an unflagged property has no opinion, not a stored NONE`() = runTest {
        assertThat(store.flagOf("1")).isEqualTo(PropertyFlag.NONE)
        assertThat(database.propertyFlagDao().findRow("1")).isNull()
    }

    @Test
    fun `clearing a flag deletes the row instead of storing NONE`() = runTest {
        store.setFlag("1", PropertyFlag.FAVOURITE)

        store.setFlag("1", PropertyFlag.NONE)

        // Keeps the table proportional to the decisions the user actually made,
        // and keeps the DAO's `flag IS NULL` branch meaningful.
        assertThat(database.propertyFlagDao().findRow("1")).isNull()
    }

    @Test
    fun `favouriting stores the instant and the zone from the clock`() = runTest {
        clock.advanceTo(Instant.parse("2026-03-03T18:12:00Z"))
        clock.travelTo(ZoneId.of("Europe/Madrid"))

        store.setFlag("1", PropertyFlag.FAVOURITE)

        val row = database.propertyFlagDao().findRow("1")!!
        assertThat(row.updatedAtEpochMillis).isEqualTo(Instant.parse("2026-03-03T18:12:00Z").toEpochMilli())
        assertThat(row.timeZoneId).isEqualTo("Europe/Madrid")

        // And the wall clock survives a later trip abroad.
        val mark = FavouriteMark(row.updatedAtEpochMillis, row.timeZoneId)
        assertThat(mark.atOriginalZone().hour).isEqualTo(19)
    }

    @Test
    fun `the second favourite records the zone the user is in by then`() = runTest {
        store.setFlag("1", PropertyFlag.FAVOURITE)

        clock.travelTo(ZoneId.of("America/Mexico_City"))
        store.setFlag("2", PropertyFlag.FAVOURITE)

        assertThat(database.propertyFlagDao().findRow("1")!!.timeZoneId).isEqualTo("Europe/Madrid")
        assertThat(database.propertyFlagDao().findRow("2")!!.timeZoneId)
            .isEqualTo("America/Mexico_City")
    }

    @Test
    fun `setting a flag overwrites the previous one in place`() = runTest {
        store.setFlag("1", PropertyFlag.DISCARDED)
        store.setFlag("1", PropertyFlag.FAVOURITE)

        assertThat(store.flagOf("1")).isEqualTo(PropertyFlag.FAVOURITE)
    }

    @Test
    fun `restore all reports the number restored`() = runTest {
        store.setFlag("1", PropertyFlag.DISCARDED)
        store.setFlag("2", PropertyFlag.DISCARDED)
        store.setFlag("3", PropertyFlag.FAVOURITE)

        val restored = store.restoreAllDiscarded()

        assertThat(restored.getOrNull()).isEqualTo(2)
        assertThat(store.flagOf("3")).isEqualTo(PropertyFlag.FAVOURITE)
    }
}
