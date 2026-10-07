package rocks.pkb.plektra

import rocks.pkb.plektra.backup.PreferenceSchemas
import rocks.pkb.plektra.backup.PreferenceValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsManagerKeyboardThemeAssignmentTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.setQualifiers("+notnight")
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("plektra_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun systemThemeEditSourceDoesNotTargetDormantSavedThemeOrActiveSavedOverride() {
        val context = RuntimeEnvironment.getApplication()
        val target = SettingsManager.KeyboardThemeTarget.HARDWARE
        val dormant = theme(0xFF123456.toInt())
        SettingsManager.saveKeyboardTheme(context, "MyTheme", dormant)
        SettingsManager.setKeyboardTheme(context, target, dormant)
        SettingsManager.setKeyboardThemeAssignmentMode(context, target, SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM)
        val override = theme(0xFF654321.toInt())
        SettingsManager.saveKeyboardTheme(context, "Override", override)
        SettingsManager.upsertKeyboardThemeLayoutOverride(context, target, "de-DE", "qwertz", override)
        val options = themeOptions(target) + SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() }
        val systemTheme = SettingsManager.getKeyboardThemeSystemSlot(context, target, dark = true)
        assertEquals("saved:Override", matchKeyboardThemeOption(options,
            SettingsManager.getEffectiveKeyboardTheme(context, target, "de-DE", "qwertz").toKeyboardThemePreset("Custom")))
        assertEquals("builtin:Plektra Dark", keyboardThemeEditSourceKey(
            SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM, "saved:MyTheme", options,
            systemTheme.toKeyboardThemePreset("Custom")))
        assertEquals("saved:MyTheme", keyboardThemeEditSourceKey(
            SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED, "saved:MyTheme", options,
            dormant.toKeyboardThemePreset("MyTheme")))
        SettingsManager.setKeyboardTheme(context, target, systemTheme.copy(accent = 0xFFABCDEF.toInt()))
        assertEquals(dormant, SettingsManager.getSavedKeyboardThemes(context).first { it.name == "MyTheme" }.theme)
        assertEquals(override, SettingsManager.getSavedKeyboardThemes(context).first { it.name == "Override" }.theme)
    }

    @Test
    fun activePresetUsesResolvedDefaultInsteadOfDormantEpaper() {
        val context = RuntimeEnvironment.getApplication()
        for (target in SettingsManager.KeyboardThemeTarget.entries) {
            for (dark in listOf(false, true)) {
                RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
                val options = themeOptions(target)
                assertEquals("builtin:ePaper", matchKeyboardThemeOption(options,
                    SettingsManager.getKeyboardTheme(context, target).toKeyboardThemePreset("Custom")))
                val effective = SettingsManager.getEffectiveKeyboardTheme(context, target)
                assertEquals(if (dark) "builtin:Plektra Dark" else "builtin:Plektra Light",
                    matchKeyboardThemeOption(options, effective.toKeyboardThemePreset("Custom")))
            }
        }
    }

    @Test
    fun storedLegacyEpaperRemainsActiveWithoutAnAssignmentPreference() {
        val context = RuntimeEnvironment.getApplication()
        for (target in SettingsManager.KeyboardThemeTarget.entries) {
            val epaper = themeOptions(target).first { it.key == "builtin:ePaper" }.preset.toSettingsTheme()
            SettingsManager.getPreferences(context).edit()
                .putString(SettingsManager.keyboardThemeKeyForTarget(target), SettingsManager.keyboardThemeToJsonString(epaper))
                .commit()
            assertEquals(SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED,
                SettingsManager.getKeyboardThemeAssignmentMode(context, target))
            assertEquals(epaper, SettingsManager.getEffectiveKeyboardTheme(context, target))
        }
    }

    @Test
    fun followSystemRetainsDormantEpaperAndChoosingItExplicitlyActivatesFixedMode() {
        val context = RuntimeEnvironment.getApplication()
        for (target in SettingsManager.KeyboardThemeTarget.entries) {
            val epaper = themeOptions(target).first { it.key == "builtin:ePaper" }.preset.toSettingsTheme()
            SettingsManager.setKeyboardTheme(context, target, epaper)
            SettingsManager.setKeyboardThemeAssignmentMode(context, target, SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM)
            assertEquals(epaper, SettingsManager.getKeyboardTheme(context, target))
            assertEquals("builtin:Plektra Light", matchKeyboardThemeOption(themeOptions(target),
                SettingsManager.getEffectiveKeyboardTheme(context, target).toKeyboardThemePreset("Custom")))
            SettingsManager.setKeyboardTheme(context, target, epaper)
            assertEquals(SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED,
                SettingsManager.getKeyboardThemeAssignmentMode(context, target))
            assertEquals(epaper, SettingsManager.getEffectiveKeyboardTheme(context, target))
        }
    }

    @Test
    fun activePresetUsesCurrentInputStyleOverride() {
        val context = RuntimeEnvironment.getApplication()
        val target = SettingsManager.KeyboardThemeTarget.HARDWARE
        val options = themeOptions(target)
        val epaper = options.first { it.key == "builtin:ePaper" }.preset.toSettingsTheme()
        SettingsManager.upsertKeyboardThemeLayoutOverride(context, target, "de-DE", "qwertz", epaper)
        assertEquals("builtin:ePaper", matchKeyboardThemeOption(options,
            SettingsManager.getEffectiveKeyboardTheme(context, target, "de-DE", "qwertz").toKeyboardThemePreset("Custom")))
        assertEquals("builtin:Plektra Light", matchKeyboardThemeOption(options,
            SettingsManager.getEffectiveKeyboardTheme(context, target, "en-US", "qwerty").toKeyboardThemePreset("Custom")))
    }

    private fun themeOptions(target: SettingsManager.KeyboardThemeTarget): List<KeyboardThemeOption> =
        keyboardThemePresets().map {
            val preset = if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) it.withSoftwareKeyboardDefaults() else it
            KeyboardThemeOption("builtin:${it.name}", preset, preset)
        }

    @Test
    fun unconfiguredTargetsFollowSystemLightAndDark() {
        val context = RuntimeEnvironment.getApplication()
        for (target in SettingsManager.KeyboardThemeTarget.entries) {
            assertEquals(SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM,
                SettingsManager.getKeyboardThemeAssignmentMode(context, target))
            RuntimeEnvironment.setQualifiers("+notnight")
            assertEquals(SettingsManager.getKeyboardThemeSystemSlot(context, target, false),
                SettingsManager.getEffectiveKeyboardTheme(context, target))
            RuntimeEnvironment.setQualifiers("+night")
            assertEquals(SettingsManager.getKeyboardThemeSystemSlot(context, target, true),
                SettingsManager.getEffectiveKeyboardTheme(context, target))
        }
    }

    @Test
    fun explicitlyFixedModeIsPreservedWithoutStoredTheme() {
        val context = RuntimeEnvironment.getApplication()
        val target = SettingsManager.KeyboardThemeTarget.HARDWARE
        SettingsManager.setKeyboardThemeAssignmentMode(context, target, SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED)
        assertEquals(SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED,
            SettingsManager.getKeyboardThemeAssignmentMode(context, target))
    }

    @Test
    fun fixedMode_returnsFixedTargetTheme_evenWhenSystemSlotsExist() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF101010.toInt())
        val light = theme(0xFFFFFFFF.toInt())
        val dark = theme(0xFF000000.toInt())

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.HARDWARE, dark = false, light)
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.HARDWARE, dark = true, dark)

        assertEquals(fixed.background, SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE).background)
    }

    @Test
    fun followSystem_usesLightAndDarkSlotsForTarget() {
        val context = RuntimeEnvironment.getApplication()
        val light = theme(0xFFEFEFEF.toInt())
        val dark = theme(0xFF111111.toInt())

        SettingsManager.setKeyboardThemeAssignmentMode(
            context,
            SettingsManager.KeyboardThemeTarget.SOFTWARE,
            SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
        )
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, dark = false, light)
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, dark = true, dark)

        RuntimeEnvironment.setQualifiers("+notnight")
        assertEquals(light.background, SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE).background)

        RuntimeEnvironment.setQualifiers("+night")
        assertEquals(dark.background, SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE).background)
    }

    @Test
    fun overrideBeatsFollowSystem() {
        val context = RuntimeEnvironment.getApplication()
        val dark = theme(0xFF111111.toInt())
        val override = theme(0xFFABCDEF.toInt())

        SettingsManager.setKeyboardThemeAssignmentMode(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
        )
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.HARDWARE, dark = true, dark)
        SettingsManager.upsertKeyboardThemeLayoutOverride(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale = "de-DE",
            layout = "qwertz",
            theme = override
        )

        RuntimeEnvironment.setQualifiers("+night")

        assertEquals(
            override.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-DE", "qwertz").background
        )
    }

    @Test
    fun overrideBeatsFixedMode() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF111111.toInt())
        val override = theme(0xFF222222.toInt())

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        SettingsManager.upsertKeyboardThemeLayoutOverride(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale = null,
            layout = "qwerty",
            theme = override
        )

        assertEquals(
            override.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwerty").background
        )
    }

    @Test
    fun mostSpecificOverrideWinsOverLanguageAndLayoutOnlyMatches() {
        val context = RuntimeEnvironment.getApplication()
        val layoutOnly = theme(0xFF111111.toInt())
        val languageAndLayout = theme(0xFF222222.toInt())
        val exactLocaleAndLayout = theme(0xFF333333.toInt())

        SettingsManager.upsertKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, null, "qwertz", layoutOnly)
        SettingsManager.upsertKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de", "qwertz", languageAndLayout)
        SettingsManager.upsertKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-DE", "qwertz", exactLocaleAndLayout)

        assertEquals(
            exactLocaleAndLayout.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-DE", "qwertz").background
        )
        assertEquals(
            languageAndLayout.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-AT", "qwertz").background
        )
        assertEquals(
            layoutOnly.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwertz").background
        )
    }

    @Test
    fun localeUnderscoresAreNormalizedForOverrides() {
        val context = RuntimeEnvironment.getApplication()
        val override = theme(0xFFABCDEF.toInt())

        SettingsManager.upsertKeyboardThemeLayoutOverride(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale = "de_DE",
            layout = "qwertz",
            theme = override
        )

        assertEquals(
            override.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-DE", "qwertz").background
        )
    }

    @Test
    fun overridesAreIsolatedByHardwareAndSoftwareTarget() {
        val context = RuntimeEnvironment.getApplication()
        val hardwareOverride = theme(0xFF123456.toInt())
        val softwareFixed = theme(0xFF654321.toInt())

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, softwareFixed)
        SettingsManager.upsertKeyboardThemeLayoutOverride(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale = "en-US",
            layout = "qwerty",
            theme = hardwareOverride
        )

        assertEquals(
            softwareFixed.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, "en-US", "qwerty").background
        )
    }

    @Test
    fun removingOverrideRevealsUnderlyingMode() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF111111.toInt())
        val override = theme(0xFF222222.toInt())

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        SettingsManager.upsertKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwerty", override)
        SettingsManager.removeKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en_US", "qwerty")

        assertEquals(
            fixed.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwerty").background
        )
    }

    @Test
    fun malformedOverrideJsonFallsBackWithoutCrashing() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF111111.toInt())
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        SettingsManager.getPreferences(context).edit()
            .putString("keyboard_theme_layout_overrides_hardware", "{broken")
            .commit()

        assertEquals(
            fixed.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwerty").background
        )
    }

    @Test
    fun deletingSavedTheme_isCaseInsensitiveAndKeepsOtherThemes() {
        val context = RuntimeEnvironment.getApplication()
        val first = theme(0xFF123456.toInt())
        val second = theme(0xFFABCDEF.toInt())
        SettingsManager.saveKeyboardTheme(context, "First", first)
        SettingsManager.saveKeyboardTheme(context, "Second", second)

        SettingsManager.deleteKeyboardTheme(context, " first ")

        val savedThemes = SettingsManager.getSavedKeyboardThemes(context)
        assertEquals(listOf("Second"), savedThemes.map { it.name })
        assertEquals(second, savedThemes.single().theme)
    }

    @Test
    fun savingAndEditingDraft_doesNotMutateActiveThemes() {
        val context = RuntimeEnvironment.getApplication()
        val activeHardware = theme(0xFF281B35.toInt())
        val activeSoftware = theme(0xFF102030.toInt())
        val initialDraft = SettingsManager.KeyboardThemeDraft(
            name = "Draft",
            theme = theme(0xFFF0F0F0.toInt())
        )

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, activeHardware)
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, activeSoftware)
        SettingsManager.saveKeyboardThemeDraft(context, initialDraft)
        SettingsManager.saveKeyboardThemeDraft(
            context,
            initialDraft.copy(
                theme = theme(0xFFABCDEF.toInt()),
                populatedFields = setOf("background")
            )
        )

        assertEquals(
            activeHardware,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE)
        )
        assertEquals(
            activeSoftware,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE)
        )
        assertFalse(SettingsManager.isKeyboardThemePreferenceKey("keyboard_theme_drafts"))
    }

    @Test
    fun keyboardThemeAssignmentPreferences_areRecognizedForRestore() {
        listOf(
            "keyboard_theme_assignment_mode_hardware",
            "keyboard_theme_assignment_mode_software",
            "keyboard_theme_light_hardware",
            "keyboard_theme_light_software",
            "keyboard_theme_dark_hardware",
            "keyboard_theme_dark_software",
            "keyboard_theme_layout_overrides_hardware",
            "keyboard_theme_layout_overrides_software",
            "modifier_indicator_mode"
        ).forEach { key ->
            assertEquals(
                PreferenceValueType.STRING,
                PreferenceSchemas.expectedType("plektra_prefs", key)
            )
        }
    }

    private fun theme(background: Int): SettingsManager.KeyboardThemeSettings =
        SettingsManager.defaultKeyboardTheme().copy(
            background = background,
            accent = background xor 0x00FFFFFF
        )
}
