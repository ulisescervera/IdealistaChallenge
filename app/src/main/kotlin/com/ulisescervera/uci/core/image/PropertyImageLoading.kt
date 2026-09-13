package com.ulisescervera.uci.core.image

import android.widget.ImageView
import coil3.load
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import com.ulisescervera.uci.R

/**
 * Every property photo in the app goes through here.
 *
 * Centralising it buys three things:
 *
 * - **One placeholder.** A grey block with a photo glyph, so a slow row never
 *   shows a white hole or, worse, the previous row's photo (`ImageView` keeps
 *   its old bitmap when the URL changes).
 * - **Crossfade only when animations are on.** The duration is passed in by the
 *   caller, which lets a skeleton-to-content transition be instant when the user
 *   has disabled animations.
 * - **A shared memory/disk cache policy.** Carousels re-request the same URL
 *   constantly as the user swipes back and forth; without an explicit policy
 *   the defaults are fine, but stating them makes the behaviour reviewable.
 *
 * [onReady] exists for the shared-element transition: the detail screen must not
 * start its postponed enter transition until the target image has real pixels,
 * otherwise the photo pops in after the animation has already finished.
 *
 * ### Coil 3 note
 * `crossfade`, `placeholder`, `error`, `fallback` and `allowHardware` are
 * extension functions on the builder in Coil 3, not members -- hence the
 * per-function imports above. Miss one and the call silently resolves to
 * nothing at all (it will not compile, which is the good outcome).
 */
fun ImageView.loadPropertyImage(
    url: String?,
    crossfadeMillis: Int = DEFAULT_CROSSFADE_MILLIS,
    onReady: (() -> Unit)? = null,
) {
    load(url) {
        crossfade(crossfadeMillis)
        placeholder(R.drawable.bg_uci_image_placeholder)
        error(R.drawable.bg_uci_image_placeholder)
        fallback(R.drawable.bg_uci_image_placeholder)
        memoryCachePolicy(CachePolicy.ENABLED)
        diskCachePolicy(CachePolicy.ENABLED)
        allowHardware(true)
        listener(
            onSuccess = { _, _ -> onReady?.invoke() },
            // A failed load must not block a postponed transition forever.
            onError = { _, _ -> onReady?.invoke() },
            onCancel = { onReady?.invoke() },
        )
    }
}

const val DEFAULT_CROSSFADE_MILLIS = 180
const val NO_CROSSFADE_MILLIS = 0

/**
 * Stable name shared by a list carousel page and the detail carousel page that
 * shows the same photo.
 *
 * The **page index** is part of the name on purpose. The alternative -- one name
 * per property, pushed onto "whichever ImageView is visible" -- depends on the
 * pager having already laid out when the name is assigned, which it has not
 * during `bind()`. Encoding the index makes the pairing declarative: the list
 * page the user tapped and the detail page it opens on carry the same name
 * because they are the same index, with no timing assumption at all.
 *
 * If the detail clamps to a different index (a staler cache with fewer photos),
 * the names simply do not match and the transition degrades to a plain
 * navigation instead of animating the wrong photo.
 */
fun sharedImageTransitionName(propertyId: String, imageIndex: Int): String =
    "uci_property_image_${propertyId}_$imageIndex"
