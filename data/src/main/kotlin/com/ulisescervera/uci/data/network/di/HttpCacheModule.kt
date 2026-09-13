package com.ulisescervera.uci.data.network.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import okhttp3.Cache

/**
 * HTTP cache, kept in its own module because it is the only part of the network
 * stack that needs a [Context] -- which keeps [NetworkModule] Android-free and
 * therefore unit-testable without Robolectric.
 *
 * This is a *transport* cache (conditional GETs, 304s). It is not the offline
 * story: that is Room. The two are complementary -- OkHttp saves bandwidth,
 * Room makes the app work with the radio off.
 */
@Module
@InstallIn(SingletonComponent::class)
object HttpCacheModule {

    @Provides
    @Singleton
    fun httpCache(@ApplicationContext context: Context): Cache =
        Cache(directory = File(context.cacheDir, CACHE_DIRECTORY), maxSize = CACHE_SIZE_BYTES)

    private const val CACHE_DIRECTORY = "uci_http_cache"
    private const val CACHE_SIZE_BYTES = 10L * 1024L * 1024L
}
