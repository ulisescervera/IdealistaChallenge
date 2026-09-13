package com.ulisescervera.uci.feature.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.image.loadPropertyImage
import com.ulisescervera.uci.core.ui.ext.mergeChildrenForAccessibility
import com.ulisescervera.uci.databinding.UciItemRelatedPropertyBinding
import com.ulisescervera.uci.domain.model.Property

/**
 * The horizontal "inmuebles relacionados" carousel at the bottom of the detail.
 *
 * It renders exactly the subset the brief specifies -- a single teaser photo,
 * formatted name, price, size, rooms, and floor plus lift for a flat -- by
 * reusing [PropertyFormatter.facts]. That is the same function the main list
 * uses, so the two blocks cannot format the same property differently.
 *
 * The photo is a plain `ImageView`, not [com.ulisescervera.uci.core.carousel.PropertyCarouselView]:
 * per request, this row is a teaser link to the property, not another gallery
 * to swipe through -- one photo is enough to recognise it.
 *
 * Tapping a card navigates to *that* property's detail, which the navigation
 * graph supports through a self-action.
 */
internal class RelatedPropertiesAdapter(
    private val formatter: PropertyFormatter,
    private val onPropertyClicked: (Property) -> Unit,
) : ListAdapter<Property, RelatedPropertiesAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        UciItemRelatedPropertyBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(
        private val binding: UciItemRelatedPropertyBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(property: Property) = with(binding) {
            uciRelatedTitle.text = formatter.title(property)
            uciRelatedPrice.text = formatter.price(property.price)
            uciRelatedFacts.text = formatter.factsLine(property)

            uciRelatedImage.loadPropertyImage(property.carouselImages.firstOrNull()?.url)

            uciRelatedCard.mergeChildrenForAccessibility(formatter.accessibilityDescription(property))
            uciRelatedCard.setOnClickListener { onPropertyClicked(property) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Property>() {
            override fun areItemsTheSame(oldItem: Property, newItem: Property) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Property, newItem: Property) = oldItem == newItem
        }
    }
}
