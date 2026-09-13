package com.ulisescervera.uci.data.di

import com.ulisescervera.uci.domain.common.DispatcherProvider
import com.ulisescervera.uci.domain.common.UciClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * Ambient concerns -- threads and time -- turned into injectable values.
 *
 * These two are the classic sources of flaky tests. Once they are constructor
 * parameters, a repository test runs on a single test dispatcher with a frozen
 * clock and asserts exact timestamps.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    fun dispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider

    @Provides
    @Singleton
    fun clock(): UciClock = SystemUciClock
}

internal object DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineContext get() = Dispatchers.Main.immediate
    override val default: CoroutineContext get() = Dispatchers.Default
    override val io: CoroutineContext get() = Dispatchers.IO
}

/**
 * Reads the zone on every call rather than caching it: the user can cross a
 * border between two favourites, and the second one must record the new zone.
 */
internal object SystemUciClock : UciClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
