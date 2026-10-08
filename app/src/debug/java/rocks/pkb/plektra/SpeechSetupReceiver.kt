package rocks.pkb.plektra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import rocks.pkb.plektra.inputmethod.speech.*
import java.io.File

/** Debug-only shell entry point. Credentials arrive through private app storage, never intent extras. */
class SpeechSetupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = File(context.cacheDir, "speech-setup.json")
        try {
            val data = JSONObject(pending.readText())
            val provider = SpeechProvider.valueOf(data.getString("provider"))
            require(provider != SpeechProvider.SYSTEM)
            val silenceMs = data.getLong("silence_ms")
            require(silenceMs in listOf(0L, 1500L, 3000L, 5000L))
            val keys = listOf(SpeechProvider.OPENROUTER to data.getString("openrouter_key"),
                SpeechProvider.OPENAI to data.getString("openai_key"))
            require(keys.all { (_, key) -> key.isNotBlank() && key.none { it.isWhitespace() } })
            keys.forEach { (service, key) ->
                SpeechSettings.saveKey(context, service, key)
                check(SpeechSettings.readKey(context, service) == key)
            }
            listOf(SpeechProvider.OPENROUTER to data.getString("openrouter_model"),
                SpeechProvider.OPENAI to data.getString("openai_model")).forEach { (service, id) ->
                val defaults = if (service == SpeechProvider.OPENROUTER) SpeechModels.openRouterDefaults else SpeechModels.openAiDefaults
                SpeechSettings.setModel(context, service, defaults.firstOrNull { it.id == id } ?: SpeechModel(id, id))
            }
            SpeechSettings.setSilenceMs(context, silenceMs)
            SpeechSettings.setProvider(context, provider)
            resultCode = 0
            resultData = "Speech configuration saved; both encrypted keys verified"
        } catch (_: Exception) {
            resultCode = 1
            resultData = "Speech configuration failed"
        } finally {
            pending.delete()
        }
    }
}
