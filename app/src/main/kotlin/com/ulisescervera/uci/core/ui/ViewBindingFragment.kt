package com.ulisescervera.uci.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Boilerplate remover for ViewBinding, and a leak fixer.
 *
 * A `Fragment` outlives its view hierarchy. Holding the binding in a plain
 * field is the single most common leak in view-based Android: the fragment
 * survives a `replace()`, the binding survives with it, and it keeps the whole
 * destroyed view tree alive. Nulling it in [onDestroyView] is the fix, and
 * doing it in a base class is how it stops being forgotten.
 *
 * Accessing [binding] outside `onCreateView`..`onDestroyView` throws with a
 * message that names the fragment, instead of an anonymous NPE.
 */
abstract class ViewBindingFragment<B : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> B,
) : Fragment() {

    private var nullableBinding: B? = null

    protected val binding: B
        get() = checkNotNull(nullableBinding) {
            "${this::class.simpleName}: binding accessed outside of the view lifecycle " +
                "(before onCreateView or after onDestroyView)."
        }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflate(inflater, container, false).also { nullableBinding = it }.root

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onBindingCreated(binding, savedInstanceState)
    }

    /** Set up views here. The binding is guaranteed non-null for its whole scope. */
    protected abstract fun onBindingCreated(binding: B, savedInstanceState: Bundle?)

    override fun onDestroyView() {
        onBindingDestroyed(binding)
        nullableBinding = null
        super.onDestroyView()
    }

    /** Detach adapters and listeners that hold a view reference. */
    protected open fun onBindingDestroyed(binding: B) = Unit
}
