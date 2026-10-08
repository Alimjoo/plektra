package rocks.pkb.plektra.inputmethod.speech

import android.content.Context
import kotlinx.serialization.json.*

data class SpeechUsage(
    val provider: String,
    val model: String,
    val recordings: Long = 0,
    val words: Long = 0,
    val durationMs: Long = 0,
    val costUsd: Double = 0.0,
    val pricedRecordings: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0
) {
    val wordsPerMinute: Double get() = if (durationMs > 0) words * 60000.0 / durationMs else 0.0
    fun plus(audio: RecordedSpeech, result: SpeechResult) = copy(
        recordings = recordings + 1,
        words = words + result.text.trim().split(Regex("\\s+")).count { it.isNotEmpty() },
        durationMs = durationMs + audio.durationMs,
        costUsd = costUsd + (result.costUsd ?: 0.0),
        pricedRecordings = pricedRecordings + if (result.costUsd != null) 1 else 0,
        inputTokens = inputTokens + (result.inputTokens ?: 0),
        outputTokens = outputTokens + (result.outputTokens ?: 0)
    )
}

class SpeechUsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("speech_usage", Context.MODE_PRIVATE)

    @Synchronized
    fun record(request: SpeechRequest, audio: RecordedSpeech, result: SpeechResult) {
        val key = "${request.provider.name}:${request.model.id}"
        val usage = decode(prefs.getString(key, null), request.provider.name, request.model.id).plus(audio, result)
        prefs.edit().putString(key, buildJsonObject {
            put("provider", usage.provider); put("model", usage.model)
            put("recordings", usage.recordings); put("words", usage.words); put("durationMs", usage.durationMs)
            put("costUsd", usage.costUsd); put("pricedRecordings", usage.pricedRecordings)
            put("inputTokens", usage.inputTokens); put("outputTokens", usage.outputTokens)
        }.toString()).apply()
    }

    fun all(): List<SpeechUsage> = prefs.all.mapNotNull { (key, value) ->
        val parts = key.split(':', limit = 2)
        if (parts.size != 2 || value !is String) null else decode(value, parts[0], parts[1])
    }.sortedByDescending { it.recordings }

    private fun decode(body: String?, provider: String, model: String): SpeechUsage {
        val json = body?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        fun number(key: String) = json?.get(key)?.jsonPrimitive?.longOrNull ?: 0L
        return SpeechUsage(provider, model, number("recordings"), number("words"), number("durationMs"),
            json?.get("costUsd")?.jsonPrimitive?.doubleOrNull ?: 0.0, number("pricedRecordings"),
            number("inputTokens"), number("outputTokens"))
    }
}
