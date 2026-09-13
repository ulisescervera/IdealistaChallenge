package com.ulisescervera.uci.data.mapper

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.local.entity.ImageRecord
import com.ulisescervera.uci.data.local.entity.PropertyDetailEntity
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity
import com.ulisescervera.uci.data.local.pojo.DiscardedPropertyRow
import com.ulisescervera.uci.data.local.pojo.PropertyWithLocalState
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.ParkingSpace
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.model.PropertyType
import org.junit.Test

/**
 * Where three tables become one model. Pure functions, so no Robolectric.
 *
 * Every test here corresponds to a decision that has a wrong answer which would
 * still compile and still look plausible on screen.
 */
class PropertyEntityMapperTest {

    private val mapper = PropertyEntityMapper()

    @Test
    fun `no flag row means the user has no opinion, not a negative one`() {
        val property = mapper.toDomain(row(flag = null))

        assertThat(property.flag).isEqualTo(PropertyFlag.NONE)
        assertThat(property.isFavourite).isFalse()
        assertThat(property.isDiscarded).isFalse()
    }

    @Test
    fun `a favourite row produces the mark with its stored zone`() {
        val property = mapper.toDomain(
            row(flag = flagEntity(PropertyFlag.FAVOURITE, updatedAt = 5_000L, zone = "Europe/Madrid")),
        )

        assertThat(property.isFavourite).isTrue()
        assertThat(property.favouriteMark?.epochMillis).isEqualTo(5_000L)
        assertThat(property.favouriteMark?.zoneId).isEqualTo("Europe/Madrid")
    }

    @Test
    fun `a discarded row has no favourite mark, even though the row has a timestamp`() {
        val property = mapper.toDomain(
            row(flag = flagEntity(PropertyFlag.DISCARDED, updatedAt = 9_000L)),
        )

        // `updated_at` exists on every flag row. Surfacing it as a favourite
        // date would put "Guardado en favoritos el …" on a discarded property.
        assertThat(property.isDiscarded).isTrue()
        assertThat(property.favouriteMark).isNull()
    }

    @Test
    fun `the lift comes from the cached detail and is null until it exists`() {
        assertThat(mapper.toDomain(row(detail = null)).hasLift).isNull()
        assertThat(mapper.toDomain(row(detail = detailEntity(hasLift = true))).hasLift).isTrue()
        assertThat(mapper.toDomain(row(detail = detailEntity(hasLift = false))).hasLift).isFalse()
    }

    @Test
    fun `raw strings become closed types`() {
        val property = mapper.toDomain(row())

        assertThat(property.propertyType).isEqualTo(PropertyType.FLAT)
        assertThat(property.operation).isEqualTo(Operation.SALE)
        assertThat(property.floor).isEqualTo(Floor.Numbered(2))
        assertThat(property.location).isEqualTo(GeoPoint(40.4362687, -3.6833686))
    }

    @Test
    fun `an unusable coordinate pair becomes no location at all`() {
        // Which is what hides the "ver en el mapa" button, rather than opening a
        // map centred on the Gulf of Guinea.
        val property = mapper.toDomain(row(latitude = 0.0, longitude = 0.0))

        assertThat(property.location).isNull()
        assertThat(property.hasLocation).isFalse()
    }

    @Test
    fun `unreported parking stays unreported instead of becoming a no`() {
        assertThat(mapper.toDomain(row()).parkingSpace).isEqualTo(ParkingSpace.Unreported)
        assertThat(mapper.toDomain(row(hasParking = false)).parkingSpace).isEqualTo(ParkingSpace.None)
        assertThat(mapper.toDomain(row(hasParking = true, parkingIncluded = true)).parkingSpace)
            .isEqualTo(ParkingSpace.Available(isIncludedInPrice = true))
    }

    @Test
    fun `images keep their order and their localised names`() {
        val property = mapper.toDomain(
            row(
                images = listOf(
                    ImageRecord("https://a.test", "livingRoom", "Salón", 1),
                    ImageRecord("https://b.test", "kitchen", "Cocina", 2),
                ),
            ),
        )

        assertThat(property.images.map { it.url }).containsExactly("https://a.test", "https://b.test").inOrder()
        assertThat(property.images.first().localizedName).isEqualTo("Salón")
    }

    @Test
    fun `the discarded projection picks the neighbourhood, falling back to the district`() {
        val withNeighbourhood = mapper.toDiscarded(discardedRow(neighborhood = "Castellana"))
        val withoutNeighbourhood = mapper.toDiscarded(
            discardedRow(neighborhood = null, district = "Centro"),
        )

        assertThat(withNeighbourhood.zone).isEqualTo("Castellana")
        assertThat(withoutNeighbourhood.zone).isEqualTo("Centro")
    }

    @Test
    fun `the discarded projection treats a blank neighbourhood as absent`() {
        val row = mapper.toDiscarded(discardedRow(neighborhood = "  ", district = "Centro"))

        assertThat(row.zone).isEqualTo("Centro")
    }

    // ------------------------------------------------------------------ helpers

    private fun row(
        flag: PropertyFlagEntity? = null,
        detail: PropertyDetailEntity? = null,
        latitude: Double? = 40.4362687,
        longitude: Double? = -3.6833686,
        hasParking: Boolean? = null,
        parkingIncluded: Boolean? = null,
        images: List<ImageRecord> = listOf(ImageRecord("https://example.test/1.webp")),
    ) = PropertyWithLocalState(
        property = PropertyEntity(
            propertyCode = "1",
            orderInFeed = 0,
            propertyType = "flat",
            operation = "sale",
            street = "calle de Lagasca",
            neighborhood = "Castellana",
            district = "Barrio de Salamanca",
            municipality = "Madrid",
            province = "Madrid",
            countryCode = "es",
            latitude = latitude,
            longitude = longitude,
            priceAmount = 1_195_000.0,
            priceCurrencySuffix = "€",
            sizeSquareMeters = 133.0,
            rooms = 3,
            bathrooms = 2,
            rawFloor = "2",
            hasParkingSpace = hasParking,
            isParkingIncludedInPrice = parkingIncluded,
            isExterior = false,
            hasAirConditioning = true,
            hasBoxRoom = false,
            hasSwimmingPool = null,
            hasTerrace = null,
            hasGarden = null,
            thumbnailUrl = "https://example.test/thumb.webp",
            images = images,
            summary = "Venta.",
            cachedAtEpochMillis = 1_000L,
        ),
        flag = flag,
        detail = detail,
    )

    private fun flagEntity(
        flag: PropertyFlag,
        updatedAt: Long = 1_000L,
        zone: String = "Europe/Madrid",
    ) = PropertyFlagEntity(
        propertyCode = "1",
        flag = flag,
        updatedAtEpochMillis = updatedAt,
        timeZoneId = zone,
    )

    private fun detailEntity(hasLift: Boolean?) = PropertyDetailEntity(
        propertyCode = "1",
        comment = "",
        images = emptyList(),
        latitude = null,
        longitude = null,
        priceAmount = null,
        priceCurrencySuffix = null,
        operation = null,
        propertyType = null,
        communityCostsPerMonth = null,
        rooms = null,
        bathrooms = null,
        isExterior = null,
        housingFurniture = null,
        agencyIsABank = null,
        energyCertificationType = null,
        flatLocation = null,
        modificationDateEpochMillis = null,
        constructedAreaSquareMeters = null,
        hasLift = hasLift,
        hasBoxRoom = null,
        isDuplex = null,
        rawFloor = null,
        status = null,
        energyTitle = null,
        energyConsumptionType = null,
        energyEmissionsType = null,
        cachedAtEpochMillis = 2_000L,
    )

    private fun discardedRow(
        neighborhood: String? = "Castellana",
        district: String? = "Barrio de Salamanca",
    ) = DiscardedPropertyRow(
        propertyCode = "1",
        propertyType = "flat",
        neighborhood = neighborhood,
        district = district,
        municipality = "Madrid",
        province = "Madrid",
        priceAmount = 1_195_000.0,
        priceCurrencySuffix = "€",
        thumbnailUrl = null,
        discardedAtEpochMillis = 1_000L,
    )
}
