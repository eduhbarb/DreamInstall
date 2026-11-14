package dev.oni.dreaminstaller.core

import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES-ECB with no padding. Android's AES is hardware accelerated, so this is also the base for
 * the little endian CTR and the CMAC used by the 3DS.
 */
class AesEcb(key: ByteArray) {
    private val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    private val spec = SecretKeySpec(key, "AES")

    init {
        require(key.size == 16) { "AES key must be 16 bytes" }
        cipher.init(Cipher.ENCRYPT_MODE, spec)
    }

    fun encrypt(block: ByteArray): ByteArray = cipher.doFinal(block)

    fun encryptBlocks(blocks: ByteArray): ByteArray = cipher.doFinal(blocks)
}

/**
 * AES-CTR as the 3DS uses it: a 128 bit big endian counter block, incremented the same way.
 * The counter block comes from the SD path hash or from the NCCH partition/section pair.
 */
class CtrStream(key: ByteArray, iv: ByteArray) {
    private val aes = AesEcb(key)
    private val counter = iv.copyOf(16)
    private var pending = ByteArray(0)

    init {
        require(iv.size >= 16) { "IV must be 16 bytes" }
    }

    fun process(input: ByteArray, offset: Int = 0, length: Int = input.size - offset): ByteArray {
        val out = ByteArray(length)
        var written = 0

        // use what is left of the previous keystream blocks first
        if (pending.isNotEmpty()) {
            val take = minOf(length, pending.size)
            for (i in 0 until take) {
                out[i] = (input[offset + i].toInt() xor pending[i].toInt()).toByte()
            }
            pending = if (take < pending.size) pending.copyOfRange(take, pending.size) else ByteArray(0)
            written = take
        }

        val remaining = length - written
        if (remaining > 0) {
            val blocks = (remaining + 15) / 16
            val counters = ByteArray(blocks * 16)
            for (b in 0 until blocks) {
                System.arraycopy(counter, 0, counters, b * 16, 16)
                incrementCounter()
            }
            val keystream = aes.encryptBlocks(counters)
            for (i in 0 until remaining) {
                out[written + i] = (input[offset + written + i].toInt() xor keystream[i].toInt()).toByte()
            }
            if (remaining % 16 != 0) {
                pending = keystream.copyOfRange(remaining, blocks * 16)
            }
        }

        return out
    }

    private fun incrementCounter() {
        var i = 15
        while (i >= 0) {
            val v = (counter[i].toInt() and 0xFF) + 1
            counter[i] = (v and 0xFF).toByte()
            if (v <= 0xFF) return
            i--
        }
    }
}

object Crypto {
    private val MASK_128: BigInteger = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
    private val KEYGEN_CONST = BigInteger("1FF9E9AAC5FE0408024591DC5D52768A", 16)

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    fun rol(value: BigInteger, bits: Int): BigInteger {
        val v = value.and(MASK_128)
        val left = v.shiftLeft(bits).and(MASK_128)
        val right = v.shiftRight(128 - bits)
        return left.or(right)
    }

    /** 3DS AES key scrambler: normal key from KeyX and KeyY. */
    fun keygen(keyX: ByteArray, keyY: ByteArray): ByteArray {
        val x = BigInteger(1, keyX)
        val y = BigInteger(1, keyY)
        val normal = rol(rol(x, 2).xor(y).add(KEYGEN_CONST), 87)
        return toFixed(normal, 16)
    }

    fun toFixed(value: BigInteger, size: Int): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(size)
        if (raw.size > size) {
            System.arraycopy(raw, raw.size - size, out, 0, size)
        } else {
            System.arraycopy(raw, 0, out, size - raw.size, raw.size)
        }
        return out
    }

    /** IV for a file inside an id1 directory: sha256(utf-16le path + NUL), halves XORed. */
    fun sdPathToIv(path: String): ByteArray {
        var normalised = path.lowercase().replace('\\', '/')
        if (normalised.startsWith("/backup") && normalised.length > 28) {
            val tidUpper = normalised.substring(12, 20)
            val tidLower = normalised.substring(20, 28)
            normalised = "/title/$tidUpper/$tidLower/data" + normalised.substring(28)
        }
        val encoded = normalised.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        val hash = sha256(encoded)
        val out = ByteArray(16)
        for (i in 0 until 16) {
            out[i] = (hash[i].toInt() xor hash[16 + i].toInt()).toByte()
        }
        return out
    }

    /** AES-CMAC (RFC 4493). */
    fun cmac(key: ByteArray, data: ByteArray): ByteArray {
        val aes = AesEcb(key)
        val l = aes.encrypt(ByteArray(16))
        val k1 = dbl(l)
        val k2 = dbl(k1)

        val n = if (data.isEmpty()) 1 else (data.size + 15) / 16
        val complete = data.isNotEmpty() && data.size % 16 == 0

        var x = ByteArray(16)
        for (i in 0 until n - 1) {
            val block = data.copyOfRange(i * 16, i * 16 + 16)
            x = aes.encrypt(xor(x, block))
        }

        val lastStart = (n - 1) * 16
        val last = if (complete) {
            xor(data.copyOfRange(lastStart, lastStart + 16), k1)
        } else {
            val padded = ByteArray(16)
            val tail = data.copyOfRange(lastStart, data.size)
            System.arraycopy(tail, 0, padded, 0, tail.size)
            padded[tail.size] = 0x80.toByte()
            xor(padded, k2)
        }
        return aes.encrypt(xor(x, last))
    }

    private fun dbl(input: ByteArray): ByteArray {
        val out = ByteArray(16)
        var carry = 0
        for (i in 15 downTo 0) {
            val v = input[i].toInt() and 0xFF
            out[i] = ((v shl 1) or carry).toByte()
            carry = (v ushr 7) and 1
        }
        if ((input[0].toInt() and 0x80) != 0) {
            out[15] = (out[15].toInt() xor 0x87).toByte()
        }
        return out
    }

    fun xor(a: ByteArray, b: ByteArray): ByteArray {
        val out = ByteArray(minOf(a.size, b.size))
        for (i in out.indices) out[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        return out
    }
}

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

fun ByteArray.hexUpper(): String = joinToString("") { "%02x".format(it) }.uppercase()

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "odd hex length" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

fun ByteArray.u16le(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)

fun ByteArray.u16be(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

fun ByteArray.u32le(offset: Int): Long =
    (u8(offset).toLong()) or (u8(offset + 1).toLong() shl 8) or
        (u8(offset + 2).toLong() shl 16) or (u8(offset + 3).toLong() shl 24)

fun ByteArray.u32be(offset: Int): Long =
    (u8(offset).toLong() shl 24) or (u8(offset + 1).toLong() shl 16) or
        (u8(offset + 2).toLong() shl 8) or u8(offset + 3).toLong()

fun ByteArray.u64le(offset: Int): Long {
    var v = 0L
    for (i in 7 downTo 0) v = (v shl 8) or (u8(offset + i).toLong())
    return v
}

fun ByteArray.u64be(offset: Int): Long {
    var v = 0L
    for (i in 0 until 8) v = (v shl 8) or (u8(offset + i).toLong())
    return v
}

fun le32(value: Long): ByteArray = ByteArray(4) { ((value shr (8 * it)) and 0xFF).toByte() }

fun le16(value: Int): ByteArray = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

fun le64(value: Long): ByteArray = ByteArray(8) { ((value shr (8 * it)) and 0xFF).toByte() }

fun be16(value: Int): ByteArray = byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())

fun be32(value: Long): ByteArray = ByteArray(4) { ((value shr (8 * (3 - it))) and 0xFF).toByte() }

fun be64(value: Long): ByteArray = ByteArray(8) { ((value shr (8 * (7 - it))) and 0xFF).toByte() }

fun roundUp(value: Long, alignment: Long): Long =
    if (value % alignment == 0L) value else (value / alignment + 1) * alignment

/** Adds a block count to a 128 bit big endian counter block. */
fun addToCounterBe(base: ByteArray, blocks: Long): ByteArray {
    val out = base.copyOf(16)
    var carry = blocks
    var i = 15
    while (carry != 0L && i >= 0) {
        val sum = (out[i].toLong() and 0xFF) + (carry and 0xFF)
        out[i] = (sum and 0xFF).toByte()
        carry = (carry ushr 8) + (sum ushr 8)
        i--
    }
    return out
}
