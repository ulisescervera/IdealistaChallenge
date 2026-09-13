package com.ulisescervera.uci.feature.discarded

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.format.UciErrorFormatter
import com.ulisescervera.uci.core.ui.ext.collectWhileStarted
import com.ulisescervera.uci.core.ui.ext.isVisible
import com.ulisescervera.uci.databinding.UciFragmentDiscardedSheetBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The discarded-items sheet.
 *
 * A `BottomSheetDialogFragment` reached through a `<dialog>` destination, so the
 * list stays visible underneath and the system back button dismisses the sheet
 * without popping the list. It cannot extend `ViewBindingFragment` -- that base
 * class is for plain fragments -- so it manages its binding explicitly, with the
 * same null-on-destroy discipline.
 */
@AndroidEntryPoint
class DiscardedPropertiesSheetFragment : BottomSheetDialogFragment() {

    private val viewModel: DiscardedPropertiesViewModel by viewModels()

    @Inject lateinit var propertyFormatter: PropertyFormatter

    @Inject lateinit var errorFormatter: UciErrorFormatter

    private var nullableBinding: UciFragmentDiscardedSheetBinding? = null
    private val binding get() = checkNotNull(nullableBinding)

    private var listAdapter: DiscardedPropertiesAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = UciFragmentDiscardedSheetBinding.inflate(inflater, container, false)
        .also { nullableBinding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listAdapter = DiscardedPropertiesAdapter(
            formatter = propertyFormatter,
            onRestoreClicked = { item ->
                viewModel.dispatch(DiscardedPropertiesIntent.RestoreClicked(item.id))
            },
        ).also { binding.uciDiscardedList.adapter = it }

        binding.uciDiscardedRestoreAll.setOnClickListener {
            viewModel.dispatch(DiscardedPropertiesIntent.RestoreAllClicked)
        }

        viewModel.state.collectWhileStarted(viewLifecycleOwner) { render(it) }
        viewModel.effects.collectWhileStarted(viewLifecycleOwner) { handle(it) }
    }

    private fun render(state: DiscardedPropertiesUiState) = with(binding) {
        listAdapter?.submitList(state.items)
        uciDiscardedSheetSubtitle.text = resources.getQuantityString(
            R.plurals.uci_discarded_subtitle,
            state.items.size,
            state.items.size,
        )
        uciDiscardedEmpty.isVisible(state.isEmpty)
        uciDiscardedList.isVisible(!state.isEmpty)
        uciDiscardedRestoreAll.isEnabled = state.items.isNotEmpty()
    }

    private fun handle(effect: DiscardedPropertiesEffect) {
        when (effect) {
            is DiscardedPropertiesEffect.Restored -> announceRestored(effect.count)
            DiscardedPropertiesEffect.Dismiss -> dismissAllowingStateLoss()
            is DiscardedPropertiesEffect.ShowError -> Snackbar
                .make(binding.root, errorFormatter.message(effect.error), Snackbar.LENGTH_LONG)
                .show()
        }
    }

    /**
     * Announced rather than shown as a snackbar: the sheet is about to close,
     * and a snackbar anchored to a dismissed dialog goes with it. TalkBack still
     * gets the confirmation.
     */
    private fun announceRestored(count: Int) {
        binding.root.announceForAccessibility(
            resources.getQuantityString(R.plurals.uci_discarded_restored_feedback, count, count),
        )
    }

    override fun onDestroyView() {
        binding.uciDiscardedList.adapter = null
        listAdapter = null
        nullableBinding = null
        super.onDestroyView()
    }
}
