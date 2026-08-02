package com.deniscerri.ytdl.ui.analytics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Calendar

/**
 * GitHub contribution graph style heatmap. Weeks run left to right, days top to bottom.
 * Purely custom drawn so it needs no extra dependency.
 */
class ActivityHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(9f)
        color = Color.GRAY
    }
    private val rect = RectF()

    /** day-start-millis -> count */
    private var data: Map<Long, Int> = emptyMap()
    private var days: List<Long> = emptyList()
    private var monthLabels: List<Pair<Int, String>> = emptyList()
    private var maxCount = 1

    private var weeks = 26
    private val cellRadius = dp(3f)

    /** Called with the day millis and its count when a cell is tapped. */
    var onCellTapped: ((Long, Int) -> Unit)? = null

    /** Empty-cell colour and the darkest colour of the ramp. */
    var emptyColor: Int = Color.parseColor("#26808080")
    var rampColor: Int = Color.parseColor("#2EA043")

    private fun dp(v: Float) = v * resources.displayMetrics.density

    fun setData(newData: Map<Long, Int>, weeksToShow: Int = 26) {
        data = newData
        weeks = weeksToShow.coerceAtLeast(4)
        maxCount = (newData.values.maxOrNull() ?: 1).coerceAtLeast(1)
        rebuildDays()
        requestLayout()
        invalidate()
    }

    private fun rebuildDays() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // advance to the end of the current week so columns line up as whole weeks
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val end = cal.timeInMillis
        val totalDays = weeks * 7
        val list = ArrayList<Long>(totalDays)
        val walker = Calendar.getInstance()
        walker.timeInMillis = end
        walker.add(Calendar.DAY_OF_YEAR, -(totalDays - 1))

        val labels = ArrayList<Pair<Int, String>>()
        val monthNames = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        var lastMonth = -1
        for (i in 0 until totalDays) {
            list.add(walker.timeInMillis)
            if (i % 7 == 0) {
                val m = walker.get(Calendar.MONTH)
                if (m != lastMonth) {
                    labels.add((i / 7) to monthNames[m])
                    lastMonth = m
                }
            }
            walker.add(Calendar.DAY_OF_YEAR, 1)
        }
        days = list
        monthLabels = labels
    }

    private fun cellSizeFor(w: Int): Float {
        val spacing = dp(3f)
        val available = w - paddingLeft - paddingRight
        return ((available - spacing * (weeks - 1)) / weeks.toFloat()).coerceAtLeast(dp(4f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (days.isEmpty()) rebuildDays()
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val cell = cellSizeFor(w)
        val spacing = dp(3f)
        val labelRow = dp(14f)
        val h = (labelRow + cell * 7 + spacing * 6 + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return

        val cell = cellSizeFor(width)
        val spacing = dp(3f)
        val labelRow = dp(14f)

        monthLabels.forEach { (weekIndex, name) ->
            val x = paddingLeft + weekIndex * (cell + spacing)
            canvas.drawText(name, x, paddingTop + dp(9f), labelPaint)
        }

        days.forEachIndexed { index, day ->
            val col = index / 7
            val row = index % 7
            val x = paddingLeft + col * (cell + spacing)
            val y = paddingTop + labelRow + row * (cell + spacing)
            rect.set(x, y, x + cell, y + cell)
            cellPaint.color = colorFor(data[day] ?: 0)
            canvas.drawRoundRect(rect, cellRadius, cellRadius, cellPaint)
        }
    }

    private fun colorFor(count: Int): Int {
        if (count <= 0) return emptyColor
        // four buckets, like GitHub
        val ratio = count.toFloat() / maxCount.toFloat()
        val level = when {
            ratio <= 0.25f -> 0
            ratio <= 0.5f -> 1
            ratio <= 0.75f -> 2
            else -> 3
        }
        val alpha = intArrayOf(70, 130, 195, 255)[level]
        return Color.argb(
            alpha,
            Color.red(rampColor),
            Color.green(rampColor),
            Color.blue(rampColor)
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || days.isEmpty()) {
            return event.action == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
        }
        val cell = cellSizeFor(width)
        val spacing = dp(3f)
        val labelRow = dp(14f)
        val col = ((event.x - paddingLeft) / (cell + spacing)).toInt()
        val row = ((event.y - paddingTop - labelRow) / (cell + spacing)).toInt()
        if (col < 0 || row < 0 || row > 6) return true
        val index = col * 7 + row
        if (index in days.indices) {
            val day = days[index]
            onCellTapped?.invoke(day, data[day] ?: 0)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
