package com.ulisescervera.uci.data.network

import com.google.common.truth.Truth.assertThat
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit

/**
 * Contract tests for the network layer, against `MockWebServer`.
 *
 * They serve two purposes. First, they prove the DTOs actually parse the shape
 * the challenge serves -- the payloads below are trimmed copies of the real
 * ones, quirks included. Second, they pin down the `Json` configuration, which
 * is the single most consequential object in `:data`: `ignoreUnknownKeys` is
 * what stops a new server-side field from crashing a shipped client.
 */
class UciPropertyApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: UciPropertyApi

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = false
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UciPropertyApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `the list payload parses, including its nested price and sparse features`() = runTest {
        server.enqueue(jsonResponse(LIST_PAYLOAD))

        val items = api.properties()

        assertThat(items).hasSize(2)
        with(items[0]) {
            assertThat(propertyCode).isEqualTo("1")
            assertThat(propertyType).isEqualTo("flat")
            assertThat(priceInfo?.price?.amount).isEqualTo(1195000.0)
            assertThat(priceInfo?.price?.currencySuffix).isEqualTo("€")
            assertThat(multimedia?.images).hasSize(2)
            assertThat(features?.hasAirConditioning).isTrue()
            // Not present in this item's payload at all.
            assertThat(features?.hasGarden).isNull()
            assertThat(parkingSpace).isNull()
        }
        with(items[1]) {
            // The rental whose two prices disagree.
            assertThat(price).isEqualTo(2750000.0)
            assertThat(priceInfo?.price?.amount).isEqualTo(1200.0)
            assertThat(priceInfo?.price?.currencySuffix).isEqualTo("€/mes")
            assertThat(parkingSpace?.hasParkingSpace).isTrue()
        }
    }

    @Test
    fun `the detail payload parses, and priceInfo is flatter than in the list`() = runTest {
        server.enqueue(jsonResponse(DETAIL_PAYLOAD))

        val detail = api.propertyDetail()

        assertThat(detail.adId).isEqualTo(1)
        // Note the shape difference the two DTOs exist to document.
        assertThat(detail.priceInfo?.amount).isEqualTo(1195000.0)
        assertThat(detail.propertyType).isEqualTo("homes")
        assertThat(detail.extendedPropertyType).isEqualTo("flat")
        assertThat(detail.moreCharacteristics?.lift).isTrue()
        assertThat(detail.moreCharacteristics?.floor).isEqualTo("2")
        assertThat(detail.ubication?.latitude).isEqualTo(40.4362687)
        assertThat(detail.multimedia?.images?.first()?.localizedName).isEqualTo("Salón")
    }

    @Test
    fun `unknown server fields are ignored instead of failing the request`() = runTest {
        // The whole point of ignoreUnknownKeys: a backend deploy must not brick
        // an already-published app.
        server.enqueue(
            jsonResponse(
                """[{"propertyCode":"1","brandNewField":{"nested":true},"anotherOne":[1,2,3]}]""",
            ),
        )

        val items = api.properties()

        assertThat(items).hasSize(1)
        assertThat(items.single().propertyCode).isEqualTo("1")
    }

    @Test
    fun `an empty array is a valid empty list, not an error`() = runTest {
        server.enqueue(jsonResponse("[]"))

        assertThat(api.properties()).isEmpty()
    }

    @Test(expected = HttpException::class)
    fun `a non 2xx response raises HttpException for ErrorMapper to translate`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        api.properties()
    }

    @Test
    fun `the endpoints are requested at the documented paths`() = runTest {
        server.enqueue(jsonResponse("[]"))
        api.properties()
        assertThat(server.takeRequest().path).isEqualTo("/list.json")

        server.enqueue(jsonResponse(DETAIL_PAYLOAD))
        api.propertyDetail()
        assertThat(server.takeRequest().path).isEqualTo("/detail.json")
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(body)

    private companion object {
        val LIST_PAYLOAD = """
            [
              {
                "propertyCode": "1",
                "thumbnail": "https://img.test/1.webp",
                "floor": "2",
                "price": 1195000.0,
                "priceInfo": { "price": { "amount": 1195000.0, "currencySuffix": "€" } },
                "propertyType": "flat",
                "operation": "sale",
                "size": 133.0,
                "exterior": false,
                "rooms": 3,
                "bathrooms": 2,
                "address": "calle de Lagasca",
                "province": "Madrid",
                "municipality": "Madrid",
                "district": "Barrio de Salamanca",
                "country": "es",
                "neighborhood": "Castellana",
                "latitude": 40.4362687,
                "longitude": -3.6833686,
                "description": "Venta.Piso EN EXCLUSIVA.",
                "multimedia": {
                  "images": [
                    { "url": "https://img.test/1.webp", "tag": "livingRoom" },
                    { "url": "https://img.test/2.webp", "tag": "bedroom" }
                  ]
                },
                "features": { "hasAirConditioning": true, "hasBoxRoom": false }
              },
              {
                "propertyCode": "2",
                "floor": "6",
                "price": 2750000.0,
                "priceInfo": { "price": { "amount": 1200.0, "currencySuffix": "€/mes" } },
                "propertyType": "flat",
                "operation": "rent",
                "size": 241.0,
                "rooms": 4,
                "bathrooms": 4,
                "municipality": "Madrid",
                "district": "Chamberí",
                "neighborhood": "Almagro",
                "parkingSpace": {
                  "hasParkingSpace": true,
                  "isParkingSpaceIncludedInPrice": true
                },
                "features": { "hasAirConditioning": true, "hasBoxRoom": true }
              }
            ]
        """.trimIndent()

        val DETAIL_PAYLOAD = """
            {
              "adid": 1,
              "price": 1195000.0,
              "priceInfo": { "amount": 1195000.0, "currencySuffix": "€" },
              "operation": "sale",
              "propertyType": "homes",
              "extendedPropertyType": "flat",
              "homeType": "flat",
              "state": "active",
              "multimedia": {
                "images": [
                  {
                    "url": "https://img.test/1.webp",
                    "tag": "livingRoom",
                    "localizedName": "Salón",
                    "multimediaId": 1459427188
                  }
                ]
              },
              "propertyComment": "Venta.Piso EN EXCLUSIVA. Castellana.",
              "ubication": { "latitude": 40.4362687, "longitude": -3.6833686 },
              "country": "es",
              "moreCharacteristics": {
                "communityCosts": 330.0,
                "roomNumber": 3,
                "bathNumber": 2,
                "exterior": false,
                "housingFurnitures": "unknown",
                "agencyIsABank": false,
                "energyCertificationType": "e",
                "flatLocation": "internal",
                "modificationDate": 1727683968000,
                "constructedArea": 133,
                "lift": true,
                "boxroom": false,
                "isDuplex": false,
                "floor": "2",
                "status": "renew"
              },
              "energyCertification": {
                "title": "Certificado energético",
                "energyConsumption": { "type": "e" },
                "emissions": { "type": "e" }
              }
            }
        """.trimIndent()
    }
}
