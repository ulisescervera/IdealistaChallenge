package com.ulisescervera.uci.feature.list

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.ImageView
import androidx.core.view.MenuProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.Hold
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.format.UciErrorFormatter
import com.ulisescervera.uci.core.map.UciMapConfigurator
import com.ulisescervera.uci.core.ui.ViewBindingFragment
import com.ulisescervera.uci.core.ui.ext.collectWhileStarted
import com.ulisescervera.uci.core.ui.ext.isVisible
import com.ulisescervera.uci.databinding.UciFragmentPropertyListBinding
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.feature.list.filter.FiltersSheetFragment
import com.ulisescervera.uci.feature.list.filter.PropertyFilters
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * First tab: the property list.
 *
 * The fragment is deliberately dumb. It does three things and nothing else:
 * translate touches into [PropertyListIntent]s, render a [PropertyListUiState],
 * and execute a [PropertyListEffect]. There is no `if (list.isEmpty())` decision
 * here -- `state.surface` already decided, in a place that can be unit-tested
 * without a view.
 */
@AndroidEntryPoint
class PropertyListFragment : ViewBindingFragment<UciFragmentPropertyListBinding>(
    UciFragmentPropertyListBinding::inflate,
) {

    // Activity-scoped, not fragment-scoped: `PropertyMapFragment` reads the
    // same `visibleProperties` to draw its markers, and an activity-scoped
    // delegate is the only way two destinations get the same instance
    // without a nav-graph-scoped Hilt ViewModel dependency this repo does not
    // otherwise need.
    private val viewModel: PropertyListViewModel by activityViewModels()

    @Inject lateinit var propertyFormatter: PropertyFormatter

    @Inject lateinit var errorFormatter: UciErrorFormatter

    @Inject lateinit var mapConfigurator: UciMapConfigurator

    private var listAdapter: PropertyListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // `Hold` keeps this fragment drawn while the detail's shared-element
        // transition runs. Without it the list is removed immediately and the
        // photo appears to fly out of a blank screen.
        exitTransition = Hold()
    }

    override fun onBindingCreated(binding: UciFragmentPropertyListBinding, savedInstanceState: Bundle?) {
        setUpList()
        setUpRefresh()
        setUpMenu()
        setUpFiltersResult()
        observeState()
        observeEffects()
        viewModel.dispatch(PropertyListIntent.ScreenStarted)
    }

    /**
     * The map and filter shortcuts in the shared toolbar. A `MenuProvider`
     * rather than the Activity inflating it directly, so the one screen that
     * has it is the one screen that declares it -- `MainActivity` does not
     * need to know the list has a menu at all, only that a toolbar exists for
     * fragments to attach to.
     *
     * `Lifecycle.State.RESUMED` (not `viewLifecycleOwner`'s default) is what
     * keeps this menu from showing while the list is stopped but still on the
     * back stack, e.g. behind the detail screen.
     */
    private fun setUpMenu() {
        requireActivity().addMenuProvider(ListMenuProvider(), viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    /**
     * The filter sheet has no shared ViewModel with this fragment -- see its
     * class doc -- so the applied filters travel back through the Fragment
     * Result API instead. Both fragments are pushed onto the same
     * `NavHostFragment`, so `parentFragmentManager` is the FragmentManager they
     * share.
     */
    private fun setUpFiltersResult() {
        parentFragmentManager.setFragmentResultListener(
            FiltersSheetFragment.FILTERS_REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            viewModel.dispatch(PropertyListIntent.FiltersApplied(PropertyFilters.fromBundle(bundle)))
        }
    }

    private fun setUpList() {
        val adapter = PropertyListAdapter(
            formatter = propertyFormatter,
            mapConfigurator = mapConfigurator,
            callbacks = ListCallbacks(),
        ).also { listAdapter = it }

        // Without this, RecyclerView restores the scroll position before the
        // first submitList and lands the user at the top after a rotation.
        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        binding.uciPropertyList.apply {
            this.adapter = adapter
            setHasFixedSize(false)
            // Each row owns a ViewPager2 whose pages are ImageViews; the default
            // 5-view cache would keep several pagers' worth of bitmaps alive.
            setItemViewCacheSize(ITEM_VIEW_CACHE_SIZE)
        }
    }

    private fun setUpRefresh() {
        binding.uciListSwipeRefresh.setOnRefreshListener {
            viewModel.dispatch(PropertyListIntent.RefreshRequested)
        }
    }

    private fun observeState() {
        viewModel.state.collectWhileStarted(viewLifecycleOwner) { state -> render(state) }
    }

    private fun render(state: PropertyListUiState) = with(binding) {
        listAdapter?.submitList(state.visibleProperties)

        uciListSwipeRefresh.isRefreshing = state.isRefreshing

        val surface = state.surface
        uciListSkeleton.isVisible(surface == PropertyListUiState.Surface.Skeleton)
        uciListSwipeRefresh.isVisible(surface == PropertyListUiState.Surface.Content)
        uciListPlaceholder.isVisible(
            surface in setOf(
                PropertyListUiState.Surface.Empty,
                PropertyListUiState.Surface.FilteredEmpty,
                PropertyListUiState.Surface.AllDiscarded,
                PropertyListUiState.Surface.Error,
            ),
        )

        renderPlaceholder(surface, state)
        renderUndoBanner(state)
    }

    private fun renderPlaceholder(
        surface: PropertyListUiState.Surface,
        state: PropertyListUiState,
    ) = with(binding) {
        when (surface) {
            PropertyListUiState.Surface.Empty -> {
                uciPlaceholderIcon.setImageResource(R.drawable.ic_uci_photo)
                uciPlaceholderTitle.setText(R.string.uci_list_empty_title)
                uciPlaceholderBody.setText(R.string.uci_list_empty_body)
                uciPlaceholderAction.isVisible(false)
            }

            PropertyListUiState.Surface.FilteredEmpty -> {
                uciPlaceholderIcon.setImageResource(R.drawable.ic_uci_filter)
                uciPlaceholderTitle.setText(R.string.uci_list_filtered_empty_title)
                uciPlaceholderBody.setText(R.string.uci_list_filtered_empty_body)
                uciPlaceholderAction.setText(R.string.uci_action_clear_filters)
                uciPlaceholderAction.setOnClickListener {
                    viewModel.dispatch(PropertyListIntent.FiltersApplied(PropertyFilters()))
                }
                uciPlaceholderAction.isVisible(true)
            }

            PropertyListUiState.Surface.AllDiscarded -> {
                uciPlaceholderIcon.setImageResource(R.drawable.ic_uci_restore)
                uciPlaceholderTitle.setText(R.string.uci_list_all_discarded_title)
                uciPlaceholderBody.setText(R.string.uci_list_all_discarded_body)
                uciPlaceholderAction.isVisible(false)
            }

            PropertyListUiState.Surface.Error -> {
                val error = state.error
                uciPlaceholderIcon.setImageResource(R.drawable.ic_uci_error)
                uciPlaceholderTitle.setText(R.string.uci_list_error_title)
                uciPlaceholderBody.text = error?.let(errorFormatter::message)
                uciPlaceholderAction.setText(R.string.uci_action_retry)
                uciPlaceholderAction.setOnClickListener {
                    viewModel.dispatch(PropertyListIntent.RetryRequested)
                }
                uciPlaceholderAction.isVisible(error?.let(errorFormatter::isRetryable) ?: true)
            }

            PropertyListUiState.Surface.Skeleton, PropertyListUiState.Surface.Content -> Unit
        }
    }

    /**
     * The undo banner.
     *
     * One discarded item -> "Has borrado un inmueble" + "Restaurar".
     * Several            -> "Has borrado N inmuebles" + "Ver borrados".
     *
     * The message is announced explicitly: a banner that appears silently at the
     * top of a scroll is invisible to a screen-reader user who is halfway down
     * the list, and "undo" that cannot be discovered is not undo.
     */
    private fun renderUndoBanner(state: PropertyListUiState) = with(binding) {
        val wasVisible = uciUndoBanner.isShown
        uciUndoBanner.isVisible(state.showsUndoBanner)
        if (!state.showsUndoBanner) return@with

        val count = state.discardedCount
        val message = when (count) {
            1 -> getString(R.string.uci_undo_single)
            else -> resources.getQuantityString(R.plurals.uci_undo_multiple, count, count)
        }
        uciUndoMessage.text = message

        uciUndoAction.setText(
            if (count == 1) R.string.uci_undo_action_single else R.string.uci_undo_action_multiple,
        )
        uciUndoAction.setOnClickListener {
            viewModel.dispatch(
                if (count == 1) {
                    PropertyListIntent.UndoLastDiscardClicked
                } else {
                    PropertyListIntent.ShowDiscardedClicked
                },
            )
        }

        if (!wasVisible) uciUndoBanner.announceForAccessibility(message)
    }

    private fun observeEffects() {
        viewModel.effects.collectWhileStarted(viewLifecycleOwner) { effect ->
            when (effect) {
                is PropertyListEffect.OpenDetail -> navigateToDetail(effect, sharedImage = null)
                PropertyListEffect.OpenDiscardedSheet -> findNavController()
                    .navigate(R.id.uci_action_list_to_discarded)

                is PropertyListEffect.OpenFiltersSheet -> findNavController()
                    .navigate(R.id.uci_destination_filters_sheet, effect.filters.toBundle())

                PropertyListEffect.OpenMap -> findNavController().navigate(R.id.uci_action_list_to_map)

                is PropertyListEffect.ShowError -> Snackbar
                    .make(binding.root, errorFormatter.message(effect.error), Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.uciListSwipeRefresh)
                    .show()
            }
        }
    }

    /**
     * @param sharedImage when non-null, the photo animates into the detail's
     *   carousel. Effects arrive asynchronously and the view may already be
     *   recycled by then, so the navigation from a *click* passes the view
     *   directly instead of going through an effect.
     */
    private fun navigateToDetail(effect: PropertyListEffect.OpenDetail, sharedImage: ImageView?) {
        val directions = PropertyListFragmentDirections.uciActionListToDetail(
            propertyId = effect.propertyId,
            focusOnMap = effect.focusOnMap,
            initialImageIndex = effect.imageIndex,
        )
        val extras = sharedImage
            ?.transitionName
            ?.let { name -> FragmentNavigatorExtras(sharedImage to name) }

        if (extras != null) {
            findNavController().navigate(directions, extras)
        } else {
            findNavController().navigate(directions)
        }
    }

    override fun onBindingDestroyed(binding: UciFragmentPropertyListBinding) {
        // Breaks the adapter -> RecyclerView -> destroyed view chain; the
        // fragment itself lives on in the back stack.
        binding.uciPropertyList.adapter = null
        listAdapter = null
    }

    /**
     * Clicks that need a `View` reference go straight to navigation instead of
     * round-tripping through an effect: by the time an effect is collected the
     * `ImageView` may have been recycled, and a shared-element transition with a
     * detached view silently does nothing.
     *
     * Everything without a view payload goes through the ViewModel as an intent.
     */
    private inner class ListCallbacks : PropertyListAdapter.Callbacks {

        override fun onPropertyClicked(property: Property, imageIndex: Int, sharedImage: ImageView?) {
            navigateToDetail(
                effect = PropertyListEffect.OpenDetail(
                    propertyId = property.id,
                    imageIndex = imageIndex,
                    focusOnMap = false,
                ),
                sharedImage = sharedImage,
            )
        }

        override fun onFavouriteClicked(property: Property) {
            viewModel.dispatch(PropertyListIntent.FavouriteToggled(property.id))
        }

        override fun onDiscardClicked(property: Property) {
            viewModel.dispatch(PropertyListIntent.DiscardClicked(property.id))
        }
    }

    /**
     * Both icons dispatch: an `inner class` (not a top-level, stateless
     * provider) so it can reach [viewModel].
     */
    private inner class ListMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.uci_menu_property_list, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
            R.id.uci_menu_item_map -> {
                viewModel.dispatch(PropertyListIntent.MapButtonClicked)
                true
            }

            R.id.uci_menu_item_filter -> {
                viewModel.dispatch(PropertyListIntent.FilterButtonClicked)
                true
            }

            else -> false
        }
    }

    private companion object {
        const val ITEM_VIEW_CACHE_SIZE = 2
    }
}
