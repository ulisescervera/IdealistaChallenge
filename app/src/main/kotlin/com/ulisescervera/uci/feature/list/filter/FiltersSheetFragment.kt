package com.ulisescervera.uci.feature.list.filter

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.databinding.UciFragmentFiltersSheetBinding
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.PropertyType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The filter sheet, reached from the list's toolbar action.
 *
 * It has no ViewModel: [PropertyFilters] is plain, immutable, presentation
 * state with no repository behind it, so there is nothing here worth
 * surviving a process death for. The current filters arrive as a plain
 * [Bundle] (no `<argument>` declared in the nav graph, no SafeArgs type to
 * fight for a `Set<Int>`) and the result travels back the same way, through
 * the Fragment Result API -- this sheet and `PropertyListFragment` are both
 * children of the same `NavHostFragment`, so `parentFragmentManager` is the
 * one they share.
 */
@AndroidEntryPoint
class FiltersSheetFragment : BottomSheetDialogFragment() {

    @Inject lateinit var propertyFormatter: PropertyFormatter

    private var nullableBinding: UciFragmentFiltersSheetBinding? = null
    private val binding get() = checkNotNull(nullableBinding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = UciFragmentFiltersSheetBinding.inflate(inflater, container, false)
        .also { nullableBinding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val current = PropertyFilters.fromBundle(requireArguments())
        setUpTypeChips(current)
        setUpOperationDropdown(current)
        setUpChips(current)
        setUpTextFields(current)
        setUpActions()
    }

    /**
     * Checkboxes, not a single dropdown: the brief asks for several types to
     * be selectable at once, so this mirrors the rooms/bathrooms chip groups
     * instead of [uciFilterOperationInput]'s single-choice dropdown. Built
     * programmatically because [PROPERTY_TYPES] -- and therefore how many
     * chips exist -- is data, not a fixed count like rooms or bathrooms.
     */
    private fun setUpTypeChips(current: PropertyFilters) = with(binding) {
        uciFilterTypeGroup.removeAllViews()
        val chipContext = ContextThemeWrapper(
            requireContext(),
            com.google.android.material.R.style.Widget_Material3_Chip_Filter,
        )
        PROPERTY_TYPES.forEach { type ->
            val chip = Chip(chipContext).apply {
                text = propertyFormatter.propertyTypeLabel(type)
                isCheckable = true
                isChecked = type in current.propertyTypes
                tag = type
            }
            uciFilterTypeGroup.addView(chip)
        }
    }

    /**
     * Uses `MaterialAutoCompleteTextView` inside a
     * `...ExposedDropdownMenu`-styled `TextInputLayout`: a plain
     * `AutoCompleteTextView` with `inputType="none"` never receives the text
     * change that would normally trigger its suggestion filter, so tapping it
     * did nothing. The exposed-dropdown pairing wires the tap itself to
     * `showDropDown()`.
     */
    private fun setUpOperationDropdown(current: PropertyFilters) = with(binding) {
        val operations = listOf(null, Operation.SALE, Operation.RENT)
        val operationLabels = listOf(
            getString(R.string.uci_filters_any),
            getString(R.string.uci_filters_operation_sale),
            getString(R.string.uci_filters_operation_rent),
        )
        uciFilterOperationInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, operationLabels),
        )
        uciFilterOperationInput.setText(operationLabels[operations.indexOf(current.operation)], false)
        uciFilterOperationInput.tag = current.operation
        uciFilterOperationInput.setOnItemClickListener { _, _, position, _ ->
            uciFilterOperationInput.tag = operations[position]
        }
    }

    private fun setUpChips(current: PropertyFilters) = with(binding) {
        uciFilterRooms2.isChecked = 2 in current.rooms
        uciFilterRooms3.isChecked = 3 in current.rooms
        uciFilterRooms4.isChecked = 4 in current.rooms
        uciFilterRooms5Plus.isChecked = PropertyFilters.ROOMS_PLUS_BUCKET in current.rooms

        uciFilterBathrooms1.isChecked = 1 in current.bathrooms
        uciFilterBathrooms2.isChecked = 2 in current.bathrooms
        uciFilterBathrooms3.isChecked = 3 in current.bathrooms
        uciFilterBathrooms4Plus.isChecked = PropertyFilters.BATHROOMS_PLUS_BUCKET in current.bathrooms

        uciFilterStatusNew.isChecked = STATUS_NEW in current.statuses
        uciFilterStatusGood.isChecked = STATUS_GOOD in current.statuses
        uciFilterStatusToRenovate.isChecked = STATUS_TO_RENOVATE in current.statuses

        uciFilterFeatureLift.isChecked = current.requiresLift
        uciFilterFeatureGarage.isChecked = current.requiresGarage
    }

    private fun setUpTextFields(current: PropertyFilters) = with(binding) {
        uciFilterPriceMin.setText(current.minPrice?.toInt()?.toString().orEmpty())
        uciFilterPriceMax.setText(current.maxPrice?.toInt()?.toString().orEmpty())
        uciFilterSizeMin.setText(current.minSize?.toInt()?.toString().orEmpty())
        uciFilterSizeMax.setText(current.maxSize?.toInt()?.toString().orEmpty())
    }

    private fun setUpActions() = with(binding) {
        uciFilterClearButton.setOnClickListener { publish(PropertyFilters()) }
        uciFilterApplyButton.setOnClickListener { publish(readFilters()) }
    }

    private fun readFilters(): PropertyFilters = with(binding) {
        PropertyFilters(
            propertyTypes = (0 until uciFilterTypeGroup.childCount)
                .map { uciFilterTypeGroup.getChildAt(it) as Chip }
                .filter(Chip::isChecked)
                .map { it.tag as PropertyType }
                .toSet(),
            operation = uciFilterOperationInput.tag as? Operation,
            minPrice = uciFilterPriceMin.text?.toString()?.toDoubleOrNull(),
            maxPrice = uciFilterPriceMax.text?.toString()?.toDoubleOrNull(),
            minSize = uciFilterSizeMin.text?.toString()?.toDoubleOrNull(),
            maxSize = uciFilterSizeMax.text?.toString()?.toDoubleOrNull(),
            rooms = checkedValues(
                uciFilterRooms2 to 2,
                uciFilterRooms3 to 3,
                uciFilterRooms4 to 4,
                uciFilterRooms5Plus to PropertyFilters.ROOMS_PLUS_BUCKET,
            ),
            bathrooms = checkedValues(
                uciFilterBathrooms1 to 1,
                uciFilterBathrooms2 to 2,
                uciFilterBathrooms3 to 3,
                uciFilterBathrooms4Plus to PropertyFilters.BATHROOMS_PLUS_BUCKET,
            ),
            statuses = checkedValues(
                uciFilterStatusNew to STATUS_NEW,
                uciFilterStatusGood to STATUS_GOOD,
                uciFilterStatusToRenovate to STATUS_TO_RENOVATE,
            ),
            requiresLift = uciFilterFeatureLift.isChecked,
            requiresGarage = uciFilterFeatureGarage.isChecked,
        )
    }

    private fun <T> checkedValues(vararg pairs: Pair<Chip, T>): Set<T> =
        pairs.filter { (chip, _) -> chip.isChecked }.map { (_, value) -> value }.toSet()

    private fun publish(filters: PropertyFilters) {
        parentFragmentManager.setFragmentResult(FILTERS_REQUEST_KEY, filters.toBundle())
        dismiss()
    }

    override fun onDestroyView() {
        nullableBinding = null
        super.onDestroyView()
    }

    companion object {
        const val FILTERS_REQUEST_KEY = "uci_filters_request"

        private const val ENERGY_A_PLUS = "A+"
        private const val STATUS_NEW = "nuevo"
        private const val STATUS_GOOD = "buen_estado"
        private const val STATUS_TO_RENOVATE = "para_reformar"

        // Only [PropertyType.FLAT] is offered: it is the only value
        // https://idealista.github.io/android-challenge/list.json ever sends
        // for `propertyType` (see PropertyType's class doc). Listed as a
        // sequence, not hardcoded into a single chip, so widening this the
        // day the endpoint returns something else is a one-line change here.
        private val PROPERTY_TYPES = listOf(PropertyType.FLAT)
    }
}
