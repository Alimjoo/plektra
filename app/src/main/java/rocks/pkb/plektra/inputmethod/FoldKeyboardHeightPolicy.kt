package rocks.pkb.plektra.inputmethod

internal object FoldKeyboardHeightPolicy {
    fun limit(displayHeight: Int, horizontalFoldBottom: Int?): Int {
        val lowerHalf = displayHeight / 2
        return horizontalFoldBottom?.takeIf { it in 1 until displayHeight }
            ?.let { minOf(lowerHalf, displayHeight - it) } ?: lowerHalf
    }
}
