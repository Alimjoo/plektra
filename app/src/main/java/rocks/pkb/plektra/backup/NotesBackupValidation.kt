package rocks.pkb.plektra.backup

import org.json.JSONArray

internal object NotesBackupValidation {
    fun validate(entries: Map<String, PreferenceValue>) {
        entries["view_mode"]?.value?.let { require(it is Int && it in 0..2) { "Invalid notes view mode" } }
        entries["cursor"]?.value?.let { require(it is Int && it >= 0) { "Invalid notes cursor" } }
        val collection = entries["collection"]?.value as? String ?: return
        val array = JSONArray(collection)
        require(array.length() in 1..10000) { "Invalid notes collection size" }
        val ids = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val note = array.getJSONObject(index)
            val id = note.get("id")
            val body = note.get("body")
            val title = note.get("title")
            require(id is String && id.isNotBlank() && ids.add(id) && body is String && title is String) {
                "Invalid note content"
            }
            if (note.has("cursor")) {
                val cursor = note.get("cursor")
                require(cursor is Int && cursor in 0..(if (note.optBoolean("headingFocused")) title.length else body.length)) { "Invalid note cursor" }
            }
            require(!note.has("headingFocused") || note.get("headingFocused") is Boolean) { "Invalid note focus" }
        }
        val active = entries["active_note"]?.value
        require(active is String && active in ids) { "Invalid active note" }
    }
}
