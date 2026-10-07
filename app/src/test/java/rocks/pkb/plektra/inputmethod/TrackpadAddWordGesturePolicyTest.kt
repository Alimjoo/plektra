package rocks.pkb.plektra.inputmethod

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadAddWordGesturePolicyTest {
    @Test
    fun addWordGesture_stillWorksFromLeftThirdWhenFullWidthDisabled() {
        assertTrue(
            TrackpadAddWordGesturePolicy.canAddWordByGesture(
                third = 0,
                addWordGestureEnabled = true,
                fullWidthWhenAddOnlyEnabled = false,
                addWordCandidate = "Plektra",
                visibleSuggestions = emptyList()
            )
        )
    }

    @Test
    fun addOnlyCandidate_canUseAnyThirdWhenFullWidthEnabled() {
        assertTrue(
            TrackpadAddWordGesturePolicy.canAddWordByGesture(
                third = 2,
                addWordGestureEnabled = true,
                fullWidthWhenAddOnlyEnabled = true,
                addWordCandidate = "Plektra",
                visibleSuggestions = emptyList()
            )
        )
    }

    @Test
    fun visibleSuggestions_keepNonLeftThirdForSuggestionSelection() {
        assertFalse(
            TrackpadAddWordGesturePolicy.canAddWordByGesture(
                third = 1,
                addWordGestureEnabled = true,
                fullWidthWhenAddOnlyEnabled = true,
                addWordCandidate = "Plektra",
                visibleSuggestions = listOf("past", "paste")
            )
        )
    }

    @Test
    fun disabledAddWordGesture_neverAddsWord() {
        assertFalse(
            TrackpadAddWordGesturePolicy.canAddWordByGesture(
                third = 0,
                addWordGestureEnabled = false,
                fullWidthWhenAddOnlyEnabled = true,
                addWordCandidate = "Plektra",
                visibleSuggestions = emptyList()
            )
        )
    }
}
