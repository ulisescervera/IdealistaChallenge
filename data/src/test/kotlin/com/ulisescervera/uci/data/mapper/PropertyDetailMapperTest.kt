package com.ulisescervera.uci.data.mapper

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.support.DtoFixtures
import com.ulisescervera.uci.data.support.FixedUciClock
import com.ulisescervera.uci.domain.model.EnergyRating
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Money
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.ParkingSpace
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyAddress
import com.ulisescervera.uci.domain.model.PropertyFeatures
import com.ulisescervera.uci.domain.model.PropertyImage
import com.ulisescervera.uci.domain.model.PropertyType
import org.junit.Test

class PropertyDetailMapperTest {

    private val clock = FixedUciClock()
    private val mapper = PropertyDetailMapper(clock)

    @Test
    fun `the housing type comes from extendedPropertyType, not propertyType`() {
        // `propertyType` is the family ("homes"). Reading it would make every
        // flat UNKNOWN, which silently hides the floor and lift rows because
        // Floor.from returns NotApplicable for non-vertical housing.
        val entity = mapper.toEntity("1", DtoFixtures.detail())

        assertThat(entity.propertyType).isEqualTo("flat")
    }

    @Test
    fun `homeType is the fallback when extendedPropertyType is absent`() {
        val entity = mapper.toEntity("1", DtoFixtures.detail(extendedType = null))

        assertThat(entity.propertyType).isEqualTo("flat")
    }

    @Test
    fun `the response is stored under the requested id, not under adid`() {
        // detail.json is a static fixture that always answers adid = 1. Storing
        // it under the requested id is what makes opening property 3 work.
        val entity = mapper.toEntity("3", DtoFixtures.detail(adId = 1))

        assertThat(entity.propertyCode).isEqualTo("3")
    }

    @Test
    fun `the detail is the only source of the lift`() {
        val entity = mapper.toEntity("1", DtoFixtures.detail(lift = true))

        assertThat(entity.hasLift).isTrue()
    }

    @Test
    fun `mapping to domain merges the cached summary`() {
        val summary = summaryWithGarage()
        val entity = mapper.toEntity("1", DtoFixtures.detail())

        val detail = mapper.toDomain(entity, summary)

        assertThat(detail.id).isEqualTo("1")
        assertThat(detail.propertyType).isEqualTo(PropertyType.FLAT)
        assertThat(detail.hasLift).isTrue()
        assertThat(detail.floor).isEqualTo(Floor.Numbered(2))
        assertThat(detail.location).isEqualTo(GeoPoint(40.4362687, -3.6833686))
        assertThat(detail.comment).contains("EN EXCLUSIVA")
    }

    @Test
    fun `the garage always comes from the summary because the detail omits it`() {
        val summary = summaryWithGarage()
        val entity = mapper.toEntity("1", DtoFixtures.detail())

        val detail = mapper.toDomain(entity, summary)

        assertThat(detail.summary.parkingSpace).isEqualTo(ParkingSpace.Available(isIncludedInPrice = true))
    }

    @Test
    fun `the summary location is used when the detail has none`() {
        val entity = mapper.toEntity("1", DtoFixtures.detail(latitude = null, longitude = null))

        val detail = mapper.toDomain(entity, summaryWithGarage())

        assertThat(detail.location).isEqualTo(GeoPoint(40.4362687, -3.6833686))
    }

    @Test
    fun `the energy certificate is parsed into ratings`() {
        val entity = mapper.toEntity("1", DtoFixtures.detail())

        val detail = mapper.toDomain(entity, summaryWithGarage())

        // Bound to a local: `energyCertification` is a public val declared in
        // :domain, and Kotlin will not smart cast a public property from another
        // module.
        val certification = detail.energyCertification
        assertThat(certification).isNotNull()
        assertThat(certification!!.consumption).isEqualTo(EnergyRating.E)
        assertThat(certification.emissions).isEqualTo(EnergyRating.E)
    }

    @Test
    fun `no certificate block means no certificate section`() {
        val dto = DtoFixtures.detail().copy(energyCertificationDto = null)
        val entity = mapper.toEntity("1", dto)

        val detail = mapper.toDomain(entity, summaryWithGarage())

        // `null` rather than a certificate full of UNKNOWNs, so the UI can omit
        // the row instead of printing "Consumo sin datos".
        assertThat(detail.energyCertification).isNull()
    }

    private fun summaryWithGarage() = Property(
        id = "1",
        propertyType = PropertyType.FLAT,
        operation = Operation.SALE,
        address = PropertyAddress(municipality = "Madrid", neighborhood = "Castellana"),
        location = GeoPoint(40.4362687, -3.6833686),
        price = Money(1_195_000.0, "€"),
        sizeSquareMeters = 133.0,
        rooms = 3,
        bathrooms = 2,
        floor = Floor.Numbered(2),
        hasLift = null,
        parkingSpace = ParkingSpace.Available(isIncludedInPrice = true),
        isExterior = false,
        features = PropertyFeatures.Empty,
        thumbnailUrl = null,
        images = listOf(PropertyImage("https://example.test/1.webp", null, null, null)),
        summary = "",
    )
}
