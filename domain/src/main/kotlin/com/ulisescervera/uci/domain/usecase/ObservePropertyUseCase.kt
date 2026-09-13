package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The cached summary of a single property.
 *
 * This is what the detail screen renders *before* the network answers, which is
 * why it is a separate use case from [ObservePropertyDetailUseCase] rather than
 * a nullable field on it.
 */
class ObservePropertyUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    operator fun invoke(propertyId: String): Flow<Property?> = repository.observeProperty(propertyId)
}
