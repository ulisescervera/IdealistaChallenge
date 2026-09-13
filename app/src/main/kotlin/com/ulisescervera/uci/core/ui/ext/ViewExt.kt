package com.ulisescervera.uci.core.ui.ext

import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/** `visibility` as a boolean, because `if (x) VISIBLE else GONE` adds up. */
fun View.isVisible(visible: Boolean, useInvisible: Boolean = false) {
    visibility = when {
        visible -> View.VISIBLE
        useInvisible -> View.INVISIBLE
        else -> View.GONE
    }
}

/**
 * Replaces the verb TalkBack announces for a click ("pulsa dos veces para
 * *activar*") with something specific ("pulsa dos veces para *restaurar*").
 *
 * The default is almost always wrong for a custom row, and it is the cheapest
 * accessibility win available: one line per actionable view.
 */
fun View.setAccessibilityClickLabel(label: CharSequence) {
    ViewCompat.setAccessibilityDelegate(
        this,
        object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        label,
                    ),
                )
            }
        },
    )
}

/**
 * Makes a container the single accessibility focus target, so TalkBack reads a
 * property card as one utterance instead of stopping on seven separate labels.
 * The container must carry a `contentDescription` that says everything.
 */
fun View.mergeChildrenForAccessibility(description: CharSequence) {
    contentDescription = description
    ViewCompat.setScreenReaderFocusable(this, true)
    isFocusable = true
    // Children stop being individually reachable; the buttons inside opt back in
    // explicitly by setting importantForAccessibility = yes.
    ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
}

/** Announces a transient change (undo banner appearing, page changing). */
fun View.announceForAccessibilityCompat(message: CharSequence) {
    if (message.isNotBlank()) announceForAccessibility(message)
}
