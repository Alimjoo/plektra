package rocks.pkb.plektra

import android.util.Base64

internal object MainBackgroundCipher {
    const val IDLE_SCORE_PACKAGED = "bytBL1xHREQ1U0M4OkMzQShATEEyQjVAY1R9ODRHMzg9PD05Q1NTIzg6Q0I0KSs/NVBTJDU7VD5CPj0oOy9DNTA1OEdcTyksNU42NDg1QkIxQz88K0Q4Ni1OK1VLNEIyOE4ySi08Q0YsQjRCRzk0PklXTyMwK045RDk4Tz08Pk87LT4zRjo7JyxGT0Q3TkkySEc6Pj85PFJPeTw0SVs2NU41NUdBKUM+MVVEME9BPTI0PWNTZT4qU0M4Oj1PNyg8OEExQ082REkvPkMxNT1JRTpENy9KOD48Q0U/Rjo7JyxGTzg7Tj1dTkd+Pj9VNyY+RUVEP0kpT0MvK0BNQjg5QDc8PDU0Rz04Pj5TOTgrQVpPOCU8O0Y6OycwUz48SUkrPDYxKU45SFM0PCpgNTFILDk+NSs3T0J0MjxLSUlENy1FPjhCTURELj09N1Q9P0RGKTQwSUhPMUVUM0NOLCsyMzo5REcuNUNPTDNCOjpFOTE1UzE5QlQ5Nk48LjQ3Y1NEcSs3MEVIKU9DLytMQTE5NTFVOTM2NS04NDhKUzk4L0I2Q2JDJDcsP1IyOEc4TEtISUQ+RzZEPlVILTVFNzc/Pic1TiknNi1CPT5MSj0vOUFHLjA4OkdST0V0PTRUOzg4MytSNTJBNUxDNkNPQi0rTj4+OS5CRXU7MTgvcTQ5OEw1QjVDTEg6NjkyO1FOQTtTODUrUz8/SDc5MTAyPDYsOUNaVVRrNTIpO0IvVUgtNUVEND9FMDVOPi5CQSRGNUw5RytKSEcrPTlLOy1POkNORD0tPjpGOjsnLEZPPT5EODVCRzk3OUtAKU8nODBASUNEPkY6OycwX08uSDpJRDctRT44O0dERytTMUU+MDROLTVFTw=="
    private const val ALPHABET_START = 0x20
    private const val ALPHABET_SIZE = 0x5f
    private const val MASK = 0x5a

    private val maskedKey = intArrayOf(
        0x1d, 0x1f, 0x14, 0x13, 0x0f, 0x09, 0x1e, 0x15, 0x1f, 0x09,
        0x14, 0x15, 0x0e, 0x19, 0x15, 0x14, 0x1c, 0x1f, 0x08, 0x1b,
        0x18, 0x09, 0x15, 0x16, 0x0f, 0x0e, 0x13, 0x15, 0x14
    )

    fun decodePackaged(encoded: String): CharArray {
        val ciphertext = Base64.decode(encoded, Base64.DEFAULT)
        return CharArray(ciphertext.size) { index ->
            val cipher = ciphertext[index].toInt() and 0xff
            val key = maskedKey[index % maskedKey.size] xor MASK
            (ALPHABET_START +
                (cipher - key + ALPHABET_SIZE) % ALPHABET_SIZE).toChar()
        }
    }

    fun decryptAscii(ciphertext: String, maskedKeyBytes: IntArray = maskedKey): String {
        require(maskedKeyBytes.isNotEmpty())
        val output = CharArray(ciphertext.length)
        ciphertext.forEachIndexed { index, character ->
            require(character.code in ALPHABET_START until ALPHABET_START + ALPHABET_SIZE)
            val key = maskedKeyBytes[index % maskedKeyBytes.size] xor MASK
            output[index] = (ALPHABET_START +
                (character.code - ALPHABET_START - (key - ALPHABET_START) + ALPHABET_SIZE) %
                    ALPHABET_SIZE).toChar()
        }
        return String(output)
    }

    fun decodeAsciiBase64(encoded: String): String = String(decodePackaged(encoded))
}
