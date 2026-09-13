package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Backs the "discarded items" sheet reachable from the undo banner. */
class ObserveDiscardedPropertiesUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    operator fun invoke(): Flow<List<DiscardedProperty>> = repository.observeDiscardedProperties()
}
