package dev.oni.dreaminstall.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Random access to a CIA, either a real file or a SAF document. */
class ByteSource private constructor(
    private val channel: FileChannel,
    private val closeables: List<Closeable>,
    val size: Long,
    val displayName: String,
) : Closeable {

    fun readAt(position: Long, length: Int): ByteArray {
        if (length <= 0 || position >= size) return ByteArray(0)
        val buffer = ByteBuffer.allocate(length)
        var pos = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, pos)
            if (read <= 0) break
            pos += read
        }
        val out = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(out)
        return out
    }

    fun readExactly(position: Long, length: Int): ByteArray {
        val data = readAt(position, length)
        if (data.size != length) throw CiaError("leitura curta em 0x${position.toString(16)}")
        return data
    }

    override fun close() {
        closeables.forEach { runCatching { it.close() } }
    }

    companion object {
        fun fromFile(file: File): ByteSource {
            val raf = RandomAccessFile(file, "r")
            return ByteSource(raf.channel, listOf(raf), raf.length(), file.name)
        }

        fun fromUri(context: Context, uri: Uri): ByteSource {
            val resolver = context.contentResolver
            val name = queryName(resolver, uri) ?: uri.lastPathSegment ?: "title.cia"
            val pfd = resolver.openFileDescriptor(uri, "r")
                ?: throw CiaError("não foi possível abrir $name")
            val stream = FileInputStream(pfd.fileDescriptor)
            val source = ByteSource(stream.channel, listOf(stream, pfd), pfd.statSize, name)
            // providers that hand out pipes are not seekable; copy once so the parser works
            return try {
                if (source.size <= 0L) throw CiaError("tamanho desconhecido")
                source.readAt(0, 1)
                source
            } catch (_: Exception) {
                val cache = File(context.cacheDir, "cia-${name.hashCode()}.cia")
                resolver.openInputStream(uri)!!.use { input ->
                    cache.outputStream().use { output -> input.copyTo(output) }
                }
                source.close()
                fromFile(cache)
            }
        }

        private fun queryName(resolver: ContentResolver, uri: Uri): String? {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
            return null
        }
    }
}

class CiaError(message: String) : Exception(message)

data class ContentRecord(
    val id: String,
    val index: Int,
    val encrypted: Boolean,
    val size: Long,
    val hash: ByteArray,
)

class Tmd(
    val titleId: String,
    val saveSize: Long,
    val titleVersion: Int,
    val contentCount: Int,
    val records: List<ContentRecord>,
    val raw: ByteArray,
)

data class NcchInfo(
    val programId: String,
    val version: Int,
    val productCode: String,
    val keyY: ByteArray,
    val partitionId: Long,
    val cryptoMethod: Int,
    val fixedCryptoKey: Boolean,
    val noCrypto: Boolean,
    val usesSeed: Boolean,
    val exheaderSize: Long,
    val exefsOffset: Long,
    val exefsSize: Long,
)

/** A parsed CIA. Keeps the source open for the install. */
class Cia internal constructor(
    val source: ByteSource,
    val ticket: ByteArray,
    val tmd: Tmd,
    val titleKey: ByteArray,
    val contents: List<ContentRecord>,
    private val contentOffsets: Map<Int, Long>,
    var titleName: String?,
    var iconRgb565: ByteArray?,
    val ncch: NcchInfo?,
    private val ncchKey: ByteArray?,
    var extData: ByteArray,
    private val label: String,
) : Closeable {

    val titleId: String get() = tmd.titleId
    val isDlc: Boolean get() = titleId.startsWith("0004008c")
    val titleVersion: Int get() = tmd.titleVersion
    val saveSize: Long get() = tmd.saveSize
    val fileName: String get() = source.displayName
    val hasManual: Boolean get() = !isDlc && contents.any { it.index == 1 }
    val cmdId: Int get() = if (isDlc) contents.size else 1
    val usesSeed: Boolean get() = ncch?.usesSeed == true
    val programId: Long
        get() = ncch?.programId?.let { java.lang.Long.parseUnsignedLong(it, 16) } ?: 0L
    val extDataId: ByteArray get() = extData
    val ncchVersion: Int get() = ncch?.version ?: 0
    val productCode: String get() = ncch?.productCode ?: ""

    /** Total size the Title Info Entry reports for this title, exactly as custom-install does. */
    fun installSize(): Long {
        val sizes = ArrayList<Long>(8)
        repeat(5) { sizes.add(1) }
        if (saveSize > 0) {
            sizes.add(1)
            sizes.add(saveSize)
        }
        contents.forEach { sizes.add(it.size) }
        return sizes.sumOf { roundUp(it, 0x8000) }
    }

    fun contentOffset(index: Int): Long = contentOffsets[index] ?: throw CiaError("conteúdo $index ausente")

    val displayLabel: String
        get() = titleName?.takeIf { it.isNotBlank() } ?: label

    /** Reads part of a content with the CIA layer (title key) removed. */
    fun readDecrypted(index: Int, offset: Long, length: Int): ByteArray {
        val record = contents.first { it.index == index }
        if (!record.encrypted) return source.readAt(contentOffset(index) + offset, length)

        val aligned = offset - (offset % 16)
        val skip = (offset - aligned).toInt()
        val total = ((skip + length + 15) / 16) * 16
        val absolute = contentOffset(index) + aligned
        val cipherText = source.readAt(absolute, total)
        if (cipherText.isEmpty()) return ByteArray(0)
        val iv = if (aligned == 0L) {
            be16(index) + ByteArray(14)
        } else {
            source.readAt(absolute - 16, 16)
        }
        val plain = decryptCbc(titleKey, iv, cipherText)
        val end = minOf(skip + length, plain.size)
        return plain.copyOfRange(skip, end)
    }

    /**
     * Removes the NCCH section crypto (KeyY from the header + KeyX from boot9) on top of the
     * CIA layer. Only the main NCCH keyslot is used: exheader, ExeFS header, icon and banner
     * always use it, even when .code uses another keyslot.
     */
    private fun ncchDecrypt(section: Int, offsetInSection: Long, data: ByteArray): ByteArray {
        val key = ncchKey ?: return data
        if (ncch?.noCrypto == true) return data
        if (data.isEmpty()) return data
        val skip = (offsetInSection % 16).toInt()
        val aligned = offsetInSection - skip
        val base = ByteArray(16)
        var pid = ncch?.partitionId ?: 0L
        for (i in 0 until 8) {
            base[7 - i] = ((pid ushr (8 * i)) and 0xFF).toByte()
        }
        base[8] = section.toByte()
        val counter = addToCounterBe(base, aligned / 16)
        val stream = CtrStream(key, counter)
        if (skip > 0) stream.process(ByteArray(skip))
        return stream.process(data)
    }

    /** Reads [length] bytes from an NCCH section starting at [offsetInSection], fully decrypted. */
    private fun readNcchSection(section: Int, sectionStart: Long, offsetInSection: Long, length: Int): ByteArray {
        val raw = readDecrypted(0, sectionStart + offsetInSection, length)
        return ncchDecrypt(section, offsetInSection, raw)
    }

    override fun close() = source.close()

    private fun readIcnFile(): ByteArray? {
        val ncchInfo = ncch ?: return null
        if (ncchInfo.exefsOffset == 0L || ncchInfo.exefsSize == 0L) return null
        val exefsHeader = readNcchSection(SECTION_EXEFS, ncchInfo.exefsOffset, 0, 0x200)
        if (exefsHeader.size < 0x200) return null
        for (i in 0 until 10) {
            val base = i * 0x10
            val name = String(exefsHeader.copyOfRange(base, base + 8), Charsets.US_ASCII).trim('\u0000')
            if (!name.startsWith("icon")) continue
            val fileOffset = exefsHeader.u32le(base + 8)
            val fileSize = exefsHeader.u32le(base + 12)
            if (fileSize == 0L) return null
            val within = 0x200L + fileOffset
            return readNcchSection(SECTION_EXEFS, ncchInfo.exefsOffset, within, fileSize.toInt())
        }
        return null
    }

    private fun loadMetadata() {
        val icn = readIcnFile() ?: return
        // SMDH: 16 application titles of 0x200 bytes, English first, then Japanese, then the rest
        val order = intArrayOf(1, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        for (lang in order) {
            val start = 0x8 + lang * 0x200
            if (icn.size < start + 0x80) break
            val text = String(icn.copyOfRange(start, start + 0x80), Charsets.UTF_16LE).trim('\u0000')
            if (text.isNotBlank()) {
                titleName = text
                break
            }
        }
        if (icn.size >= 0x24C0 + 0x1200) {
            iconRgb565 = icn.copyOfRange(0x24C0, 0x24C0 + 0x1200)
        }
    }

    companion object {
        private const val ALIGN = 64L
        private const val HEADER_SIZE = 0x2020L
        private const val SECTION_EXHEADER = 1
        private const val SECTION_EXEFS = 2
        private const val FIXED_SYSTEM_KEY = "527CE630A9CA305F3696F3CDE954194B"

        fun open(source: ByteSource, keys: ConsoleKeys): Cia {
            val header = source.readExactly(0, 0x20)
            val archiveHeaderSize = header.u32le(0)
            if (archiveHeaderSize != HEADER_SIZE) {
                throw CiaError("não parece um CIA (magic 0x${archiveHeaderSize.toString(16)})")
            }
            val certSize = header.u32le(0x8)
            val ticketSize = header.u32le(0xC)
            val tmdSize = header.u32le(0x10)
            val contentSize = header.u32le(0x18)

            val indexBytes = source.readExactly(0x20, (archiveHeaderSize - 0x20).toInt())
            val active = HashSet<Int>()
            for (byteIndex in indexBytes.indices) {
                val value = indexBytes[byteIndex].toInt() and 0xFF
                // the archive header writes the enabled contents most significant bit first
                for (bit in 0 until 8) {
                    if ((value shr bit) and 1 == 1) active.add(byteIndex * 8 + (7 - bit))
                }
            }

            val certOffset = roundUp(archiveHeaderSize, ALIGN)
            val ticketOffset = certOffset + roundUp(certSize, ALIGN)
            val tmdOffset = ticketOffset + roundUp(ticketSize, ALIGN)
            val contentOffset = tmdOffset + roundUp(tmdSize, ALIGN)

            val ticket = source.readExactly(ticketOffset, ticketSize.toInt())
            val titleKey = keys.decryptTitleKey(ticket)
            val tmdRaw = source.readExactly(tmdOffset, tmdSize.toInt())
            val tmd = parseTmd(tmdRaw)

            val records = tmd.records.filter { it.index in active }
            if (records.size != active.size) throw CiaError("TMD não contém todos os conteúdos do CIA")

            val offsets = LinkedHashMap<Int, Long>()
            var offset = contentOffset
            for (record in records) {
                offsets[record.index] = offset
                offset += record.size
            }

            // the NCCH header is plaintext before encryption: read the first 0x800 bytes decrypted
            val contentZero = records.firstOrNull { it.index == 0 }
            var ncch: NcchInfo? = null
            if (contentZero != null) {
                val first = source.readAt(contentOffset, minOf(0x800L, contentZero.size).toInt())
                val plain = if (contentZero.encrypted) decryptCbc(titleKey, ByteArray(16), first) else first
                // 0x0 is the signature (also the NCCH KeyY); the magic lives at 0x100
                if (plain.size >= 0x200 && plain[0x100] == 'N'.code.toByte() && plain[0x101] == 'C'.code.toByte() &&
                    plain[0x102] == 'C'.code.toByte() && plain[0x103] == 'H'.code.toByte()
                ) {
                    ncch = parseNcch(plain)
                }
            }

            val ncchKey = ncch?.let { buildNcchKey(it, keys) }
            val cia = Cia(
                source = source,
                ticket = ticket,
                tmd = tmd,
                titleKey = titleKey,
                contents = records,
                contentOffsets = offsets,
                titleName = null,
                iconRgb565 = null,
                ncch = ncch,
                ncchKey = ncchKey,
                extData = ByteArray(8),
                label = source.displayName.substringBeforeLast('.'),
            )
            if (ncch != null) {
                cia.loadExtData()
                cia.loadMetadata()
            }
            return cia
        }

        private fun buildNcchKey(info: NcchInfo, keys: ConsoleKeys): ByteArray? {
            if (info.noCrypto) return null
            if (info.fixedCryptoKey) {
                val bit = java.lang.Long.parseUnsignedLong(info.programId, 16) and (0x10L shl 32)
                return if (bit != 0L) FIXED_SYSTEM_KEY.hexToBytes() else ByteArray(16)
            }
            // pyctr (and the 3DS) keep the original KeyY for the main keyslot: the exheader,
            // the ExeFS header, the icon and the banner are not seeded. Only .code and RomFS
            // switch to the seeded key in the extra keyslot.
            return Crypto.keygen(keys.ncchKeyX(), info.keyY)
        }

        fun decryptCbc(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return cipher.doFinal(data)
        }

        private fun parseTmd(raw: ByteArray): Tmd {
            if (raw.size < 4) throw CiaError("TMD vazio")
            val sigType = raw.u32be(0)
            val (sigSize, padding) = when (sigType) {
                0x00010000L -> 0x200 to 0x3C
                0x00010001L -> 0x100 to 0x3C
                0x00010002L -> 0x3C to 0x40
                0x00010003L -> 0x200 to 0x3C
                0x00010004L -> 0x100 to 0x3C
                0x00010005L -> 0x3C to 0x40
                else -> throw CiaError("tipo de assinatura TMD desconhecido: 0x${sigType.toString(16)}")
            }
            val headerOffset = 4 + sigSize + padding
            if (raw.size < headerOffset + 0xC4) throw CiaError("TMD truncado")
            val header = raw.copyOfRange(headerOffset, headerOffset + 0xC4)
            val titleId = header.copyOfRange(0x4C, 0x54).hex()
            val saveSize = header.u32le(0x5A)
            val version = header.u16be(0x9C)
            val count = header.u16be(0x9E)
            val infoStart = headerOffset + 0xC4
            val chunkStart = infoStart + 0x900
            if (raw.size < chunkStart + count * 0x30) throw CiaError("TMD truncado nos conteúdos")

            val infoHash = header.copyOfRange(0xA4, 0xC4)
            val infoRecords = raw.copyOfRange(infoStart, infoStart + 0x900)
            if (!Crypto.sha256(infoRecords).contentEquals(infoHash)) {
                throw CiaError("hash dos registros do TMD não confere")
            }

            val records = ArrayList<ContentRecord>(count)
            for (i in 0 until count) {
                val base = chunkStart + i * 0x30
                records.add(
                    ContentRecord(
                        id = raw.copyOfRange(base, base + 4).hex(),
                        index = raw.u16be(base + 4),
                        encrypted = (raw.u16be(base + 6) and 1) != 0,
                        size = raw.u64be(base + 8),
                        hash = raw.copyOfRange(base + 16, base + 48),
                    ),
                )
            }
            return Tmd(titleId, saveSize, version, count, records, raw)
        }

        private fun parseNcch(header: ByteArray): NcchInfo {
            val version = header.u16le(0x112)
            val productCode = String(header.copyOfRange(0x150, 0x160), Charsets.US_ASCII).trim('\u0000')
            val programId = header.copyOfRange(0x118, 0x120).reversedArray().hex()
            val flags = header.copyOfRange(0x188, 0x190)
            return NcchInfo(
                programId = programId,
                version = version,
                productCode = productCode,
                keyY = header.copyOfRange(0x0, 0x10),
                partitionId = header.u64le(0x108),
                cryptoMethod = flags[3].toInt() and 0xFF,
                fixedCryptoKey = (flags[7].toInt() and 0x1) != 0,
                noCrypto = (flags[7].toInt() and 0x4) != 0,
                usesSeed = (flags[7].toInt() and 0x20) != 0,
                exheaderSize = header.u32le(0x180),
                exefsOffset = header.u32le(0x1A0) * 0x200,
                exefsSize = header.u32le(0x1A4) * 0x200,
            )
        }
    }

    private fun loadExtData() {
        val info = ncch ?: return
        if (info.exheaderSize != 0x400L) return
        val exheader = readNcchSection(SECTION_EXHEADER, 0x200, 0, 0x800)
        if (exheader.size >= 0x238) {
            extData = exheader.copyOfRange(0x230, 0x238)
        }
    }
}
