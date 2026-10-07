package rocks.pkb.plektra

import android.content.Context
import java.time.LocalDate

enum class MainBackgroundPreference(val storageValue: String) {
    Daily("daily"),
    Strings("strings"),
    Keyboard("keyboard");

    companion object {
        fun fromStorage(value: String?): MainBackgroundPreference =
            entries.firstOrNull { it.storageValue == value } ?: Daily
    }
}

object MainBackgroundPreferences {
    const val KEY = "main_background_preference"

    fun get(context: Context): MainBackgroundPreference =
        MainBackgroundPreference.fromStorage(
            SettingsManager.getPreferences(context).getString(KEY, null)
        )

    fun set(context: Context, preference: MainBackgroundPreference) {
        SettingsManager.getPreferences(context).edit()
            .putString(KEY, preference.storageValue)
            .apply()
    }

    fun resolve(
        preference: MainBackgroundPreference,
        epochDay: Long = LocalDate.now().toEpochDay()
    ): MainBackgroundPreference = when (preference) {
        MainBackgroundPreference.Daily -> if (epochDay % 2L == 0L) {
            MainBackgroundPreference.Strings
        } else {
            MainBackgroundPreference.Keyboard
        }
        else -> preference
    }
}
