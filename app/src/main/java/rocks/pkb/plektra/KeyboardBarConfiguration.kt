package rocks.pkb.plektra

import android.content.Context

/** Functional choices are shared by hardware and software keyboards, independently of themes. */
data class KeyboardBarConfiguration(
    val suggestions: Boolean,
    val actions: Boolean,
    val bottomIndicators: Boolean,
    val androidIndicators: Boolean,
    val inlineIndicators: Boolean
) {
    fun presentation(slim: Boolean) = KeyboardBarPresentation(
        suggestions = suggestions,
        actions = actions && !slim,
        bottomIndicators = bottomIndicators,
        inlineIndicators = inlineIndicators && suggestions
    )

    companion object {
        fun read(context: Context) = KeyboardBarConfiguration(
            suggestions = SettingsManager.getSuggestionsEnabled(context),
            actions = SettingsManager.areStatusBarVariationsEnabled(context),
            bottomIndicators = SettingsManager.getModifierIndicatorShowsBottomStrip(context),
            androidIndicators = SettingsManager.getModifierIndicatorShowsMenuBar(context),
            inlineIndicators = SettingsManager.getModifierIndicatorShowsStatusBar(context)
        )
    }
}

data class KeyboardBarPresentation(
    val suggestions: Boolean,
    val actions: Boolean,
    val bottomIndicators: Boolean,
    val inlineIndicators: Boolean
) {
    val hasPersistentContent: Boolean get() = suggestions || actions || bottomIndicators
}
