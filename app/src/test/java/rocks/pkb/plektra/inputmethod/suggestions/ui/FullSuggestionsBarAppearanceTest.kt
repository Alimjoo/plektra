package rocks.pkb.plektra.inputmethod.suggestions.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import rocks.pkb.plektra.SettingsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class FullSuggestionsBarAppearanceTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test fun togglingWeightRendersTheSameCandidatesImmediately() {
        val bar = FullSuggestionsBar(context)
        val view = bar.ensureView()
        bar.setContentEnabled(suggestions = true, menuButtons = false)
        fun render() = bar.update(listOf("LongCandidateWithAccentsÄÖÜ", "Second", "Third"), true,
            null, null, false, "OwnWord", null, null, null, null, null, null)
        fun candidates() = descendants(view).filterIsInstance<TextView>().filter {
            it.text.toString() in listOf("LongCandidateWithAccentsÄÖÜ", "Second", "Third", "OwnWord")
        }
        render()
        assertTrue(candidates().isNotEmpty())
        assertTrue(candidates().all { it.typeface?.isBold != true })
        SettingsManager.setSuggestionsBold(context, true)
        render()
        assertTrue(candidates().all { it.typeface?.isBold == true })
        SettingsManager.setSuggestionsBold(context, false)
        render()
        assertTrue(candidates().all { it.typeface?.isBold != true })
    }

    @Test fun suppliedUserCandidatesRemainVisibleWithoutSubtypeDictionary() {
        val bar = FullSuggestionsBar(context)
        val view = bar.ensureView()
        bar.setContentEnabled(suggestions = true, menuButtons = false)
        bar.update(listOf("PersonalWord"), true, null, null, false, null,
            null, null, null, null, null, null)
        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(descendants(view).filterIsInstance<TextView>().any { it.text.toString() == "PersonalWord" })
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        else emptyList()
}
