package rocks.pkb.plektra.inputmethod

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LauncherEditorPolicyTest {
    @Test fun recognizesHomeButKeepsVisibleSearchAndOtherApps() {
        val editor = EditorInfo().apply {
            packageName = "bitpit.launcher"
            fieldId = -1
            inputType = 0x800b1
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        assertTrue(LauncherEditorPolicy.isNiagaraHomeEditor(editor))
        editor.inputType = 0x84001
        editor.imeOptions = 0x6000003
        editor.fieldId = 2131362607
        assertFalse(LauncherEditorPolicy.isNiagaraHomeEditor(editor))
        editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER
        editor.imeOptions = EditorInfo.IME_ACTION_DONE
        editor.fieldId = -1
        editor.packageName = "another.launcher"
        assertFalse(LauncherEditorPolicy.isNiagaraHomeEditor(editor))
        assertFalse(LauncherEditorPolicy.isNiagaraHomeEditor(null))
    }
}
