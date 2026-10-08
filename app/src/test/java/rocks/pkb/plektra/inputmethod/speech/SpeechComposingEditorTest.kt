package rocks.pkb.plektra.inputmethod.speech

import android.app.Activity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechComposingEditorTest {
    @Test fun partialAndFinalReplaceOnlyDictationAndPreserveSurroundingText() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val field = EditText(activity).apply { setText("Vorher. Nachher."); setSelection(8) }
        activity.setContentView(field)
        val connection = field.onCreateInputConnection(EditorInfo())!!
        val editor = SpeechComposingEditor(connection, "de-DE", true)
        editor.partial("hallo we")
        assertEquals("Vorher. Hallo weNachher.", field.text.toString())
        editor.partial("hallo welt")
        assertEquals("Vorher. Hallo weltNachher.", field.text.toString())
        editor.complete("hallo Welt.")
        assertEquals("Vorher. Hallo Welt. Nachher.", field.text.toString())
    }

    @Test fun endingLiveInputPreservesVisibleTextBeforeTypingContinues() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val field = EditText(activity).apply { setText("Start"); setSelection(5) }
        activity.setContentView(field)
        val connection = field.onCreateInputConnection(EditorInfo())!!
        val editor = SpeechComposingEditor(connection, "de-DE", false)
        editor.partial("weiter")
        assertEquals("Start weiter", field.text.toString())
        editor.finish()
        connection.commitText("!", 1)
        assertEquals("Start weiter!", field.text.toString())
    }
}
