package rocks.pkb.plektra.inputmethod.aospkeyboard

internal object SoftwareKeyboardActionLabels {
    fun display(content: String): String = when (content) {
        "__DPAD_UP__" -> "↑"
        "__DPAD_DOWN__" -> "↓"
        "__DPAD_LEFT__" -> "←"
        "__DPAD_RIGHT__" -> "→"
        else -> content
    }
}
