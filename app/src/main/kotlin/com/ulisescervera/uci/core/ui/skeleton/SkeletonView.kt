package com.ulisescervera.uci.core.ui.skeleton

import android.content.Context
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import com.ulisescervera.uci.R

/**
 * A single shimmering placeholder block: a fake line of text, a fake avatar, a
 * fake image.
 *
 * Accessibility is the interesting part. A skeleton is *decorative*: it carries
 * no information a screen-reader user can act on, and announcing eight grey
 * rectangles is strictly worse than announcing nothing. So every instance sets
 * [IMPORTANT_FOR_ACCESSIBILITY_NO] on itself. The *container* of a skeleton
 * screen is the thing that gets a `contentDescription` ("Cargando inmuebles"),
 * which is how the state reaches TalkBack exactly once.
 *
 * Usage:
 * ```xml
 * <com.ulisescervera.uci.core.ui.skeleton.SkeletonView
 *     android:layout_width="180dp"
 *     android:layout_height="@dimen/uci_skeleton_line_height"
 *     app:uciSkeletonShape="line" />
 * ```
 */
class SkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var shape = Shape.RECTANGLE
    private var explicitCornerRadiusPx: Float? = null

    private val shimmer: SkeletonShimmerDrawable

    init {
        context.withStyledAttributes(attrs, R.styleable.UciSkeletonView, defStyleAttr) {
            shape = Shape.fromAttr(getInt(R.styleable.UciSkeletonView_uciSkeletonShape, Shape.RECTANGLE.attrValue))
            if (hasValue(R.styleable.UciSkeletonView_uciSkeletonCornerRadius)) {
                explicitCornerRadiusPx =
                    getDimension(R.styleable.UciSkeletonView_uciSkeletonCornerRadius, 0f)
            }
        }

        shimmer = SkeletonShimmerDrawable(
            baseColor = color(R.color.uci_skeleton_base),
            highlightColor = color(R.color.uci_skeleton_highlight),
            cornerRadiusPx = explicitCornerRadiusPx ?: defaultCornerRadiusPx(),
            animated = context.animationsEnabled(),
        )
        background = shimmer

        // Decorative by definition -- see the class doc.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        isClickable = false
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (shape == Shape.CIRCLE) {
            // A circle is a rounded rect whose radius is half its shortest side,
            // so the shape follows the view instead of a magic dimen.
            shimmer.setCornerRadius(minOf(width, height) / 2f)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        shimmer.start()
    }

    override fun onDetachedFromWindow() {
        shimmer.stop()
        super.onDetachedFromWindow()
    }

    private fun defaultCornerRadiusPx(): Float = when (shape) {
        Shape.RECTANGLE -> resources.getDimension(R.dimen.uci_skeleton_corner)
        // A text line reads as text when its radius is half its height.
        Shape.LINE -> resources.getDimension(R.dimen.uci_skeleton_line_height) / 2f
        Shape.CIRCLE -> 0f // recomputed in onSizeChanged
    }

    @ColorInt
    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)

    private enum class Shape(val attrValue: Int) {
        RECTANGLE(0),
        LINE(1),
        CIRCLE(2),
        ;

        companion object {
            fun fromAttr(value: Int): Shape = entries.firstOrNull { it.attrValue == value } ?: RECTANGLE
        }
    }
}

/**
 * `false` when the user has switched animations off in developer options or in
 * accessibility settings. Respecting it is not optional: sweeping gradients are
 * a known migraine and vestibular trigger.
 */
internal fun Context.animationsEnabled(): Boolean = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}.getOrDefault(true)
