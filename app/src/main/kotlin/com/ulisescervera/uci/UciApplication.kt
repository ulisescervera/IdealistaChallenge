package com.ulisescervera.uci

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import org.osmdroid.config.Configuration

/**
 * Application entry point and the Hilt root.
 *
 * The only work done here is what genuinely cannot be deferred:
 *
 * - **osmdroid configuration.** Left to its defaults, osmdroid writes its tile
 *   cache to shared external storage and needs `WRITE_EXTERNAL_STORAGE`.
 *   Pointing it at the app's private cache directory removes a permission from
 *   the manifest entirely, and satisfies scoped storage on modern Android. It
 *   also sets a real User-Agent, which the OSM tile servers require -- the
 *   default `android` string gets rate-limited and the map silently stays grey.
 * - **Following the system light/dark setting.** Explicit rather than implied,
 *   so `AppCompatDelegate` cannot be left at a stale value by a library.
 *
 * Nothing else. Work in `Application.onCreate` is on the critical path of every
 * cold start, including the one triggered by a deep link.
 */
@HiltAndroidApp
class UciApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        configureTheme()
        configureMaps()
    }

    private fun configureTheme() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun configureMaps() {
        Configuration.getInstance().apply {
            // Required by the OSM tile usage policy; an app-specific agent also
            // makes our traffic attributable if it ever misbehaves.
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(cacheDir, OSMDROID_DIRECTORY).apply { mkdirs() }
            osmdroidTileCache = File(osmdroidBasePath, TILE_CACHE_DIRECTORY).apply { mkdirs() }
        }
    }

    private companion object {
        const val OSMDROID_DIRECTORY = "osmdroid"
        const val TILE_CACHE_DIRECTORY = "tiles"
    }
}
