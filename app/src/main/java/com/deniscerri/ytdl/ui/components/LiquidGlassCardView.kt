package com.deniscerri.ytdl.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.deniscerri.ytdl.R
import com.google.android.material.color.MaterialColors

/**
 * A reusable frosted "glass" (iOS-style) card.
 *
 * IMPORTANT: This intentionally does NOT do any runtime backdrop capture/blur.
 * An earlier version used PixelCopy to snapshot and blur whatever was behind the
 * card, toggling its own visibility each capture. On some devices that produced a
 * visible dim/bright flicker, so that approach was removed entirely.
 *
 * What remains is a purely static, translucent frosted card with a soft rounded
 * outline and a gentle press animation. It never captures the screen, never
 * toggles visibility, and never invalidates itself on a loop - so it cannot
 * flicker. It also has zero effect on any download / backend behaviour.
 */
class LiquidGlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var cornerRadiusPx: Float
    private var tintAlpha: Float

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassCardView)
        cornerRadiusPx = ta.getDimension(R.styleable.LiquidGlassCardView_glassCornerRadius, dp(24f))
        // glassBlurRadius / glassAutoCapture are kept in attrs for compatibility but no longer used.
        tintAlpha = ta.getFloat(R.styleable.LiquidGlassCardView_glassTintAlpha, 0.72f)
        ta.recycle()

        clipToOutline = true
        applyFrostedBackground()
        setupPressInteraction()
    }

    private fun applyFrostedBackground() {
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // Prefer theme surface color so it fits every accent/theme, with a sensible fallback.
        val surface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            if (isDark) Color.parseColor("#1C1C1E") else Color.WHITE
        )
        val tinted = Color.argb(
            (255 * tintAlpha).toInt().coerceIn(0, 255),
            Color.red(surface), Color.green(surface), Color.blue(surface)
        )

        val strokeColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOutline, Color.LTGRAY
        )

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(tinted)
            setStroke(
                dp(1f).toInt(),
                Color.argb(70, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor))
            )
        }
        background = bg

        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
    }

    /**
     * No-op kept for source compatibility with any caller that previously asked the
     * card to re-capture its backdrop. There is deliberately nothing to refresh now.
     */
    fun refreshGlass() { /* intentionally no-op */ }

    private fun setupPressInteraction() {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.94f).setDuration(120).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
            }
            false // never consume - clicks and child handling still work normally
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        // Re-tint once on light/dark switch. One-shot, not a loop.
        applyFrostedBackground()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
