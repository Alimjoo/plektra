package rocks.pkb.plektra

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import rocks.pkb.plektra.backup.FileBackupHelper
import rocks.pkb.plektra.data.layout.LayoutFileStore
import rocks.pkb.plektra.ui.theme.PlektraTheme
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Runs the real import UI and DocumentsUI against private synthetic application state. */
@RunWith(AndroidJUnit4::class)
class PastieraBackupDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val real get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private lateinit var sandbox: Context
    private lateinit var root: File
    private val published = mutableListOf<Uri>()
    private val preferenceNames = mutableSetOf<String>()
    private val broadcasts = mutableListOf<Intent>()
    private var originalPreferences: Map<String, *> = emptyMap<String, Any>()
    private var originalCredentials: Map<String, *> = emptyMap<String, Any>()
    private var originalFiles = emptyMap<String, String>()
    private var originalIme: String? = null

    @Before fun setUp() {
        originalPreferences = SettingsManager.getPreferences(real).all.toMap()
        originalCredentials = real.getSharedPreferences("speech_credentials", Context.MODE_PRIVATE).all.toMap()
        originalFiles = fingerprints(real.filesDir)
        originalIme = Settings.Secure.getString(real.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        root = File.createTempFile("backup_ui_e2e_", "", real.cacheDir).apply { delete(); mkdirs() }
        val activity = compose.activity
        sandbox = object : ContextWrapper(activity) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val isolatedName = "${root.name}_$name"
                preferenceNames.add(isolatedName)
                return super.getSharedPreferences(isolatedName, mode)
            }
            // Keep subtype registration and broadcasts away from the user's running IME.
            override fun getSystemService(name: String): Any? =
                if (name == INPUT_METHOD_SERVICE) null else super.getSystemService(name)
            override fun sendBroadcast(intent: Intent) { broadcasts.add(intent) }
        }
        SettingsManager.getPreferences(sandbox).edit()
            .putString("keyboard_layout", "azerty")
            .putString("speech_provider", "OPENAI").putString("speech_model_OPENAI", "gpt-live-transcribe")
            .putLong("speech_silence_ms", 3000).putString("target_only_setting", "keep")
            .putString("physical_keyboard_profile_override", "auto")
            .putBoolean("slim_mode_active", false)
            .putString("software_keyboard_mode_runtime_override", "force_hardware").commit()
        sandbox.getSharedPreferences("speech_credentials", Context.MODE_PRIVATE).edit()
            .putString("OPENAI", "synthetic-encrypted-device-value").commit()
        File(sandbox.filesDir, "variations.json").writeText("{\"targetOnlyVariations\":[\"keep\"]}")
        compose.activityRule.scenario.onActivity {
            it.setContent {
                CompositionLocalProvider(LocalContext provides sandbox) {
                    PlektraTheme { AdvancedSettingsScreen(Modifier.fillMaxSize(), onBack = {}) }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun tearDown() {
        published.forEach { real.contentResolver.delete(it, null, null) }
        repeat(3) {
            if (device.currentPackageName?.contains("documentsui") == true) device.pressBack()
        }
        compose.activityRule.scenario.close()
        preferenceNames.forEach { real.deleteSharedPreferences(it) }
        if (::root.isInitialized) root.deleteRecursively()
        assertTrue("User preferences changed", originalPreferences == SettingsManager.getPreferences(real).all)
        assertTrue("User credentials changed", originalCredentials ==
            real.getSharedPreferences("speech_credentials", Context.MODE_PRIVATE).all)
        assertTrue("User files changed", originalFiles == fingerprints(real.filesDir))
        assertEquals("Active IME changed", originalIme,
            Settings.Secure.getString(real.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
    }

    @Test fun pastiera085ImportsThroughFilePickerAndPreservesTargetState() = importVersion("085")
    @Test fun pastiera086ImportsThroughFilePickerAndAdaptsForPixel() = importVersion("086")

    private fun importVersion(version: String) {
        val name = "Plektra-e2e-$version-${System.currentTimeMillis()}.zip"
        publishArchive(version, name)
        repeat(2) { attempt ->
            pickArchive(name)
            if (version == "086") {
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(real.getString(R.string.restore_device_change_adapt))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                if (attempt == 0) screenshot("pastiera-$version-device-dialog")
                compose.onNodeWithText(real.getString(R.string.restore_device_change_adapt)).performClick()
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText(real.getString(R.string.restore_completed))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val prefs = SettingsManager.getPreferences(sandbox)
            assertEquals("qwertz", SettingsManager.getKeyboardLayout(sandbox))
            assertEquals(450L, prefs.getLong("long_press_threshold", 0))
            assertEquals("slim", prefs.getString("slim_mode_override", null))
            assertEquals(SettingsManager.StatusBarPresentationMode.SLIM,
                SettingsManager.getStatusBarPresentationMode(sandbox))
            assertFalse(prefs.getBoolean("slim_mode_active", true))
            assertEquals("force_hardware", prefs.getString("software_keyboard_mode_runtime_override", null))
            assertEquals("auto", prefs.getString("physical_keyboard_profile_override", null))
            assertEquals("OPENAI", prefs.getString("speech_provider", null))
            assertEquals("gpt-live-transcribe", prefs.getString("speech_model_OPENAI", null))
            assertEquals(3000L, prefs.getLong("speech_silence_ms", 0))
            assertEquals("keep", prefs.getString("target_only_setting", null))
            assertEquals("synthetic-encrypted-device-value", sandbox.getSharedPreferences(
                "speech_credentials", Context.MODE_PRIVATE).getString("OPENAI", null))
            assertTrue(sandbox.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE).all.isEmpty())
            assertFalse(File(sandbox.filesDir, "unknown.json").exists())
            val layout = File(sandbox.filesDir, "keyboard_layouts/custom-pastiera.json").inputStream().use {
                LayoutFileStore.loadLayoutFromStream(it)
            }!!
            assertEquals("ä", layout[android.view.KeyEvent.KEYCODE_A]!!.lowercase)
            val dictionary = org.json.JSONArray(File(sandbox.filesDir, "user_defaults.json").readText())
            assertEquals("PastieraWord", dictionary.getJSONObject(0).getString("w"))
            val variations = JSONObject(File(sandbox.filesDir, "variations.json").readText())
            assertEquals("keep", variations.getJSONArray("targetOnlyVariations").getString(0))
            assertEquals("test@example.invalid", variations.getJSONArray("emailVariations").getString(0))
            if (attempt == 0) screenshot("pastiera-$version-import-success")
            // Let the success snackbar finish so it cannot satisfy the second import's wait.
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(real.getString(R.string.restore_completed))
                    .fetchSemanticsNodes().isEmpty()
            }
        }
        assertEquals(2, broadcasts.count { it.action == AppBroadcastActions.USER_DICTIONARY_UPDATED })
        val before = SettingsManager.getPreferences(sandbox).all.toMap()
        val filesBefore = fingerprints(sandbox.filesDir)
        val badName = name.replace(".zip", "-corrupt.zip")
        publishArchive(version, badName, corrupt = true)
        pickArchive(badName)
        val failurePrefix = real.getString(R.string.restore_failed, "").trim()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(failurePrefix, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("Failed import changed settings", before == SettingsManager.getPreferences(sandbox).all)
        assertEquals(filesBefore, fingerprints(sandbox.filesDir))
        screenshot("pastiera-$version-corrupt-rejected")
    }

    private fun pickArchive(name: String) {
        compose.onNodeWithText(real.getString(R.string.restore_from_file)).performScrollTo().performClick()
        val roots = device.wait(Until.findObject(By.desc("Show roots")), 10_000)
            ?: error("DocumentsUI root selector not visible")
        roots.click()
        device.waitForIdle()
        val downloadsSelector = By.text("Downloads").hasAncestor(
            By.res(Pattern.compile(".*:id/roots_list")))
        device.wait(Until.hasObject(downloadsSelector), 5_000)
        device.waitForIdle()
        device.findObject(downloadsSelector)!!.click()
        assertTrue("Downloads did not open", device.wait(Until.hasObject(
            By.text("Files in Downloads")), 10_000))
        device.waitForIdle()
        val folderSelector = By.text("PlektraE2E").res("android:id/title")
        device.wait(Until.findObject(folderSelector), 5_000)?.click()
        device.waitForIdle()
        val file = device.wait(Until.findObject(By.text(name)), 10_000)
            ?: error("Test ZIP not visible: $name")
        file.click()
    }

    private fun publishArchive(version: String, name: String, corrupt: Boolean = false) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/PlektraE2E")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = real.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        published.add(uri)
        val assets = instrumentation.context.assets
        val prefs = if (corrupt) "{broken" else assets.open("backup/pastiera-$version-prefs.json")
            .bufferedReader().use { it.readText() }
        val metadata = assets.open("backup/pastiera-$version-meta.json").bufferedReader().use { it.readText() }
        ZipOutputStream(real.contentResolver.openOutputStream(uri)!!).use { zip ->
            mapOf("backup_meta.json" to metadata, "prefs/pastiera_prefs.json" to prefs,
                "files/user_defaults.json" to "[{\"w\":\"PastieraWord\",\"f\":30}]",
                "files/variations.json" to "{\"emailVariations\":[\"test@example.invalid\"]}",
                "files/keyboard_layouts/custom-pastiera.json" to
                    """{"name":"custom-pastiera","mappings":{"KEYCODE_A":{"lowercase":"ä","uppercase":"Ä"}}}""",
                "prefs/speech_credentials.json" to "ignored",
                "files/unknown.json" to "ignored").forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
        real.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        android.os.SystemClock.sleep(500) // DocumentsUI's window transition uses the device clock.
        device.waitForIdle()
        val directory = File(real.getExternalFilesDir(null), "backup-e2e").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
    }

    private fun fingerprints(directory: File): Map<String, String> =
        FileBackupHelper.managedRestoreRoots().flatMap { name ->
            File(directory, name).walkTopDown().filter { it.isFile }.map { file ->
                file.relativeTo(directory).path to MessageDigest.getInstance("SHA-256")
                    .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            }.toList()
        }.toMap()
}
