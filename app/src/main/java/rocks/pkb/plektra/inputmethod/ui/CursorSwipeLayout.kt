package rocks.pkb.plektra.inputmethod.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import rocks.pkb.plektra.SettingsManager
import rocks.pkb.plektra.inputmethod.TextSelectionHelper
import kotlin.math.abs

/** Intercepts horizontal drags while leaving taps and long presses to the row buttons. */
internal class CursorSwipeLayout(context: Context) : FrameLayout(context) {
    var connection: () -> InputConnection? = { null }
    var onCursorMoved: () -> Unit = {}
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y; lastX = event.x; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - startX) > slop && abs(event.x - startX) > abs(event.y - startY)) {
                    dragging = connection() != null
                    if (dragging) parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            val threshold = (SettingsManager.getSwipeIncrementalThreshold(context) *
                resources.displayMetrics.density).coerceAtLeast(1f)
            val input = connection() ?: return true
            while (abs(event.x - lastX) >= threshold) {
                val right = event.x > lastX
                val moved = if (right) TextSelectionHelper.moveCursorRight(input)
                    else TextSelectionHelper.moveCursorLeft(input)
                lastX += if (right) threshold else -threshold
                if (!moved) { lastX = event.x; break }
                onCursorMoved()
            }
        } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            dragging = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}
