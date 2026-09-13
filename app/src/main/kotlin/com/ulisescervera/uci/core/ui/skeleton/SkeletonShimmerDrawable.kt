package com.ulisescervera.uci.core.ui.skeleton

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * The shimmer used by every loading placeholder in UCI.
 *
 * Written by hand rather than pulled from a shimmer library, for three reasons
 * that all matter here:
 *
 * 1. **One dependency fewer** for ~120 lines of well-understood code.
 * 2. **It honours "remove animations".** [animated] is wired to the system
 *    animator duration scale by [SkeletonView]; when the user has turned
 *    animations off -- a real accessibility setting, often used because motion
 *    triggers nausea -- the placeholder renders as a static block instead of
 *    sweeping forever. Most shimmer libraries ignore this.
 * 3. **It stops when off-screen.** [setVisible] drives the animator, so a
 *    recycled skeleton row does not keep a `ValueAnimator` alive.
 *
 * The sweep is a three-stop linear gradient translated across the bounds; the
 * shader is rebuilt only on bounds change, and each frame is one matrix update.
 */
internal class SkeletonShimmerDrawable(
    private val baseColor: Int,
    private val highlightColor: Int,
    private var cornerRadiusPx: Float,
    private val animated: Boolean,
) : Drawable(), Animatable {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = RectF()
    private val shaderMatrix = Matrix()
    private var shader: LinearGradient? = null
    private var sweep = 0f

    private val animator = ValueAnimator.ofFloat(SWEEP_FROM, SWEEP_TO).apply {
        duration = SWEEP_DURATION_MILLIS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            sweep = it.animatedValue as Float
            invalidateSelf()
        }
    }

    fun setCornerRadius(radiusPx: Float) {
        cornerRadiusPx = radiusPx
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        shape.set(bounds)
        rebuildShader(bounds.width().toFloat())
    }

    private fun rebuildShader(width: Float) {
        if (width <= 0f) {
            shader = null
            return
        }
        shader = LinearGradient(
            0f,
            0f,
            width,
            0f,
            intArrayOf(baseColor, highlightColor, baseColor),
            floatArrayOf(0f, HIGHLIGHT_POSITION, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        val activeShader = shader
        if (activeShader == null || !animated) {
            // Static fallback: still a visible placeholder, just not moving.
            paint.shader = null
            paint.color = baseColor
        } else {
            shaderMatrix.setTranslate(sweep * shape.width(), 0f)
            activeShader.setLocalMatrix(shaderMatrix)
            paint.shader = activeShader
        }
        canvas.drawRoundRect(shape, cornerRadiusPx, cornerRadiusPx, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Required by the Drawable contract on API < 29.")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun start() {
        if (animated && !animator.isStarted) animator.start()
    }

    override fun stop() {
        if (animator.isStarted) animator.cancel()
    }

    override fun isRunning(): Boolean = animator.isRunning

    /** Frees the animator when the drawable scrolls out of the window. */
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) start() else stop()
        return changed
    }

    private companion object {
        /**
         * Starts one full width before the left edge and ends one full width
         * past the right edge, so the highlight enters and leaves cleanly
         * instead of popping into existence mid-view.
         */
        const val SWEEP_FROM = -1f
        const val SWEEP_TO = 2f
        const val HIGHLIGHT_POSITION = 0.5f
        const val SWEEP_DURATION_MILLIS = 1_150L
    }
}
