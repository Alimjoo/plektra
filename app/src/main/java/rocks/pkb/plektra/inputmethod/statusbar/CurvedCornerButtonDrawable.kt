package rocks.pkb.plektra.inputmethod.statusbar

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import rocks.pkb.plektra.inputmethod.ImeChromeLayout

/** Draws the button and its border inside the display and LED contours. */
internal class CurvedCornerButtonDrawable(
    private val view: View,
    private val normalColor: Int,
    private val pressedColor: Int,
    private val cornerRadius: Float,
    private val borderColor: Int?,
    private val borderWidth: Int,
    val leftEdge: Boolean,
    val rightEdge: Boolean = !leftEdge
) : Drawable() {
    private var drawableAlpha = 255
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPath = Path()
    private val location = IntArray(2)
    private val chromeLocation = IntArray(2)
    private var lastPlacementKey: List<Any>? = null
    private var lastPlacement = ContentPlacement(0f, 0f, 1f)
    data class ContentPlacement(val x: Float, val y: Float, val scale: Float)


    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }

    private fun updatePath() {
        val halfStroke = borderWidth / 2f
        val rect = RectF(bounds).apply { inset(halfStroke, halfStroke) }
        var ancestor = view.parent
        while (ancestor != null && ancestor !is ImeChromeLayout) ancestor = ancestor.parent
        val chrome = ancestor as? ImeChromeLayout
        val geometry = chrome?.geometry ?: rocks.pkb.plektra.inputmethod.KeyboardBarGeometry(view.context)
        view.getLocationInWindow(location)
        chrome?.getLocationInWindow(chromeLocation) ?: chromeLocation.fill(0)
        val x = if (chrome != null) (location[0] - chromeLocation[0]).toFloat() else 0f
        val y = if (chrome != null) (location[1] - chromeLocation[1]).toFloat() else 0f
        rect.offset(x, y)
        buttonPath.set(geometry.surfacePath(chrome?.width?.toFloat() ?: view.width.toFloat(),
            chrome?.height?.toFloat() ?: view.height.toFloat(), rect, cornerRadius, leftEdge, rightEdge, halfStroke))
        buttonPath.offset(-x, -y)
    }

    /** Center the visible content in the remaining shape, then fit its complete ink box. */
    fun contentCenter(contentWidth: Float, contentHeight: Float, preferredX: Float,
                      preferredY: Float, leftEdge: Boolean): ContentPlacement {
        setBounds(0, 0, view.width, view.height)
        updatePath()
        val minScale = 1f - rocks.pkb.plektra.SettingsManager.getTitan2EliteMaxIconShrink(view.context) / 100f
        val key = listOf(minScale,view.width.toFloat(), view.height.toFloat(), contentWidth, contentHeight,
            preferredX, preferredY, location[0].toFloat(), location[1].toFloat(),
            chromeLocation[0].toFloat(), chromeLocation[1].toFloat(),
            buttonPath.approximate(0.5f).contentHashCode(), if (leftEdge) 1f else 0f)
        if (lastPlacementKey == key) return lastPlacement
        val region = android.graphics.Region().apply {
            setPath(buttonPath, android.graphics.Region(this@CurvedCornerButtonDrawable.bounds))
        }
        val center = rocks.pkb.plektra.inputmethod.KeyboardBarGeometry.centroid(region, PointF(preferredX, preferredY))
        val gap = 2f * view.resources.displayMetrics.density + borderWidth / 2f
        fun findPlacement(scale: Float): ContentPlacement? {
            val halfWidth = contentWidth * scale / 2f + gap
            val halfHeight = contentHeight * scale / 2f + gap
            fun fits(x: Float, y: Float): Boolean =
                region.contains(kotlin.math.floor(x - halfWidth).toInt(), kotlin.math.floor(y - halfHeight).toInt()) &&
                region.contains(kotlin.math.ceil(x + halfWidth).toInt(), kotlin.math.floor(y - halfHeight).toInt()) &&
                region.contains(kotlin.math.floor(x - halfWidth).toInt(), kotlin.math.ceil(y + halfHeight).toInt()) &&
                region.contains(kotlin.math.ceil(x + halfWidth).toInt(), kotlin.math.ceil(y + halfHeight).toInt())
            if (fits(center.x, center.y)) return ContentPlacement(center.x, center.y, scale)
            var best: ContentPlacement? = null
            var bestDistance = Float.POSITIVE_INFINITY
            for (yPixel in halfHeight.toInt()..(view.height - halfHeight).toInt()) {
                for (xPixel in halfWidth.toInt()..(view.width - halfWidth).toInt()) {
                    val x = xPixel.toFloat()
                    val y = yPixel.toFloat()
                    val distance = (x - center.x) * (x - center.x) + (y - center.y) * (y - center.y)
                    if (distance >= bestDistance) continue
                    if (fits(x, y)) {
                        bestDistance = distance
                        best = ContentPlacement(x, y, scale)
                    }
                }
            }
            return best
        }
        // Search permitted sizes first. If none fits the contour, honor the
        // user's size limit while keeping the content inside the button bounds.
        val minimumPercent = kotlin.math.round(minScale * 100f).toInt()
        val permitted = (100 downTo minimumPercent step 2).firstNotNullOfOrNull { findPlacement(it / 100f) }
            ?: findPlacement(minScale)
        val limited = permitted ?: run {
            val smaller = (minimumPercent downTo 10 step 2).firstNotNullOfOrNull { findPlacement(it / 100f) }
                ?: ContentPlacement(preferredX, preferredY, minScale)
            val halfWidth = (contentWidth * minScale / 2f).coerceAtMost(view.width / 2f)
            val halfHeight = (contentHeight * minScale / 2f).coerceAtMost(view.height / 2f)
            ContentPlacement(
                smaller.x.coerceIn(halfWidth, view.width - halfWidth),
                smaller.y.coerceIn(halfHeight, view.height - halfHeight), minScale)
        }
        lastPlacementKey = key
        lastPlacement = limited
        return limited
    }

    override fun draw(canvas: Canvas) {
        updatePath()
        paint.style = Paint.Style.FILL
        paint.color = if (state.contains(android.R.attr.state_pressed)) pressedColor else normalColor
        paint.alpha = android.graphics.Color.alpha(paint.color) * drawableAlpha / 255
        canvas.drawPath(buttonPath, paint)
        if (borderColor != null && borderWidth > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderWidth.toFloat()
            paint.color = borderColor
            paint.alpha = android.graphics.Color.alpha(paint.color) * drawableAlpha / 255
            canvas.drawPath(buttonPath, paint)
        }
    }

    fun visibleCenter(): PointF {
        setBounds(0, 0, view.width, view.height)
        updatePath()
        val region = android.graphics.Region().apply {
            setPath(buttonPath, android.graphics.Region(this@CurvedCornerButtonDrawable.bounds))
        }
        return rocks.pkb.plektra.inputmethod.KeyboardBarGeometry.centroid(region,
            PointF(view.width / 2f, view.height / 2f))
    }

    override fun setAlpha(alpha: Int) { drawableAlpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
