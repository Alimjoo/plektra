package rocks.pkb.plektra.inputmethod

import android.text.InputType
import android.view.inputmethod.EditorInfo

internal object LauncherEditorPolicy {
    fun isNiagaraHomeEditor(info: EditorInfo?): Boolean =
        info?.packageName == "bitpit.launcher" &&
            info.fieldId == -1 &&
            info.inputType and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION) ==
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER) &&
            info.imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_DONE
}
