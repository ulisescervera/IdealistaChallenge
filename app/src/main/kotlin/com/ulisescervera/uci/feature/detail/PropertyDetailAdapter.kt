package com.ulisescervera.uci.feature.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.image.sharedImageTransitionName
import com.ulisescervera.uci.core.map.UciMapConfigurator
import com.ulisescervera.uci.core.ui.ext.isVisible
import com.ulisescervera.uci.core.ui.ext.mergeChildrenForAccessibility
import com.ulisescervera.uci.core.ui.ext.setAccessibilityClickLabel
import com.ulisescervera.uci.databinding.UciItemDetailActionsBinding
import com.ulisescervera.uci.databinding.UciItemDetailCharacteristicsBinding
import com.ulisescervera.uci.databinding.UciItemDetailCommentBinding
import com.ulisescervera.uci.databinding.UciItemDetailGalleryBinding
import com.ulisescervera.uci.databinding.UciItemDetailHeaderBinding
import com.ulisescervera.uci.databinding.UciItemDetailMapBinding
import com.ulisescervera.uci.databinding.UciItemDetailRelatedBinding
import com.ulisescervera.uci.databinding.UciItemDetailRelatedSkeletonBinding
import com.ulisescervera.uci.databinding.UciItemDetailTextSkeletonBinding
import com.ulisescervera.uci.databinding.UciViewCharacteristicRowBinding
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Property

/**
 * The detail screen, as one vertical RecyclerView with a view type per section.
 *
 * Notes worth reading before changing this:
 *
 * - **The map row owns a `MapView`.** [onViewRecycled] releases it, otherwise a
 *   scroll up and down leaves tile-download threads running.
 * - **The gallery row is bound once per property.** Re-binding it would reset
 *   the carousel, so the state that changes often -- the flag, the comment
 *   expansion -- lives in *other* rows, and `DiffUtil` leaves the gallery
 *   untouched.
 * - **The related row hosts a nested horizontal RecyclerView** whose adapter is
 *   created in `onCreateViewHolder`, not in `bind`: creating it per bind would
 *   discard the horizontal scroll position on every state emission.
 */
internal class PropertyDetailAdapter(
    private val formatter: PropertyFormatter,
    private val mapConfigurator: UciMapConfigurator,
    private val callbacks: Callbacks,
) : ListAdapter<DetailRow, RecyclerView.ViewHolder>(DIFF) {

    interface Callbacks {
        fun onImageClicked(imageIndex: Int)
        fun onMapPageClicked()
        fun onMapClicked(location: GeoPoint, addressLine: String?)
        fun onFavouriteClicked()
        fun onDiscardClicked()
        fun onShareClicked()
        fun onCommentToggleClicked()
        fun onRelatedPropertyClicked(property: Property)

        /** Page 0 of the gallery has pixels: safe to start the enter transition. */
        fun onGalleryReady()
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DetailRow.Gallery -> TYPE_GALLERY
        is DetailRow.Header -> TYPE_HEADER
        is DetailRow.Actions -> TYPE_ACTIONS
        is DetailRow.Comment -> TYPE_COMMENT
        DetailRow.CommentSkeleton, DetailRow.CharacteristicsSkeleton -> TYPE_TEXT_SKELETON
        is DetailRow.Map -> TYPE_MAP
        is DetailRow.Characteristics -> TYPE_CHARACTERISTICS
        is DetailRow.Related -> TYPE_RELATED
        DetailRow.RelatedSkeleton -> TYPE_RELATED_SKELETON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_GALLERY -> GalleryViewHolder(UciItemDetailGalleryBinding.inflate(inflater, parent, false))
            TYPE_HEADER -> HeaderViewHolder(UciItemDetailHeaderBinding.inflate(inflater, parent, false))
            TYPE_ACTIONS -> ActionsViewHolder(UciItemDetailActionsBinding.inflate(inflater, parent, false))
            TYPE_COMMENT -> CommentViewHolder(UciItemDetailCommentBinding.inflate(inflater, parent, false))
            TYPE_MAP -> MapViewHolder(UciItemDetailMapBinding.inflate(inflater, parent, false))
            TYPE_CHARACTERISTICS -> CharacteristicsViewHolder(
                UciItemDetailCharacteristicsBinding.inflate(inflater, parent, false),
            )

            TYPE_RELATED -> RelatedViewHolder(UciItemDetailRelatedBinding.inflate(inflater, parent, false))
            TYPE_RELATED_SKELETON -> SimpleViewHolder(
                UciItemDetailRelatedSkeletonBinding.inflate(inflater, parent, false).root,
            )

            else -> SimpleViewHolder(UciItemDetailTextSkeletonBinding.inflate(inflater, parent, false).root)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is DetailRow.Gallery -> (holder as GalleryViewHolder).bind(row)
            is DetailRow.Header -> (holder as HeaderViewHolder).bind(row)
            is DetailRow.Actions -> (holder as ActionsViewHolder).bind(row)
            is DetailRow.Comment -> (holder as CommentViewHolder).bind(row)
            is DetailRow.Map -> (holder as MapViewHolder).bind(row)
            is DetailRow.Characteristics -> (holder as CharacteristicsViewHolder).bind(row)
            is DetailRow.Related -> (holder as RelatedViewHolder).bind(row)
            DetailRow.CommentSkeleton,
            DetailRow.CharacteristicsSkeleton,
            DetailRow.RelatedSkeleton,
            -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is MapViewHolder -> holder.release()
            is GalleryViewHolder -> holder.release()
            is RelatedViewHolder -> holder.release()
        }
        super.onViewRecycled(holder)
    }

    // -----------------------------------------------------------------------
    // View holders
    // -----------------------------------------------------------------------

    private inner class GalleryViewHolder(
        private val binding: UciItemDetailGalleryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DetailRow.Gallery) {
            val carousel = binding.uciDetailCarousel
            carousel.bind(
                pages = row.pages,
                formatter = formatter,
                mapConfigurator = mapConfigurator,
                initialIndex = row.initialImageIndex,
                onImageClick = { index -> callbacks.onImageClicked(index) },
                onMapPageClick = { callbacks.onMapPageClicked() },
                onFirstPageReady = { callbacks.onGalleryReady() },
                // The counterpart of the list row's names. Same property, same
                // index, same name -- so the shared element finds its pair with
                // no dependency on when the pager laid out.
                transitionNameFor = { index -> sharedImageTransitionName(row.propertyId, index) },
            )
        }

        fun release() = binding.uciDetailCarousel.release()
    }

    private inner class HeaderViewHolder(
        private val binding: UciItemDetailHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DetailRow.Header) = with(binding) {
            uciDetailTitle.text = row.title
            uciDetailPrice.text = row.price
            uciDetailPricePerMeter.text = row.pricePerSquareMeter
            uciDetailPricePerMeter.isVisible(row.pricePerSquareMeter != null)
            bindFacts(row.facts)
            uciDetailHeader.mergeChildrenForAccessibility(row.accessibilityDescription)
        }

        private fun bindFacts(facts: List<PropertyFormatter.Fact>) = with(binding.uciDetailFacts) {
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
        }
    }

    private inner class ActionsViewHolder(
        private val binding: UciItemDetailActionsBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DetailRow.Actions) = with(binding) {
            uciDetailFavouriteButton.apply {
                isChecked = row.isFavourite
                // Icon-only: the label used to be the accessible name, so it
                // moves to contentDescription verbatim rather than disappearing.
                contentDescription = context.getString(
                    if (row.isFavourite) R.string.uci_action_favourite_remove
                    else R.string.uci_action_favourite_add,
                )
                setIconResource(
                    if (row.isFavourite) R.drawable.ic_uci_favourite_filled else R.drawable.ic_uci_favourite,
                )
                ViewCompat.setStateDescription(
                    this,
                    context.getString(
                        if (row.isFavourite) R.string.uci_a11y_favourite_state_on
                        else R.string.uci_a11y_favourite_state_off,
                    ),
                )
                setOnClickListener { callbacks.onFavouriteClicked() }
            }

            uciDetailDiscardButton.apply {
                contentDescription = context.getString(
                    if (row.isDiscarded) R.string.uci_action_restore else R.string.uci_action_discard,
                )
                setIconResource(
                    if (row.isDiscarded) R.drawable.ic_uci_restore else R.drawable.ic_uci_delete,
                )
                setOnClickListener { callbacks.onDiscardClicked() }
            }

            uciDetailShareButton.setOnClickListener { callbacks.onShareClicked() }

            // The favourite date, in the zone the mark was created in.
            uciDetailFavouritedOn.text = row.favouritedOn
            uciDetailFavouritedOn.isVisible(row.favouritedOn != null)
        }
    }

    private inner class CommentViewHolder(
        private val binding: UciItemDetailCommentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DetailRow.Comment) = with(binding) {
            uciDetailCommentBody.text = row.body
            uciDetailCommentBody.maxLines = if (row.isExpanded) Int.MAX_VALUE else COLLAPSED_COMMENT_LINES
            uciDetailCommentToggle.setText(
                if (row.isExpanded) R.string.uci_detail_comment_collapse else R.string.uci_detail_comment_expand,
            )
            uciDetailCommentToggle.setOnClickListener { callbacks.onCommentToggleClicked() }
            // A one-paragraph description needs no toggle.
            uciDetailCommentToggle.isVisible(row.body.length > COMMENT_TOGGLE_THRESHOLD)
        }
    }

    private inner class MapViewHolder(
        private val binding: UciItemDetailMapBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DetailRow.Map) = with(binding) {
            mapConfigurator.configureInteractive(uciDetailMap, row.location)
            uciDetailMapAddress.text = row.addressLine
            uciDetailMapAddress.isVisible(row.addressLine != null)
            // A raster of map tiles is invisible to a screen reader, so the
            // overlay says in words where the property is; it is also the
            // click target that opens the full-screen map, since a MapView
            // with `configureInteractive` already consumes touches for pan
            // and zoom and would never see a tap meant for a click listener.
            uciDetailMapDescription.contentDescription = row.accessibilityDescription
            uciDetailMapDescription.setAccessibilityClickLabel(
                root.context.getString(R.string.uci_a11y_open_full_map),
            )
            uciDetailMapDescription.setOnClickListener {
                callbacks.onMapClicked(row.location, row.addressLine)
            }
        }

        fun release() = mapConfigurator.release(binding.uciDetailMap)
    }

    private inner class CharacteristicsViewHolder(
        private val binding: UciItemDetailCharacteristicsBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DetailRow.Characteristics) = with(binding.uciDetailCharacteristicsContainer) {
            removeAllViews()
            val inflater = LayoutInflater.from(context)
            row.rows.forEach { characteristic ->
                val rowBinding = UciViewCharacteristicRowBinding.inflate(inflater, this, false)
                rowBinding.uciCharacteristicLabel.text = characteristic.label
                rowBinding.uciCharacteristicValue.text = characteristic.value
                // One node per pair: two separate TextViews would be read as
                // "Ascensor" ... "Sí" with no connection between them.
                rowBinding.uciCharacteristicRow.contentDescription =
                    "${characteristic.label}: ${characteristic.value}"
                addView(rowBinding.root)
            }
        }
    }

    private inner class RelatedViewHolder(
        private val binding: UciItemDetailRelatedBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        // Created once per holder, so the horizontal scroll position survives a
        // state emission from the parent.
        private val relatedAdapter = RelatedPropertiesAdapter(
            formatter = formatter,
            onPropertyClicked = callbacks::onRelatedPropertyClicked,
        ).also { binding.uciRelatedList.adapter = it }

        fun bind(row: DetailRow.Related) = with(binding) {
            relatedAdapter.submitList(row.items)
            uciRelatedEmpty.isVisible(row.items.isEmpty())
            uciRelatedList.isVisible(row.items.isNotEmpty())
        }

        fun release() {
            binding.uciRelatedList.adapter = null
        }
    }

    private class SimpleViewHolder(view: View) : RecyclerView.ViewHolder(view)

    companion object {
        private const val TYPE_GALLERY = 0
        private const val TYPE_HEADER = 1
        private const val TYPE_ACTIONS = 2
        private const val TYPE_COMMENT = 3
        private const val TYPE_TEXT_SKELETON = 4
        private const val TYPE_MAP = 5
        private const val TYPE_CHARACTERISTICS = 6
        private const val TYPE_RELATED = 7
        private const val TYPE_RELATED_SKELETON = 8

        private const val COLLAPSED_COMMENT_LINES = 6

        /** Roughly six lines at default font size. */
        private const val COMMENT_TOGGLE_THRESHOLD = 320

        private val DIFF = object : DiffUtil.ItemCallback<DetailRow>() {
            override fun areItemsTheSame(oldItem: DetailRow, newItem: DetailRow): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DetailRow, newItem: DetailRow): Boolean =
                oldItem == newItem
        }
    }
}
