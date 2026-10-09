package rocks.pkb.plektra.inputmethod

import android.content.pm.ActivityInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeConfigurationPolicyTest {
    @Test fun connectionAndDisconnectionKeepTheAttachedView() {
        assertTrue(ImeConfigurationPolicy.isHardwareOnlyChange(ActivityInfo.CONFIG_KEYBOARD or ActivityInfo.CONFIG_KEYBOARD_HIDDEN))
        assertTrue(ImeConfigurationPolicy.isHardwareOnlyChange(ActivityInfo.CONFIG_NAVIGATION))
    }

    @Test fun themeRotationAndSizeChangesStillReachTheFramework() {
        for (change in listOf(ActivityInfo.CONFIG_UI_MODE, ActivityInfo.CONFIG_ORIENTATION, ActivityInfo.CONFIG_SCREEN_SIZE, ActivityInfo.CONFIG_LOCALE)) {
            assertFalse(ImeConfigurationPolicy.isHardwareOnlyChange(change or ActivityInfo.CONFIG_KEYBOARD))
        }
        assertFalse(ImeConfigurationPolicy.isHardwareOnlyChange(-1))
        assertFalse(ImeConfigurationPolicy.isHardwareOnlyChange(0))
    }
}
