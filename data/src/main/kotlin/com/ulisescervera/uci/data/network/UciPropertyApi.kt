package com.ulisescervera.uci.data.network

import com.ulisescervera.uci.data.network.dto.PropertyDetailDto
import com.ulisescervera.uci.data.network.dto.PropertyListItemDto
import retrofit2.http.GET

/**
 * The two static endpoints of the challenge.
 *
 * [propertyDetail] takes no id because the fixture does not accept one: it always
 * answers with `adid = 1`. The signature keeps a [propertyId]-shaped call site
 * out of here on purpose -- re-associating the response with the requested id is
 * the mapper's job, not a lie told by the API interface.
 */
interface UciPropertyApi {

    @GET("list.json")
    suspend fun properties(): List<PropertyListItemDto>

    @GET("detail.json")
    suspend fun propertyDetail(): PropertyDetailDto
}
