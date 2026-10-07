package rocks.pkb.plektra.inputmethod

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.pkb.plektra.SettingsManager
import rocks.pkb.plektra.T2eCornerCalibration
import rocks.pkb.plektra.inputmethod.statusbar.CurvedCornerButtonDrawable
import rocks.pkb.plektra.inputmethod.statusbar.KeyboardBarRowGeometry
import rocks.pkb.plektra.inputmethod.ui.ModifierLedLayouts

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreparedGeometryPerformanceTest {
    @Test
    fun repeatedColorDrawAndTouchReusePreparedHardwareAndSoftwareGeometry() {
        for (software in listOf(false, true)) {
            val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
            SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(activity, true)
            SettingsManager.setTitan2EliteMaxIconShrink(activity, 90)
            val chrome = ImeChromeLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                bottomCornerRadiiPx = 100 to 100
                softwareKeyboardModeActive = software
                contourIntegratedIndicators = true
                geometry.ledLayout = ModifierLedLayouts.TITAN_2_ELITE
            }
            val row = LinearLayout(activity)
            fun icon() = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER
                setImageBitmap(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
                KeyboardBarRowGeometry.style(this, Color.GRAY, Color.BLUE, 12f, Color.WHITE, 2)
            }
            val left = icon()
            val right = icon()
            row.addView(left, LinearLayout.LayoutParams(160, 150))
            val middle = View(activity)
            row.addView(middle, LinearLayout.LayoutParams(680, 150))
            row.addView(right, LinearLayout.LayoutParams(160, 150))
            KeyboardBarRowGeometry.bind(row, listOf(left, right))
            chrome.addView(row, LinearLayout.LayoutParams(1000, 150))
            activity.setContentView(chrome)
            fun layout() {
                chrome.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(150, View.MeasureSpec.EXACTLY))
                chrome.layout(0, 0, 1000, 150)
                chrome.viewTreeObserver.dispatchOnPreDraw()
            }
            val canvas = Canvas(Bitmap.createBitmap(1000, 150, Bitmap.Config.ARGB_8888))
            val geometry = chrome.geometry
            fun render() {
                chrome.viewTreeObserver.dispatchOnPreDraw()
                chrome.draw(canvas)
                for (segment in geometry.ledLayout.segments) geometry.ledSectionPath(1000f, 150f, segment)
                geometry.contains(1000f, 150f, 500f, 140f)
                geometry.ledContains(1000f, 150f, 30f, 130f)
            }
            layout(); render()
            val drawable = left.background as CurvedCornerButtonDrawable
            val center = drawable.visibleCenter()
            val matrix = FloatArray(9).also { left.imageMatrix.getValues(it) }
            assertEquals(center.x, matrix[Matrix.MTRANS_X] + 16f * matrix[Matrix.MSCALE_X], 1f)
            assertEquals(center.y, matrix[Matrix.MTRANS_Y] + 16f * matrix[Matrix.MSCALE_Y], 1f)
            fun counts() = listOf(geometry.geometryBuildCount, geometry.surfaceBuildCount,
                geometry.placementCount, geometry.railBuildCount, geometry.touchRegionBuildCount)
            val prepared = counts()
            repeat(12) { index ->
                KeyboardBarRowGeometry.style(left, if (index % 2 == 0) Color.RED else Color.GRAY,
                    Color.BLUE, 12f, Color.WHITE, 2)
                left.isPressed = index % 2 == 0
                render()
                assertSame("Style colors reuse the same shaped drawable", drawable, left.background)
            }
            assertEquals("Repeated typing/color draw and hit testing perform no geometry or fitting work", prepared, counts())
            SettingsManager.getPreferences(activity).edit().putString("unrelated_typing_test", "changed").commit()
            render()
            assertEquals("Unrelated preferences do not invalidate shape caches", prepared, counts())
            SettingsManager.setTitan2EliteMaxIconShrink(activity, 30)
            render()
            assertEquals(prepared[1], geometry.surfaceBuildCount)
            assertTrue("Content constraints refresh without rebuilding shapes", geometry.placementCount > prepared[2])
            val owner = Any()
            try {
                val oldRevision = geometry.revision
                T2eCornerCalibration.setPreview(owner, T2eCornerCalibration(offsetPx = 5f))
                layout(); render()
                assertTrue("Calibration preview invalidates the prepared snapshot", geometry.revision > oldRevision)
                assertTrue(geometry.surfaceBuildCount > prepared[1])
            } finally {
                T2eCornerCalibration.setPreview(owner, null)
            }
            activity.finish()
        }
    }
}
