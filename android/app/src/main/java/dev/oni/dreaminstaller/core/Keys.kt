package dev.oni.dreaminstaller.core

import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class KeyError(message: String) : Exception(message)

/** Console unique keys, all derived from boot9.bin and movable.sed. Never bundled with the app. */
class ConsoleKeys private constructor(
    val boot9: ByteArray,
    val movable: ByteArray,
    val seedDb: ByteArray?,
) {
    private val keyblob: ByteArray

    /** Directory name of the console on the SD card (16 bytes, hex). */
    val id0: ByteArray

    /** Normal key for SD contents (path based CTR). */
    val sdKey: ByteArray

    /** Normal key for the title database CMACs. */
    val cmacKey: ByteArray

    /** Normal key that decrypts the title key inside a ticket. */
    val commonKey: ByteArray get() = commonKey(0)

    /** Seeds indexed by program id. */
    val seeds: Map<Long, ByteArray>

    private val commonKeys = HashMap<Int, ByteArray>()

    init {
        if (boot9.size != 0x10000) {
            throw KeyError("boot9.bin precisa ter 0x10000 bytes (dump completo), recebeu 0x${boot9.size.toString(16)}")
        }
        if (movable.size !in intArrayOf(0x120, 0x140, 0x10)) {
            throw KeyError("movable.sed precisa ter 0x120 bytes, recebeu 0x${movable.size.toString(16)}")
        }
        // a full boot9 dump keeps the retail keyblob at 0xD860, the same offset save3ds reads
        keyblob = boot9.copyOfRange(0xD860, 0xD860 + 0x400)

        val keyY = if (movable.size == 0x10) movable else movable.copyOfRange(0x110, 0x120)

        sdKey = Crypto.keygen(keyslotX(0x34), keyY)
        cmacKey = Crypto.keygen(keyslotX(0x30), keyY)

        val hash = Crypto.sha256(keyY).copyOfRange(0, 16)
        id0 = ByteArray(16)
        for (i in 0 until 4) {
            id0[i * 4 + 0] = hash[i * 4 + 3]
            id0[i * 4 + 1] = hash[i * 4 + 2]
            id0[i * 4 + 2] = hash[i * 4 + 1]
            id0[i * 4 + 3] = hash[i * 4 + 0]
        }

        seeds = seedDb?.let { parseSeedDb(it) } ?: emptyMap()
    }

    private fun keyslotX(slot: Int): ByteArray {
        // pyctr's key_loop reads one 16 byte KeyX and assigns it to four consecutive slots
        // (0x2C..0x2F from 0x170, 0x30..0x33 from 0x180, 0x34..0x37 from 0x190, 0x38..0x3B from 0x1A0),
        // while 0x3C..0x3F get one each starting at 0x1B0.
        val offset = when {
            slot in 0x2C..0x3B -> 0x170 + ((slot - 0x2C) / 4) * 0x10
            slot in 0x3C..0x3F -> 0x1B0 + (slot - 0x3C) * 0x10
            else -> throw KeyError("keyslot 0x${slot.toString(16)} não vem do keyblob")
        }
        if (offset + 0x10 > keyblob.size) throw KeyError("keyblob do boot9 pequeno demais")
        return keyblob.copyOfRange(offset, offset + 0x10)
    }

    fun ncchKeyX(): ByteArray = keyslotX(0x2C)

    fun sdCtr(path: String): CtrStream = CtrStream(sdKey, Crypto.sdPathToIv(path))

    fun commonKey(index: Int): ByteArray = commonKeys.getOrPut(index) {
        val keyY = COMMON_KEY_YS.getOrElse(index) { COMMON_KEY_YS[0] }
        Crypto.keygen(keyslotX(0x3D), keyY.hexToBytes())
    }

    fun decryptTitleKey(ticket: ByteArray): ByteArray {
        if (ticket.size < 0x2AC) throw KeyError("ticket pequeno demais")
        val encrypted = ticket.copyOfRange(0x1BF, 0x1CF)
        val titleId = ticket.copyOfRange(0x1DC, 0x1E4)
        val commonKeyIndex = ticket[0x1F1].toInt() and 0xFF
        val iv = titleId + ByteArray(8)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(commonKey(commonKeyIndex), "AES"), IvParameterSpec(iv))
        return cipher.doFinal(encrypted)
    }

    fun cmac(data: ByteArray): ByteArray = Crypto.cmac(cmacKey, data)

    fun seedFor(programId: Long): ByteArray? = seeds[programId]

    val id0Hex: String get() = id0.hex()

    companion object {
        private val COMMON_KEY_YS = listOf(
            "D07B337F9CA4385932A2E25723232EB9",
            "0C767230F0998F1C46828202FAACBE4C",
            "C475CB3AB8C788BB575E12A10907B8A4",
            "E486EEE3D0C09C902F6686D4C06F649F",
            "ED31BA9C04B067506C4497A35B7804FC",
            "5E66998AB4E8931606850FD7A16DD755",
        )

        fun load(boot9: File, movable: File, seedDb: File?): ConsoleKeys = ConsoleKeys(
            boot9.readBytes(),
            movable.readBytes(),
            seedDb?.let { if (it.exists()) it.readBytes() else null },
        )

        fun of(boot9: ByteArray, movable: ByteArray, seedDb: ByteArray? = null): ConsoleKeys =
            ConsoleKeys(boot9, movable, seedDb)

        private fun parseSeedDb(data: ByteArray): Map<Long, ByteArray> {
            if (data.size < 0x10) return emptyMap()
            val count = data.u32le(0).toInt()
            val map = HashMap<Long, ByteArray>(count)
            var offset = 0x10
            for (i in 0 until count) {
                if (offset + 0x20 > data.size) break
                val titleId = data.u64le(offset)
                map[titleId] = data.copyOfRange(offset + 8, offset + 0x18)
                offset += 0x20
            }
            return map
        }
    }
}
