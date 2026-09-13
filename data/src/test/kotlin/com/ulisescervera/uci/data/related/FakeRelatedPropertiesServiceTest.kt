package com.ulisescervera.uci.data.related

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.local.UciDatabase
import com.ulisescervera.uci.data.local.entity.ImageRecord
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity
import com.ulisescervera.uci.domain.model.PropertyFlag
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The simulated recommendation endpoint.
 *
 * Constructed with zero latency: the production 650 ms exists so the user sees
 * the skeleton, and a test that waited for it would just be slower for no
 * additional coverage. That the latency is a constructor parameter at all is
 * why this file runs in milliseconds.
 */
@RunWith(RobolectricTestRunner::class)
class FakeRelatedPropertiesServiceTest {

    private lateinit var database: UciDatabase
    private lateinit var service: FakeRelatedPropertiesService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UciDatabase::class.java,
        ).allowMainThreadQueries().build()
        service = FakeRelatedPropertiesService(
            propertyDao = database.propertyDao(),
            simulatedLatencyMillis = FakeRelatedPropertiesService.NO_LATENCY_MILLIS,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `it never returns the requested property`() = runTest {
        seed("1", "2", "3", "4")

        val ids = service.relatedPropertyIds("1", limit = 10)

        assertThat(ids).doesNotContain("1")
        assertThat(ids).containsExactly("2", "3", "4")
    }

    @Test
    fun `it excludes properties the user discarded`() = runTest {
        seed("1", "2", "3")
        database.propertyFlagDao().upsert(
            PropertyFlagEntity("2", PropertyFlag.DISCARDED, 1_000L, "Europe/Madrid"),
        )

        // Recommending something the user explicitly removed would be rude.
        assertThat(service.relatedPropertyIds("1", limit = 10)).containsExactly("3")
    }

    @Test
    fun `properties of the same operation rank first`() = runTest {
        database.propertyDao().replaceFeed(
            listOf(
                entity("1", operation = "sale", price = 1_000_000.0),
                entity("2", operation = "rent", price = 1_010_000.0),
                entity("3", operation = "sale", price = 1_500_000.0),
            ),
        )

        val ids = service.relatedPropertyIds("1", limit = 10)

        // A rental is not a substitute for a purchase, even at a closer price.
        assertThat(ids.first()).isEqualTo("3")
    }

    @Test
    fun `within the same operation the closest price ranks first`() = runTest {
        database.propertyDao().replaceFeed(
            listOf(
                entity("1", price = 1_000_000.0),
                entity("2", price = 5_000_000.0),
                entity("3", price = 1_050_000.0),
            ),
        )

        assertThat(service.relatedPropertyIds("1", limit = 10)).containsExactly("3", "2").inOrder()
    }

    @Test
    fun `the limit is honoured`() = runTest {
        seed("1", "2", "3", "4", "5")

        assertThat(service.relatedPropertyIds("1", limit = 2)).hasSize(2)
    }

    @Test
    fun `a non positive limit short circuits without touching the database`() = runTest {
        seed("1", "2")

        assertThat(service.relatedPropertyIds("1", limit = 0)).isEmpty()
    }

    @Test
    fun `an unknown reference property falls back to feed order`() = runTest {
        seed("1", "2", "3")

        // Deep-linked into a detail whose summary is not cached yet.
        assertThat(service.relatedPropertyIds("99", limit = 10))
            .containsExactly("1", "2", "3")
            .inOrder()
    }

    private suspend fun seed(vararg codes: String) {
        database.propertyDao().replaceFeed(codes.map { entity(it) })
    }

    private fun entity(
        code: String,
        operation: String = "sale",
        price: Double = 1_000_000.0,
    ) = PropertyEntity(
        propertyCode = code,
        orderInFeed = code.toIntOrNull() ?: 0,
        propertyType = "flat",
        operation = operation,
        street = null,
        neighborhood = "Castellana",
        district = null,
        municipality = "Madrid",
        province = "Madrid",
        countryCode = "es",
        latitude = null,
        longitude = null,
        priceAmount = price,
        priceCurrencySuffix = "€",
        sizeSquareMeters = 100.0,
        rooms = 3,
        bathrooms = 2,
        rawFloor = "2",
        hasParkingSpace = null,
        isParkingIncludedInPrice = null,
        isExterior = null,
        hasAirConditioning = null,
        hasBoxRoom = null,
        hasSwimmingPool = null,
        hasTerrace = null,
        hasGarden = null,
        thumbnailUrl = null,
        images = listOf(ImageRecord("https://example.test/$code.webp")),
        summary = "",
        cachedAtEpochMillis = 0L,
    )
}
