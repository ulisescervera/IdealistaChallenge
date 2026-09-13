package com.ulisescervera.uci.feature.list

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.carousel.buildCarouselPages
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.image.sharedImageTransitionName
import com.ulisescervera.uci.core.map.UciMapConfigurator
import com.ulisescervera.uci.core.ui.ext.isVisible
import com.ulisescervera.uci.core.ui.ext.mergeChildrenForAccessibility
import com.ulisescervera.uci.core.ui.ext.setAccessibilityClickLabel
import com.ulisescervera.uci.databinding.UciItemPropertyBinding
import com.ulisescervera.uci.domain.model.Property

/**
 * The property list adapter.
 *
 * ### The payload is the interesting part
 * Toggling a favourite changes one field of one [Property]. Without a change
 * payload, `ListAdapter` rebinds the whole row -- including
 * `PropertyCarouselView.bind` -- and the carousel snaps back to photo 1. A user
 * who has swiped to photo 5 and taps the heart would watch the gallery reset,
 * which reads as a bug.
 *
 * So [PropertyDiffCallback] returns [PropertyDiffCallback.PAYLOAD_FLAG] when
 * only the flag or the favourite mark differ, and [ViewHolder.bindFlag] updates
 * just the heart. Everything else falls through to a full rebind.
 *
 * ### Dependencies
 * [PropertyFormatter] and [UciMapConfigurator] are constructor parameters, not
 * `Context` lookups, so a Robolectric test can hand in fakes and assert what a
 * row renders.
 */
internal class PropertyListAdapter(
    private val formatter: PropertyFormatter,
    private val mapConfigurator: UciMapConfigurator,
    private val callbacks: Callbacks,
) : ListAdapter<Property, PropertyListAdapter.ViewHolder>(PropertyDiffCallback) {

    /**
     * What a row can ask of its host.
     *
     * An interface rather than five lambdas: five constructor lambdas in a row
     * are impossible to read at the call site, and this way the fragment
     * implements one object.
     */
    interface Callbacks {
        /**
         * @param imageIndex the carousel page the user was looking at.
         * @param sharedImage the `ImageView` to hand to the shared-element
         *   transition, or `null` if the property has no photos.
         */
        fun onPropertyClicked(property: Property, imageIndex: Int, sharedImage: ImageView?)

        fun onFavouriteClicked(property: Property)

        fun onDiscardClicked(property: Property)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        UciItemPropertyBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        when {
            payloads.isEmpty() -> super.onBindViewHolder(holder, position, payloads)
            payloads.all { it == PropertyDiffCallback.PAYLOAD_FLAG } -> holder.bindFlag(getItem(position))
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class ViewHolder(
        private val binding: UciItemPropertyBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(property: Property) = with(binding) {
            uciPropertyTitle.text = formatter.title(property)
            uciPropertyPrice.text = formatter.price(property.price)

            val pricePerMeter = formatter.pricePerSquareMeter(property)
            uciPropertyPricePerMeter.text = pricePerMeter
            // Hidden rather than blank: an empty view still takes baseline space
            // and pushes the price label off-centre.
            uciPropertyPricePerMeter.isVisible(pricePerMeter != null)

            bindFacts(property)
            bindFlag(property)
            bindCarousel(property)

            // TalkBack reads the metadata as one utterance; the carousel and the
            // two buttons stay individually reachable.
            uciPropertyInfo.mergeChildrenForAccessibility(formatter.accessibilityDescription(property))
            uciPropertyInfo.setOnClickListener {
                callbacks.onPropertyClicked(property, uciPropertyCarousel.currentPageIndex, sharedImage())
            }
            uciPropertyInfo.setAccessibilityClickLabel(root.context.getString(R.string.uci_detail_title))

            uciPropertyDiscardButton.setOnClickListener { callbacks.onDiscardClicked(property) }
        }

        /** Only the heart. See the class doc for why this exists. */
        fun bindFlag(property: Property) = with(binding.uciPropertyFavouriteButton) {
            isChecked = property.isFavourite
            setIconResource(
                if (property.isFavourite) R.drawable.ic_uci_favourite_filled else R.drawable.ic_uci_favourite,
            )
            // The action label says what the tap will *do*; the state
            // description says what is true *now*. TalkBack reads both, and
            // conflating them ("favourite") tells the user neither.
            contentDescription = context.getString(
                if (property.isFavourite) R.string.uci_action_favourite_remove else R.string.uci_action_favourite_add,
            )
            ViewCompat.setStateDescription(
                this,
                context.getString(
                    if (property.isFavourite) R.string.uci_a11y_favourite_state_on
                    else R.string.uci_a11y_favourite_state_off,
                ),
            )
            setOnClickListener { callbacks.onFavouriteClicked(property) }
        }

        private fun bindFacts(property: Property) = with(binding.uciPropertyFacts) {
            val facts = formatter.facts(property)
            // Reuse the chips already in the group; only inflate the shortfall.
            // A property list scrolls fast and inflating a Chip is not cheap.
            while (childCount > facts.size) removeViewAt(childCount - 1)
            while (childCount < facts.size) {
                addView(LayoutInflater.from(context).inflate(R.layout.uci_view_fact_chip, this, false))
            }
            facts.forEachIndexed { index, fact ->
                (getChildAt(index) as Chip).apply {
                    text = fact.label
                    setChipIconResource(fact.iconRes)
                }
            }
            isVisible(facts.isNotEmpty())
        }

        private fun bindCarousel(property: Property) = with(binding) {
            val pages = buildCarouselPages(
                images = property.carouselImages,
                location = property.location,
                // No MapView in a list row: one tile-downloading map per
                // visible row would make scrolling stutter and drain the battery.
                includeMapPage = false,
            )
            uciPropertyCarousel.bind(
                pages = pages,
                formatter = formatter,
                mapConfigurator = mapConfigurator,
                onImageClick = { imageIndex ->
                    callbacks.onPropertyClicked(property, imageIndex, sharedImage())
                },
                // Names every photo page, so the page the user taps already has
                // the name the detail's enter transition will look for.
                transitionNameFor = { index -> sharedImageTransitionName(property.id, index) },
            )
        }

        private fun sharedImage(): ImageView? = binding.uciPropertyCarousel.currentImageView()

        fun recycle() {
            binding.uciPropertyCarousel.release()
            binding.uciPropertyInfo.setOnClickListener(null)
        }
    }

}

/**
 * Diffing rules for the property list, extracted from the adapter so they can be
 * unit-tested without inflating a single view.
 *
 * [getChangePayload] is the interesting one: toggling a favourite changes one
 * field of one [Property], and a full rebind would re-run
 * `PropertyCarouselView.bind` and snap the carousel back to photo 1. A user who
 * has swiped to photo 5 and taps the heart would watch the gallery reset, which
 * reads as a bug.
 */
internal object PropertyDiffCallback : DiffUtil.ItemCallback<Property>() {

    /** "Only the favourite/discarded state changed." */
    const val PAYLOAD_FLAG = "uci_payload_flag"

    override fun areItemsTheSame(oldItem: Property, newItem: Property): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Property, newItem: Property): Boolean =
        oldItem == newItem

    /**
     * Returns [PAYLOAD_FLAG] when the two differ *only* in local user state.
     *
     * The check is written as "normalise the flag fields, then compare for
     * equality" rather than as a field-by-field diff, so adding a field to
     * [Property] can never silently make this claim a partial update for a
     * change it does not actually cover.
     */
    override fun getChangePayload(oldItem: Property, newItem: Property): Any? {
        val flagChanged = oldItem.flag != newItem.flag ||
            oldItem.favouriteMark != newItem.favouriteMark
        if (!flagChanged) return null
        val restIdentical = oldItem.copy(
            flag = newItem.flag,
            favouriteMark = newItem.favouriteMark,
        ) == newItem
        return if (restIdentical) PAYLOAD_FLAG else null
    }
}
