package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Outline
import android.graphics.Matrix
import android.graphics.Path
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.ui.LedStatusView

internal class ImeChromeLayout(context: Context) : LinearLayout(context) {
    var ledOnlyReservedHeightPx: Int? = null
    private val screenAwakeController = ImeTouchScreenAwakeController(context)
    var regularCornerColors: Pair<Int, Int> = Color.BLACK to Color.BLACK
    var compactCornerColors: Pair<Int, Int> = Color.BLACK to Color.BLACK
    var bottomFillColors: Pair<Int, Int> = Color.BLACK to Color.BLACK
    var expandedCloseColor: Int = Color.BLACK
    var expandedCloseButton: View? = null
    var expandedPickerButtons: Pair<View, View>? = null
    var expandedKeyHeightPx: Int = (56f * resources.displayMetrics.density).toInt()
    private val cornerFillPaint = Paint()
    private val calibrationPreviewListener: () -> Unit = {
        applyBottomCornerClip()
        requestLayout()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        it.palsoftware.pastiera.T2eCornerCalibration.addPreviewListener(calibrationPreviewListener)
    }

    override fun draw(canvas: Canvas) {
        if (ledOnlyReservedHeightPx != null) {
            super.draw(canvas)
            return
        }
        val radii = bottomCornerRadiiPx
        if (radii != null) {
            drawStatusRowSideFill(canvas, radii)
        }
        if (fillDisplayCorners || radii == null || radii.first <= 0 && radii.second <= 0) {
            super.draw(canvas)
            return
        }
        val calibration = it.palsoftware.pastiera.T2eCornerCalibration.read(context)
        val path = it.palsoftware.pastiera.T2eCornerGeometry.path(
            width.toFloat(), height.toFloat(), radii.first.toFloat(), radii.second.toFloat(),
            calibration
        ).apply {
            // Preserve the calibrated physical corners, but do not carry their
            // inward bottom offset across the straight center of the display.
            addRect(
                radii.first.toFloat(),
                0f,
                width.toFloat() - radii.second,
                height.toFloat(),
                Path.Direction.CW
            )
        }
        val bottomContour = it.palsoftware.pastiera.T2eCornerGeometry.bottomContourPath(
            width.toFloat(), height.toFloat(), radii.first.toFloat(), radii.second.toFloat(),
            calibration
        )
        cornerFillPaint.color = bottomFillColors.first
        cornerFillPaint.style = Paint.Style.STROKE
        cornerFillPaint.strokeWidth = 2f * resources.displayMetrics.density
        cornerFillPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(bottomContour, cornerFillPaint)
        cornerFillPaint.style = Paint.Style.FILL

        val contentSave = canvas.save()
        canvas.clipPath(path)
        super.draw(canvas)
        canvas.restoreToCount(contentSave)
    }

    private fun drawStatusRowSideFill(canvas: Canvas, radii: Pair<Int, Int>) {
        val row = nestedRow ?: return
        val colors = if (row === compactStatusRow) compactCornerColors else regularCornerColors
        cornerFillPaint.style = Paint.Style.FILL
        cornerFillPaint.color = colors.first
        canvas.drawRect(0f, row.top.toFloat(), radii.first.toFloat(), row.bottom.toFloat(), cornerFillPaint)
        cornerFillPaint.color = colors.second
        canvas.drawRect(
            (width - radii.second).toFloat(),
            row.top.toFloat(),
            width.toFloat(),
            row.bottom.toFloat(),
            cornerFillPaint
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bottomCornerRadiiPx != null && expandedSurfaceView?.visibility == View.VISIBLE) {
            val surface = surfaceView
            val content = expandedSurfaceView
            if (surface != null && content != null) {
                cornerFillPaint.color = bottomFillColors.first
                canvas.drawRect(0f, (surface.top + content.bottom).toFloat(), width.toFloat(), height.toFloat(), cornerFillPaint)
                expandedCloseButton?.takeIf { it.visibility == View.VISIBLE }?.let { button ->
                    cornerFillPaint.color = expandedCloseColor
                    canvas.drawRect(
                        (surface.left + button.left).toFloat(), (surface.top + button.top).toFloat(),
                        width.toFloat(), height.toFloat(), cornerFillPaint
                    )
                }
                expandedPickerButtons?.let { (leftButton, rightButton) ->
                    listOf(leftButton, rightButton).forEach { button ->
                        if (button.visibility != View.VISIBLE) return@forEach
                        val bounds = android.graphics.Rect(0, 0, button.width, button.height)
                        offsetDescendantRectToMyCoords(button, bounds)
                        cornerFillPaint.color = expandedCloseColor
                        canvas.drawRect(
                            if (button === leftButton) 0f else bounds.left.toFloat(), bounds.top.toFloat(),
                            if (button === rightButton) width.toFloat() else bounds.right.toFloat(),
                            height.toFloat(), cornerFillPaint
                        )
                    }
                }
            }
        }
        val row = nestedRow ?: return
        val radii = bottomCornerRadiiPx ?: return
        val colors = if (row === compactStatusRow) compactCornerColors else regularCornerColors
        // Continue the row's themed surface to the bottom of the display.
        // Children draw afterward, keeping the modifier lights above the fill.
        cornerFillPaint.color = if (row === compactStatusRow) bottomFillColors.second else bottomFillColors.first
        canvas.drawRect(0f, row.bottom.toFloat(), width.toFloat(), height.toFloat(), cornerFillPaint)
        cornerFillPaint.color = colors.second
        canvas.drawRect((width - radii.second).toFloat(), row.bottom.toFloat(), width.toFloat(), height.toFloat(), cornerFillPaint)
    }
    var indicatorView: View? = null
    var expandedSurfaceView: View? = null
    var compactStatusRow: View? = null
    var contourIntegratedIndicators: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }
    var onContourGeometryChanged: ((LedStatusView.ContourGeometry?) -> Unit)? = null
    private var nestedRow: View? = null
    private var originalRowMargins = intArrayOf(0, 0, 0)
    private var originalRowOutline: ViewOutlineProvider? = null
    private var originalRowClip = false
    private var originalRowMinHeight = 0
    private val originalIconTransforms = mutableMapOf<ImageView, Pair<ImageView.ScaleType, Matrix>>()

    private fun updateNestedStatusRow() {
        indicatorView?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        (indicatorView?.layoutParams as? LayoutParams)?.topMargin = 0
        expandedSurfaceView?.clipToOutline = false
        nestedRow?.let { row ->
            (row.layoutParams as LayoutParams).apply {
                leftMargin = originalRowMargins[0]
                rightMargin = originalRowMargins[1]
                bottomMargin = originalRowMargins[2]
            }
            row.outlineProvider = originalRowOutline
            row.clipToOutline = originalRowClip
            row.minimumHeight = originalRowMinHeight
        }
        nestedRow = null
        val radii = bottomCornerRadiiPx ?: return
        if (expandedSurfaceView?.visibility == View.VISIBLE && indicatorView?.visibility == View.VISIBLE) {
            val density = resources.displayMetrics.density
            val radius = maxOf(radii.first, radii.second)
            val stripHeight = (LedStatusView.LED_ZONE_HEIGHT_DP * density).toInt()
            val ledHeight = maxOf(radius + density.toInt(), expandedKeyHeightPx + stripHeight)
            (indicatorView?.layoutParams as? LayoutParams)?.apply {
                height = ledHeight
                topMargin = -(ledHeight - stripHeight).coerceAtLeast(0)
            }
            expandedSurfaceView?.apply {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val path = it.palsoftware.pastiera.T2eCornerGeometry.path(
                            view.width.toFloat(),
                            view.height.toFloat(),
                            radii.first.toFloat(),
                            radii.second.toFloat(),
                            it.palsoftware.pastiera.T2eCornerCalibration.read(context),
                            stripHeight.toFloat()
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) outline.setPath(path)
                        else {
                            @Suppress("DEPRECATION")
                            outline.setConvexPath(path)
                        }
                    }
                }
                clipToOutline = true
                invalidateOutline()
            }
            return
        }
        if (softwareKeyboardModeActive || expandedSurfaceView?.visibility != View.GONE ||
            indicatorView?.visibility != View.VISIBLE || surfaceView?.visibility != View.VISIBLE
        ) return
        val surfaceIndex = indexOfChild(surfaceView)
        val row = (surfaceIndex - 1 downTo 0)
            .map(::getChildAt).firstOrNull { it.visibility == View.VISIBLE } ?: return
        val params = row.layoutParams as LayoutParams
        nestedRow = row
        originalRowMargins = intArrayOf(params.leftMargin, params.rightMargin, params.bottomMargin)
        originalRowOutline = row.outlineProvider
        originalRowClip = row.clipToOutline
        originalRowMinHeight = row.minimumHeight
        // Reserve a dedicated band so both LED rows remain visibly separate.
        val inset = if (contourIntegratedIndicators) 0 else
            (LedStatusView.LED_ZONE_HEIGHT_DP * resources.displayMetrics.density).toInt()
        val radius = maxOf(radii.first, radii.second)
        val stripTop = (resources.displayMetrics.density).toInt()
        // The LED surface draws first; overlap its empty center with the row.
        val requestedRowHeight = params.height.coerceAtLeast(0)
        val bottomInset = inset
        indicatorView?.layoutParams?.height = maxOf(radius + stripTop, requestedRowHeight + bottomInset)
        // A fixed-height row does not honor minimumHeight during measurement.
        // Overlapping more than that height puts the LED surface above the
        // row's top, while LinearLayout still reserves its full height below.
        // onLayout expands the row to meet the LEDs after measurement.
        val overlap = if (params.height >= 0) requestedRowHeight
            else (radius + stripTop - inset).coerceAtLeast(0)
        params.bottomMargin -= overlap
        row.minimumHeight = maxOf(originalRowMinHeight, overlap)
        // Each outer button draws against the calibrated contour; the chrome clips the display edge.
        row.outlineProvider = originalRowOutline
        row.clipToOutline = false
        row.invalidateOutline()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        originalIconTransforms.forEach { (icon, original) ->
            icon.scaleType = original.first
            icon.imageMatrix = original.second
        }
        originalIconTransforms.clear()
        val row = nestedRow as? ViewGroup ?: return
        val originalContentHeight = row.height
        // A fixed-height row can be shorter than the requested overlap.
        // Anchor its actual bottom to the inner LED contour after layout.
        // The straight lower indicators occupy only the lower LED row;
        // their top edge is closer to the bottom than the two-row side arcs.
        val bottomInset = if (contourIntegratedIndicators) 0 else
            (LedStatusView.LED_ZONE_HEIGHT_DP * resources.displayMetrics.density).toInt()
        surfaceView?.let { surface ->
            val targetBottom = surface.bottom - bottomInset
            val extraHeight = (targetBottom - row.bottom).coerceAtLeast(0)
            // Fill the space up to the row's original top instead of translating
            // a short row downward and exposing an empty band above it.
            fun extendContent(view: View) {
                val oldTop = if (view === row) view.top else 0
                val oldLeft = view.left
                val oldWidth = view.width
                val oldHeight = view.height
                val targetHeight = if (view === row) oldHeight + extraHeight
                    else (view.parent as View).height
                view.measure(
                    MeasureSpec.makeMeasureSpec(oldWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)
                )
                view.layout(oldLeft, oldTop, oldLeft + oldWidth, oldTop + targetHeight)
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        val child = view.getChildAt(index)
                        if (child.visibility == View.VISIBLE &&
                            child.height >= originalContentHeight - 4f * resources.displayMetrics.density &&
                            child.height <= view.height
                        ) extendContent(child)
                    }
                }
            }
            if (extraHeight > 0) extendContent(row)
        }
        val curvedButtons = mutableListOf<View>()
        fun collectCurvedButtons(view: View) {
            if (view.background is it.palsoftware.pastiera.inputmethod.statusbar.CurvedCornerButtonDrawable) {
                curvedButtons.add(view)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collectCurvedButtons(view.getChildAt(index))
            }
        }
        collectCurvedButtons(row)

        if (contourIntegratedIndicators) {
            fun containsExtendableButton(view: View): Boolean {
                if (view.visibility != View.VISIBLE) return false
                if (view.isClickable && view.background != null) return true
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        if (containsExtendableButton(view.getChildAt(index))) return true
                    }
                }
                return false
            }
            fun extendButtonBranches(view: ViewGroup, offsetY: Int) {
                val calibration = it.palsoftware.pastiera.T2eCornerCalibration.read(context)
                val borderInset = kotlin.math.ceil(
                    LedStatusView.CONTOUR_BUTTON_INSET_DP * resources.displayMetrics.density +
                        (calibration.offsetPx - calibration.shiftYPx).coerceAtLeast(0f)
                ).toInt()
                val visibleBottom = (row.height - this@ImeChromeLayout.paddingBottom).coerceAtLeast(0)
                for (index in 0 until view.childCount) {
                    val child = view.getChildAt(index)
                    if (!containsExtendableButton(child)) continue
                    val absoluteBottom = offsetY + child.bottom
                    val isButton = child.isClickable && child.background != null
                    val targetBottom = visibleBottom - if (isButton) borderInset else 0
                    val adjustment = targetBottom - absoluteBottom
                    if (adjustment != 0 && child.bottom + adjustment > child.top) {
                        child.layout(child.left, child.top, child.right, child.bottom + adjustment)
                    }
                    if (child is ViewGroup) extendButtonBranches(child, offsetY + child.top)
                }
            }
            extendButtonBranches(row, 0)
            val surface = surfaceView
            if (surface != null && curvedButtons.size >= 2) {
                val buttons = curvedButtons.map { button ->
                    button to android.graphics.Rect(0, 0, button.width, button.height).also { rect ->
                        offsetDescendantRectToMyCoords(button, rect)
                    }
                }.sortedBy { it.second.left }
                val surfaceTop = surface.top.toFloat()
                val surfaceLeft = surface.left.toFloat()
                val (leftButtonView, leftButton) = buttons.first()
                val (rightButtonView, rightButton) = buttons.last()
                fun contourFor(button: View): LedStatusView.ButtonContour? {
                    val drawable = button.background as?
                        it.palsoftware.pastiera.inputmethod.statusbar.CurvedCornerButtonDrawable
                        ?: return null
                    val contour = drawable.outerContourCenterline() ?: return null
                    return LedStatusView.ButtonContour(
                        points = contour.points.map { point ->
                            android.graphics.PointF(point.x - surfaceLeft, point.y - surfaceTop)
                        },
                        borderHalfWidthPx = contour.borderHalfWidthPx
                    )
                }
                onContourGeometryChanged?.invoke(
                    LedStatusView.ContourGeometry(
                        buttonTopPx = minOf(leftButton.top, rightButton.top) - surfaceTop,
                        leftButtonEndPx = leftButton.right.toFloat(),
                        rightButtonStartPx = rightButton.left.toFloat(),
                        leftButtonContour = contourFor(leftButtonView),
                        rightButtonContour = contourFor(rightButtonView)
                    )
                )
            } else {
                onContourGeometryChanged?.invoke(null)
            }
        } else {
            // Keep the existing vertical alignment when the contour LEDs are not active.
            for (index in 0 until row.childCount) {
                val child = row.getChildAt(index)
                if (child.visibility == View.VISIBLE && child.height < row.height) {
                    child.offsetTopAndBottom(row.height - row.paddingBottom - child.bottom)
                }
            }
            onContourGeometryChanged?.invoke(null)
        }
        val radii = bottomCornerRadiiPx ?: return
        fun fitIcons(view: View, offsetX: Int) {
            if (view is ImageView && view.visibility == View.VISIBLE &&
                view.background !is it.palsoftware.pastiera.inputmethod.statusbar.CurvedCornerButtonDrawable
            ) {
                val onLeft = offsetX < radii.first
                val onRight = offsetX + view.width > row.width - radii.second
                val drawable = view.drawable
                if ((onLeft || onRight) && drawable != null &&
                    drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0
                ) {
                    originalIconTransforms[view] = view.scaleType to Matrix(view.imageMatrix)
                    val iconFraction = if (row === compactStatusRow) 0.64f else 0.48f
                    val iconHeight = if (row === compactStatusRow) {
                        (minOf(view.height, originalContentHeight) - 4f * resources.displayMetrics.density).coerceAtLeast(1f)
                    } else view.height.toFloat()
                    val size = minOf(view.width.toFloat(), iconHeight) * iconFraction
                    val requestedScale = size / maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight)
                    val scale = if (row === compactStatusRow) requestedScale else minOf(1f, requestedScale)
                    val iconCenterX = view.width / 2f
                    view.scaleType = ImageView.ScaleType.MATRIX
                    view.imageMatrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            iconCenterX - drawable.intrinsicWidth * scale / 2f - view.paddingLeft,
                            (view.height / 2f) -
                                drawable.intrinsicHeight * scale / 2f - view.paddingTop
                        )
                    }
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    val child = view.getChildAt(index)
                    fitIcons(child, offsetX + child.left)
                }
            }
        }
        fitIcons(row, 0)
    }

    // (left, right) display corner radii in px; null disables the outline clip.
    var bottomCornerRadiiPx: Pair<Int, Int>? = null
        set(value) {
            if (field == value) {
                applyBottomCornerClip()
                invalidate()
                requestLayout()
                return
            }
            field = value
            applyBottomCornerClip()
            requestLayout()
        }

    var fillDisplayCorners: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var surfaceView: View? = null
        set(value) {
            field = value
            requestLayout()
        }
    var softwareKeyboardModeActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init {
        setChildrenDrawingOrderEnabled(true)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        screenAwakeController.onTouchAction(event.actionMasked)
        return super.dispatchTouchEvent(event)
    }

    // Extend the shape above the view so even a bar shorter than the display
    // radius follows the original arc, rather than shrinking it to fit the bar.
    private fun applyBottomCornerClip() {
        val radii = bottomCornerRadiiPx
        val radius = radii?.let { maxOf(it.first, it.second) } ?: 0
        if (radius <= 0) {
            clipToOutline = false
            outlineProvider = ViewOutlineProvider.BACKGROUND
            return
        }
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val left = radii!!.first.coerceIn(0, view.width / 2).toFloat()
                val right = radii.second.coerceIn(0, view.width / 2).toFloat()
                val path = it.palsoftware.pastiera.T2eCornerGeometry.path(
                    view.width.toFloat(), view.height.toFloat(), left, right,
                    it.palsoftware.pastiera.T2eCornerCalibration.read(context)
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    outline.setPath(path)
                } else {
                    @Suppress("DEPRECATION")
                    outline.setConvexPath(path)
                }
            }
        }
        // draw() clips the content and paints the bottom extension behind it.
        clipToOutline = false
        invalidateOutline()
    }

    override fun onDetachedFromWindow() {
        it.palsoftware.pastiera.T2eCornerCalibration.removePreviewListener(calibrationPreviewListener)
        screenAwakeController.release()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateNestedStatusRow()
        val surface = surfaceView
        if (!softwareKeyboardModeActive || surface == null || surface.visibility == View.GONE) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        var totalChildHeight = 0
        var maxWidth = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            measureChildWithMargins(
                child,
                widthMeasureSpec,
                0,
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                0
            )
            val params = child.layoutParams as MarginLayoutParams
            totalChildHeight += child.measuredHeight + params.topMargin + params.bottomMargin
            maxWidth = maxOf(maxWidth, child.measuredWidth + params.leftMargin + params.rightMargin)
        }

        val measuredWidth = resolveSize(maxWidth + paddingLeft + paddingRight, widthMeasureSpec)
        val desiredHeight = paddingTop + paddingBottom + totalChildHeight
        val measuredHeight = desiredHeight
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        val surfaceIndex = surfaceView
            ?.let(::indexOfChild)
            ?.takeIf { it in 0 until childCount }
            ?: return super.getChildDrawingOrder(childCount, drawingPosition)

        if (contourIntegratedIndicators) {
            return if (drawingPosition == childCount - 1) {
                surfaceIndex
            } else if (drawingPosition < surfaceIndex) {
                drawingPosition
            } else {
                drawingPosition + 1
            }
        }

        return if (drawingPosition == 0) {
            surfaceIndex
        } else {
            val shiftedPosition = drawingPosition - 1
            if (shiftedPosition < surfaceIndex) shiftedPosition else shiftedPosition + 1
        }
    }
}
