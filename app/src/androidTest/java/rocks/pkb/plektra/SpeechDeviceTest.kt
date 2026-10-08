package rocks.pkb.plektra

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rocks.pkb.plektra.inputmethod.speech.*
import rocks.pkb.plektra.inputmethod.statusbar.StatusBarCallbacks
import rocks.pkb.plektra.inputmethod.statusbar.button.MicrophoneButtonFactory
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class SpeechDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun microphoneLongPressOpensSpeechSettingsWithoutStartingRecording() {
        var settingsIntent: Intent? = null
        val buttonContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { settingsIntent = intent }
        }
        var recordingRequests = 0
        var button: View? = null
        try {
            instrumentation.runOnMainSync {
                button = MicrophoneButtonFactory().create(buttonContext, 96,
                    StatusBarCallbacks(onSpeechRecognitionRequested = { recordingRequests++ })).view
                assertTrue(button!!.performLongClick())
            }
            assertNotNull("Speech settings were not requested", settingsIntent)
            assertEquals(SettingsActivity::class.java.name, settingsIntent!!.component!!.className)
            assertEquals("main.speech_recognition",
                SettingLinkRegistry.parseSettingLinkUri(settingsIntent!!.data!!))
            assertEquals("Long press started a recording", 0, recordingRequests)
            instrumentation.runOnMainSync { button!!.performClick() }
            assertEquals("Short tap no longer starts recording", 1, recordingRequests)
        } finally {
            instrumentation.runOnMainSync { button?.let { MicrophoneButtonFactory().cleanup(it) } }
        }
    }

    @Test fun credentialsRoundTripThroughAndroidKeystoreAndStayOutOfSettingsExport() {
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("speech_device_test_$name", mode)
        }
        val provider = SpeechProvider.OPENROUTER
        try {
            SpeechSettings.saveKey(isolated, provider, "synthetic-device-test-key")
            assertEquals("synthetic-device-test-key", SpeechSettings.readKey(isolated, provider))
            val saved = isolated.getSharedPreferences("speech_credentials", Context.MODE_PRIVATE).getString(provider.name, null)!!
            assertFalse(saved.contains("synthetic-device-test-key"))
            assertFalse(SettingsManager.getPreferences(context).all.values.any { it == "synthetic-device-test-key" })
        } finally {
            SpeechSettings.removeKey(isolated, provider)
        }
    }

    @Test fun microphoneCountdownAndProcessingRemainVisibleWhileAudioLevelsChange() {
        instrumentation.runOnMainSync {
            val factory = MicrophoneButtonFactory()
            val button = factory.create(context, 96, StatusBarCallbacks()).view as ImageView
            button.layout(0, 0, 96, 96)
            factory.setActive(button, true)
            factory.setSpeechFeedback(button, false, 2)
            val description = androidx.core.view.ViewCompat.getStateDescription(button)?.toString().orEmpty()
            assertTrue(description.contains("2"))
            val pause = render(button)
            factory.updateAudioLevel(button, 10f)
            assertEquals(pause.getPixel(4, 48), render(button).getPixel(4, 48))
            factory.setSpeechFeedback(button, true, null)
            val processing = render(button)
            assertNotEquals(pause.getPixel(4, 48), processing.getPixel(4, 48))
            factory.updateAudioLevel(button, 10f)
            assertEquals(processing.getPixel(4, 48), render(button).getPixel(4, 48))
            factory.setActive(button, false)
            assertNotEquals(processing.getPixel(4, 48), render(button).getPixel(4, 48))
            factory.cleanup(button)
        }
    }

    @Test fun spokenFixtureSurvivesTwoSecondPauseAndStopsAfterThreeSecondsQuiet() {
        val pcm = instrumentation.context.assets.open("speech-pause.wav").use { it.readBytes() }.drop(44).toByteArray()
        val detector = SpeechSilenceDetector()
        var endMs = 0L
        val frames = pcm.size / 960
        for (frame in 0 until frames) {
            var energy = 0.0
            repeat(480) { index ->
                val offset = frame * 960 + index * 2
                val sample = ((pcm[offset].toInt() and 255) or (pcm[offset + 1].toInt() shl 8)).toShort()
                val value = sample / 32768.0
                energy += value * value
            }
            if (detector.accept(kotlin.math.sqrt(energy / 480), 30, 3000)) { endMs = (frame + 1) * 30L; break }
        }
        // The second spoken part ends around 6.15 s; automatic stop must include it.
        assertTrue("Stopped too early: $endMs ms", endMs > 8500)
        assertTrue("Did not stop after final quiet: $endMs ms", endMs in 8500..10200)
    }

    @Test fun realPixelMicrophoneSurvivesSpokenPause() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("speechAcoustic") == "true")
        val intent = Intent(context, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = android.net.Uri.parse("plektra://setting/main.speech_recognition")
        }
        val scenario = ActivityScenario.launch<SettingsActivity>(intent)
        val audioManager = context.getSystemService(AudioManager::class.java)
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pcm = instrumentation.context.assets.open("speech-pause.wav").use { it.readBytes() }.drop(44).toByteArray()
        val player = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size).build()
        val feedback = Collections.synchronizedList(mutableListOf<Pair<Long, Long?>>())
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2, 0)
            player.write(pcm, 0, pcm.size)
            val started = SystemClock.elapsedRealtime()
            val recording = async(Dispatchers.IO) {
                PcmSpeechRecorder().record(3000) { _, remaining -> feedback += (SystemClock.elapsedRealtime() - started) to remaining }
            }
            delay(400)
            player.play()
            val result = withTimeout(15000) { recording.await() }
            assertTrue("Recording stopped early: ${result.durationMs} ms", result.durationMs > 7500)
            assertTrue("No silence countdown", feedback.any { it.second != null })
            val initialPause = feedback.firstOrNull { it.second != null }
            assertNotNull(initialPause)
            assertTrue("Countdown did not reset on second phrase", feedback.any { it.first > initialPause!!.first + 500 && it.second == null })
            android.util.Log.i("SpeechDeviceTest", "Acoustic recording duration=${result.durationMs}ms; countdown reset verified")
        } finally {
            player.stop(); player.release()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            scenario.close()
        }
    }

    @Test fun configuredCloudProvidersTranscribeGermanFixture() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("speechCloud") == "true")
        val pcm = instrumentation.context.assets.open("speech-pause.wav").use { it.readBytes() }.drop(44).toByteArray()
        val audio = RecordedSpeech(pcm)
        val client = SpeechApiClient()
        for (provider in listOf(SpeechProvider.OPENROUTER, SpeechProvider.OPENAI)) {
            val key = SpeechSettings.readKey(context, provider)
            assertTrue("Missing stored key for $provider", !key.isNullOrBlank())
            val selected = SpeechSettings.model(context, provider)
            val batchModel = if (selected.protocol == SpeechProtocol.LIVE_TRANSCRIPTION)
                SpeechModels.openAiDefaults.first { it.protocol == SpeechProtocol.TRANSCRIPTION } else selected
            val configured = SpeechRequest(provider, batchModel, key!!, "de-DE", 1)
            val request = client.prepareRequest(if (provider == SpeechProvider.OPENROUTER)
                configured.copy(model = SpeechModel("unavailable/device-test", "Unavailable")) else configured)
            assertFalse("Unavailable model was not replaced", request.model.id == "unavailable/device-test")
            client.validateKey(provider, key)
            val result = withTimeout(100000) { client.transcribe(request, audio) }
            val normalized = result.text.lowercase(java.util.Locale.GERMAN)
            assertTrue("First phrase missing for $provider", normalized.contains("erste"))
            assertTrue("Second phrase missing for $provider", normalized.contains("weiter"))
            val encrypted = context.getSharedPreferences("speech_credentials", Context.MODE_PRIVATE)
                .getString(provider.name, null)
            assertTrue("Credential not encrypted", encrypted != null && !encrypted.contains(key))
            assertFalse("Credential in settings export", SettingsManager.getPreferences(context).all.values.any { it == key })
            android.util.Log.i("SpeechDeviceTest", "$provider: model=${request.model.id}; authentication and both German phrases verified")
        }
        assertFalse(java.io.File(context.cacheDir, "speech-setup.json").exists())
    }

    @Test fun configuredLiveProviderTranscribesWhileMicrophoneRecords() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("speechLive") == "true")
        val key = SpeechSettings.readKey(context, SpeechProvider.OPENAI)
        assertFalse("OpenAI key missing", key.isNullOrBlank())
        val scenario = ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val audioManager = context.getSystemService(AudioManager::class.java)
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pcm = instrumentation.context.assets.open("speech-pause.wav").use { it.readBytes() }.drop(44).toByteArray()
        val player = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size).build()
        val started = CompletableDeferred<Unit>()
        val partials = mutableListOf<String>()
        var finished = false
        var earlyPartial = false
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2, 0)
            player.write(pcm, 0, pcm.size)
            val live = async {
                OpenAiLiveSpeechClient().transcribe(SpeechRequest(SpeechProvider.OPENAI,
                    SpeechModels.openAiDefaults.first(), key!!, "de-DE", 1), PcmSpeechRecorder(24000),
                    { _, _ -> started.complete(Unit) },
                    { text -> partials += text; if (!finished) earlyPartial = true }, { finished = true })
            }
            withTimeout(20000) { started.await() }
            delay(400)
            player.play()
            val result = withTimeout(45000) { live.await() }
            assertTrue("No text before recording ended", earlyPartial)
            assertTrue("No transcript updates", partials.isNotEmpty())
            assertEquals(24000, result.audio.sampleRate)
            assertTrue("Recording stopped during thinking pause", result.audio.durationMs > 7500)
            val text = result.result.text.lowercase(java.util.Locale.GERMAN)
            assertTrue("First phrase missing", text.contains("erste"))
            assertTrue("Second phrase missing", text.contains("weiter"))
            android.util.Log.i("SpeechDeviceTest", "Live microphone: ${partials.size} partial updates before completion; both phrases verified; ${result.audio.durationMs}ms")
        } finally {
            player.stop(); player.release()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            scenario.close()
        }
    }

    private fun render(view: View): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { view.draw(Canvas(this)) }
}
