package rocks.pkb.plektra.inputmethod

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.CursorAnchorInfo
import kotlin.math.max
import kotlin.math.roundToInt

internal class SmoothCaretOverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val density = appContext.resources.displayMetrics.density
    private val widthPx = max(3, (2f * density).roundToInt())
    private val caretView = View(appContext).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = widthPx.toFloat()
            setColor(if (rocks.pkb.plektra.BuildConfig.RELEASE_CHANNEL == "nightly") 0xFF2F80ED.toInt() else 0xFFFC7A0F.toInt())
        }
        alpha = 0f
    }
    private val params = WindowManager.LayoutParams(
        widthPx,
        (20f * density).roundToInt(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 0
    }
    private var attached = false
    private var animator: ValueAnimator? = null
    private var x = 0f
    private var y = 0f
    private var height = params.height.toFloat()

    fun canShow(): Boolean = Settings.canDrawOverlays(appContext)

    fun update(info: CursorAnchorInfo?) {
        if (info == null || !canShow()) {
            hide()
            return
        }
        val horizontal = info.insertionMarkerHorizontal
        val top = info.insertionMarkerTop
        val bottom = info.insertionMarkerBottom
        if (!horizontal.isFinite() || !top.isFinite() || !bottom.isFinite() || bottom <= top) {
            hide()
            return
        }
        val points = floatArrayOf(horizontal, top, horizontal, bottom)
        Matrix(info.matrix).mapPoints(points)
        val targetX = points[0] - widthPx / 2f
        val targetY = minOf(points[1], points[3])
        val targetHeight = max(1f, kotlin.math.abs(points[3] - points[1]))
        showAt(targetX, targetY, targetHeight)
    }

    fun hide() {
        animator?.cancel()
        caretView.alpha = 0f
    }

    fun release() {
        animator?.cancel()
        animator = null
        if (attached) runCatching { windowManager.removeView(caretView) }
        attached = false
    }

    private fun showAt(targetX: Float, targetY: Float, targetHeight: Float) {
        if (!attached) {
            x = targetX
            y = targetY
            height = targetHeight
            applyLayout()
            windowManager.addView(caretView, params)
            attached = true
            caretView.alpha = 1f
            return
        }
        val startX = x
        val startY = y
        val startHeight = height
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 110L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                x = startX + (targetX - startX) * fraction
                y = startY + (targetY - startY) * fraction
                height = startHeight + (targetHeight - startHeight) * fraction
                applyLayout()
            }
            start()
        }
        caretView.alpha = 1f
    }

    private fun applyLayout() {
        params.x = x.roundToInt()
        params.y = y.roundToInt()
        params.height = height.roundToInt().coerceAtLeast(1)
        if (attached) runCatching { windowManager.updateViewLayout(caretView, params) }
    }
}
