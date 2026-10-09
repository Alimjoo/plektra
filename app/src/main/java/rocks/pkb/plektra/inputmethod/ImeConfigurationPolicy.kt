package rocks.pkb.plektra.inputmethod

import android.content.pm.ActivityInfo

internal object ImeConfigurationPolicy {
    private const val HARDWARE_CHANGES = ActivityInfo.CONFIG_KEYBOARD or
        ActivityInfo.CONFIG_KEYBOARD_HIDDEN or ActivityInfo.CONFIG_NAVIGATION

    fun isHardwareOnlyChange(diff: Int): Boolean = diff != 0 && (diff and HARDWARE_CHANGES.inv()) == 0
}
