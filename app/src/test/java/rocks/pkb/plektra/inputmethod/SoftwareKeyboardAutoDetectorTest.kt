package rocks.pkb.plektra.inputmethod

import android.hardware.input.InputManager
import android.util.SparseArray
import android.view.InputDevice
import rocks.pkb.plektra.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.util.ReflectionHelpers
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowInputManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SoftwareKeyboardAutoDetectorTest {

    @After
    fun tearDown() {
        SoftwareKeyboardAutoDetector.onInputDevicesChanged()
        DeviceSpecific.clearTestOverrides()
    }

    @Test
    fun autoResolvesToHardware_forDetectedPhysicalKeyboardDevices() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )

        val context = RuntimeEnvironment.getApplication()
        SoftwareKeyboardAutoDetector.updateSystemInputViewDecision(true)
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SoftwareKeyboardAutoDetector.resolve(context)
        )
    }

    @Test
    @Config(shadows = [ConnectedInputManager::class])
    fun connectedClicksSelectsHardwareEvenWhenAndroidRequestsAnInputView() {
        DeviceSpecific.setBuildFingerprintForTests("samsung", "samsung", "SM-F956B", "q6q", "q6q")
        val context = RuntimeEnvironment.getApplication()
        val clicks = ShadowInputDevice.makeInputDeviceNamed("Power Keyboard-A100-5")
        ReflectionHelpers.setField(clicks, "mId", 34)
        ReflectionHelpers.setField(clicks, "mKeyboardType", InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        ReflectionHelpers.setField(clicks, "mSources", InputDevice.SOURCE_KEYBOARD)
        val manager = context.getSystemService(InputManager::class.java)
        ReflectionHelpers.setField(manager, "mInputDevices", SparseArray<InputDevice>().apply { put(34, clicks) })
        SoftwareKeyboardAutoDetector.updateSystemInputViewDecision(true)
        assertEquals(SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE, SoftwareKeyboardAutoDetector.resolve(context))
    }

    @Implements(InputManager::class)
    class ConnectedInputManager : ShadowInputManager() {
        @Implementation
        public override fun getInputDeviceIds(): IntArray = intArrayOf(34)
    }

    @Test
    fun autoResolvesToVirtual_forNonPhysicalKeyboardDevices() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "google",
            manufacturer = "google",
            model = "Pixel 7a",
            device = "lynx",
            product = "lynx"
        )

        val context = RuntimeEnvironment.getApplication()
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            SoftwareKeyboardAutoDetector.resolve(context)
        )
    }
}
