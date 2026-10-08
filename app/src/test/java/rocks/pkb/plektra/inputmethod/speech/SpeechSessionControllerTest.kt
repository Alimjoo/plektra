package rocks.pkb.plektra.inputmethod.speech

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SpeechSessionControllerTest {
    @Test fun cancelledLiveSessionCannotWritePartialsOrFinalIntoNextEditor() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val callbacks = mutableListOf<(String) -> Unit>()
        val replies = mutableListOf<CompletableDeferred<LiveSpeechResult>>()
        val partials = mutableListOf<Pair<Long, String>>()
        val finals = mutableListOf<Long>()
        val live = object : LiveSpeechTranscriber {
            override suspend fun transcribe(request: SpeechRequest, recorder: SpeechAudioRecorder,
                onLevel: (Float, Long?) -> Unit, onPartial: (String) -> Unit,
                onRecordingFinished: () -> Unit): LiveSpeechResult = withContext(NonCancellable) {
                callbacks += onPartial
                CompletableDeferred<LiveSpeechResult>().also { replies += it }.await()
            }
        }
        val controller = SpeechSessionController(scope, { Recorder() },
            { _, _ -> error("Unexpected batch upload") }, {}, { _, _ -> },
            { req, _, _ -> finals += req.editorSession }, { throw it },
            liveTranscriber = live, liveRecorderFactory = { Recorder() },
            onPartial = { req, text -> partials += req.editorSession to text })
        fun liveRequest(editor: Long) = request(editor).copy(provider = SpeechProvider.OPENAI,
            model = SpeechModels.openAiDefaults.first())
        controller.start(liveRequest(1))
        callbacks[0]("first")
        controller.cancel()
        controller.start(liveRequest(2))
        callbacks[0]("stale")
        replies[0].complete(LiveSpeechResult(RecordedSpeech(ByteArray(0)), SpeechResult("stale")))
        callbacks[1]("current")
        replies[1].complete(LiveSpeechResult(RecordedSpeech(ByteArray(0)), SpeechResult("current")))
        assertEquals(listOf(1L to "first", 2L to "current"), partials)
        assertEquals(listOf(2L), finals)
        scope.cancel()
    }

    private class Recorder : SpeechAudioRecorder {
        val audio = CompletableDeferred<RecordedSpeech>()
        var feedback: ((Float, Long?) -> Unit)? = null
        override suspend fun record(silenceMs: Long, onLevel: (Float, Long?) -> Unit): RecordedSpeech {
            feedback = onLevel
            return audio.await()
        }
        override fun finish() { audio.complete(RecordedSpeech(ByteArray(32000))) }
    }
    private fun request(editor: Long) = SpeechRequest(SpeechProvider.OPENROUTER,
        SpeechModels.openRouterDefaults.first(), "key", if (editor == 1L) "de-DE" else "en-GB", editor)

    @Test fun finishTranscribesButCancelDropsLateResultsAndOldFeedback() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val recorders = mutableListOf<Recorder>()
        val replies = mutableMapOf<Long, CompletableDeferred<SpeechResult>>()
        val delivered = mutableListOf<SpeechRequest>()
        val levels = mutableListOf<Float>()
        val states = mutableListOf<SpeechState>()
        val controller = SpeechSessionController(scope, { Recorder().also { recorders += it } },
            { req, _ -> withContext(NonCancellable) { replies.getValue(req.editorSession).await() } },
            { states += it }, { level, _ -> levels += level }, { req, _, _ -> delivered += req }, { throw it })
        replies[1] = CompletableDeferred(); replies[2] = CompletableDeferred()
        controller.start(request(1))
        controller.toggleStop()
        assertEquals(SpeechState.TRANSCRIBING, controller.state)
        controller.toggleStop()
        assertEquals(SpeechState.IDLE, controller.state)
        controller.start(request(2))
        recorders[0].feedback?.invoke(7f, 1000)
        assertTrue(levels.isEmpty())
        replies.getValue(1).complete(SpeechResult("stale"))
        assertEquals(SpeechState.RECORDING, controller.state)
        assertTrue(delivered.isEmpty())
        controller.toggleStop()
        replies.getValue(2).complete(SpeechResult("current"))
        assertEquals(listOf(2L), delivered.map { it.editorSession })
        assertEquals("en-GB", delivered.single().languageTag)
        assertEquals(SpeechState.IDLE, controller.state)
        assertTrue(states.contains(SpeechState.TRANSCRIBING))
        scope.cancel()
    }

    @Test fun fieldSwitchDuringRecordingNeverUploadsTheCancelledAudio() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var uploads = 0
        val controller = SpeechSessionController(scope, { Recorder() },
            { _, _ -> uploads++; SpeechResult("text") }, {}, { _, _ -> }, { _, _, _ -> fail("Cancelled result") }, { throw it })
        controller.start(request(1))
        controller.cancel()
        yield()
        assertEquals(0, uploads)
        assertEquals(SpeechState.IDLE, controller.state)
        scope.cancel()
    }
    @Test fun failedZdrPreparationNeverCallsTranscription() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var uploads = 0
        val errors = mutableListOf<Exception>()
        val controller = SpeechSessionController(scope, { Recorder() },
            { _, _ -> uploads++; SpeechResult("text") }, {}, { _, _ -> },
            { _, _, _ -> fail("Unexpected result") }, { errors += it },
            prepareRequest = { throw NoZdrModelException() })
        controller.start(request(1))
        controller.toggleStop()
        assertEquals(0, uploads)
        assertTrue(errors.single() is NoZdrModelException)
        assertEquals(SpeechState.IDLE, controller.state)
        scope.cancel()
    }

    @Test fun automaticModelReplacementIsUsedForUploadAndUsageResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val replacement = SpeechModel("available", "Available")
        var uploaded: SpeechRequest? = null
        var delivered: SpeechRequest? = null
        val controller = SpeechSessionController(scope, { Recorder() },
            { req, _ -> uploaded = req; SpeechResult("text") }, {}, { _, _ -> },
            { req, _, _ -> delivered = req }, { throw it },
            prepareRequest = { it.copy(model = replacement) })
        controller.start(request(1))
        controller.toggleStop()
        assertEquals(replacement, uploaded?.model)
        assertEquals(uploaded, delivered)
        scope.cancel()
    }

}
