package rocks.pkb.plektra.inputmethod.statusbar

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Region
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import rocks.pkb.plektra.R
import rocks.pkb.plektra.SettingsManager

/** A row owns its outer edges; its renderers only supply themed surfaces. */
internal object KeyboardBarRowGeometry {
    data class CanvasSurface(val path: Path, val center: PointF, val ledShaped: Boolean)

    fun canvasSurface(view: View, bounds: RectF, radius: Float, left: Boolean, right: Boolean): CanvasSurface {
        var ancestor = view.parent
        while (ancestor != null && ancestor !is rocks.pkb.plektra.inputmethod.ImeChromeLayout) ancestor = ancestor.parent
        val chrome = ancestor as? rocks.pkb.plektra.inputmethod.ImeChromeLayout
        val location = IntArray(2).also(view::getLocationInWindow)
        val origin = IntArray(2).also { chrome?.getLocationInWindow(it) }
        val x = if (chrome != null) (location[0] - origin[0]).toFloat() else 0f
        val y = if (chrome != null) (location[1] - origin[1]).toFloat() else 0f
        val global = RectF(bounds).apply { offset(x, y) }
        val geometry = chrome?.geometry ?: rocks.pkb.plektra.inputmethod.KeyboardBarGeometry(view.context)
        val width = chrome?.width?.toFloat() ?: view.width.toFloat()
        val height = chrome?.height?.toFloat() ?: view.height.toFloat()
        val path = geometry.surfacePath(width, height, global, radius, left, right).apply { offset(-x, -y) }
        val region = Region().apply { setPath(path, Region(bounds.left.toInt(), bounds.top.toInt(),
            kotlin.math.ceil(bounds.right).toInt(), kotlin.math.ceil(bounds.bottom).toInt())) }
        return CanvasSurface(path, rocks.pkb.plektra.inputmethod.KeyboardBarGeometry.centroid(region,
            PointF(bounds.centerX(), bounds.centerY())), geometry.ownsLedSurface(global, left, right))
    }
    data class Style(val normal: Int, val pressed: Int, val radius: Float,
        val border: Int?, val borderWidth: Int, val flat: Drawable)
    private data class Surface(
        var style: Style,
        var left: Boolean = false,
        var right: Boolean = false,
        var rowOwned: Boolean = false,
        val content: View,
        val padding: IntArray,
        val textSize: Float?,
        val scaleType: ImageView.ScaleType?,
        val imageMatrix: Matrix?
    )
    private data class Row(val surfaces: List<View>)
    private data class CanvasRow(val top: Float, val bottom: Float, val radius: Float, val left: RectF, val right: RectF)

    fun bindCanvasRow(view: View, top: Float, bottom: Float, radius: Float,
                      left: RectF = RectF(0f, top, view.width / 10f, bottom),
                      right: RectF = RectF(view.width * 0.9f, top, view.width.toFloat(), bottom)) {
        view.setTag(R.id.keyboard_bar_canvas_geometry, CanvasRow(top, bottom, radius, RectF(left), RectF(right)))
    }

    fun setStyle(view: View, style: Style, fallbackLeft: Boolean = false, fallbackRight: Boolean = false,
                 content: View = view) {
        val surface = view.getTag(R.id.keyboard_bar_surface_geometry) as? Surface ?: Surface(
            style = style,
            content = content,
            padding = intArrayOf(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom),
            textSize = (content as? TextView)?.textSize,
            scaleType = (content as? ImageView)?.scaleType,
            imageMatrix = (content as? ImageView)?.let { Matrix(it.imageMatrix) }
        ).also {
            view.addOnLayoutChangeListener { changed, _, _, _, _, _, _, _, _ ->
                (changed.getTag(R.id.keyboard_bar_surface_geometry) as? Surface)?.let { geometry ->
                    placeContent(changed, geometry)
                }
            }
            val listener = ViewTreeObserver.OnPreDrawListener {
                (view.getTag(R.id.keyboard_bar_surface_geometry) as? Surface)?.let { geometry ->
                    drawSurface(view, geometry, force = false)
                    placeContent(view, geometry)
                }
                true
            }
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(attached: View) {
                    attached.viewTreeObserver.addOnPreDrawListener(listener)
                }

                override fun onViewDetachedFromWindow(detached: View) {
                    detached.viewTreeObserver.removeOnPreDrawListener(listener)
                }
            })
        }
        surface.style = style
        if (!surface.rowOwned) { surface.left = fallbackLeft; surface.right = fallbackRight }
        view.setTag(R.id.keyboard_bar_surface_geometry, surface)
        drawSurface(view, surface)
        placeContent(view, surface)
    }

    fun surfaces(group: ViewGroup?): List<View> = buildList {
        fun collect(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view.getTag(R.id.keyboard_bar_surface_geometry) is Surface) add(view)
            else if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
        group?.let(::collect)
    }

    fun style(view: View, normal: Int, pressed: Int, radius: Float, border: Int?, borderWidth: Int,
              content: View = view, left: Boolean = false, right: Boolean = false) {
        val flat = android.graphics.drawable.StateListDrawable().apply {
            fun shape(color: Int) = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = radius
                border?.let { setStroke(borderWidth, it) }
            }
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
            addState(intArrayOf(), shape(normal))
        }
        setStyle(view, Style(normal, pressed, radius, border, borderWidth, flat), left, right, content)
    }

    fun placeTrailingControl(view: View, rowHeight: Int, slots: Int = 10) {
        val width = (view.rootView.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels) / slots
        val params = (view.layoutParams as? android.widget.FrameLayout.LayoutParams)
            ?: android.widget.FrameLayout.LayoutParams(width, rowHeight)
        if (params.width != width || params.height != rowHeight ||
            params.gravity != (android.view.Gravity.BOTTOM or android.view.Gravity.END) || params.bottomMargin != 0) {
            params.width = width
            params.height = rowHeight
            params.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            params.bottomMargin = 0
            view.layoutParams = params
        }
    }

    fun bind(row: ViewGroup, surfaces: List<View>) {
        if (row.getTag(R.id.keyboard_bar_row_geometry) == null) {
            row.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update(row) }
        }
        row.setTag(R.id.keyboard_bar_row_geometry, Row(surfaces))
        surfaces.forEach { (it.getTag(R.id.keyboard_bar_surface_geometry) as? Surface)?.rowOwned = true }
        row.requestLayout()
    }

    fun updateRows(root: View) {
        if (root.visibility != View.VISIBLE) return
        if (root is ViewGroup) {
            update(root)
            for (index in 0 until root.childCount) updateRows(root.getChildAt(index))
        }
    }

    data class VisibleRow(val top: Int, val bottom: Int, val radius: Float,
        val left: rocks.pkb.plektra.inputmethod.KeyboardBarGeometry.OuterSurface?,
        val right: rocks.pkb.plektra.inputmethod.KeyboardBarGeometry.OuterSurface?)

    fun bottomRowRadius(chrome: rocks.pkb.plektra.inputmethod.ImeChromeLayout): Float? = bottomRow(chrome)?.radius

    fun bottomRowTop(chrome: rocks.pkb.plektra.inputmethod.ImeChromeLayout): Int? = bottomRow(chrome)?.top

    fun bottomRow(chrome: rocks.pkb.plektra.inputmethod.ImeChromeLayout): VisibleRow? {
        var lowest: VisibleRow? = null
        fun visit(view: View) {
            if (view.visibility != View.VISIBLE) return
            val canvasRow = view.getTag(R.id.keyboard_bar_canvas_geometry) as? CanvasRow
            val row = view.getTag(R.id.keyboard_bar_row_geometry) as? Row
            if (canvasRow != null || row != null) {
                val bounds = Rect(0, 0, view.width, view.height).also { chrome.offsetDescendantRectToMyCoords(view, it) }
                fun canvasSurface(rect: RectF) = rocks.pkb.plektra.inputmethod.KeyboardBarGeometry.OuterSurface(
                    RectF(rect).apply { offset(bounds.left.toFloat(), bounds.top.toFloat()) }, canvasRow!!.radius)
                val surfaces = row?.surfaces.orEmpty().filter { it.visibility == View.VISIBLE && it.width > 0 }.mapNotNull {
                    val style = (it.getTag(R.id.keyboard_bar_surface_geometry) as? Surface)?.style ?: return@mapNotNull null
                    val rect = Rect(0, 0, it.width, it.height).also { rect -> chrome.offsetDescendantRectToMyCoords(it, rect) }
                    rocks.pkb.plektra.inputmethod.KeyboardBarGeometry.OuterSurface(RectF(rect), style.radius, style.borderWidth / 2f)
                }.sortedBy { it.bounds.left }
                val candidate = if (canvasRow != null) VisibleRow(bounds.top + canvasRow.top.toInt(),
                    bounds.top + canvasRow.bottom.toInt(), canvasRow.radius,
                    canvasSurface(canvasRow.left), canvasSurface(canvasRow.right))
                else VisibleRow(bounds.top + view.paddingTop, bounds.bottom,
                    surfaces.firstOrNull()?.radius ?: 0f, surfaces.firstOrNull(), surfaces.lastOrNull())
                if (lowest == null || candidate.bottom >= lowest!!.bottom - 1) lowest = candidate
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        for (index in 0 until chrome.childCount) visit(chrome.getChildAt(index))
        return lowest
    }

    private fun update(row: ViewGroup) {
        val binding = row.getTag(R.id.keyboard_bar_row_geometry) as? Row ?: return
        if (row.width <= 0) return
        val visible = binding.surfaces.filter { it.visibility == View.VISIBLE && it.parent != null }.map { view ->
            view to Rect(0, 0, view.width, view.height).also { row.offsetDescendantRectToMyCoords(view, it) }
        }.filter { it.second.width() > 0 }
        val first = visible.minByOrNull { it.second.left }
        val last = visible.maxByOrNull { it.second.right }
        val tolerance = 3f * row.resources.displayMetrics.density
        visible.forEach { (view, bounds) ->
            val surface = view.getTag(R.id.keyboard_bar_surface_geometry) as? Surface ?: return@forEach
            val left = view === first?.first && bounds.left <= row.paddingLeft + tolerance
            val right = view === last?.first && bounds.right >= row.width - row.paddingRight - tolerance
            if (surface.left != left || surface.right != right) {
                surface.left = left; surface.right = right
                drawSurface(view, surface)
            }
            placeContent(view, surface)
        }
    }

    private fun drawSurface(view: View, surface: Surface, force: Boolean = true) {
        val style = surface.style
        val rounded = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(view.context) &&
            (surface.left || surface.right)
        val current = view.background as? CurvedCornerButtonDrawable
        if (!force && rounded && current != null &&
            current.leftEdge == surface.left && current.rightEdge == surface.right) return
        if (!force && !rounded && current == null) return
        view.background = if (rounded) CurvedCornerButtonDrawable(
            view, style.normal, style.pressed, style.radius,
            style.border, style.borderWidth, surface.left, surface.right
        ) else style.flat
    }

    private fun placeContent(view: View, surface: Surface) {
        val content = surface.content
        val contour = view.background as? CurvedCornerButtonDrawable
        if (view.width <= 0 || view.height <= 0 || (contour == null && content !is TextView)) {
            restoreContent(content, surface)
            return
        }
        var opticalOffsetX = 0f
        var opticalOffsetY = 0f
        val contentSize = when (content) {
            is ImageView -> content.drawable?.takeIf { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }?.let {
                it.intrinsicWidth.toFloat() to it.intrinsicHeight.toFloat()
            }
            is TextView -> {
                val paint = TextPaint(content.paint).apply { surface.textSize?.let { textSize = it } }
                val text = content.text?.toString().orEmpty()
                if (text.isEmpty()) { restoreContent(content, surface); return }
                val textWidth = paint.measureText(text)
                val ink = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
                val metrics = paint.fontMetrics
                opticalOffsetX = ink.exactCenterX() - textWidth / 2f
                opticalOffsetY = ink.exactCenterY() -
                    if (content.includeFontPadding) (metrics.top + metrics.bottom) / 2f else (metrics.ascent + metrics.descent) / 2f
                val drawables = content.compoundDrawables.filterNotNull()
                val drawableWidth = drawables.sumOf { it.intrinsicWidth.coerceAtLeast(0) }.toFloat() +
                    if (textWidth > 0f && drawables.isNotEmpty()) content.compoundDrawablePadding.toFloat() else 0f
                val drawableHeight = drawables.maxOfOrNull { it.intrinsicHeight.coerceAtLeast(0) }?.toFloat() ?: 0f
                if (drawables.isNotEmpty()) { opticalOffsetX = 0f; opticalOffsetY = 0f }
                (maxOf(ink.width().toFloat(), textWidth) + drawableWidth) to maxOf(ink.height().toFloat(), drawableHeight)
            }
            else -> null
        } ?: return
        val preferredX = view.width / 2f
        val preferredY = view.height / 2f
        val placement = contour?.contentCenter(
            contentSize.first.coerceAtMost(view.width.toFloat()),
            contentSize.second.coerceAtMost(view.height.toFloat()),
            preferredX,
            preferredY,
            contour.leftEdge
        ) ?: CurvedCornerButtonDrawable.ContentPlacement(preferredX, preferredY, 1f)
        when (content) {
            is ImageView -> {
                val drawable = content.drawable ?: return
                val baseScale = minOf(
                    1f,
                    contentSize.first / drawable.intrinsicWidth,
                    contentSize.second / drawable.intrinsicHeight
                )
                val scale = baseScale * placement.scale
                content.scaleType = ImageView.ScaleType.MATRIX
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(
                        placement.x - drawable.intrinsicWidth * scale / 2f - content.paddingLeft - content.left.takeIf { content !== view }.orZero(),
                        placement.y - drawable.intrinsicHeight * scale / 2f - content.paddingTop - content.top.takeIf { content !== view }.orZero()
                    )
                }
                if (content.imageMatrix != matrix) content.imageMatrix = matrix
            }
            is TextView -> {
                surface.textSize?.let {
                    val target = it * placement.scale
                    if (content.textSize != target) content.setTextSize(TypedValue.COMPLEX_UNIT_PX, target)
                }
                val dx = placement.x - preferredX - opticalOffsetX * placement.scale
                val dy = placement.y - preferredY - opticalOffsetY * placement.scale
                val left = surface.padding[0] + kotlin.math.round(2f * dx.coerceAtLeast(0f)).toInt()
                val right = surface.padding[2] + kotlin.math.round(-2f * dx.coerceAtMost(0f)).toInt()
                val top = surface.padding[1] + kotlin.math.round(2f * dy.coerceAtLeast(0f)).toInt()
                val bottom = surface.padding[3] + kotlin.math.round(-2f * dy.coerceAtMost(0f)).toInt()
                if (content.paddingLeft != left || content.paddingTop != top ||
                    content.paddingRight != right || content.paddingBottom != bottom) {
                    content.setPadding(left, top, right, bottom)
                }
            }
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun restoreContent(view: View, surface: Surface) {
        if (view.paddingLeft != surface.padding[0] || view.paddingTop != surface.padding[1] ||
            view.paddingRight != surface.padding[2] || view.paddingBottom != surface.padding[3]) {
            view.setPadding(surface.padding[0], surface.padding[1], surface.padding[2], surface.padding[3])
        }
        if (view is TextView && surface.textSize != null && view.textSize != surface.textSize) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, surface.textSize)
        }
        if (view is ImageView && surface.scaleType != null) {
            view.scaleType = surface.scaleType
            surface.imageMatrix?.let { if (view.imageMatrix != it) view.imageMatrix = Matrix(it) }
        }
    }
}
