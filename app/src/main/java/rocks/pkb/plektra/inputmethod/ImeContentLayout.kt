package rocks.pkb.plektra.inputmethod

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.widget.LinearLayout

/** All expanded panels share the chrome's content boundary, including canvas-based keyboards. */
internal class ImeContentLayout(context: Context) : LinearLayout(context) {
    private val position = IntArray(2)
    private val chromePosition = IntArray(2)

    private fun chrome(): ImeChromeLayout? {
        var ancestor = parent
        while (ancestor != null && ancestor !is ImeChromeLayout) ancestor = ancestor.parent
        return ancestor as? ImeChromeLayout
    }

    private fun withinContent(canvas: Canvas, draw: () -> Unit) {
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        val chrome = chrome()
        val path = chrome?.geometry?.contentPath(chrome.width.toFloat(), chrome.height.toFloat())
        if (path != null) {
            getLocationInWindow(position)
            chrome.getLocationInWindow(chromePosition)
            path.offset((chromePosition[0] - position[0]).toFloat(), (chromePosition[1] - position[1]).toFloat())
            canvas.clipPath(path)
        }
        draw()
        canvas.restoreToCount(save)
    }

    override fun draw(canvas: Canvas) = withinContent(canvas) { super.draw(canvas) }

    override fun dispatchDraw(canvas: Canvas) = withinContent(canvas) { super.dispatchDraw(canvas) }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val chrome = chrome()
        if (chrome != null && event.actionMasked == MotionEvent.ACTION_DOWN) {
            getLocationInWindow(position)
            chrome.getLocationInWindow(chromePosition)
            if (!chrome.geometry.contains(chrome.width.toFloat(), chrome.height.toFloat(),
                    event.x + position[0] - chromePosition[0], event.y + position[1] - chromePosition[1])) return false
        }
        return super.dispatchTouchEvent(event)
    }
}
