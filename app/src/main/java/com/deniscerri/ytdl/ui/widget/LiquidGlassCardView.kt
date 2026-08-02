package com.deniscerri.ytdl.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import com.deniscerri.ytdl.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * iOS style "liquid glass" surface.
 *
 * On Android 12+ the card's own backdrop is blurred with a [RenderEffect], which is what gives the
 * acrylic frosting that distorts whatever sits behind it. Below that the view degrades gracefully
 * to a translucent tinted surface, so nothing breaks on older devices.
 *
 * A soft top-to-bottom sheen and a hairline highlight stroke are drawn on top to sell the depth.
 */
class LiquidGlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var sheenShader: LinearGradient? = null
    private var blurRadius = 22f

    /** Values set explicitly in XML win over the defaults applied below. */
    private var explicitRadius = -1f
    private var explicitTint = 0
    private var hasExplicitTint = false

    init {
        setWillNotDraw(false)
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(
                attrs,
                intArrayOf(
                    com.google.android.material.R.attr.cardCornerRadius,
                    com.google.android.material.R.attr.cardBackgroundColor
                )
            )
            explicitRadius = ta.getDimension(0, -1f)
            if (ta.hasValue(1)) {
                explicitTint = ta.getColor(1, 0)
                hasExplicitTint = true
            }
            ta.recycle()
        }
        applyGlass()
    }

    private fun isLightTheme(): Boolean {
        val bg = MaterialColors.getColor(this, android.R.attr.colorBackground, Color.WHITE)
        return androidx.core.graphics.ColorUtils.calculateLuminance(bg) > 0.5
    }

    private fun applyGlass() {
        val light = isLightTheme()

        radius = if (explicitRadius >= 0f) explicitRadius
                 else resources.getDimension(R.dimen.glass_corner_radius)
        cardElevation = 0f
        strokeWidth = 0

        // Tint: a translucent wash of the theme surface so content behind still reads through.
        val surface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            if (light) Color.WHITE else Color.BLACK
        )
        if (hasExplicitTint) {
            setCardBackgroundColor(explicitTint)
        } else {
            val tintAlpha = if (light) 0.62f else 0.42f
            setCardBackgroundColor(
                Color.argb(
                    (255 * tintAlpha).toInt(),
                    Color.red(surface),
                    Color.green(surface),
                    Color.blue(surface)
                )
            )
        }

        strokePaint.color = if (light) Color.argb(70, 255, 255, 255) else Color.argb(46, 255, 255, 255)
        strokePaint.strokeWidth = resources.displayMetrics.density * 1f
    }

    /**
     * Blurs whatever is rendered *behind* this card. Call with the container that should be
     * frosted; passing null clears it. Kept explicit because blurring the card's own content
     * would smear the text inside it.
     */
    fun frost(backdrop: android.view.View?, radiusPx: Float = 22f) {
        blurRadius = radiusPx
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (backdrop == null) return
        runCatching {
            backdrop.setRenderEffect(
                RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val light = isLightTheme()
        sheenShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                if (light) Color.argb(96, 255, 255, 255) else Color.argb(52, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.65f),
            Shader.TileMode.CLAMP
        )
        sheenPaint.shader = sheenShader
    }

    override fun dispatchDraw(canvas: Canvas) {
        val r = radius
        // sheen under the content
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, sheenPaint)
        super.dispatchDraw(canvas)
        // hairline highlight on top
        val inset = strokePaint.strokeWidth / 2f
        canvas.drawRoundRect(
            inset, inset, width - inset, height - inset, r, r, strokePaint
        )
    }
}
