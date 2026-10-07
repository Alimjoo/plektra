package rocks.pkb.plektra

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainBackgroundTest {
    @Before
    fun clearPreferences() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("plektra_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun decoderMatchesReferenceVectorsWithoutEmbeddingCleartext() {
        val vectors = listOf(
            Triple("207d", intArrayOf(0x7a, 0x24), "cf3fd0d5534d688a22eb5653628054370bdfd02e5b9339d23bf27399fbbf8304"),
            Triple("424344", intArrayOf(0x7b), "b5d4045c3f466fa91fe2cc6abe79232a1a57cdf104f7a26e716e0a1e2789df78"),
            Triple("6f2b3b36455f447a2b4d4250", null, "81dc77dcdc333cdeb6bfd577fcd9426e9049226e283da95ab6cf8f59452ed937")
        )
        vectors.forEach { (cipherHex, key, expectedHash) ->
            val ciphertext = cipherHex.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
            val clear = if (key == null) MainBackgroundCipher.decryptAscii(ciphertext)
                else MainBackgroundCipher.decryptAscii(ciphertext, key)
            assertEquals(expectedHash, sha256(clear))
        }
        assertEquals(
            "96e0b55c0b5bdbfefe23a82ca765c96e319045cceb91b112a18dbaf291b2a513",
            sha256(String(MainBackgroundCipher.decodePackaged(MainBackgroundCipher.IDLE_SCORE_PACKAGED)))
        )
    }

    @Test
    fun dailyChoiceAlternatesAndExplicitChoicePersists() {
        assertEquals(MainBackgroundPreference.Strings,
            MainBackgroundPreferences.resolve(MainBackgroundPreference.Daily, 2L))
        assertEquals(MainBackgroundPreference.Keyboard,
            MainBackgroundPreferences.resolve(MainBackgroundPreference.Daily, 3L))
        val context: Context = RuntimeEnvironment.getApplication()
        MainBackgroundPreferences.set(context, MainBackgroundPreference.Keyboard)
        assertEquals(MainBackgroundPreference.Keyboard, MainBackgroundPreferences.get(context))
    }

    @Test
    fun idlePlaybackPausesWithoutLosingItsPosition() {
        val cursor = IdlePlaybackCursor(charArrayOf(0x41.toChar(), 0x62.toChar()))
        assertEquals("Shift", cursor.next(0L, typing = false)?.label)
        assertNull(cursor.next(100L, typing = true))
        assertNull(cursor.next(850L, typing = false))
        assertEquals(0, cursor.index)
        assertEquals("A", cursor.next(900L, typing = false)?.label)
        assertEquals(1, cursor.index)
    }

    @Test
    fun idlePlaybackUsesAltChordForMappedSpecialCharacters() {
        val cursor = IdlePlaybackCursor(charArrayOf('!', 'a'), mapOf('!' to "B"))
        val stroke = cursor.next(0L, typing = false)
        assertEquals("B", stroke?.label)
        assertEquals("Alt", stroke?.modifierLabel)
        assertEquals(1, cursor.index)
    }

    @Test
    fun idlePlaybackResumesImmediatelyWithoutResettingItsPosition() {
        val cursor = IdlePlaybackCursor(charArrayOf('a', 'b'))
        assertEquals("A", cursor.next(0L, typing = false)?.label)
        assertNull(cursor.next(100L, typing = false))

        cursor.resumeImmediately()

        assertEquals("B", cursor.next(100L, typing = false)?.label)
        assertEquals(0, cursor.index)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.US_ASCII))
        .joinToString("") { "%02x".format(it) }
}
