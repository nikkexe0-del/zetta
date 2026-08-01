package com.deniscerri.ytdl.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
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
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import com.deniscerri.ytdl.R
import com.google.android.material.color.MaterialColors

/**
 * A reusable "Liquid Glass" (iOS-style acrylic/glassmorphism) card.
 *
 * How the blur works:
 * - On Android 12+ (API 31), it captures a one-off screenshot of whatever is
 *   currently rendered directly behind it (via [PixelCopy]) and blurs that
 *   snapshot with [RenderEffect]. This is a *refreshable snapshot* blur, not a
 *   continuous per-frame backdrop blur - Android does not expose true live
 *   per-view backdrop blur, only whole-window "blur behind" for windows/dialogs.
 *   Call [refreshGlass] any time the content behind it changes (e.g. after a
 *   scroll settles, or when the screen first loads) to re-capture it.
 * - On API < 31, or if the capture fails for any reason, it silently falls
 *   back to a plain frosted, translucent card with no blur. It never throws
 *   and never blocks the UI thread with anything but a lightweight bitmap copy.
 *
 * This view is entirely additive/opt-in: it does not modify any existing
 * screens, and failure to blur (older device, capture error, wrong container)
 * simply results in a normal-looking translucent card, never a crash.
 */
class LiquidGlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backdropImageView: ImageView
    private val tintOverlay: View

    private var cornerRadiusPx: Float
    private var blurRadiusPx: Float
    private var tintAlpha: Float
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

        backdropImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(backdropImageView)

        tintOverlay = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(tintOverlay)

        applyOutlineAndTint()
        setupPressInteraction()
    }

    private fun applyOutlineAndTint() {
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val baseColor = if (isDark) Color.parseColor("#1C1C1E") else Color.WHITE
        val tinted = Color.argb((255 * tintAlpha).toInt(),
            Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        tintOverlay.setBackgroundColor(tinted)

        val strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, Color.LTGRAY)
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setStroke(dp(1f).toInt(), Color.argb(90, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)))
            setColor(Color.TRANSPARENT)
        }
        foreground = border

        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
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

    /**
     * Re-captures and re-blurs whatever is currently rendered behind this card.
     * Safe to call as often as needed (e.g. after data loads or a scroll settles) -
     * it no-ops gracefully on unsupported API levels or if capture isn't possible.
     */
    fun refreshGlass() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (width <= 0 || height <= 0) return
        runCatching { captureAndBlur() }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun captureAndBlur() {
        val activity = context.findActivity() ?: return
        val window = activity.window ?: return

        val location = IntArray(2)
        getLocationInWindow(location)
        val rect = Rect(location[0], location[1], location[0] + width, location[1] + height)
        if (rect.width() <= 0 || rect.height() <= 0) return

        val wasVisible = isVisible
        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)

        // Hide this card for a single frame so the capture only contains
        // whatever is behind it, not the card itself.
        visibility = INVISIBLE
        post {
            runCatching {
                PixelCopy.request(window, rect, bitmap, { result ->
                    visibility = if (wasVisible) VISIBLE else INVISIBLE
                    if (result == PixelCopy.SUCCESS) {
                        runCatching { applyBlurredBitmap(bitmap) }
                    }
                }, Handler(Looper.getMainLooper()))
            }.onFailure {
                visibility = if (wasVisible) VISIBLE else INVISIBLE
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
        // Fluid micro-interaction: a gentle scale + alpha response on touch,
        // reminiscent of iOS/shadcn press states. Pure view-property animation,
        // no dependency on blur support, so it always works.
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.9f).setDuration(120).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
                }
            }
            false // don't consume - let clicks/other listeners still work normally
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
