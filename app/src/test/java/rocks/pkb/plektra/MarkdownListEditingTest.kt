package rocks.pkb.plektra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownListEditingTest {
    @Test fun buttonsReplaceExistingMarkersAndToggleOff() {
        val source = "  - item"
        val checkbox = MarkdownListEditing.toggle(source, source.length, source.length, MarkdownListEditing.Kind.CHECKBOX)
        assertEquals("  - [ ] item", checkbox.text)
        assertEquals(checkbox.text.length, checkbox.start)
        assertEquals("  item", MarkdownListEditing.toggle(checkbox.text, checkbox.start, checkbox.end, MarkdownListEditing.Kind.CHECKBOX).text)
        assertEquals("  1. item", MarkdownListEditing.toggle(source, 5, 5, MarkdownListEditing.Kind.NUMBERED).text)
    }

    @Test fun selectionIncludesWholeLastLineAndDoesNotFormatFollowingLine() {
        val source = "one\ntwo\nthree"
        assertEquals("- one\n- two\nthree", MarkdownListEditing.toggle(source, 1, 6, MarkdownListEditing.Kind.BULLET).text)
        assertEquals("1. one\n2. two\nthree", MarkdownListEditing.toggle(source, 0, 8, MarkdownListEditing.Kind.NUMBERED).text)
    }

    @Test fun conversionPreservesCheckedStateAndDoesNotStackMarkers() {
        val source = "- [x] done\n2. next"
        val edit = MarkdownListEditing.toggle(source, 0, source.length, MarkdownListEditing.Kind.CHECKBOX)
        assertEquals("- [x] done\n- [ ] next", edit.text)
    }

    @Test fun enterContinuesListsAndEmptyItemsEndThem() {
        assertEquals("- [x] done\n- [ ] ", MarkdownListEditing.continueAfterEnter("- [x] done", "- [x] done\n", 11)?.text)
        assertEquals("3. done\n4. ", MarkdownListEditing.continueAfterEnter("3. done", "3. done\n", 8)?.text)
        assertEquals("previous\n", MarkdownListEditing.continueAfterEnter("previous\n- [ ] ", "previous\n- [ ] \n", 16)?.text)
        assertNull(MarkdownListEditing.continueAfterEnter("plain", "plain\n", 6))
        assertNull(MarkdownListEditing.continueAfterEnter("", "- one\n- two", 11))
    }

    @Test fun previewTogglesOnlyTheChosenCheckboxAndSkipsFencedCode() {
        val source = "```\n- [ ] sample\n```\n- [ ] actual\n- [x] done"
        assertEquals(listOf(3, 4), MarkdownListEditing.checkboxLines(source))
        assertEquals("```\n- [ ] sample\n```\n- [x] actual\n- [x] done", MarkdownListEditing.toggleCheckbox(source, 3))
        assertEquals("- [ ] done", MarkdownListEditing.toggleCheckbox("- [x] done", 0))
    }
}
