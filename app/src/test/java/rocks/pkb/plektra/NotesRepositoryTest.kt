package rocks.pkb.plektra

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotesRepositoryTest {
    @Test fun existingSingleNoteMigratesAndMultipleNotesRemainIndependent() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("notes-repository-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("heading", "Existing").putString("body", "Testyyy").putInt("cursor", 4).commit()
        val repository = NotesRepository(prefs)
        val first = repository.active()
        assertEquals("Existing", first.title)
        assertEquals("Testyyy", first.body)
        assertEquals(4, first.cursor)
        val second = repository.create().copy(title = "Second", body = "**bold**", cursor = 2)
        repository.save(second)
        repository.select(second.id)
        val reopened = NotesRepository(prefs)
        assertEquals(second, reopened.active())
        reopened.select(first.id)
        assertEquals(first, reopened.active())
        assertEquals(2, reopened.all().size)
    }
}
