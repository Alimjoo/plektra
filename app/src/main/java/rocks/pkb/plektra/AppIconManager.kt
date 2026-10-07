package rocks.pkb.plektra

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIconVariant(val preferenceValue: String, val aliasClassName: String) {
    ClosedOrange("closed_orange", "OrangeLauncherAlias"),
    OpenOrange("open_orange", "OpenOrangeLauncherAlias"),
    ClosedBlue("closed_blue", "BlueLauncherAlias"),
    OpenBlue("open_blue", "OpenBlueLauncherAlias")
}

object AppIconManager {
    private const val PREFERENCE_KEY = "app_icon_variant"

    fun selectedVariant(context: Context): AppIconVariant {
        val explicitlyEnabled = AppIconVariant.entries.firstOrNull { variant ->
            context.packageManager.getComponentEnabledSetting(componentName(context, variant)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        if (explicitlyEnabled != null) return explicitlyEnabled

        val fallback = if (BuildConfig.RELEASE_CHANNEL == "nightly") {
            AppIconVariant.ClosedBlue
        } else {
            AppIconVariant.ClosedOrange
        }
        val stored = SettingsManager.getPreferences(context).getString(PREFERENCE_KEY, null)
        return when (stored) {
            "orange" -> AppIconVariant.ClosedOrange
            "blue" -> AppIconVariant.ClosedBlue
            else -> AppIconVariant.entries.firstOrNull { it.preferenceValue == stored } ?: fallback
        }
    }

    fun selectVariant(context: Context, variant: AppIconVariant) {
        SettingsManager.getPreferences(context)
            .edit()
            .putString(PREFERENCE_KEY, variant.preferenceValue)
            .apply()
        applyVariant(context, variant)
    }

    private fun applyVariant(context: Context, variant: AppIconVariant) {
        val packageManager = context.packageManager
        AppIconVariant.entries.forEach { candidate ->
            packageManager.setComponentEnabledSetting(
                componentName(context, candidate),
                if (candidate == variant) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun componentName(context: Context, variant: AppIconVariant): ComponentName =
        ComponentName(
            context.packageName,
            "${AppIconManager::class.java.packageName}.${variant.aliasClassName}"
        )
}
