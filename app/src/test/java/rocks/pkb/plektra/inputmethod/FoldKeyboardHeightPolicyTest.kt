package rocks.pkb.plektra.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Test

class FoldKeyboardHeightPolicyTest {
    @Test fun openedInnerDisplayReservesUpperHalf() {
        assertEquals(928, FoldKeyboardHeightPolicy.limit(1856, null))
    }

    @Test fun offCenterHorizontalHingeReservesItsEntireUpperRegion() {
        assertEquals(756, FoldKeyboardHeightPolicy.limit(1856, 1100))
        assertEquals(928, FoldKeyboardHeightPolicy.limit(1856, 800))
    }

    @Test fun featureOutsideDisplayDoesNotCollapseKeyboard() {
        assertEquals(928, FoldKeyboardHeightPolicy.limit(1856, 1900))
    }
}
