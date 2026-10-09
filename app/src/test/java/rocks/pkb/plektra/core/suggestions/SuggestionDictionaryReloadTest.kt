package rocks.pkb.plektra.core.suggestions

import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SuggestionDictionaryReloadTest {
    @Test fun downloadedDictionaryRefreshesThePartiallyTypedWordAndTypingContinues() {
        val context = RuntimeEnvironment.getApplication()
        val old = FakeDictionaryRepository().apply { isReady = true; addTestEntry("palm", 200) }
        val downloaded = FakeDictionaryRepository().apply { addTestEntry("paladin", 200) }
        val released = CompletableDeferred<Unit>()
        val new = object : DictionaryRepository by downloaded {
            override suspend fun loadIfNeeded() {
                downloaded.isLoadStarted = true
                released.await()
                downloaded.isReady = true
            }
        }
        var creation = 0
        val snapshots = mutableListOf<List<SuggestionResult>>()
        val controller = SuggestionController(context, context.assets,
            settingsProvider = { SuggestionSettings(suggestionsEnabled = true, maxSuggestions = 3) },
            onSuggestionsUpdated = { snapshots.add(it) }, currentLocale = Locale.ENGLISH,
            dictionaryRepositoryFactory = { _, _, _, _, _ -> if (creation++ == 0) old else new })
        fun awaitCurrentWord() {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                val latest = snapshots.lastOrNull().orEmpty()
                if (latest.any { it.candidate == "paladin" && it.kind == SuggestionKind.CURRENT_WORD }) return
                Thread.sleep(10)
            }
            fail("Expected current-word suggestion after dictionary reload; got ${snapshots.lastOrNull()}")
        }
        try {
            controller.onCharacterCommitted("pal", null)
            controller.reloadDictionaries()
            released.complete(Unit)
            awaitCurrentWord()
            snapshots.clear()
            controller.onCharacterCommitted("a", null)
            awaitCurrentWord()
        } finally { controller.destroy() }
    }
}
