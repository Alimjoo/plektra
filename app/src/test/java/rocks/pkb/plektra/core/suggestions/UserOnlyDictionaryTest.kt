package rocks.pkb.plektra.core.suggestions

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserOnlyDictionaryTest {
    @Test fun missingBaseStillLoadsUserWordsAndCanRefreshThem() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val store = UserDictionaryStore()
        context.getSharedPreferences("plektra_prefs", Context.MODE_PRIVATE).edit()
            .putString("user_dictionary_entries", """[{"w":"Sonderwort","f":200,"u":0}]""").commit()
        val repository = AndroidDictionaryRepository(context, context.assets, store, Locale.forLanguageTag("zz"))
        withContext(Dispatchers.IO) { repository.loadIfNeeded() }
        assertTrue(repository.isReady)
        assertTrue(repository.isKnownWord("Sonderwort"))
        assertTrue(repository.lookupByPrefixMerged("Sonder", 5).any { it.word == "Sonderwort" })
        store.addWord(context, "Neueswort")
        withContext(Dispatchers.IO) { repository.refreshUserEntries() }
        assertTrue(repository.isKnownWord("Neueswort"))
    }
}
