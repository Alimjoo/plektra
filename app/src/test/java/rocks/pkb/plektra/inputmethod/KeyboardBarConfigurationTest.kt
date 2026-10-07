package rocks.pkb.plektra.inputmethod

import android.view.View
import rocks.pkb.plektra.KeyboardBarConfiguration
import rocks.pkb.plektra.SettingsManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardBarConfigurationTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        SettingsManager.getPreferences(context).edit().clear().commit()
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE)
    }

    private fun snapshot() = StatusBarController.StatusSnapshot(
        capsLockEnabled = false, shiftPhysicallyPressed = false, shiftOneShot = false,
        ctrlLatchActive = false, ctrlPhysicallyPressed = false, ctrlOneShot = false,
        ctrlLatchFromNavMode = false, altLatchActive = false, altPhysicallyPressed = false,
        altOneShot = false, symPage = 0
    )

    @Test fun slimOnlySuppressesActionsAndKeepsNormalConfiguration() {
        val config = KeyboardBarConfiguration(true, true, true, true, true)
        assertFalse(config.presentation(true).actions)
        assertTrue(config.presentation(true).suggestions)
        assertTrue(config.presentation(true).bottomIndicators)
        assertTrue(config.presentation(false).actions)
        assertFalse(config.copy(suggestions = false).presentation(false).inlineIndicators)
    }

    @Test fun onlyAndroidIndicatorCollapsesAndRestoresHeight() {
        SettingsManager.setSuggestionsEnabled(context, false)
        SettingsManager.setStatusBarVariationsEnabled(context, false)
        SettingsManager.setModifierIndicators(context, setOf(SettingsManager.MODIFIER_INDICATOR_MENU_BAR))
        val controller = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY)
        controller.update(snapshot())
        val root = controller.getLayout()!!
        assertEquals(View.GONE, root.visibility)
        assertEquals(0, root.layoutParams.height)
        assertTrue(KeyboardBarConfiguration.read(context).androidIndicators)
        SettingsManager.setStatusBarVariationsEnabled(context, true)
        controller.update(snapshot())
        assertEquals(View.VISIBLE, root.visibility)
        assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, root.layoutParams.height)
    }

    @Test fun ledOnlyHasNoSuggestionOrActionRowsInSlim() {
        SettingsManager.setSuggestionsEnabled(context, false)
        SettingsManager.setStatusBarVariationsEnabled(context, false)
        SettingsManager.setModifierIndicators(context, setOf(SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP))
        val controller = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY)
        controller.setSlimModeActive(true)
        controller.update(snapshot())
        val root = controller.getLayout()!!
        assertEquals(View.VISIBLE, root.visibility)
        assertEquals(View.GONE, root.getChildAt(0).visibility)
        assertEquals(View.GONE, root.getChildAt(1).visibility)
        assertEquals(android.graphics.Color.TRANSPARENT,
            (root.background as android.graphics.drawable.ColorDrawable).color)
        assertNotNull((root as ImeChromeLayout).ledOnlyReservedHeightPx)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertTrue(root.measuredHeight > 0)
        assertTrue(root.measuredHeight < 16 * context.resources.displayMetrics.density)
    }

    @Test fun explicitlyOpenedEmojiSurvivesAllPersistentContentOff() {
        SettingsManager.setSuggestionsEnabled(context, false)
        SettingsManager.setStatusBarVariationsEnabled(context, false)
        SettingsManager.setModifierIndicators(context, emptySet())
        val controller = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY)
        controller.update(snapshot())
        assertEquals(View.GONE, controller.getLayout()!!.visibility)
        controller.update(snapshot().copy(symPage = 4))
        assertEquals(View.VISIBLE, controller.getLayout()!!.visibility)
        assertTrue(controller.hasTransientContent)
        controller.update(snapshot())
        assertEquals(View.GONE, controller.getLayout()!!.visibility)
    }

    @Test fun ledStripStaysAtBottomWhenAndroidEnlargesTheWindowAfterMeasurement() {
        SettingsManager.setSuggestionsEnabled(context, false)
        SettingsManager.setStatusBarVariationsEnabled(context, false)
        SettingsManager.setModifierIndicators(context, setOf(SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP))
        val controller = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY)
        controller.update(snapshot())
        val root = controller.getLayout()!!
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val surface = root.getChildAt(root.childCount - 1) as android.view.ViewGroup
        val stack = surface.getChildAt(0) as android.view.ViewGroup
        val enlargedHeight = stack.height + 15
        stack.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(enlargedHeight, View.MeasureSpec.EXACTLY))
        stack.layout(0, 0, 1080, enlargedHeight)
        val leds = stack.getChildAt(stack.childCount - 1)
        assertEquals("Extra window height must go above the LEDs", enlargedHeight, leds.bottom)
    }

    @Test fun ledOnlyReservesCornerHeightOnlyForCurvedRails() {
        val led = rocks.pkb.plektra.inputmethod.ui.LedStatusView(context).apply {
            bottomCornerRadiiPx = 100 to 100
            compactOnly = true
            followDisplayContour = false
        }
        val view = led.ensureView()
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertTrue(view.measuredHeight > 0)
        assertTrue(view.measuredHeight < 16 * context.resources.displayMetrics.density)
        led.followDisplayContour = true
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertTrue(view.measuredHeight >= 100)
    }
}
