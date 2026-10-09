package rocks.pkb.plektra

internal object MarkdownListEditing {
    enum class Kind { BULLET, NUMBERED, CHECKBOX }
    data class Edit(val text: String, val start: Int, val end: Int = start)
    private val marker = Regex("^([\\t ]*)([-+*]|\\d+[.)])\\s+(?:\\[([ xX])\\]\\s*)?(.*)$")

    private fun kind(match: MatchResult): Kind = when {
        match.groups[3] != null -> Kind.CHECKBOX
        match.groupValues[2].first().isDigit() -> Kind.NUMBERED
        else -> Kind.BULLET
    }

    fun toggle(text: String, selectionStart: Int, selectionEnd: Int, requested: Kind): Edit {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
        val blockStart = text.lastIndexOf('\n', start - 1) + 1
        val last = if (end > start && text.getOrNull(end - 1) == '\n') end - 1 else end
        val blockEnd = text.indexOf('\n', last).let { if (it < 0) text.length else it }
        val lines = text.substring(blockStart, blockEnd).split('\n')
        val matches = lines.map { marker.matchEntire(it) }
        val remove = lines.indices.filter { lines[it].isNotBlank() }.let { affected ->
            affected.isNotEmpty() && affected.all { matches[it]?.let(::kind) == requested }
        }
        var number = matches.firstOrNull()?.groupValues?.get(2)?.takeWhile(Char::isDigit)?.toIntOrNull() ?: 1
        var originalOffset = blockStart
        var newOffset = blockStart
        var mappedStart = start
        var mappedEnd = end
        val edited = lines.mapIndexed { index, line ->
            val match = matches[index]
            val indent = match?.groupValues?.get(1) ?: line.takeWhile { it == ' ' || it == '\t' }
            val body = match?.groupValues?.get(4) ?: line.drop(indent.length)
            val oldPrefix = line.length - body.length
            val prefix = when {
                remove || (line.isBlank() && lines.size > 1) -> indent
                requested == Kind.BULLET -> "$indent- "
                requested == Kind.NUMBERED -> "$indent${number++}. "
                else -> "$indent- [${match?.groupValues?.get(3)?.takeIf { it.isNotEmpty() } ?: " "}] "
            }
            fun map(position: Int): Int = newOffset + prefix.length + (position - originalOffset - oldPrefix).coerceAtLeast(0)
            if (start in originalOffset..originalOffset + line.length) mappedStart = map(start)
            if (end in originalOffset..originalOffset + line.length) mappedEnd = map(end)
            originalOffset += line.length + 1
            newOffset += prefix.length + body.length + 1
            prefix + body
        }.joinToString("\n")
        if (end > blockEnd) mappedEnd = end + edited.length - (blockEnd - blockStart)
        val result = text.replaceRange(blockStart, blockEnd, edited)
        return Edit(result, mappedStart.coerceIn(0, result.length), mappedEnd.coerceIn(0, result.length))
    }

    fun continueAfterEnter(before: String, after: String, cursor: Int): Edit? {
        if (after.length != before.length + 1 || cursor !in 1..after.length || after[cursor - 1] != '\n' ||
            after.removeRange(cursor - 1, cursor) != before) return null
        val lineStart = before.lastIndexOf('\n', cursor - 2) + 1
        val line = before.substring(lineStart, cursor - 1)
        val match = marker.matchEntire(line) ?: return null
        val indent = match.groupValues[1]
        if (match.groupValues[4].isBlank()) {
            return Edit(after.replaceRange(lineStart, cursor, indent), lineStart + indent.length)
        }
        val prefix = when (kind(match)) {
            Kind.CHECKBOX -> "$indent- [ ] "
            Kind.BULLET -> "$indent${match.groupValues[2]} "
            Kind.NUMBERED -> "$indent${(match.groupValues[2].dropLast(1).toIntOrNull() ?: return null) + 1}${match.groupValues[2].last()} "
        }
        return Edit(after.substring(0, cursor) + prefix + after.substring(cursor), cursor + prefix.length)
    }

    fun toggleCheckbox(text: String, lineIndex: Int): String {
        val lines = text.split('\n').toMutableList()
        val line = lines.getOrNull(lineIndex) ?: return text
        val match = marker.matchEntire(line.trimStart('>',' ')) ?: marker.matchEntire(line) ?: return text
        val checked = match.groups[3] ?: return text
        val offset = line.indexOf('[') + 1
        if (offset <= 0) return text
        lines[lineIndex] = line.replaceRange(offset, offset + 1, if (checked.value.equals("x", true)) " " else "x")
        return lines.joinToString("\n")
    }

    fun checkboxLines(text: String): List<Int> {
        var fence: Char? = null
        return text.lines().mapIndexedNotNull { index, line ->
            val content = line.trimStart(' ', '\t', '>')
            if (content.startsWith("```") || content.startsWith("~~~")) {
                val delimiter = content.first()
                if (fence == null) fence = delimiter else if (fence == delimiter) fence = null
                null
            } else if (fence == null && marker.matchEntire(content)?.groups?.get(3) != null) index else null
        }
    }
}
