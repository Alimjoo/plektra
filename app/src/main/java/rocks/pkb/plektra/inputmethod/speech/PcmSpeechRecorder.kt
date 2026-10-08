package rocks.pkb.plektra.inputmethod.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

internal class SpeechSilenceDetector {
    private var elapsedMs = 0L
    private var lastSoundMs = 0L
    private var noiseFloor = 0.002
    var heardSpeech = false
        private set
    var silenceRemainingMs: Long? = null
        private set

    fun accept(rms: Double, durationMs: Long, silenceMs: Long): Boolean {
        elapsedMs += durationMs
        val threshold = maxOf(0.006, noiseFloor * 3)
        if (rms >= threshold) {
            heardSpeech = true
            lastSoundMs = elapsedMs
        } else {
            noiseFloor = minOf(noiseFloor * 1.002 + 0.000001, rms)
        }
        val quietMs = elapsedMs - lastSoundMs
        silenceRemainingMs = if (silenceMs > 0 && heardSpeech && quietMs >= 300) {
            (silenceMs - quietMs).coerceAtLeast(0)
        } else null
        return elapsedMs >= 60000 ||
            (!heardSpeech && elapsedMs >= 8000) ||
            (silenceMs > 0 && heardSpeech && quietMs >= silenceMs)
    }
}

class PcmSpeechRecorder(private val sampleRate: Int = 16000) : SpeechAudioRecorder {
    private val finishing = AtomicBoolean(false)

    override suspend fun record(silenceMs: Long, onLevel: (Float, Long?) -> Unit): RecordedSpeech =
        recordStreaming(silenceMs, onLevel) {}

    override suspend fun recordStreaming(silenceMs: Long, onLevel: (Float, Long?) -> Unit,
        onPcm: suspend (ByteArray) -> Unit): RecordedSpeech = withContext(Dispatchers.IO) {
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Audio recording unavailable" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 4096))
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Audio recorder initialization failed" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone unavailable" }
            val samples = ShortArray(sampleRate * 30 / 1000)
            val pcm = ByteArrayOutputStream()
            val detector = SpeechSilenceDetector()
            while (!finishing.get()) {
                currentCoroutineContext().ensureActive()
                val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                check(count > 0) { "Audio recording failed" }
                var energy = 0.0
                val chunk = ByteArray(count * 2)
                repeat(count) { i ->
                    val value = samples[i].toInt()
                    chunk[i * 2] = value.toByte(); chunk[i * 2 + 1] = (value shr 8).toByte()
                    val normalized = value / 32768.0
                    energy += normalized * normalized
                }
                pcm.write(chunk)
                onPcm(chunk)
                val rms = sqrt(energy / count)
                val stop = detector.accept(rms, count * 1000L / sampleRate, silenceMs)
                onLevel((20 * log10(maxOf(rms, 0.00001)) + 60).toFloat().coerceIn(-10f, 10f), detector.silenceRemainingMs)
                if (stop) break
            }
            currentCoroutineContext().ensureActive()
            if (!detector.heardSpeech || pcm.size() < sampleRate * 2 / 5) throw NoSpeechException()
            RecordedSpeech(pcm.toByteArray(), sampleRate)
        } finally {
            try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
            finally { recorder.release() }
        }
    }

    override fun finish() { finishing.set(true) }
}
