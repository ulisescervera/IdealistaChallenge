package com.ulisescervera.uci.feature.detail

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.format.UciErrorFormatter
import com.ulisescervera.uci.core.map.UciMapConfigurator
import com.ulisescervera.uci.core.share.PropertySharer
import com.ulisescervera.uci.core.ui.ViewBindingFragment
import com.ulisescervera.uci.core.ui.ext.collectWhileStarted
import com.ulisescervera.uci.core.ui.ext.isVisible
import com.ulisescervera.uci.databinding.UciFragmentPropertyDetailBinding
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Property
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The property detail.
 *
 * Three pieces of view-layer machinery live here and nowhere else:
 *
 * 1. **The shared-element transition.** The enter transition is postponed until
 *    the gallery's first photo has pixels ([onGalleryReady]); starting it
 *    earlier animates an empty `ImageView` into place and the photo pops in
 *    afterwards. A timeout guards against a photo that never loads -- a
 *    permanently postponed transition is a permanently blank screen.
 * 2. **"Reached the end" detection**, which triggers the related-properties
 *    load. A scroll listener rather than a sentinel row: the list is short
 *    enough that a `canScrollVertically` check on every scroll is cheaper than
 *    an extra view type, and it also fires when the content is shorter than the
 *    viewport (nothing to scroll, so the bottom is already reached).
 * 3. **Scroll-to-map**, for the list's "ver en el mapa" shortcut and the map
 *    deep link.
 */
@AndroidEntryPoint
class PropertyDetailFragment : ViewBindingFragment<UciFragmentPropertyDetailBinding>(
    UciFragmentPropertyDetailBinding::inflate,
) {

    // The navigation arguments are read by the ViewModel from its
    // SavedStateHandle, not here: that way they survive process death and the
    // fragment never has to pass them along.
    private val viewModel: PropertyDetailViewModel by viewModels()

    @Inject lateinit var propertyFormatter: PropertyFormatter

    @Inject lateinit var errorFormatter: UciErrorFormatter

    @Inject lateinit var mapConfigurator: UciMapConfigurator

    @Inject lateinit var rowsFactory: DetailRowsFactory

    @Inject lateinit var sharer: PropertySharer

    private var detailAdapter: PropertyDetailAdapter? = null
    private var hasStartedEnterTransition = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            // The card grows into the full-bleed gallery, so both ends need the
            // screen background or the transform shows a coloured seam.
            drawingViewId = R.id.uciDetailRoot
            duration = TRANSITION_DURATION_MILLIS
            isElevationShadowEnabled = false
        }
        postponeEnterTransition()
    }

    override fun onBindingCreated(binding: UciFragmentPropertyDetailBinding, savedInstanceState: Bundle?) {
        applyInsets()
        setUpToolbar()
        setUpList()
        observeState()
        observeEffects()
        viewModel.dispatch(PropertyDetailIntent.ScreenStarted)

        // Safety net: if the first photo never resolves (no network, dead URL),
        // release the transition anyway so the screen is not stuck invisible.
        binding.root.postDelayed(::startEnterTransitionOnce, TRANSITION_TIMEOUT_MILLIS)
    }

    private fun applyInsets() = with(binding) {
        // The toolbar pads its own top for the status bar via
        // `fitsSystemWindows`, so by the time it is laid out its height
        // already includes that inset -- exactly the top padding the list
        // needs to start right below it instead of underneath it.
        uciDetailToolbar.doOnLayout { toolbar -> uciDetailList.updatePadding(top = toolbar.height) }
        ViewCompat.setOnApplyWindowInsetsListener(uciDetailList) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun setUpToolbar() {
        binding.uciDetailToolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setUpList() {
        val adapter = PropertyDetailAdapter(
            formatter = propertyFormatter,
            mapConfigurator = mapConfigurator,
            callbacks = DetailCallbacks(),
        ).also { detailAdapter = it }

        binding.uciDetailList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            addOnScrollListener(EndOfListListener())
        }
    }

    private fun observeState() {
        viewModel.state.collectWhileStarted(viewLifecycleOwner) { state -> render(state) }
    }

    private fun render(state: PropertyDetailUiState) = with(binding) {
        uciDetailErrorState.isVisible(state.showsBlockingError)
        uciDetailList.isVisible(!state.showsBlockingError)

        if (state.showsBlockingError) {
            renderBlockingError(state)
            // Nothing will ever be drawn into the shared element, so let go.
            startEnterTransitionOnce()
            return@with
        }

        detailAdapter?.submitList(rowsFactory.build(state)) {
            if (state.isMapFocusPending) consumeMapFocus()
            // Content shorter than the viewport still counts as "reached the
            // end", so the related block loads without a scroll gesture.
            uciDetailList.post { notifyIfAtBottom() }
        }
    }

    /**
     * The single action button adapts to the failure.
     *
     * A `PropertyNotFound` is not retryable -- the property is gone from the feed
     * -- so offering "reintentar" would be a button that cannot work. It becomes
     * "volver al listado" instead, which is the only useful move left.
     */
    private fun renderBlockingError(state: PropertyDetailUiState) = with(binding) {
        val error = state.error
        uciDetailErrorBody.text = error?.let(errorFormatter::message)

        val isRetryable = error?.let(errorFormatter::isRetryable) ?: true
        uciDetailErrorAction.setText(
            if (isRetryable) R.string.uci_action_retry else R.string.uci_detail_back_to_list,
        )
        uciDetailErrorAction.setOnClickListener {
            if (isRetryable) {
                viewModel.dispatch(PropertyDetailIntent.RetryRequested)
            } else {
                findNavController().navigateUp()
            }
        }
    }

    /**
     * Honours the list's "ver en el mapa" shortcut: land on the map row instead
     * of the top of the screen. The intent is dispatched back so a rotation
     * does not scroll again.
     */
    private fun consumeMapFocus() {
        val index = detailAdapter?.currentList?.indexOfFirst { it is DetailRow.Map } ?: -1
        if (index >= 0) {
            (binding.uciDetailList.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(index, 0)
        }
        viewModel.dispatch(PropertyDetailIntent.MapFocusConsumed)
    }

    private fun scrollToMap() {
        val index = detailAdapter?.currentList?.indexOfFirst { it is DetailRow.Map } ?: return
        if (index >= 0) binding.uciDetailList.smoothScrollToPosition(index)
    }

    private fun observeEffects() {
        viewModel.effects.collectWhileStarted(viewLifecycleOwner) { effect ->
            when (effect) {
                is PropertyDetailEffect.OpenImageViewer -> findNavController().navigate(
                    PropertyDetailFragmentDirections.uciActionDetailToImageViewer(
                        propertyId = effect.propertyId,
                        initialImageIndex = effect.imageIndex,
                    ),
                )

                PropertyDetailEffect.ScrollToMap -> scrollToMap()

                is PropertyDetailEffect.OpenFullMap -> findNavController().navigate(
                    PropertyDetailFragmentDirections.uciActionDetailToFullMap(
                        latitude = effect.location.latitude.toFloat(),
                        longitude = effect.location.longitude.toFloat(),
                        addressLine = effect.addressLine,
                    ),
                )

                is PropertyDetailEffect.ShareProperty -> share(effect.property)

                is PropertyDetailEffect.OpenRelatedProperty -> findNavController().navigate(
                    PropertyDetailFragmentDirections.uciActionDetailToDetail(propertyId = effect.propertyId),
                )

                is PropertyDetailEffect.ShowError -> Snackbar
                    .make(binding.root, errorFormatter.message(effect.error), Snackbar.LENGTH_LONG)
                    .show()

                PropertyDetailEffect.CloseAfterDiscard -> findNavController().navigateUp()
            }
        }
    }

    private fun share(property: Property) {
        startActivity(sharer.intentFor(requireContext(), property))
    }

    /**
     * Starts the postponed transition exactly once, whether the trigger is the
     * first photo loading, an error state, or the timeout.
     */
    private fun startEnterTransitionOnce() {
        if (hasStartedEnterTransition) return
        hasStartedEnterTransition = true
        startPostponedEnterTransition()
    }

    private fun notifyIfAtBottom() {
        val list = binding.uciDetailList
        if (!list.canScrollVertically(SCROLL_DOWN)) {
            viewModel.dispatch(PropertyDetailIntent.ReachedEnd)
        }
    }

    override fun onBindingDestroyed(binding: UciFragmentPropertyDetailBinding) {
        binding.uciDetailList.adapter = null
        detailAdapter = null
    }

    private inner class EndOfListListener : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            if (!recyclerView.canScrollVertically(SCROLL_DOWN)) {
                // The ViewModel guards against repeats; this listener does not
                // need to remember whether it already fired.
                viewModel.dispatch(PropertyDetailIntent.ReachedEnd)
            }
        }
    }

    private inner class DetailCallbacks : PropertyDetailAdapter.Callbacks {
        override fun onImageClicked(imageIndex: Int) =
            viewModel.dispatch(PropertyDetailIntent.ImageClicked(imageIndex))

        override fun onMapPageClicked() = viewModel.dispatch(PropertyDetailIntent.MapPageClicked)

        override fun onMapClicked(location: GeoPoint, addressLine: String?) =
            viewModel.dispatch(PropertyDetailIntent.MapClicked(location, addressLine))

        override fun onFavouriteClicked() = viewModel.dispatch(PropertyDetailIntent.FavouriteToggled)

        override fun onDiscardClicked() = viewModel.dispatch(PropertyDetailIntent.DiscardToggled)

        override fun onShareClicked() = viewModel.dispatch(PropertyDetailIntent.ShareClicked)

        override fun onCommentToggleClicked() =
            viewModel.dispatch(PropertyDetailIntent.CommentExpansionToggled)

        override fun onRelatedPropertyClicked(property: Property) =
            viewModel.dispatch(PropertyDetailIntent.RelatedPropertyClicked(property.id))

        override fun onGalleryReady() = startEnterTransitionOnce()
    }

    private companion object {
        const val SCROLL_DOWN = 1
        const val TRANSITION_DURATION_MILLIS = 300L

        /** Longer than a reasonable image load, shorter than the user's patience. */
        const val TRANSITION_TIMEOUT_MILLIS = 700L
    }
}
