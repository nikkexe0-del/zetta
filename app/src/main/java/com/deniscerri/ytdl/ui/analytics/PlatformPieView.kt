package com.deniscerri.ytdl.ui.analytics

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Lightweight donut chart. No third party charting library, no extra dependencies.
 */
class PlatformPieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(val label: String, val value: Float, val color: Int)

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1F808080")
    }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val centerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val bounds = RectF()
    private var slices: List<Slice> = emptyList()
    private var progress = 1f

    var centerTitle: String = ""
    var centerSubtitle: String = ""
    var holeColor: Int = Color.TRANSPARENT
    var titleColor: Int = Color.DKGRAY
    var subtitleColor: Int = Color.GRAY

    private var animator: ValueAnimator? = null

    fun setSlices(newSlices: List<Slice>, animate: Boolean = true) {
        slices = newSlices
        animator?.cancel()
        if (animate && newSlices.isNotEmpty()) {
            progress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 650
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            progress = 1f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        if (size <= 0f) return

        val pad = size * 0.06f
        val left = (width - size) / 2f + pad
        val top = (height - size) / 2f + pad
        bounds.set(left, top, left + size - pad * 2, top + size - pad * 2)

        canvas.drawOval(bounds, trackPaint)

        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total > 0f) {
            var start = -90f
            slices.forEach { slice ->
                val sweep = (slice.value / total) * 360f * progress
                slicePaint.color = slice.color
                // small gap between slices for a cleaner look
                val gap = if (slices.size > 1) 1.2f else 0f
                canvas.drawArc(bounds, start + gap, maxOf(sweep - gap, 0f), true, slicePaint)
                start += sweep
            }
        }

        // donut hole
        holePaint.color = holeColor
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        canvas.drawCircle(cx, cy, bounds.width() * 0.30f, holePaint)

        if (centerTitle.isNotEmpty()) {
            centerTitlePaint.color = titleColor
            centerTitlePaint.textSize = bounds.width() * 0.135f
            centerSubPaint.color = subtitleColor
            centerSubPaint.textSize = bounds.width() * 0.072f

            if (centerSubtitle.isEmpty()) {
                canvas.drawText(centerTitle, cx, cy + centerTitlePaint.textSize / 3f, centerTitlePaint)
            } else {
                canvas.drawText(centerTitle, cx, cy, centerTitlePaint)
                canvas.drawText(centerSubtitle, cx, cy + centerSubPaint.textSize * 1.5f, centerSubPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
