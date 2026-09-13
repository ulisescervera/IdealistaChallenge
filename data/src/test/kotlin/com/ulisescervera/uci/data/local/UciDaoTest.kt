package com.ulisescervera.uci.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.local.dao.PropertyDao
import com.ulisescervera.uci.data.local.dao.PropertyDetailDao
import com.ulisescervera.uci.data.local.dao.PropertyFlagDao
import com.ulisescervera.uci.data.local.entity.ImageRecord
import com.ulisescervera.uci.data.local.entity.PropertyDetailEntity
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity
import com.ulisescervera.uci.domain.model.PropertyFlag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The DAOs, against a real in-memory SQLite through Robolectric.
 *
 * Not mocked: the behaviour under test *is* the SQL. `observeVisible` returning
 * an empty list because of an `INNER JOIN` instead of a `LEFT JOIN` is the kind
 * of bug a mocked DAO cannot have, and a fresh install can.
 */
@RunWith(RobolectricTestRunner::class)
class UciDaoTest {

    private lateinit var database: UciDatabase
    private lateinit var propertyDao: PropertyDao
    private lateinit var detailDao: PropertyDetailDao
    private lateinit var flagDao: PropertyFlagDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UciDatabase::class.java,
        ).allowMainThreadQueries().build()
        propertyDao = database.propertyDao()
        detailDao = database.propertyDetailDao()
        flagDao = database.propertyFlagDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a fresh install with no flags still shows every property`() = runTest {
        // The LEFT JOIN plus `flag IS NULL` branch. An INNER JOIN here would
        // make the app look empty until the user favourited something.
        propertyDao.replaceFeed(listOf(entity("1"), entity("2"), entity("3")))

        val visible = propertyDao.observeVisible().first()

        assertThat(visible.map { it.property.propertyCode }).containsExactly("1", "2", "3")
    }

    @Test
    fun `visible properties keep the backend order`() = runTest {
        propertyDao.replaceFeed(listOf(entity("9", order = 0), entity("4", order = 1), entity("7", order = 2)))

        val visible = propertyDao.observeVisible().first()

        assertThat(visible.map { it.property.propertyCode }).containsExactly("9", "4", "7").inOrder()
    }

    @Test
    fun `a discarded property disappears from the list`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1"), entity("2")))

        flagDao.upsert(flag("1", PropertyFlag.DISCARDED))

        assertThat(propertyDao.observeVisible().first().map { it.property.propertyCode })
            .containsExactly("2")
    }

    @Test
    fun `a favourite stays in the list`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1"), entity("2")))

        flagDao.upsert(flag("1", PropertyFlag.FAVOURITE))

        assertThat(propertyDao.observeVisible().first()).hasSize(2)
    }

    @Test
    fun `favourites are ordered by most recently marked`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1"), entity("2"), entity("3")))

        flagDao.upsert(flag("1", PropertyFlag.FAVOURITE, updatedAt = 100))
        flagDao.upsert(flag("3", PropertyFlag.FAVOURITE, updatedAt = 300))
        flagDao.upsert(flag("2", PropertyFlag.FAVOURITE, updatedAt = 200))

        assertThat(propertyDao.observeFavourites().first().map { it.property.propertyCode })
            .containsExactly("3", "2", "1")
            .inOrder()
    }

    @Test
    fun `discarded rows are projected newest first with everything the sheet needs`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1"), entity("2")))
        flagDao.upsert(flag("1", PropertyFlag.DISCARDED, updatedAt = 100))
        flagDao.upsert(flag("2", PropertyFlag.DISCARDED, updatedAt = 200))

        val rows = propertyDao.observeDiscarded().first()

        assertThat(rows.map { it.propertyCode }).containsExactly("2", "1").inOrder()
        with(rows.first()) {
            assertThat(neighborhood).isEqualTo("Castellana")
            assertThat(municipality).isEqualTo("Madrid")
            assertThat(priceAmount).isEqualTo(1_195_000.0)
            assertThat(discardedAtEpochMillis).isEqualTo(200)
        }
    }

    @Test
    fun `the flag is exclusive because it is one column`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1")))

        flagDao.upsert(flag("1", PropertyFlag.DISCARDED))
        flagDao.upsert(flag("1", PropertyFlag.FAVOURITE))

        // The second upsert replaced the first: there is no row in which the
        // property is both.
        assertThat(flagDao.findRow("1")?.flag).isEqualTo(PropertyFlag.FAVOURITE)
        assertThat(propertyDao.observeVisible().first()).hasSize(1)
    }

    @Test
    fun `refreshing the feed preserves flags`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1"), entity("2")))
        flagDao.upsert(flag("1", PropertyFlag.FAVOURITE))

        // Same ids, new payload -- what a pull-to-refresh does.
        propertyDao.replaceFeed(listOf(entity("1", price = 999.0), entity("2")))

        assertThat(flagDao.findRow("1")?.flag).isEqualTo(PropertyFlag.FAVOURITE)
        assertThat(propertyDao.findOne("1")?.property?.priceAmount).isEqualTo(999.0)
    }

    @Test
    fun `refreshing the feed preserves cached details, and therefore known lifts`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1")))
        detailDao.upsert(detailEntity("1", hasLift = true))

        propertyDao.replaceFeed(listOf(entity("1", price = 1.0)))

        // A DELETE-then-INSERT refresh would have cascaded the detail away and
        // the list would go back to "ascensor sin confirmar".
        assertThat(propertyDao.findOne("1")?.detail?.hasLift).isTrue()
    }

    @Test
    fun `properties that leave the feed are removed along with their details`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1"), entity("2")))
        detailDao.upsert(detailEntity("2"))

        propertyDao.replaceFeed(listOf(entity("1")))

        assertThat(propertyDao.count()).isEqualTo(1)
        assertThat(detailDao.find("2")).isNull()
    }

    @Test
    fun `the lift reaches the summary through the detail relation`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1")))

        // Before the detail is fetched the lift is genuinely unknown.
        assertThat(propertyDao.findOne("1")?.detail).isNull()

        detailDao.upsert(detailEntity("1", hasLift = true))

        assertThat(propertyDao.findOne("1")?.detail?.hasLift).isTrue()
    }

    @Test
    fun `related properties exclude the requested one and the discarded ones`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1"), entity("2"), entity("3"), entity("4")))
        flagDao.upsert(flag("3", PropertyFlag.DISCARDED))

        val related = propertyDao.findRelatedTo("1", limit = 10)

        assertThat(related.map { it.property.propertyCode }).containsExactly("2", "4")
    }

    @Test
    fun `restore all deletes only the discarded rows and reports the count`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1"), entity("2"), entity("3")))
        flagDao.upsert(flag("1", PropertyFlag.DISCARDED))
        flagDao.upsert(flag("2", PropertyFlag.DISCARDED))
        flagDao.upsert(flag("3", PropertyFlag.FAVOURITE))

        val restored = flagDao.deleteAllDiscarded()

        assertThat(restored).isEqualTo(2)
        assertThat(flagDao.findRow("3")?.flag).isEqualTo(PropertyFlag.FAVOURITE)
        assertThat(propertyDao.observeVisible().first()).hasSize(3)
    }

    @Test
    fun `an empty payload does not wipe the cache`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1")))

        // A server hiccup returning [] must not look like "you have no
        // properties"; the cache is the offline story.
        propertyDao.replaceFeed(emptyList())

        assertThat(propertyDao.count()).isEqualTo(1)
    }

    @Test
    fun `the time zone is stored with the favourite row`() = runTest {
        propertyDao.replaceFeed(listOf(entity("1")))

        flagDao.upsert(flag("1", PropertyFlag.FAVOURITE, zoneId = "America/Mexico_City"))

        assertThat(flagDao.findRow("1")?.timeZoneId).isEqualTo("America/Mexico_City")
    }

    // ------------------------------------------------------------------ helpers

    private fun entity(
        code: String,
        order: Int = code.toIntOrNull() ?: 0,
        price: Double = 1_195_000.0,
    ) = PropertyEntity(
        propertyCode = code,
        orderInFeed = order,
        propertyType = "flat",
        operation = "sale",
        street = "calle de Lagasca",
        neighborhood = "Castellana",
        district = "Barrio de Salamanca",
        municipality = "Madrid",
        province = "Madrid",
        countryCode = "es",
        latitude = 40.4362687,
        longitude = -3.6833686,
        priceAmount = price,
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
        thumbnailUrl = "https://example.test/thumb.webp",
        images = listOf(ImageRecord("https://example.test/$code.webp")),
        summary = "Venta.",
        cachedAtEpochMillis = 1_000L,
    )

    private fun detailEntity(code: String, hasLift: Boolean? = null) = PropertyDetailEntity(
        propertyCode = code,
        comment = "Comentario",
        images = listOf(ImageRecord("https://example.test/detail-$code.webp")),
        latitude = 40.4362687,
        longitude = -3.6833686,
        priceAmount = 1_195_000.0,
        priceCurrencySuffix = "€",
        operation = "sale",
        propertyType = "flat",
        communityCostsPerMonth = 330.0,
        rooms = 3,
        bathrooms = 2,
        isExterior = false,
        housingFurniture = "unknown",
        agencyIsABank = false,
        energyCertificationType = "e",
        flatLocation = "internal",
        modificationDateEpochMillis = 1_727_683_968_000,
        constructedAreaSquareMeters = 133.0,
        hasLift = hasLift,
        hasBoxRoom = false,
        isDuplex = false,
        rawFloor = "2",
        status = "renew",
        energyTitle = "Certificado energético",
        energyConsumptionType = "e",
        energyEmissionsType = "e",
        cachedAtEpochMillis = 2_000L,
    )

    private fun flag(
        code: String,
        flag: PropertyFlag,
        updatedAt: Long = 1_000L,
        zoneId: String = "Europe/Madrid",
    ) = PropertyFlagEntity(
        propertyCode = code,
        flag = flag,
        updatedAtEpochMillis = updatedAt,
        timeZoneId = zoneId,
    )
}
