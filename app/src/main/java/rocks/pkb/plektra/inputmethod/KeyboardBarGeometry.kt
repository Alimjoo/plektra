package rocks.pkb.plektra.inputmethod

import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Paint
import rocks.pkb.plektra.inputmethod.ui.ModifierLedLayouts
import rocks.pkb.plektra.inputmethod.ui.ModifierLedLayout
import rocks.pkb.plektra.inputmethod.ui.ModifierLedSegment
import android.graphics.Region
import android.graphics.RegionIterator
import android.graphics.Rect
import android.graphics.RectF
import rocks.pkb.plektra.T2eCornerCalibration
import rocks.pkb.plektra.T2eCornerGeometry
import rocks.pkb.plektra.inputmethod.ui.LedStatusView
import rocks.pkb.plektra.SettingsManager
import kotlin.math.PI
import kotlin.math.hypot

/** Shared display boundary and the surfaces and rails owned by the outer row slots. */
internal class KeyboardBarGeometry(private val context: Context) {
    data class OuterSurface(val bounds: RectF, val radius: Float, val borderHalfWidth: Float = 0f)
    var radii: Pair<Int, Int>? = null
    var ledsVisible = false
    var hardwareTopRounding = true
    var ledRowTop: Float? = null
    var ledRowRadius = 0f
    var leftSurface: OuterSurface? = null
    var rightSurface: OuterSurface? = null
    var ledRadii: Pair<Int, Int>? = null
    var ledLayout: ModifierLedLayout = ModifierLedLayouts.DEFAULT
    var ledContentHeight = 8f
    var ledTopPadding = 1f
    var followDisplayContour = true

    /** The physical display boundary is independent of LED visibility. */
    fun displayPath(width: Float, height: Float, strokeInset: Float = 0f): Path? {
        val corners = radii ?: return null
        val calibration = T2eCornerCalibration.read(context)
        return T2eCornerGeometry.path(width, height, corners.first.toFloat(), corners.second.toFloat(),
            calibration, strokeInset).apply {
            addRect(corners.first * calibration.size, -height,
                width - corners.second * calibration.size, height - strokeInset, Path.Direction.CW)
        }
    }

    fun contentPath(width: Float, height: Float, strokeInset: Float = 0f): Path? =
        displayPath(width, height, strokeInset)

    fun contains(width: Float, height: Float, x: Float, y: Float): Boolean {
        val path = contentPath(width, height) ?: return true
        val region = Region().apply { setPath(path, Region(0, 0,
            kotlin.math.ceil(width).toInt(), kotlin.math.ceil(height).toInt())) }
        if (!region.contains(kotlin.math.floor(x).toInt(), kotlin.math.floor(y).toInt())) return false
        if (!ledsVisible) return true
        return !Region().apply {
            setPath(ledExclusionPath(width, height), Region(0, 0, width.toInt(), height.toInt()))
        }.contains(kotlin.math.floor(x).toInt(), kotlin.math.floor(y).toInt())
    }

    fun reservedLedHeight(straightStripHeight: Int): Int =
        if (followDisplayContour && (radii != null || ledsVisible)) 0 else straightStripHeight

    data class Transition(val start: PointF, val firstHandle: PointF, val secondHandle: PointF, val join: PointF)

    fun ledStrokeWidth(segment: ModifierLedSegment): Float =
        if (followDisplayContour && ledLayout == ModifierLedLayouts.TITAN_2_ELITE)
            LedStatusView.CONTOUR_LED_STROKE_DP * context.resources.displayMetrics.density
        else segment.height * ledContentHeight

    private fun sideRails(left: Boolean) = ledLayout.segments.filter { (it.x < 0.5f) == left }
        .distinctBy { it.y }.sortedBy { it.y }

    private fun buttonInset(left: Boolean): Float {
        val density = context.resources.displayMetrics.density
        val rails = sideRails(left)
        return density + rails.sumOf { ledStrokeWidth(it).toDouble() }.toFloat() +
            rails.size * LedStatusView.CONTOUR_LED_GAP_DP * density
    }

    private fun outerSurface(width: Float, height: Float, left: Boolean): OuterSurface {
        (if (left) leftSurface else rightSurface)?.let { return it }
        val calibration = T2eCornerCalibration.read(context)
        val corners = ledRadii ?: radii ?: (0 to 0)
        val top = ledRowTop ?: height - maxOf(corners.first, corners.second) * calibration.size + calibration.shiftYPx
        val segments = ledLayout.segments.filter { (it.x < 0.5f) == left }
        val edge = if (left) segments.maxOf { it.x + it.width } * width else segments.minOf { it.x } * width
        return OuterSurface(RectF(if (left) 0f else edge, top, if (left) edge else width, height), ledRowRadius)
    }

    fun ledRailPath(width: Float, height: Float, segment: ModifierLedSegment): Path {
        val corners = ledRadii ?: radii ?: return Path()
        val stroke = ledStrokeWidth(segment)
        if (!followDisplayContour) {
            val calibration = T2eCornerCalibration.read(context)
            val centerY = height - calibration.offsetPx -
                (1f - segment.y - segment.height / 2f) * ledContentHeight - ledTopPadding + calibration.shiftYPx
            val left = T2eCornerGeometry.atY(corners.first.toFloat(), height,
                centerY - calibration.shiftYPx, calibration, stroke / 2f).x + calibration.shiftXPx
            val right = width - T2eCornerGeometry.atY(corners.second.toFloat(), height,
                centerY - calibration.shiftYPx, calibration, stroke / 2f).x + calibration.shiftXPx
            return Path().apply {
                moveTo(left + (right - left) * segment.x + stroke / 2f, centerY)
                lineTo(left + (right - left) * (segment.x + segment.width) - stroke / 2f, centerY)
            }
        }
        val left = segment.x < 0.5f
        val surface = outerSurface(width, height, left)
        val gap = LedStatusView.CONTOUR_LED_GAP_DP * context.resources.displayMetrics.density
        val rails = sideRails(left)
        val index = rails.indexOfFirst { it.y == segment.y }
        val preceding = rails.take(index).sumOf { (ledStrokeWidth(it) + gap).toDouble() }.toFloat()
        val offset = surface.borderHalfWidth + gap + stroke / 2f + preceding
        val points = offsetContour(innerContour(width, height, left), -offset, left)
        val trimmed = trimRail(points, surface.bounds.top + stroke / 2f,
            if (left) surface.bounds.right - stroke / 2f else surface.bounds.left + stroke / 2f, left)
        return pointsPath(trimmed)
    }

    /** Gesture area stays inside the original outer slots; drawing reserves space in their surfaces. */
    fun ledExclusionPath(width: Float, height: Float, extraClearance: Float = 0f): Path {
        if (ledRadii == null && radii == null) return Path()
        val gap = LedStatusView.CONTOUR_LED_GAP_DP * context.resources.displayMetrics.density + extraClearance
        return Path().apply {
            for (left in listOf(true, false)) {
                val occupied = Path()
                sideRails(left).forEach { segment ->
                    val rail = Path()
                    Paint().apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        strokeWidth = ledStrokeWidth(segment) + 2f * gap
                    }.getFillPath(ledRailPath(width, height, segment), rail)
                    occupied.op(rail, Path.Op.UNION)
                }
                if (followDisplayContour) {
                    val slot = Path().apply { addRect(outerSurface(width, height, left).bounds, Path.Direction.CW) }
                    occupied.op(slot, Path.Op.INTERSECT)
                }
                op(occupied, Path.Op.UNION)
            }
        }
    }

    private fun trimRail(points: List<PointF>, top: Float, endX: Float, left: Boolean): List<PointF> {
        val trimmed = ArrayList<PointF>()
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val point = points[index]
            if (trimmed.isEmpty()) {
                if (point.y < top) continue
                val ratio = if (kotlin.math.abs(point.y - previous.y) < 0.001f) 1f
                    else ((top - previous.y) / (point.y - previous.y)).coerceIn(0f, 1f)
                trimmed += PointF(previous.x + (point.x - previous.x) * ratio, top)
            }
            if (if (left) point.x >= endX else point.x <= endX) {
                val before = trimmed.last()
                val ratio = if (kotlin.math.abs(point.x - before.x) < 0.001f) 1f
                    else ((endX - before.x) / (point.x - before.x)).coerceIn(0f, 1f)
                trimmed += PointF(endX, before.y + (point.y - before.y) * ratio)
                break
            }
            trimmed += point
        }
        return trimmed
    }

    private fun offsetContour(points: List<PointF>, distance: Float, left: Boolean): List<PointF> =
        points.mapIndexed { index, point ->
            val before = points[(index - 1).coerceAtLeast(0)]
            val after = points[(index + 1).coerceAtMost(points.lastIndex)]
            val dx = after.x - before.x
            val dy = after.y - before.y
            val length = hypot(dx, dy).coerceAtLeast(0.001f)
            val nx = if (left) dy / length else -dy / length
            val ny = if (left) -dx / length else dx / length
            PointF(point.x + nx * distance, point.y + ny * distance)
        }

    private fun pointsPath(points: List<PointF>): Path = Path().apply {
        points.forEachIndexed { index, point ->
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }

    private fun insideContour(width: Float, height: Float, points: List<PointF>, left: Boolean): Path =
        pointsPath(points).apply {
            if (points.isNotEmpty()) {
                val inside = if (left) width * 2f else -width
                lineTo(inside, points.last().y)
                lineTo(inside, points.first().y)
                close()
            }
        }

    private fun matchesSurface(bounds: RectF, surface: OuterSurface?, inset: Float): Boolean =
        surface != null && kotlin.math.abs(bounds.top - surface.bounds.top - inset) < 1f &&
            kotlin.math.abs(bounds.bottom - surface.bounds.bottom + inset) < 1f &&
            kotlin.math.abs(bounds.left - surface.bounds.left - inset) < 1f &&
            kotlin.math.abs(bounds.right - surface.bounds.right + inset) < 1f

    fun ownsLedSurface(bounds: RectF, left: Boolean, right: Boolean, inset: Float = 0f): Boolean =
        ledsVisible && followDisplayContour &&
            (left && matchesSurface(bounds, leftSurface, inset) || right && matchesSurface(bounds, rightSurface, inset))

    fun surfacePath(width: Float, height: Float, bounds: RectF, radius: Float,
                    left: Boolean, right: Boolean, strokeInset: Float = 0f): Path {
        val rect = RectF(bounds)
        val lowestLeft = left && ledsVisible && followDisplayContour && matchesSurface(rect, leftSurface, strokeInset)
        val lowestRight = right && ledsVisible && followDisplayContour && matchesSurface(rect, rightSurface, strokeInset)
        if (!hardwareTopRounding) {
            if (lowestLeft) rect.left = maxOf(rect.left, innerContour(width, height, true).first().x + strokeInset)
            if (lowestRight) rect.right = minOf(rect.right, innerContour(width, height, false).first().x - strokeInset)
        }
        val bottomRadius = radius.coerceAtMost(minOf(rect.width(), rect.height()) / 2f).coerceAtLeast(0f)
        val shapedLeft = left && radii != null && hardwareTopRounding
        val shapedRight = right && radii != null && hardwareTopRounding
        val path = Path().apply {
            addRoundRect(rect, floatArrayOf(if (shapedLeft) 0f else bottomRadius, if (shapedLeft) 0f else bottomRadius,
                if (shapedRight) 0f else bottomRadius, if (shapedRight) 0f else bottomRadius,
                bottomRadius, bottomRadius, bottomRadius, bottomRadius), Path.Direction.CW)
        }
        contentPath(width, height, strokeInset)?.let { path.op(it, Path.Op.INTERSECT) }
        for (side in listOf(true, false)) {
            if (if (side) lowestLeft else lowestRight) {
                val contour = offsetContour(innerContour(width, height, side),
                    (strokeInset - outerSurface(width, height, side).borderHalfWidth).coerceAtLeast(0f), side)
                path.op(insideContour(width, height, contour, side), Path.Op.INTERSECT)
            } else if (hardwareTopRounding && if (side) left else right) {
                val available = if (side) rect.right else width - rect.left
                val transition = topTransition(width, height, rect.top, rect.bottom, side, radius, available, strokeInset)
                    ?: continue
                path.op(insideContour(width, height, transitionPoints(transition) + PointF(
                    if (side) -width else width * 2f, rect.bottom + height), side), Path.Op.INTERSECT)
            }
        }
        return path
    }

    fun topTransition(width: Float, height: Float, top: Float, bottom: Float, left: Boolean,
                      radius: Float, availableWidth: Float, extraInset: Float = 0f): Transition? {
        val corners = radii ?: return null
        val calibration = T2eCornerCalibration.read(context)
        val displayRadius = (if (left) corners.first else corners.second).toFloat()
        val bottomRadius = radius.coerceAtMost(minOf(availableWidth, bottom - top) / 2f)
        val multiplier = if (hardwareTopRounding) SettingsManager.getTitan2EliteTopCornerMultiplier(context) else 1
        val topRadius = (radius * multiplier)
            .coerceAtMost(minOf(availableWidth - bottomRadius, bottom - top - bottomRadius)).coerceAtLeast(0f)
        fun boundary(y: Float) = T2eCornerGeometry.atY(displayRadius, height,
            y - calibration.shiftYPx, calibration, extraInset).x
        val joinY = (top + topRadius).coerceAtMost(bottom - bottomRadius)
        val joinX = boundary(joinY)
        val dx = (boundary(joinY + 0.05f) - boundary(joinY - 0.05f)).coerceAtLeast(0f)
        val tangentLength = hypot(dx, 0.1f)
        val startX = (boundary(top) + topRadius).coerceAtMost(availableWidth - bottomRadius)
        val handle = minOf((joinY - top) * 0.55228475f,
            (startX - joinX).coerceAtLeast(0f) + topRadius * 0.25f)
        fun x(local: Float) = (if (left) local else width - local) + calibration.shiftXPx
        return Transition(PointF(x(startX), top), PointF(x(startX - handle), top),
            PointF(x(joinX - dx / tangentLength * handle), joinY - 0.1f / tangentLength * handle),
            PointF(x(joinX), joinY))
    }

    private fun transitionPoints(transition: Transition): List<PointF> = (0..32).map { index ->
        val t = index / 32f
        val u = 1f - t
        PointF(u*u*u*transition.start.x + 3*u*u*t*transition.firstHandle.x +
            3*u*t*t*transition.secondHandle.x + t*t*t*transition.join.x,
            u*u*u*transition.start.y + 3*u*u*t*transition.firstHandle.y +
                3*u*t*t*transition.secondHandle.y + t*t*t*transition.join.y)
    }

    fun edgeContour(width: Float, height: Float, top: Float, left: Boolean): List<PointF> =
        physicalContour(width, height, top, left, 0f)

    private fun physicalContour(width: Float, height: Float, top: Float, left: Boolean,
                                inset: Float): List<PointF> {
        val corners = ledRadii ?: radii ?: return emptyList()
        val calibration = T2eCornerCalibration.read(context)
        val radius = (if (left) corners.first else corners.second).toFloat()
        return buildList {
            val first = T2eCornerGeometry.atY(radius, height, top - calibration.shiftYPx, calibration, inset)
            add(PointF((if (left) first.x else width - first.x) + calibration.shiftXPx, top))
            for (index in 0..256) {
                val point = T2eCornerGeometry.point(radius, height, PI / 2 * index / 256, calibration, inset)
                if (point.y + calibration.shiftYPx > top) add(PointF(
                    (if (left) point.x else width - point.x) + calibration.shiftXPx, point.y + calibration.shiftYPx))
            }
        }
    }

    private fun innerContour(width: Float, height: Float, left: Boolean): List<PointF> {
        val surface = outerSurface(width, height, left)
        val rect = RectF(surface.bounds).apply { inset(surface.borderHalfWidth, surface.borderHalfWidth) }
        val inset = buttonInset(left) + surface.borderHalfWidth
        val transition = if (hardwareTopRounding && radii != null)
            topTransition(width, height, rect.top, rect.bottom, left, surface.radius,
                if (left) rect.right else width - rect.left, inset) else null
        val top = transition?.join?.y ?: rect.top
        val physical = physicalContour(width, height, top, left, inset)
        return if (transition == null) physical else transitionPoints(transition) + physical.drop(1)
    }

    companion object {
        fun centroid(region: Region, fallback: PointF): PointF {
            val iterator = RegionIterator(region)
            val rectangle = Rect()
            var area = 0.0
            var x = 0.0
            var y = 0.0
            while (iterator.next(rectangle)) {
                val size = rectangle.width().toDouble() * rectangle.height()
                area += size
                x += rectangle.exactCenterX() * size
                y += rectangle.exactCenterY() * size
            }
            return if (area > 0.0) PointF((x / area).toFloat(), (y / area).toFloat()) else fallback
        }
    }
}
