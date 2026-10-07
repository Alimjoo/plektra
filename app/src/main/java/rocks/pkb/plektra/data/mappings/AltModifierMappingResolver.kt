package rocks.pkb.plektra.data.mappings

import android.content.Context
import android.content.res.AssetManager
import rocks.pkb.plektra.AltModifierBinding
import rocks.pkb.plektra.SettingsManager
import rocks.pkb.plektra.SymPagesConfig

object AltModifierMappingResolver {
    fun resolve(assets: AssetManager, context: Context): Map<Int, String> {
        val configuredBinding = SettingsManager.getAltModifierBinding(context)
        val binding = if (configuredBinding == AltModifierBinding.FirstEnabledSymKeyLayer) {
            when (SettingsManager.getSymPagesConfig(context).firstEnabledKeyLayer()) {
                SymPagesConfig.PAGE_EMOJI -> AltModifierBinding.Emoji
                SymPagesConfig.PAGE_SYMBOLS -> AltModifierBinding.Symbols
                else -> AltModifierBinding.DeviceSym
            }
        } else {
            configuredBinding
        }
        return when (binding) {
            AltModifierBinding.Emoji ->
                SettingsManager.getSymMappings(context).takeIf { it.isNotEmpty() }
                    ?: KeyMappingLoader.loadSymKeyMappings(assets)
            AltModifierBinding.Symbols ->
                SettingsManager.getSymMappingsPage2(context).takeIf { it.isNotEmpty() }
                    ?: KeyMappingLoader.loadSymKeyMappingsPage2(assets)
            AltModifierBinding.DeviceSym,
            AltModifierBinding.FirstEnabledSymKeyLayer -> DeviceSymMappingRepository.load(assets, context)
            is AltModifierBinding.DeviceSymProfile ->
                DeviceSymMappingRepository.load(assets, context, binding.profileId)
        }
    }
}
