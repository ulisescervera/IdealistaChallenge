package com.ulisescervera.uci.data.related

/**
 * The "more like this" endpoint the challenge does not provide.
 *
 * It is declared as an interface, and shaped exactly like a real recommendation
 * endpoint would be, so that replacing [FakeRelatedPropertiesService] with a
 * Retrofit implementation later is a one-line Hilt change and nothing else:
 *
 * ```
 * GET /related?propertyCode=1&limit=10   ->   { "propertyCodes": ["2", "3", "4"] }
 * ```
 *
 * Returning **ids** rather than full properties is the realistic contract: a
 * recommender ranks, it does not re-serialise the catalogue. The repository
 * hydrates the ids from the cache, which also means related items arrive with
 * their favourite/discarded state already correct.
 */
interface RelatedPropertiesService {

    /**
     * Ids of other properties related to [propertyId].
     *
     * Implementations must never include [propertyId] itself. `GetRelatedPropertiesUseCase`
     * filters it again anyway -- belt and braces, because a future real endpoint
     * is outside our control.
     */
    suspend fun relatedPropertyIds(propertyId: String, limit: Int): List<String>
}
