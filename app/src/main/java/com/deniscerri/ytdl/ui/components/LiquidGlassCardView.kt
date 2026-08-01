package com.deniscerri.ytdl.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import com.deniscerri.ytdl.R

/**
 * Reusable "Liquid Glass" (iOS-style acrylic / glassmorphism) card.
 *
 * On Android 12+ (API 31) it captures a snapshot of what is rendered behind it
 * via PixelCopy and blurs it with RenderEffect. This is a refreshable snapshot
 * blur, not continuous per-frame backdrop blur - Android exposes no true live
 * per-view backdrop blur. Call refreshGlass() when the content behind changes.
 *
 * Below API 31, or if capture fails, it degrades to a plain frosted translucent
 * card. It never throws and is fully additive/opt-in.
 */
class LiquidGlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backdropImageView: ImageView
    private val tintOverlay: View

    private val cornerRadiusPx: Float
    private val blurRadiusPx: Float
    private val tintAlpha: Float
    private val autoCapture: Boolean

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassCardView)
        cornerRadiusPx = ta.getDimension(R.styleable.LiquidGlassCardView_glassCornerRadius, dp(24f))
        blurRadiusPx = ta.getDimension(R.styleable.LiquidGlassCardView_glassBlurRadius, dp(18f))
        tintAlpha = ta.getFloat(R.styleable.LiquidGlassCardView_glassTintAlpha, 0.55f)
        autoCapture = ta.getBoolean(R.styleable.LiquidGlassCardView_glassAutoCapture, true)
        ta.recycle()

        setWillNotDraw(false)
        clipToOutline = true

        backdropImageView = ImageView(context)
        backdropImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        backdropImageView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(backdropImageView)

        tintOverlay = View(context)
        tintOverlay.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(tintOverlay)

        applyOutlineAndTint()
        setupPressInteraction()
    }

    private fun applyOutlineAndTint() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val baseColor = if (isDark) Color.parseColor("#1C1C1E") else Color.WHITE
        val tinted = Color.argb(
            (255 * tintAlpha).toInt(),
            Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)
        )
        tintOverlay.setBackgroundColor(tinted)

        val strokeArgb = if (isDark) Color.argb(70, 255, 255, 255) else Color.argb(60, 0, 0, 0)
        val border = GradientDrawable()
        border.shape = GradientDrawable.RECTANGLE
        border.cornerRadius = cornerRadiusPx
        border.setStroke(dp(1f).toInt(), strokeArgb)
        border.setColor(Color.TRANSPARENT)
        foreground = border

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoCapture) {
            post { refreshGlass() }
        }
    }

    /** Re-captures and re-blurs the content behind this card. Safe to call repeatedly. */
    fun refreshGlass() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (width <= 0 || height <= 0) return
        runCatching { captureAndBlur() }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun captureAndBlur() {
        val activity = findActivity(context) ?: return
        val window = activity.window ?: return

        val location = IntArray(2)
        getLocationInWindow(location)
        val rect = Rect(location[0], location[1], location[0] + width, location[1] + height)
        if (rect.width() <= 0 || rect.height() <= 0) return

        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)

        // Hide for a single frame so the capture excludes the card itself.
        visibility = INVISIBLE
        post {
            runCatching {
                PixelCopy.request(window, rect, bitmap, { result ->
                    visibility = VISIBLE
                    if (result == PixelCopy.SUCCESS) {
                        runCatching { applyBlurredBitmap(bitmap) }
                    }
                }, Handler(Looper.getMainLooper()))
            }.onFailure {
                visibility = VISIBLE
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyBlurredBitmap(bitmap: Bitmap) {
        backdropImageView.setImageBitmap(bitmap)
        backdropImageView.setRenderEffect(
            RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
        )
    }

    private fun setupPressInteraction() {
        // Fluid micro-interaction: gentle scale/alpha response on touch.
        // Returns false so clicks and child listeners still work normally.
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.9f).setDuration(120).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
            }
            false
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun findActivity(ctx: Context): Activity? {
        var c = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
