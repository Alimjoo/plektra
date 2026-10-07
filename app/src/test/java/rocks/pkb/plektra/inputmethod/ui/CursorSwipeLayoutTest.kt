package rocks.pkb.plektra.inputmethod.ui

import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CursorSwipeLayoutTest {
    @Test fun horizontalDragMovesCursorAndTapRemainsWithChild() {
        val context = RuntimeEnvironment.getApplication()
        var cursor = 50
        val input = object : BaseInputConnection(View(context), true) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
                text = "x".repeat(100); selectionStart = cursor; selectionEnd = cursor
            }
            override fun setSelection(start: Int, end: Int): Boolean { cursor = start; return true }
        }
        val row = CursorSwipeLayout(context).apply { connection = { input } }
        fun event(action: Int, x: Float) = MotionEvent.obtain(0, 10, action, x, 20f, 0)
        assertFalse(row.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 200f)))
        assertFalse(row.onInterceptTouchEvent(event(MotionEvent.ACTION_UP, 200f)))
        row.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 200f))
        assertTrue(row.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE, 50f)))
        row.onTouchEvent(event(MotionEvent.ACTION_MOVE, 50f))
        assertTrue(cursor < 50)
        row.onTouchEvent(event(MotionEvent.ACTION_UP, 50f))
    }
}
