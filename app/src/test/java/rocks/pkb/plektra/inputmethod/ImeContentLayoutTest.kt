package rocks.pkb.plektra.inputmethod

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImeContentLayoutTest {
    @Test
    fun softwareCanvasWithoutMenuUsesItsOwnRowAndSharedLedCoordinates() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val chrome = ImeChromeLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            bottomCornerRadiiPx = 100 to 100
            softwareKeyboardModeActive = true
            contourIntegratedIndicators = true
        }
        val frame = android.widget.FrameLayout(activity)
        val keyboard = ImeContentLayout(activity)
        keyboard.addView(View(activity).apply { setBackgroundColor(Color.RED) }, LinearLayout.LayoutParams(1000, 400))
        rocks.pkb.plektra.inputmethod.statusbar.KeyboardBarRowGeometry.bindCanvasRow(keyboard, 300f, 400f, 12f,
            android.graphics.RectF(0f, 300f, 140f, 400f), android.graphics.RectF(860f, 300f, 1000f, 400f))
        frame.addView(keyboard, android.widget.FrameLayout.LayoutParams(1000, 400))
        val leds = rocks.pkb.plektra.inputmethod.ui.LedStatusView(activity).apply {
            layout = rocks.pkb.plektra.inputmethod.ui.ModifierLedLayouts.TITAN_2_ELITE
            bottomCornerRadiiPx = 100 to 100
            barGeometry = chrome.geometry
        }
        val indicator = leds.ensureView()
        frame.addView(indicator, android.widget.FrameLayout.LayoutParams(1000,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM))
        chrome.addView(frame, LinearLayout.LayoutParams(1000, 400))
        chrome.indicatorView = indicator
        chrome.expandedSurfaceView = keyboard
        chrome.surfaceView = frame
        chrome.onLedGeometryChanged = { leds.invalidateGeometry() }
        activity.setContentView(chrome)
        fun layout() {
            chrome.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            chrome.layout(0, 0, 1000, 400)
        }
        layout()
        assertEquals(300f, chrome.geometry.ledRowTop!!, 0.01f)
        assertEquals(12f, chrome.geometry.ledRowRadius, 0.01f)
        assertEquals(400, keyboard.height)
        val coordinate = FloatArray(2)
        android.graphics.PathMeasure(chrome.geometry.ledRailPath(1000f, 400f,
            leds.layout.segments.first()), false).let { it.getPosTan(it.length / 3f, coordinate, null) }
        org.junit.Assert.assertFalse(chrome.geometry.contains(1000f, 400f, coordinate[0], coordinate[1]))
        val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN,
            coordinate[0], coordinate[1] - indicator.top, 0)
        try { org.junit.Assert.assertTrue("Overlay hit testing uses chrome-global coordinates", indicator.dispatchTouchEvent(event)) }
        finally { event.recycle() }
        val bitmap = Bitmap.createBitmap(1000, 400, Bitmap.Config.ARGB_8888)
        chrome.draw(Canvas(bitmap))
        assertEquals("Center bottom retains normal keyboard paint", Color.RED, bitmap.getPixel(500, 399))
        for (visible in listOf(false, true, false, true)) {
            chrome.contourIntegratedIndicators = visible
            layout()
            assertEquals("LED toggles preserve the software canvas height", 400, keyboard.height)
            assertEquals(300f, chrome.geometry.ledRowTop!!, 0.01f)
        }
        keyboard.visibility = View.GONE
        layout()
        org.junit.Assert.assertNull("No row does not require a menu-button fallback", chrome.geometry.ledRowTop)
        org.junit.Assert.assertFalse("The physical fallback still draws its rails",
            chrome.geometry.ledRailPath(1000f, 400f, leds.layout.segments.first()).isEmpty)
    }

    @Test
    fun ledLongPressRemainsAvailableWithoutDispatchingToKeyboardButtons() {
        val context = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val chrome = ImeChromeLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            bottomCornerRadiiPx = 100 to 100
            contourIntegratedIndicators = true
        }
        var buttonClicks = 0
        var ledLongClicks = 0
        chrome.addView(View(context).apply { setOnClickListener { buttonClicks++ } }, LinearLayout.LayoutParams(1000, 100))
        val indicator = View(context).apply { setOnLongClickListener { ledLongClicks++; true } }
        chrome.addView(indicator, LinearLayout.LayoutParams(1000, 100))
        chrome.indicatorView = indicator
        context.setContentView(chrome)
        chrome.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        chrome.layout(0, 0, 1000, 200)
        fun touch(action: Int, time: Long) {
            val event = android.view.MotionEvent.obtain(0, time, action, 105f, 190f, 0)
            try { chrome.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        touch(android.view.MotionEvent.ACTION_DOWN, 0)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(700, java.util.concurrent.TimeUnit.MILLISECONDS)
        touch(android.view.MotionEvent.ACTION_UP, 700)
        assertEquals(1, ledLongClicks)
        assertEquals(0, buttonClicks)
    }

    @Test
    fun canvasKeyboardCannotPaintOverTheRowsAboveItsContentArea() {
        val context = RuntimeEnvironment.getApplication()
        val chrome = ImeChromeLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            bottomCornerRadiiPx = 100 to 100
            clipChildren = false
            clipToPadding = false
        }
        chrome.addView(View(context).apply { setBackgroundColor(Color.BLUE) }, LinearLayout.LayoutParams(1000, 100))
        val content = ImeContentLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        content.addView(object : View(context) {
            override fun onDraw(canvas: Canvas) { canvas.drawColor(Color.RED) }
        }, LinearLayout.LayoutParams(1000, 300))
        chrome.addView(content, LinearLayout.LayoutParams(1000, 300))
        chrome.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        chrome.layout(0, 0, 1000, 400)
        val bitmap = Bitmap.createBitmap(1000, 400, Bitmap.Config.ARGB_8888)
        chrome.draw(Canvas(bitmap))
        assertEquals("The suggestion/menu row remains visible", Color.BLUE, bitmap.getPixel(500, 50))
        assertEquals("The keyboard still fills its own content area", Color.RED, bitmap.getPixel(500, 250))
    }
}
