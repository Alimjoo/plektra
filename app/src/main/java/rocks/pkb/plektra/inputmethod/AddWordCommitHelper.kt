package rocks.pkb.plektra.inputmethod

import android.view.inputmethod.InputConnection
import rocks.pkb.plektra.core.AutoSpaceTracker
import rocks.pkb.plektra.core.Punctuation

object AddWordCommitHelper {
    fun commitAutoSpaceAfterAddWord(inputConnection: InputConnection) {
        val next = inputConnection.getTextAfterCursor(1, 0)?.firstOrNull()
        if (next?.let { it.isWhitespace() || Punctuation.isWordBoundary(it, null, null) } == true) {
            AutoSpaceTracker.clear()
            return
        }
        inputConnection.commitText(" ", 1)
        AutoSpaceTracker.markAutoSpace()
    }
}
