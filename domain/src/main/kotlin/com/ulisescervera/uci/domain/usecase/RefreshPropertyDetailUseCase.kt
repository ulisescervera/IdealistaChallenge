package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject

/** Pulls `detail.json` for one property and caches it. */
class RefreshPropertyDetailUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    suspend operator fun invoke(propertyId: String): UciResult<PropertyDetail> =
        repository.refreshPropertyDetail(propertyId)
}
