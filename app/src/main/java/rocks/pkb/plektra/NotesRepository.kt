package rocks.pkb.plektra

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class NotesRepository(private val prefs: SharedPreferences) {
    data class Note(val id: String, val title: String, val body: String, val cursor: Int = 0, val headingFocused: Boolean = false)

    fun all(): List<Note> {
        val stored = prefs.getString("collection", null)
        if (stored == null) {
            val migrated = Note(UUID.randomUUID().toString(), prefs.getString("heading", "") ?: "",
                prefs.getString("body", "") ?: "", prefs.getInt("cursor", 0), prefs.getInt("focus", R.id.notes_body) == R.id.notes_heading)
            write(listOf(migrated))
            prefs.edit().putString("active_note", migrated.id).apply()
            return listOf(migrated)
        }
        val array = JSONArray(stored)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Note(item.getString("id"), item.optString("title"), item.optString("body"), item.optInt("cursor"), item.optBoolean("headingFocused"))
        }
    }

    fun active(): Note {
        val notes = all()
        return notes.firstOrNull { it.id == prefs.getString("active_note", null) } ?: notes.first()
    }

    fun select(id: String) { prefs.edit().putString("active_note", id).apply() }

    fun create(): Note = Note(UUID.randomUUID().toString(), "", "").also {
        write(all() + it)
    }

    fun save(note: Note) { write(all().map { if (it.id == note.id) note else it }) }

    private fun write(notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { note -> array.put(JSONObject().put("id", note.id).put("title", note.title)
            .put("body", note.body).put("cursor", note.cursor).put("headingFocused", note.headingFocused)) }
        prefs.edit().putString("collection", array.toString()).apply()
    }
}
