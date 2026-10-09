package rocks.pkb.plektra.inputmethod.aospkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SoftwareKeyboardActionLabelsTest {
    @Test fun clicksNavigationActionsHaveCompactLabels() {
        val actions = listOf("__DPAD_UP__", "__DPAD_DOWN__", "__DPAD_LEFT__", "__DPAD_RIGHT__")
        assertEquals(listOf("↑", "↓", "←", "→"), actions.map(SoftwareKeyboardActionLabels::display))
    }

    @Test fun symbolsAndEmojiRemainIntact() {
        listOf("€", "🙃", "@", "ß", "").forEach { assertEquals(it, SoftwareKeyboardActionLabels.display(it)) }
    }
}
