package com.ulisescervera.uci.feature.discarded

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.image.loadPropertyImage
import com.ulisescervera.uci.core.ui.ext.setAccessibilityClickLabel
import com.ulisescervera.uci.databinding.UciItemDiscardedBinding
import com.ulisescervera.uci.domain.model.DiscardedProperty

/** Rows of the discarded sheet. Tapping a row restores that property. */
internal class DiscardedPropertiesAdapter(
    private val formatter: PropertyFormatter,
    private val onRestoreClicked: (DiscardedProperty) -> Unit,
) : ListAdapter<DiscardedProperty, DiscardedPropertiesAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        UciItemDiscardedBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(
        private val binding: UciItemDiscardedBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DiscardedProperty) = with(binding) {
            val title = formatter.title(item.propertyType, item.zone, item.city)
            val price = formatter.price(item.price)

            uciDiscardedTitle.text = title
            uciDiscardedPrice.text = price
            uciDiscardedThumbnail.loadPropertyImage(item.thumbnailUrl)

            // The row is the target; its children are marked
            // importantForAccessibility="no" in the layout, so this single
            // description is the whole utterance.
            uciDiscardedRow.contentDescription =
                root.context.getString(R.string.uci_a11y_discarded_row, title, price)
            uciDiscardedRow.setAccessibilityClickLabel(
                root.context.getString(R.string.uci_action_restore),
            )
            uciDiscardedRow.setOnClickListener { onRestoreClicked(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<DiscardedProperty>() {
            override fun areItemsTheSame(oldItem: DiscardedProperty, newItem: DiscardedProperty) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DiscardedProperty, newItem: DiscardedProperty) =
                oldItem == newItem
        }
    }
}
