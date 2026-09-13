package com.ulisescervera.uci.core.carousel

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.image.DEFAULT_CROSSFADE_MILLIS
import com.ulisescervera.uci.core.map.UciMapConfigurator
import com.ulisescervera.uci.core.ui.ext.isVisible
import com.ulisescervera.uci.databinding.UciViewCarouselBinding

/**
 * The horizontal image carousel, used by the list row, the detail header and
 * the related-properties row.
 *
 * ### Why a custom view and not an `<include>`
 * Three screens need the same pager + counter arrangement, with the same
 * accessibility wiring. An `<include>` of a `<merge>` would share the
 * XML but leave every caller to re-wire the page-change listener and the
 * counter; a custom view shares the behaviour too, and gives the ViewHolder a
 * one-call [bind] API.
 *
 * ### Dependencies are passed in, not injected
 * Hilt cannot inject a view inflated from XML without `@AndroidEntryPoint`
 * plumbing on the host and a fragment-scoped component. The callers -- adapters
 * and fragments -- already hold [PropertyFormatter] and [UciMapConfigurator],
 * so handing them over in [bind] is both simpler and easier to test: a unit test
 * constructs the view with fakes and no DI graph at all.
 *
 * ### The counter counts photos, not pages
 * The map is a page but not a photo. `"3 / 7"` on a property with 7 photos and a
 * map must not read `"3 / 8"`, and the counter hides entirely on the map page,
 * which carries its own label.
 */
class PropertyCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = UciViewCarouselBinding.inflate(LayoutInflater.from(context), this)

    private var carouselAdapter: PropertyCarouselAdapter? = null

    // Callbacks are fields, not adapter constructor arguments, so the adapter
    // survives a re-bind: recreating it would snap a half-swiped carousel back
    // to page 0 every time the detail's network payload arrives.
    private var onImageClick: ((imageIndex: Int) -> Unit)? = null
    private var onMapPageClick: (() -> Unit)? = null
    private var onFirstPageReady: (() -> Unit)? = null
    private var transitionNameFor: ((imageIndex: Int) -> String?)? = null

    private var pages: List<CarouselPage> = emptyList()

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) = updateIndicator(position)
    }

    init {
        // ViewPager2 saves its state through the view hierarchy by id. Inside a
        // RecyclerView every row shares that id, and the framework throws
        // "Page can only be offset by a positive amount" on state restore.
        // Opting out of parent-driven save is the documented workaround; the
        // visible page of a recycled row is not state worth keeping anyway.
        binding.uciImageCarousel.isSaveEnabled = false
    }

    /**
     * @param initialIndex page to show when [pages] is genuinely new content.
     *   Ignored when the same content is re-emitted (e.g. the detail's network
     *   refresh re-submitting an unchanged list) so an in-flight swipe is not
     *   interrupted.
     * @param onImageClick receives the index within the *image* list, with the
     *   map page already discounted.
     * @param onFirstPageReady fires when page 0 has real pixels -- the signal a
     *   postponed shared-element transition waits for.
     */
    @Suppress("LongParameterList")
    fun bind(
        pages: List<CarouselPage>,
        formatter: PropertyFormatter,
        mapConfigurator: UciMapConfigurator,
        initialIndex: Int = 0,
        crossfadeMillis: Int = DEFAULT_CROSSFADE_MILLIS,
        onImageClick: ((imageIndex: Int) -> Unit)? = null,
        onMapPageClick: (() -> Unit)? = null,
        onFirstPageReady: (() -> Unit)? = null,
        transitionNameFor: ((imageIndex: Int) -> String?)? = null,
    ) {
        // Captured before `this.pages` is overwritten below. A recycled
        // RecyclerView row reuses this same View instance for a different
        // Property, so `carouselAdapter == null` (the previous signal for
        // "first bind") is only true the very first time the row is ever
        // bound -- on every later reuse it stays non-null and the indicator
        // silently kept whatever page the *previous* property was showing.
        // Comparing content is the only signal that tells "new property" and
        // "same property re-emitted" apart, and CarouselPage's structural
        // equality (see CarouselPage.kt) makes the comparison meaningful.
        val isNewContent = pages != this.pages
        this.pages = pages
        this.onImageClick = onImageClick
        this.onMapPageClick = onMapPageClick
        this.onFirstPageReady = onFirstPageReady
        this.transitionNameFor = transitionNameFor

        val adapter = carouselAdapter ?: PropertyCarouselAdapter(
            formatter = formatter,
            mapConfigurator = mapConfigurator,
            crossfadeMillis = crossfadeMillis,
            onImageClick = { position -> dispatchPageClick(position) },
            onMapClick = { this.onMapPageClick?.invoke() },
            onFirstPageReady = { this.onFirstPageReady?.invoke() },
            transitionNameFor = { index -> this.transitionNameFor?.invoke(index) },
        ).also {
            carouselAdapter = it
            binding.uciImageCarousel.adapter = it
        }

        adapter.submitList(pages) {
            if (isNewContent && initialIndex in pages.indices) {
                binding.uciImageCarousel.setCurrentItem(initialIndex, /* smoothScroll = */ false)
            }
            updateIndicator(binding.uciImageCarousel.currentItem)
        }

        binding.uciCarouselScrim.isVisible(pages.any { it is CarouselPage.Image })

        contentDescription = context.getString(R.string.uci_a11y_carousel, pages.imageCount())
    }

    /**
     * A tap on a page becomes an *image* index. Clicking page 8 of a carousel
     * whose page 8 is the map opens the map, not "image 8" -- which would crash
     * the viewer with an out-of-bounds index.
     */
    private fun dispatchPageClick(pagePosition: Int) {
        when (pages.getOrNull(pagePosition)) {
            is CarouselPage.Image -> onImageClick?.invoke(pagePosition)
            is CarouselPage.Map -> onMapPageClick?.invoke()
            CarouselPage.Empty, null -> Unit
        }
    }

    private fun updateIndicator(position: Int) {
        val total = pages.imageCount()
        val isImagePage = pages.getOrNull(position) is CarouselPage.Image
        binding.uciCarouselIndicator.isVisible(isImagePage && total > 1)
        if (isImagePage && total > 1) {
            binding.uciCarouselIndicator.text =
                context.getString(R.string.uci_viewer_page_indicator, position + 1, total)
            // TalkBack does not re-read a page it has already visited, so the
            // position change is announced explicitly.
            announceForAccessibility(
                context.getString(R.string.uci_a11y_page_indicator, position + 1, total),
            )
        }
    }

    /** Index of the page currently on screen. */
    val currentPageIndex: Int get() = binding.uciImageCarousel.currentItem

    /**
     * The `ImageView` of the visible page, or `null` when the map page is
     * showing. This is the shared element handed to the detail transition.
     *
     * Safe to call from a click listener -- by then the pager has laid out. It is
     * *not* safe to call right after [bind], which is why the transition name is
     * assigned inside the adapter instead of here.
     */
    fun currentImageView(): ImageView? {
        val recyclerView = binding.uciImageCarousel.getChildAt(0) as? RecyclerView
        val holder = recyclerView?.findViewHolderForAdapterPosition(currentPageIndex)
        return holder?.itemView as? ImageView
    }

    /**
     * Registered here, not in `init`, and symmetrically unregistered in
     * [onDetachedFromWindow] -- this is the fix for the numeric indicator
     * going stale after a row scrolls off-screen and back. A recycled
     * RecyclerView row is detached from the window when it goes to the
     * recycled pool and reattached when reused for a (possibly different)
     * position; registering only once, in the constructor, meant the very
     * first detach unregistered the listener for good, and every rebind after
     * that swiped through pages with no callback left to update the counter.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.uciImageCarousel.registerOnPageChangeCallback(pageChangeCallback)
    }

    override fun onDetachedFromWindow() {
        binding.uciImageCarousel.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDetachedFromWindow()
    }

    /** Detaches adapters so recycled maps stop downloading tiles. */
    fun release() {
        binding.uciImageCarousel.adapter = null
        carouselAdapter = null
        onImageClick = null
        onMapPageClick = null
        onFirstPageReady = null
        transitionNameFor = null
    }
}

internal fun List<CarouselPage>.imageCount(): Int = count { it is CarouselPage.Image }

/** True while the given page index still points at a photo. */
internal fun List<CarouselPage>.isImagePage(index: Int): Boolean = getOrNull(index) is CarouselPage.Image
