package rocks.pkb.plektra

import android.content.Intent
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShowcaseNotesActivityTest {
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

}
