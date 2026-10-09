package rocks.pkb.plektra.inputmethod

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImeChromeTransitionTest {
    @Test fun expandingViewportKeepsSoftwareKeysAtTheirFinalHeight() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val chrome = ImeChromeLayout(activity.get()).apply { orientation = LinearLayout.VERTICAL }
        val row = View(activity.get())
        val surface = View(activity.get()).apply { visibility = View.GONE }
        chrome.addView(row, LinearLayout.LayoutParams(-1, 80))
        chrome.addView(surface, LinearLayout.LayoutParams(-1, 600))
        chrome.surfaceView = surface
        activity.get().setContentView(chrome)
        val width = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST)
        chrome.measure(width, height)
        chrome.layout(0, 0, 600, chrome.measuredHeight)
        surface.visibility = View.VISIBLE
        chrome.softwareKeyboardModeActive = true
        chrome.measure(width, height)
        chrome.layout(0, 0, 600, chrome.measuredHeight)
        assertEquals(600, surface.measuredHeight)
        assertEquals(chrome.height, surface.bottom)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250))
        chrome.measure(width, height)
        chrome.layout(0, 0, 600, chrome.measuredHeight)
        assertEquals(0, row.top)
        assertEquals(chrome.height, surface.bottom)
        activity.pause().stop().destroy()
    }

    @Test fun compactRowsStayAtTheBottomOfAnExpandedWindow() {
        val context = RuntimeEnvironment.getApplication()
        val chrome = ImeChromeLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val row = View(context)
        chrome.addView(row, LinearLayout.LayoutParams(-1, 80))
        chrome.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
        chrome.layout(0, 0, 600, 500)
        assertEquals(500, row.bottom)
        assertEquals(420, row.top)
    }
}
