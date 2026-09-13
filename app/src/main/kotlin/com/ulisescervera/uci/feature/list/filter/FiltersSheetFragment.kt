package com.ulisescervera.uci.feature.list.filter

import android.os.Bundle
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
        setUpDropdowns(current)
        setUpChips(current)
        setUpTextFields(current)
        setUpActions()
    }

    private fun setUpDropdowns(current: PropertyFilters) = with(binding) {
        val types = listOf(null) + PROPERTY_TYPES
        val typeLabels = listOf(getString(R.string.uci_filters_any)) +
            PROPERTY_TYPES.map(propertyFormatter::propertyTypeLabel)
        uciFilterTypeInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, typeLabels),
        )
        uciFilterTypeInput.setText(typeLabels[types.indexOf(current.propertyType)], false)
        uciFilterTypeInput.tag = current.propertyType

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

        uciFilterTypeInput.setOnItemClickListener { _, _, position, _ ->
            uciFilterTypeInput.tag = types[position]
        }
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

        uciFilterEnergyAPlus.isChecked = ENERGY_A_PLUS in current.energyCertifications
        uciFilterEnergyA.isChecked = "A" in current.energyCertifications
        uciFilterEnergyB.isChecked = "B" in current.energyCertifications
        uciFilterEnergyC.isChecked = "C" in current.energyCertifications
        uciFilterEnergyD.isChecked = "D" in current.energyCertifications
        uciFilterEnergyE.isChecked = "E" in current.energyCertifications
        uciFilterEnergyF.isChecked = "F" in current.energyCertifications

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
            propertyType = uciFilterTypeInput.tag as? PropertyType,
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
            energyCertifications = checkedValues(
                uciFilterEnergyAPlus to ENERGY_A_PLUS,
                uciFilterEnergyA to "A",
                uciFilterEnergyB to "B",
                uciFilterEnergyC to "C",
                uciFilterEnergyD to "D",
                uciFilterEnergyE to "E",
                uciFilterEnergyF to "F",
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

        private val PROPERTY_TYPES = listOf(
            PropertyType.FLAT,
            PropertyType.DUPLEX,
            PropertyType.PENTHOUSE,
            PropertyType.STUDIO,
            PropertyType.CHALET,
            PropertyType.COUNTRY_HOUSE,
            PropertyType.HOUSE,
            PropertyType.ROOM,
            PropertyType.GARAGE,
            PropertyType.OFFICE,
            PropertyType.PREMISES,
        )
    }
}
