package rocks.pkb.plektra.backup

import rocks.pkb.plektra.DeviceIdentitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RestoreManagerAndBackupContractTest {

    @Test
    fun backupMetadata_roundTripsOptionalDeviceIdentityAndReadsLegacyMetadata() {
        val sourceDevice = device("titan2", "Titan 2")
        val metadata = BackupMetadata(
            versionCode = 42,
            versionName = "test",
            timestamp = "2026-08-17T00:00:00Z",
            components = listOf("prefs/plektra_prefs.json"),
            sourceDevice = sourceDevice
        )
        val currentFile = File.createTempFile("plektra_metadata_", ".json")
        val legacyFile = File.createTempFile("plektra_metadata_legacy_", ".json")
        try {
            currentFile.writeText(metadata.toJsonString())
            legacyFile.writeText(
                """{"versionCode":1,"versionName":"legacy","timestamp":"now","components":[]}"""
            )

            assertEquals(sourceDevice, BackupMetadata.fromFile(currentFile)?.sourceDevice)
            assertNull(BackupMetadata.fromFile(legacyFile)?.sourceDevice)
        } finally {
            currentFile.delete()
            legacyFile.delete()
        }
    }

    @Test
    fun deviceChange_usesStableIdsAndRejectsIncompleteHardwareIdentity() {
        val titan2 = device("titan2", "Titan 2")
        val elite = device("titan2-elite", "Titan 2 Elite")
        val unknown = device(null, "Unknown device")

        assertEquals(
            RestoreManager.DeviceChange(titan2, elite),
            RestoreManager.detectDeviceChange(titan2, elite)
        )
        assertNull(RestoreManager.detectDeviceChange(elite, elite))
        assertNull(RestoreManager.detectDeviceChange(null, elite))
        assertNull(RestoreManager.detectDeviceChange(titan2, unknown))
    }

    @Test
    fun deviceChange_detectsPixelWithoutARecognizedKeyboardProfile() {
        val source = device("titan2", "Titan 2")
        val pixel = device(null, "Pixel 7a").copy(manufacturer = "Google", device = "lynx")
        assertEquals(RestoreManager.DeviceChange(source, pixel), RestoreManager.detectDeviceChange(source, pixel))
        assertNull(RestoreManager.detectDeviceChange(pixel, pixel.copy(buildDisplay = "new OTA")))
    }

    @Test
    fun adaptiveImportPolicy_containsOnlyTargetDeviceDerivedSettings() {
        assertEquals(
            setOf(
                "plektra_prefs:physical_keyboard_profile_override",
                "plektra_prefs:titan2_layout_enabled",
                "plektra_prefs:titan2_elite_rounded_corner_insets"
            ),
            BackupPreferencePolicy.targetDeviceDerivedKeys
        )
    }

    @Test
    fun userDictionaryEntries_isRecognizedForFreshInstallRestore() {
        val recognized = PreferenceSchemas.isRecognized(
            prefName = "plektra_prefs",
            key = "user_dictionary_entries"
        )

        assertTrue(recognized)
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "user_dictionary_entries")
        )
    }

    private fun device(stableId: String?, displayName: String) = DeviceIdentitySnapshot(
        stableId = stableId,
        displayName = displayName,
        brand = "unihertz",
        manufacturer = "unihertz",
        model = displayName,
        device = stableId.orEmpty(),
        product = stableId.orEmpty(),
        board = "board",
        buildDisplay = displayName,
        buildFingerprint = "fingerprint"
    )

    @Test
    fun snippetExpansionPreferences_areRecognizedForFreshInstallRestore() {
        val expected = mapOf(
            "snippets_enabled" to PreferenceValueType.BOOLEAN,
            "snippets_prefix" to PreferenceValueType.STRING,
            "snippets_v1" to PreferenceValueType.STRING,
            "snippets_presentation" to PreferenceValueType.STRING,
            "snippets_exact_on_space" to PreferenceValueType.BOOLEAN,
            "snippets_accept_prefix_with_space" to PreferenceValueType.BOOLEAN,
            "snippets_accept_with_tab" to PreferenceValueType.BOOLEAN,
            "snippets_accept_with_enter" to PreferenceValueType.BOOLEAN
        )
        expected.forEach { (key, type) ->
            assertTrue(PreferenceSchemas.isRecognized("plektra_prefs", key))
            assertEquals(type, PreferenceSchemas.expectedType("plektra_prefs", key))
        }
    }

    @Test
    fun emojiAndSymbolExpansionPreferences_areRecognizedForFreshInstallRestore() {
        val expected = mapOf(
            "emoji_shortcodes_enabled" to PreferenceValueType.BOOLEAN,
            "symbol_shortcodes_enabled" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_presentation" to PreferenceValueType.STRING,
            "emoji_symbols_exact_on_space" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_accept_prefix_with_space" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_accept_with_tab" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_accept_with_enter" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_exact_on_close" to PreferenceValueType.BOOLEAN
        )
        expected.forEach { (key, type) ->
            assertTrue(PreferenceSchemas.isRecognized("plektra_prefs", key))
            assertEquals(type, PreferenceSchemas.expectedType("plektra_prefs", key))
        }
    }

    @Test
    fun layoutSwitchPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "alt_shift_layout_switch")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "alt_enter_layout_switch")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "toast_on_layout_switch")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "software_keyboard_mode_toggle_toasts")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "software_keyboard_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "input_style_suggestion_locales")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "hidden_system_input_styles")
        )
    }

    @Test
    fun currentAndLegacyAltModifierBindings_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "alt_modifier_binding")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "alt_character_layer_binding")
        )
    }

    @Test
    fun quickLauncherPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "quick_launcher_behavior")
        )
    }

    @Test
    fun accidentalKeyProtectionPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "overlapping_keys_enabled")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_overlapping_keys_enabled")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_overlapping_keys_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_number_row_input_mode")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_number_row_repeat_enabled")
        )
    }

    @Test
    fun clicksButtonModes_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_button_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_meta_button_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_alt_button_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_microphone_button_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_red_button_binding_choice")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_red_button_binding_output")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_keyboard_button_binding_choice")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_keyboard_button_binding_output")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_microphone_button_binding_choice")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "clicks_microphone_button_binding_output")
        )
    }

    @Test
    fun clicksPowerStateAndSocCalibrationAreRecognizedOnFreshInstall() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(
                "plektra_prefs",
                "clicks_power_keyboard_snapshots_v1"
            )
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(
                "plektra_prefs",
                "clicks_power_soc_calibration_PK-42"
            )
        )
        assertTrue(
            PreferenceSchemas.isRecognized(
                prefName = "plektra_prefs",
                key = "clicks_power_keyboard_snapshots_v1"
            )
        )
        assertTrue(
            PreferenceSchemas.isRecognized(
                prefName = "plektra_prefs",
                key = "clicks_power_soc_calibration_PK-42"
            )
        )
    }

    @Test
    fun punctuationSpacingPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "auto_space_punctuation")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "space_after_punctuation")
        )
    }

    @Test
    fun deleteMethodsPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "shift_backspace_delete")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "alt_backspace_delete")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "backspace_at_start_delete")
        )
    }

    @Test
    fun statusBarAndVariationPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "titan2_elite_rounded_corner_insets")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "static_variation_bar_preset")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "status_bar_variations_visible")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "status_bar_slot_left")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "status_bar_slot_right_1")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "status_bar_slot_right_2")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "status_bar_slots_left")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("plektra_prefs", "status_bar_slots_right")
        )
    }

    @Test
    fun symPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("plektra_prefs", "emoji_picker_expanded_height")
        )
    }

    @Test
    fun shouldNotifyUserDictionaryRefresh_whenUserDictionaryPrefsRestored() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = listOf("plektra_prefs:user_dictionary_entries"),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = emptyList(),
            skippedFiles = emptyList()
        )

        assertTrue(RestoreManager.shouldNotifyUserDictionaryRefresh(prefs, files))
    }

    @Test
    fun shouldNotifyUserDictionaryRefresh_whenUserDefaultsFileRestored() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = emptyList(),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = listOf("user_defaults.json"),
            skippedFiles = emptyList()
        )

        assertTrue(RestoreManager.shouldNotifyUserDictionaryRefresh(prefs, files))
    }

    @Test
    fun collectTriggeredPostRestoreActions_detectsUserDictionaryFromNestedFilePath() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = emptyList(),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = listOf("files/user_defaults.json"),
            skippedFiles = emptyList()
        )

        val actions = RestoreManager.collectTriggeredPostRestoreActions(prefs, files)

        assertTrue(actions.contains(RestoreManager.PostRestoreAction.REFRESH_USER_DICTIONARY))
        assertEquals(1, actions.size)
    }

    @Test
    fun collectTriggeredPostRestoreActions_deduplicatesWhenPrefAndFileBothMatch() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = listOf("plektra_prefs:user_dictionary_entries"),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = listOf("user_defaults.json"),
            skippedFiles = emptyList()
        )

        val actions = RestoreManager.collectTriggeredPostRestoreActions(prefs, files)

        assertEquals(setOf(RestoreManager.PostRestoreAction.REFRESH_USER_DICTIONARY), actions)
    }

    @Test
    fun collectTriggeredPostRestoreActions_registersRestoredCustomInputStyles() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = listOf("plektra_prefs:custom_input_styles"),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = listOf("keyboard_layouts/custom-layout.json"),
            skippedFiles = emptyList()
        )

        val actions = RestoreManager.collectTriggeredPostRestoreActions(prefs, files)

        assertEquals(
            setOf(RestoreManager.PostRestoreAction.REGISTER_CUSTOM_INPUT_STYLES),
            actions
        )
    }

    @Test
    fun shouldNotifyUserDictionaryRefresh_falseForUnrelatedRestore() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = listOf("plektra_prefs:keyboard_layout"),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = listOf("variations.json"),
            skippedFiles = emptyList()
        )

        assertFalse(RestoreManager.shouldNotifyUserDictionaryRefresh(prefs, files))
    }
}
