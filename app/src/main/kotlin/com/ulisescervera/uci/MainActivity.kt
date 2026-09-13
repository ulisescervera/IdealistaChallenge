package com.ulisescervera.uci

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.ulisescervera.uci.databinding.UciActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity.
 *
 * Its entire job is chrome: host the navigation graph, own the one shared
 * toolbar, keep the bottom bar in sync with it, and hide both bars on
 * destinations that own the full screen. Every piece of business logic lives
 * in a fragment's ViewModel; this class has no reference to a repository or a
 * use case, which is what keeps "single activity" from turning into "god
 * activity".
 *
 * ### Why the detail screen is not wired into this toolbar
 * [uciToolbar] is hidden while `PropertyDetailFragment` is the current
 * destination, which keeps its *own* transparent `MaterialToolbar` floating
 * over its full-bleed gallery instead. Full reasoning, and why the image
 * viewer is excluded for a different reason, in
 * `docs/adr/0003-un-toolbar-compartido-salvo-en-detalle.md`.
 *
 * ### Deep links
 * `NavHostFragment` consumes the launch intent automatically because
 * `app:defaultNavHost="true"` is set and the `<nav-graph>` tag generated the
 * matching intent filters. With `launchMode="singleTask"`, a second deep link
 * arriving while the app is alive comes through [onNewIntent] and is handed to
 * the same controller, so the user does not end up with two task stacks.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: UciActivityMainBinding

    private lateinit var appBarConfiguration: AppBarConfiguration

    private val navController: NavController
        get() = (supportFragmentManager.findFragmentById(R.id.uciNavHostFragment) as NavHostFragment)
            .navController

    override fun onCreate(savedInstanceState: Bundle?) {
        // The launcher theme exists only to paint the first frame in the right
        // colour; swap to the real theme before any view is inflated.
        setTheme(R.style.Theme_Uci)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = UciActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        setUpToolbar()
        setUpBottomNavigation()
    }

    /**
     * `onSupportNavigateUp` is what the toolbar's auto-added Up arrow calls;
     * without overriding it, tapping that arrow on a non-top-level destination
     * would do nothing. Detail and the viewer do not show [uciToolbar] at all
     * (see class doc), so in practice this only matters if a future
     * destination is added without its own back affordance.
     */
    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    /**
     * The toolbar consumes the status-bar inset and the bottom bar the
     * navigation-bar inset; the nav host consumes neither. Both bars are real
     * siblings the nav host is constrained around, not overlays, so nothing
     * here needs to know which fragment is currently showing.
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.uciToolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.uciBottomNavigation) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun setUpToolbar() {
        setSupportActionBar(binding.uciToolbar)
        // Top-level: no Up arrow, title only. Everywhere else NavigationUI adds
        // one automatically and wires it to onSupportNavigateUp.
        appBarConfiguration = AppBarConfiguration.Builder(TOP_LEVEL_DESTINATIONS).build()
        binding.uciToolbar.setupWithNavController(navController, appBarConfiguration)
    }

    private fun setUpBottomNavigation() {
        binding.uciBottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showsBottomBar = destination.id in TOP_LEVEL_DESTINATIONS
            binding.uciBottomNavigation.visibility = if (showsBottomBar) View.VISIBLE else View.GONE

            val showsToolbar = destination.id in DESTINATIONS_WITH_TOOLBAR
            binding.uciToolbar.visibility = if (showsToolbar) View.VISIBLE else View.GONE
        }
    }

    private companion object {
        /**
         * The two tabs. Anything else -- detail, image viewer -- is a leaf that
         * takes over the screen. Kept as a set rather than a `when` so adding a
         * tab means touching the menu, the graph and this line, all of which
         * fail loudly if they disagree.
         */
        val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.uci_destination_property_list,
            R.id.uci_destination_favourites,
        )

        /**
         * Same two destinations, for a different reason: these are the only
         * screens without their own chrome. Detail brings its own transparent
         * toolbar (see class doc) and the image viewer is a chromeless,
         * immersive gallery by design -- neither wants this bar layered on top.
         */
        val DESTINATIONS_WITH_TOOLBAR = TOP_LEVEL_DESTINATIONS
    }
}
