package com.deniscerri.ytdl.ui.analytics

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.max

/**
 * GitHub-contributions-style activity heatmap.
 * Purely presentational: renders whatever Map<LocalDate, Int> it is given.
 */
class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: Map<LocalDate, Int> = emptyMap()
    private var maxCount: Int = 1
    private val weeksToShow: Int = 20

    private val levelColors = intArrayOf(
        Color.parseColor("#0E4429"),
        Color.parseColor("#006D32"),
        Color.parseColor("#26A641"),
        Color.parseColor("#39D353")
    )

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val cellGapDp = 3f
    private val cornerRadiusDp = 3f

    private fun emptyCellColor(): Int {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return if (isDark) Color.parseColor("#2A2A2E") else Color.parseColor("#EBEDF0")
    }

    fun setData(newData: Map<LocalDate, Int>, newMaxCount: Int) {
        data = newData
        maxCount = max(newMaxCount, 1)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val gapPx = dpToPx(cellGapDp)
        val cellSize = (w - gapPx * (weeksToShow - 1)) / weeksToShow
        val h = (cellSize * 7 + gapPx * 6).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0) return

        val gapPx = dpToPx(cellGapDp)
        val cornerPx = dpToPx(cornerRadiusDp)
        val cellSize = (width - gapPx * (weeksToShow - 1)) / weeksToShow
        if (cellSize <= 0f) return

        val today = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        val startOfThisWeek = today.with(weekFields.dayOfWeek(), 1L)
        val gridStart = startOfThisWeek.minusWeeks((weeksToShow - 1).toLong())
        val emptyColor = emptyCellColor()

        for (week in 0 until weeksToShow) {
            for (day in 0 until 7) {
                val date = gridStart.plusWeeks(week.toLong()).plusDays(day.toLong())
                if (date.isAfter(today)) continue

                val count = data[date] ?: 0
                cellPaint.color = if (count <= 0) emptyColor else colorForCount(count)

                val left = week * (cellSize + gapPx)
                val top = day * (cellSize + gapPx)
                rect.set(left, top, left + cellSize, top + cellSize)
                canvas.drawRoundRect(rect, cornerPx, cornerPx, cellPaint)
            }
        }
    }

    private fun colorForCount(count: Int): Int {
        val ratio = count.toFloat() / maxCount.toFloat()
        val level = when {
            ratio <= 0.25f -> 0
            ratio <= 0.5f -> 1
            ratio <= 0.75f -> 2
            else -> 3
        }
        return levelColors[level]
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
