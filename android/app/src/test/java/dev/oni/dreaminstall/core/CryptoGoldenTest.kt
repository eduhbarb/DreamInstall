package dev.oni.dreaminstall.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Golden vectors generated with pyctr 0.7.x + pycryptodomex from a synthetic boot9/movable.sed.
 * They lock the keyblob offsets, keygen, SD path IV, CTR (big endian), CMAC and the NCCH
 * section counter against the reference implementation custom-install uses.
 */
class CryptoGoldenTest {

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    private val boot9 = resource("boot9.bin")
    private val movable = resource("movable.sed")

    private val testData = ByteArray(768) { (it % 256).toByte() }

    @Test
    fun consoleKeysMatchReference() {
        val keys = ConsoleKeys.of(boot9, movable)
        assertEquals("fa7d8d54bcd9537f8c35516b7cd7bbc8", keys.sdKey.hex())
        assertEquals("4937b1ff93bde2e2397bf6149e965675", keys.cmacKey.hex())
        assertEquals("1dafa756ffe1f95b2e0c60798784f61c", keys.commonKey.hex())
        assertEquals("a6f9379a443fa5f68d670c5229f4c1a9", keys.id0.hex())
    }

    @Test
    fun ticketTitleKeyMatchesReference() {
        val keys = ConsoleKeys.of(boot9, movable)
        val ticket = ByteArray(0x350)
        "bf0ac933b12ec634a64554e50fd41cc9".hexToBytes().copyInto(ticket, 0x1BF)
        "0004000000055d00".hexToBytes().copyInto(ticket, 0x1DC)
        ticket[0x1F1] = 0
        assertEquals("000102030405060708090a0b0c0d0e0f", keys.decryptTitleKey(ticket).hex())
    }

    @Test
    fun keygenMatchesReference() {
        val x = "3b38f08e793818ea00a8710d8e9fe1ab".hexToBytes()
        val y = "8d4681191c3ea05a4a5eb1b4b83c8168".hexToBytes()
        assertEquals("af6fcabf2740cf9665df6e63fd25a283", Crypto.keygen(x, y).hex())
    }

    @Test
    fun sdPathIvMatchesReference() {
        val iv = Crypto.sdPathToIv("/title/00040000/00055d00/content/00000000.app")
        assertEquals("d9202bf80299494ae201d47194e28c4b", iv.hex())
    }

    @Test
    fun cmacMatchesReference() {
        val keys = ConsoleKeys.of(boot9, movable)
        assertEquals("571a7638d02521da774859b5cbed505c", Crypto.cmac(keys.cmacKey, testData).hex())
    }

    @Test
    fun sdCtrMatchesReferenceInChunks() {
        val keys = ConsoleKeys.of(boot9, movable)
        val stream = CtrStream(keys.sdKey, Crypto.sdPathToIv("/title/00040000/00055d00/content/00000000.app"))
        val out = stream.process(testData, 0, 37) + stream.process(testData, 37, 63)
        assertEquals(
            "237ac9673f4749ce1d63b2b80d3138364e8f4f681cdddc435f6b6779da361aa8636fc6983954ecfccdad1b569daea842" +
                "aa26ad50598505435b62c98e0455cdc82301d8b3679d6471288005467752fd1ff32f012fa01fa8f09f43797db37d17eb" +
                "1772c068",
            out.hex(),
        )
    }

    @Test
    fun ncchCounterMatchesReference() {
        val key = "1367ba61355842b8dcaafa9546c165f2".hexToBytes()
        val base = ByteArray(16)
        val pid = 0x0004000000055D00L
        for (i in 0 until 8) base[7 - i] = ((pid ushr (8 * i)) and 0xFF).toByte()
        base[8] = 2

        val stream = CtrStream(key, base)
        val out = stream.process(testData, 0, 52) + stream.process(testData, 52, 148)
        assertEquals(
            "1bfd8b4868cbe5ca1ee423d5bcc8f28b0b355955dc78e448ef704aed2d05ad574f8fff83555b0e63021020d289c75c67" +
                "50094782d6a8492ae3a5df10cbad878055feed10923e135828f9a37bfdf6bcee0e80ff9cea60d2905b3ba0a632a79f9" +
                "a5db03fe541ee8b477ea3ecf4deac0d9a0679edb2e9f7c1e0364330ee6b042b67a40d359ae036ae0e83359b90b893e4" +
                "55e5917db2701d8147b6f529ab6b9989086c1800a58f3cf715d6c4d2b71b4691690b7ad423b3b2e8c8f835cefacda40" +
                "5cb73122fb7300baa84",
            out.hex(),
        )

        val offsetStream = CtrStream(key, addToCounterBe(base, 2))
        val offsetOut = offsetStream.process(testData.copyOfRange(32, 96))
        assertEquals(
            "4f8fff83555b0e63021020d289c75c6750094782d6a8492ae3a5df10cbad878055feed10923e135828f9a37bfdf6bcee" +
                "0e80ff9cea60d2905b3ba0a632a79f9a",
            offsetOut.hex(),
        )
    }

    @Test
    fun cifinishRoundTrips() {
        val dir = kotlin.io.path.createTempDirectory("cifinish").toFile()
        val file = File(dir, "cifinish.bin")
        val data = linkedMapOf<Long, ByteArray?>(
            0x0004000000055D00L to null,
            0x0004000000071A00L to ByteArray(16) { it.toByte() },
        )
        Cifinish.save(file, data)
        val loaded = Cifinish.load(file)
        assertEquals(data.keys, loaded.keys)
        assertArrayEquals(data[0x0004000000071A00L], loaded[0x0004000000071A00L])
        assertEquals(null, loaded[0x0004000000055D00L])
        dir.deleteRecursively()
    }
}
