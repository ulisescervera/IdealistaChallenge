package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The cached, fully-populated detail. `null` means "not fetched yet". */
class ObservePropertyDetailUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    operator fun invoke(propertyId: String): Flow<PropertyDetail?> = repository.observePropertyDetail(propertyId)
}
