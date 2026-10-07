package rocks.pkb.plektra.commands

import android.content.Context
import androidx.core.content.ContextCompat
import rocks.pkb.plektra.R

class PlektraCommandSource : CommandSource {
    override val id = CommandSourceId.Plektra

    override fun getCommands(context: Context): List<CommandTarget> {
        return listOf(
            CommandTarget(
                id = COMMAND_QUICK_LAUNCHER,
                source = id,
                kind = CommandKind.PlektraAction,
                label = "Plektra QuickLauncher",
                subtitle = "Open Plektra search",
                icon = CommandIcon.Search,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_QUICK_LAUNCHER),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf("Plektra", "QuickLauncher", "Search")
            ),
            CommandTarget(
                id = COMMAND_MAIN_ACTIVITY,
                source = id,
                kind = CommandKind.PlektraAction,
                label = "Plektra",
                subtitle = "Open app settings",
                icon = CommandIcon.Settings,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_MAIN_ACTIVITY),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf("Plektra", "Settings")
            ),
            CommandTarget(
                id = COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE,
                source = id,
                kind = CommandKind.PlektraAction,
                label = "Toggle Keyboard Mode",
                subtitle = "Switch Virtual / Hardware",
                icon = CommandIcon.DrawableIcon(ContextCompat.getDrawable(context, R.drawable.expansion_panels_24)),
                launch = CommandLaunchSpec.InternalAction(ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE),
                capabilities = setOf(CommandCapability.AdjustsDeviceState),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.QuickLauncher, CommandSurface.NavMode),
                searchTokens = listOf("Keyboard", "Software", "Virtual", "Hardware", "Toggle")
            )
        )
    }

    companion object {
        const val COMMAND_QUICK_LAUNCHER = "plektra.quick_launcher"
        const val COMMAND_MAIN_ACTIVITY = "plektra.main"
        const val COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE = "plektra.toggle_software_keyboard_mode"
        const val ACTION_OPEN_QUICK_LAUNCHER = "open_quick_launcher"
        const val ACTION_OPEN_MAIN_ACTIVITY = "open_main_activity"
        const val ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE = "toggle_software_keyboard_mode"
    }
}
