package com.ulisescervera.uci.support

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Money
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.ParkingSpace
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyAddress
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.model.PropertyFeatures
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.model.PropertyImage
import com.ulisescervera.uci.domain.model.PropertyType
import com.ulisescervera.uci.domain.repository.PropertyRepository
import com.ulisescervera.uci.domain.repository.RelatedPropertiesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Repository fakes for the instrumented tests, bound with `@BindValue`.
 *
 * Instrumented tests run against a real device or emulator, so hitting the real
 * network would make them slow, flaky and dependent on GitHub Pages being up.
 * The fakes also let a test set up the exact state a screen should be in --
 * "three properties, one of them discarded" -- which is the whole point.
 *
 * Image URLs are deliberately unreachable: Coil falls back to the placeholder,
 * the test never waits on a download, and the assertions are about text and
 * behaviour rather than pixels.
 */
class AndroidFakePropertyRepository : PropertyRepository {

    private val visible = MutableStateFlow<List<Property>>(emptyList())
    private val favourites = MutableStateFlow<List<Property>>(emptyList())
    private val discarded = MutableStateFlow<List<DiscardedProperty>>(emptyList())
    private val details = MutableStateFlow<Map<String, PropertyDetail>>(emptyMap())
    private val flags = MutableStateFlow<Map<String, PropertyFlag>>(emptyMap())

    /** Every `setFlag` call, in order, for assertions about what the UI wrote. */
    val writes = mutableListOf<Pair<String, PropertyFlag>>()

    fun setVisible(properties: List<Property>) {
        visible.value = properties
    }

    fun setFavourites(properties: List<Property>) {
        favourites.value = properties
    }

    fun setDiscarded(items: List<DiscardedProperty>) {
        discarded.value = items
    }

    fun setDetail(detail: PropertyDetail) {
        details.value = details.value + (detail.id to detail)
    }

    override fun observeVisibleProperties(): Flow<List<Property>> = visible
    override fun observeFavouriteProperties(): Flow<List<Property>> = favourites
    override fun observeDiscardedProperties(): Flow<List<DiscardedProperty>> = discarded
    override fun observeProperty(propertyId: String): Flow<Property?> =
        visible.map { list -> list.firstOrNull { it.id == propertyId } }

    override fun observePropertyDetail(propertyId: String): Flow<PropertyDetail?> =
        details.map { it[propertyId] }

    override suspend fun refreshProperties(): UciResult<Unit> = UciResult.Success(Unit)

    override suspend fun refreshPropertyDetail(propertyId: String): UciResult<PropertyDetail> =
        details.value[propertyId]
            ?.let { UciResult.Success(it) }
            ?: UciResult.Failure(
                com.ulisescervera.uci.domain.common.UciError.PropertyNotFound(propertyId),
            )

    override suspend fun flagOf(propertyId: String): PropertyFlag =
        flags.value[propertyId] ?: PropertyFlag.NONE

    override suspend fun setFlag(propertyId: String, flag: PropertyFlag): UciResult<Unit> {
        writes += propertyId to flag
        flags.value = flags.value.toMutableMap().apply {
            if (flag == PropertyFlag.NONE) remove(propertyId) else put(propertyId, flag)
        }
        return UciResult.Success(Unit)
    }

    override suspend fun restoreAllDiscarded(): UciResult<Int> {
        val discardedFlags = flags.value.filterValues { it == PropertyFlag.DISCARDED }
        flags.value = flags.value - discardedFlags.keys
        return UciResult.Success(discardedFlags.size)
    }
}

class AndroidFakeRelatedRepository : RelatedPropertiesRepository {
    var items: List<Property> = emptyList()

    override suspend fun relatedTo(propertyId: String, limit: Int): UciResult<List<Property>> =
        UciResult.Success(items)
}

/** Fixtures for the instrumented tests. */
object UiFixtures {

    fun property(
        id: String = "1",
        type: PropertyType = PropertyType.FLAT,
        neighborhood: String? = "Castellana",
        municipality: String? = "Madrid",
        price: Money = Money(1_195_000.0, "€"),
        size: Double = 133.0,
        rooms: Int = 3,
        bathrooms: Int = 2,
        floor: Floor = Floor.Numbered(2),
        hasLift: Boolean? = true,
        parkingSpace: ParkingSpace = ParkingSpace.Available(isIncludedInPrice = true),
        location: GeoPoint? = GeoPoint(40.4362687, -3.6833686),
        flag: PropertyFlag = PropertyFlag.NONE,
        images: List<PropertyImage> = listOf(image("a"), image("b")),
    ) = Property(
        id = id,
        propertyType = type,
        operation = Operation.SALE,
        address = PropertyAddress(
            street = "calle de Lagasca",
            neighborhood = neighborhood,
            district = "Barrio de Salamanca",
            municipality = municipality,
            province = "Madrid",
            countryCode = "es",
        ),
        location = location,
        price = price,
        sizeSquareMeters = size,
        rooms = rooms,
        bathrooms = bathrooms,
        floor = floor,
        hasLift = hasLift,
        parkingSpace = parkingSpace,
        isExterior = false,
        features = PropertyFeatures(hasAirConditioning = true),
        thumbnailUrl = null,
        images = images,
        summary = "Venta.",
        flag = flag,
    )

    /** Unreachable on purpose: Coil shows the placeholder and nothing blocks. */
    fun image(id: String) = PropertyImage(
        url = "https://uci.invalid/$id.webp",
        tag = "livingRoom",
        localizedName = "Salón",
        multimediaId = id.hashCode().toLong(),
    )

    fun discarded(id: String = "1", at: Long = 1_000L) = DiscardedProperty(
        id = id,
        propertyType = PropertyType.FLAT,
        zone = "Castellana",
        city = "Madrid",
        price = Money(1_195_000.0, "€"),
        thumbnailUrl = null,
        discardedAtEpochMillis = at,
    )
}
