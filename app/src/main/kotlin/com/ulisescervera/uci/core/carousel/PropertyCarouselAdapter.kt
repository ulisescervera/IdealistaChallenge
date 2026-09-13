package com.ulisescervera.uci.core.carousel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.image.DEFAULT_CROSSFADE_MILLIS
import com.ulisescervera.uci.core.image.loadPropertyImage
import com.ulisescervera.uci.core.map.UciMapConfigurator
import com.ulisescervera.uci.databinding.UciItemCarouselEmptyBinding
import com.ulisescervera.uci.databinding.UciItemCarouselImageBinding
import com.ulisescervera.uci.databinding.UciItemCarouselMapBinding

/**
 * Pages of a property carousel: photos, plus the map as the last page in the
 * detail.
 *
 * A `ListAdapter` with a real [DiffUtil.ItemCallback] rather than
 * `notifyDataSetChanged`, because the detail carousel is re-submitted every time
 * the network detail arrives with a longer image list (the list endpoint sends 7
 * photos, the detail endpoint sends 10). Diffing keeps the currently visible
 * page in place instead of snapping back to the first one mid-swipe.
 *
 * [onImageClick] receives the *adapter position*, which the caller maps back to
 * an index in the original image list -- the map page is not an image and must
 * not shift the indices the viewer opens with.
 */
internal class PropertyCarouselAdapter(
    private val formatter: PropertyFormatter,
    private val mapConfigurator: UciMapConfigurator,
    private val crossfadeMillis: Int = DEFAULT_CROSSFADE_MILLIS,
    private val onImageClick: ((position: Int) -> Unit)? = null,
    private val onMapClick: (() -> Unit)? = null,
    /** Called once the first page has real pixels; drives postponed transitions. */
    private val onFirstPageReady: (() -> Unit)? = null,
    /**
     * Names each photo page for the shared-element transition. Applied here, at
     * bind time, so the name exists as soon as the view does -- see
     * [com.ulisescervera.uci.core.image.sharedImageTransitionName].
     */
    private val transitionNameFor: ((imageIndex: Int) -> String?)? = null,
) : ListAdapter<CarouselPage, RecyclerView.ViewHolder>(DIFF) {

    /**
     * The photo count, excluding the map and the empty placeholder. Used for
     * "3 / 7" and for the accessibility labels, both of which must count photos
     * and not pages.
     */
    private val imageCount: Int get() = currentList.count { it is CarouselPage.Image }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CarouselPage.Image -> TYPE_IMAGE
        is CarouselPage.Map -> TYPE_MAP
        CarouselPage.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_IMAGE -> ImageViewHolder(UciItemCarouselImageBinding.inflate(inflater, parent, false))
            TYPE_MAP -> MapViewHolder(UciItemCarouselMapBinding.inflate(inflater, parent, false))
            else -> EmptyViewHolder(UciItemCarouselEmptyBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val page = getItem(position)) {
            is CarouselPage.Image -> (holder as ImageViewHolder).bind(page, position)
            is CarouselPage.Map -> (holder as MapViewHolder).bind(page)
            CarouselPage.Empty -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        // A MapView holds a tile-download thread and a tile cache; leaking one
        // per recycled page is how a carousel ends up with a dozen live maps.
        if (holder is MapViewHolder) holder.detach()
        super.onViewRecycled(holder)
    }

    private inner class ImageViewHolder(
        private val binding: UciItemCarouselImageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: CarouselPage.Image, position: Int) = with(binding.uciCarouselImage) {
            transitionName = transitionNameFor?.invoke(position)
            contentDescription = formatter.imageAccessibilityDescription(
                position = position,
                total = imageCount,
                localizedName = page.image.localizedName,
            )
            // Individually focusable: swiping through photos with TalkBack has to
            // be possible, and the card's merged description covers the metadata,
            // not the gallery.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true

            onImageClick?.let { click ->
                setOnClickListener { click(bindingAdapterPosition) }
                contentDescription = "$contentDescription. ${context.getString(R.string.uci_a11y_open_viewer)}"
            }

            loadPropertyImage(
                url = page.image.url,
                crossfadeMillis = crossfadeMillis,
                onReady = { if (position == 0) onFirstPageReady?.invoke() },
            )
        }
    }

    private inner class MapViewHolder(
        private val binding: UciItemCarouselMapBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: CarouselPage.Map) {
            mapConfigurator.configureStatic(binding.uciCarouselMap, page.location)
            binding.uciCarouselMapTouchShield.apply {
                contentDescription = page.label?.let {
                    binding.root.context.getString(R.string.uci_a11y_map, it)
                } ?: binding.root.context.getString(R.string.uci_a11y_carousel_map_page)
                setOnClickListener { onMapClick?.invoke() }
            }
        }

        fun detach() = mapConfigurator.release(binding.uciCarouselMap)
    }

    private class EmptyViewHolder(
        binding: UciItemCarouselEmptyBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_MAP = 1
        private const val TYPE_EMPTY = 2

        private val DIFF = object : DiffUtil.ItemCallback<CarouselPage>() {
            override fun areItemsTheSame(oldItem: CarouselPage, newItem: CarouselPage): Boolean =
                oldItem.stableId == newItem.stableId

            override fun areContentsTheSame(oldItem: CarouselPage, newItem: CarouselPage): Boolean =
                oldItem == newItem
        }
    }
}
