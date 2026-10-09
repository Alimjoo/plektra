package rocks.pkb.plektra

import android.content.Intent
import android.content.res.Configuration
import android.widget.EditText
import android.widget.TextView
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotesActivityTest {
    @Test fun syntheticCaptureDoesNotOverwritePersonalNotes() {
        val personal = Robolectric.buildActivity(NotesActivity::class.java).setup()
        personal.get().findViewById<EditText>(R.id.notes_body).setText("Personal note")
        personal.pause().stop().destroy()
        val capture = Robolectric.buildActivity(ShowcaseNotesActivity::class.java,
            Intent().putExtra("text", "Synthetic capture")).setup()
        capture.pause().stop().destroy()
        val reopened = Robolectric.buildActivity(NotesActivity::class.java).setup()
        assertEquals("Personal note", reopened.get().findViewById<EditText>(R.id.notes_body).text.toString())
        reopened.pause().stop().destroy()
    }

    @Test fun previewCheckboxClickUpdatesAndPersistsMarkdown() {
        val controller = Robolectric.buildActivity(NotesActivity::class.java).setup()
        val activity = controller.get()
        val editor = activity.findViewById<EditText>(R.id.notes_body)
        editor.setText("- [ ] One\n- [x] Two")
        activity.findViewById<TextView>(R.id.notes_preview_toggle).performClick()
        val preview = activity.findViewById<TextView>(R.id.notes_preview_body)
        preview.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        preview.layout(0, 0, 600, 600)
        val y = preview.totalPaddingTop + preview.layout.getLineBottom(0) / 2f
        val x = preview.totalPaddingLeft + 3f
        preview.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0))
        preview.dispatchTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, x, y, 0))
        assertEquals("- [x] One\n- [x] Two", editor.text.toString())
        controller.pause().stop().destroy()
        val reopened = Robolectric.buildActivity(NotesActivity::class.java).setup()
        assertEquals("- [x] One\n- [x] Two", reopened.get().findViewById<EditText>(R.id.notes_body).text.toString())
        reopened.pause().stop().destroy()
    }

    @Test fun imeEnterContinuesChecklistWithAnUncheckedItem() {
        val controller = Robolectric.buildActivity(NotesActivity::class.java).setup()
        val editor = controller.get().findViewById<EditText>(R.id.notes_body)
        editor.setText("- [x] One")
        editor.setSelection(editor.length())
        editor.onCreateInputConnection(EditorInfo()).commitText("\n", 1)
        assertEquals("- [x] One\n- [ ] ", editor.text.toString())
        assertEquals(editor.length(), editor.selectionStart)
        controller.pause().stop().destroy()
    }

    @Test fun hardwareChangesKeepEditorFocusAndSelection() {
        val controller = Robolectric.buildActivity(NotesActivity::class.java).setup()
        val activity = controller.get()
        val editor = activity.findViewById<EditText>(R.id.notes_body)
        editor.setText("Testyyy")
        editor.requestFocus()
        editor.setSelection(2, 5)
        for (keyboard in listOf(Configuration.KEYBOARD_QWERTY, Configuration.KEYBOARD_NOKEYS)) {
            activity.onConfigurationChanged(Configuration(activity.resources.configuration).apply { this.keyboard = keyboard })
            assertTrue(editor.hasFocus())
            assertEquals(2, editor.selectionStart)
            assertEquals(5, editor.selectionEnd)
            assertEquals("Testyyy", editor.text.toString())
        }
        assertFalse(activity.findViewById<EditText>(R.id.notes_heading).hasFocus())
        controller.pause().stop().destroy()
    }

    @Test fun recreationDoesNotReplayOriginalCaptureText() {
        val controller = Robolectric.buildActivity(ShowcaseNotesActivity::class.java,
            Intent().putExtra("text", "Test").putExtra("title", "Original")).setup()
        val activity = controller.get()
        activity.findViewById<EditText>(R.id.notes_body).apply { setText("Testyyy"); setSelection(4) }
        activity.findViewById<EditText>(R.id.notes_heading).setText("Meine Notiz")
        controller.recreate()
        assertEquals("Testyyy", controller.get().findViewById<EditText>(R.id.notes_body).text.toString())
        assertEquals("Meine Notiz", controller.get().findViewById<EditText>(R.id.notes_heading).text.toString())
        assertEquals(4, controller.get().findViewById<EditText>(R.id.notes_body).selectionStart)
        controller.pause().stop().destroy()
    }

    @Test fun reopeningRestoresNoteAndPreviewRendersMarkdownWithoutChangingSource() {
        val first = Robolectric.buildActivity(NotesActivity::class.java).setup()
        first.get().findViewById<EditText>(R.id.notes_body).setText("## Heading\n\n**bold**")
        first.get().findViewById<EditText>(R.id.notes_heading).setText("Saved title")
        first.pause().stop().destroy()
        val second = Robolectric.buildActivity(NotesActivity::class.java).setup()
        second.get().findViewById<TextView>(R.id.notes_preview_toggle).performClick()
        val preview = second.get().findViewById<TextView>(R.id.notes_preview_body).text.toString()
        assertFalse(preview.contains("**"))
        assertEquals("## Heading\n\n**bold**", second.get().findViewById<EditText>(R.id.notes_body).text.toString())
        assertEquals("Saved title", second.get().findViewById<EditText>(R.id.notes_heading).text.toString())
        second.pause().stop().destroy()
    }

    @Test fun splitViewUpdatesPreviewWhileTypingAndPreservesSource() {
        val controller = Robolectric.buildActivity(NotesActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<TextView>(R.id.notes_both_toggle).performClick()
        activity.findViewById<EditText>(R.id.notes_body).setText("**updated**")
        assertEquals("updated", activity.findViewById<TextView>(R.id.notes_preview_body).text.toString())
        assertEquals("**updated**", activity.findViewById<EditText>(R.id.notes_body).text.toString())
        controller.pause().stop().destroy()
    }
}
