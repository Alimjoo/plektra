package rocks.pkb.plektra.inputmethod.speech

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.util.Locale

/** Replaces only this dictation's composing span, never text elsewhere in the editor. */
class SpeechComposingEditor(
    private val connection: InputConnection,
    languageTag: String,
    private val capitalize: Boolean
) {
    private val locale = Locale.forLanguageTag(languageTag)
    private val prefix = if (connection.getTextBeforeCursor(1, 0)?.lastOrNull()?.isLetterOrDigit() == true) " " else ""
    private val suffix = if (connection.getTextAfterCursor(1, 0)?.firstOrNull()?.isLetterOrDigit() != false) " " else ""
    private val start = connection.getExtractedText(ExtractedTextRequest(), 0)?.let {
        it.startOffset + minOf(it.selectionStart, it.selectionEnd)
    }
    private var composing = false
    var expectedCursor: Int? = start
        private set

    private fun format(text: String): String {
        val trimmed = text.trim()
        return prefix + if (capitalize) trimmed.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        } else trimmed
    }

    fun partial(text: String) {
        if (text.isBlank()) return
        val formatted = format(text)
        expectedCursor = start?.plus(formatted.length)
        check(connection.setComposingText(formatted, 1)) { "Editor rejected live transcription" }
        composing = true
    }

    fun complete(text: String) {
        if (text.isBlank()) {
            if (composing) connection.commitText("", 1)
            composing = false
            return
        }
        check(connection.commitText(format(text) + suffix, 1)) { "Editor rejected transcription" }
        composing = false
    }

    fun finish() {
        if (composing) connection.finishComposingText()
        composing = false
    }
}
