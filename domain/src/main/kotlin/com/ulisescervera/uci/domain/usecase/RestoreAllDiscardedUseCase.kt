package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject

/** "Restore all" in the discarded sheet. Reports how many came back. */
class RestoreAllDiscardedUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    suspend operator fun invoke(): UciResult<Int> = repository.restoreAllDiscarded()
}
