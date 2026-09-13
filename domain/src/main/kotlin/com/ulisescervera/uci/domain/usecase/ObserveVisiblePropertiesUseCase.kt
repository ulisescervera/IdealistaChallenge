package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The list screen's data source: every cached property the user has not
 * discarded. Filtering happens in SQL, not here, so a 10k-row cache does not
 * become a 10k-item allocation on every flag change.
 */
class ObserveVisiblePropertiesUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    operator fun invoke(): Flow<List<Property>> = repository.observeVisibleProperties()
}
