package rocks.pkb.plektra.inputmethod

import android.view.KeyEvent
import rocks.pkb.plektra.inputmethod.aospkeyboard.SoftwareKeyboardSymLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StatusBarControllerSoftwareSymTest {

    @Test
    fun actualSoftwareProviderUsesCyrillicTextButKeepsPhysicalSymbolPositions() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        rocks.pkb.plektra.SettingsManager.getPreferences(context).edit().clear().commit()
        rocks.pkb.plektra.SettingsManager.setKeyboardLayout(context, "russian_standard")
        val controller = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY)
        val view = rocks.pkb.plektra.inputmethod.aospkeyboard.AospKeyboardView(context)
        val snapshot = StatusBarController.StatusSnapshot(
            capsLockEnabled = false, shiftPhysicallyPressed = false, shiftOneShot = false,
            ctrlLatchActive = false, ctrlPhysicallyPressed = false, ctrlOneShot = false,
            ctrlLatchFromNavMode = false, altLatchActive = false, altPhysicallyPressed = false,
            altOneShot = false, symPage = 0, activeKeyboardLayoutName = "russian_standard")
        val configure = StatusBarController::class.java.getDeclaredMethod("configureSoftwareKeyboard",
            view.javaClass, snapshot.javaClass, android.view.inputmethod.InputConnection::class.java, Map::class.java)
        configure.isAccessible = true
        configure.invoke(controller, view, snapshot, null, null)
        rocks.pkb.plektra.SettingsManager.setLongPressModifier(context, "shift")
        assertEquals(listOf("Й"), view.longPressAlternatesProvider!!.invoke("q"))
        rocks.pkb.plektra.SettingsManager.setLongPressModifier(context, "variations")
        val alternatives = view.longPressAlternatesProvider!!.invoke("e")
        assertTrue(alternatives.none { text -> text.any { it in 'a'..'z' || it in 'A'..'Z' || it == 'é' } })
        rocks.pkb.plektra.SettingsManager.setLongPressModifier(context, "sym_symbols")
        val symbols = rocks.pkb.plektra.data.mappings.KeyMappingLoader.loadSymKeyMappingsPage2(context.assets)
        assertEquals(listOfNotNull(symbols[KeyEvent.KEYCODE_Q]), view.longPressAlternatesProvider!!.invoke("q"))
    }

    @Test
    fun softwareSymExtraLettersExposeOwnEmojiDefaults() {
        val rows = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
        val mappings = mapOf(
            KeyEvent.KEYCODE_A to "😢",
            KeyEvent.KEYCODE_O to "😡",
            KeyEvent.KEYCODE_U to "❤️"
        )
        val content = SoftwareKeyboardSymLabels.buildContentByChar(
            page = 1,
            rows = rows,
            symMappings = mappings,
            layoutName = "qwertz"
        )

        assertEquals("🙃", content['ü'])
        assertEquals("🧐", content['ö'])
        assertEquals("🍎", content['ä'])
        assertNotEquals(mappings[KeyEvent.KEYCODE_U], content['ü'])
        assertNotEquals(mappings[KeyEvent.KEYCODE_O], content['ö'])
        assertNotEquals(mappings[KeyEvent.KEYCODE_A], content['ä'])
        assertTrue(listOf(content['ü'], content['ö'], content['ä']).distinct().size == 3)
    }

    @Test
    fun softwareSymExtraLettersExposeOwnSymbolDefaults() {
        val rows = listOf("qwertzuiopü+", "asdfghjklöä#", "<yxcvbnm,.-")
        val content = SoftwareKeyboardSymLabels.buildContentByChar(
            page = 2,
            rows = rows,
            symMappings = emptyMap(),
            layoutName = "qwertz"
        )

        assertEquals("€", content['ü'])
        assertEquals("@", content['ö'])
        assertEquals("#", content['ä'])
        assertEquals("+", content['+'])
        assertEquals("<", content['<'])
    }

    @Test
    fun qwertzSoftwareSymLayerKeepsHardwareKeyPositions() {
        val rows = listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
        val mappings = mapOf(
            KeyEvent.KEYCODE_T to "[",
            KeyEvent.KEYCODE_Y to "]",
            KeyEvent.KEYCODE_Z to "»",
            KeyEvent.KEYCODE_X to "«"
        )

        val content = SoftwareKeyboardSymLabels.buildContentByChar(
            page = 2,
            rows = rows,
            symMappings = mappings,
            layoutName = "qwertz"
        )

        assertEquals("[", content['t'])
        assertEquals("]", content['z'])
        assertEquals("»", content['y'])
        assertEquals("«", content['x'])
    }
}
