package dev.oni.dreaminstaller.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class TitleState { Waiting, Writing, Finishing, Done, Failed, Skipped }

data class InstallResult(
    val done: List<String>,
    val failed: Map<String, String>,
    val finalizeCopied: Boolean,
    val usedTitleCount: Int,
    val backupDir: File?,
)

class InstallCancelled : Exception("cancelado")

class SeedMissing(val programId: String) : Exception("seed ausente para $programId")

/**
 * The custom-install pipeline, ported from ihaveamac/custom-install (MIT).
 * Writes contents to the SD card, builds the cmd file, updates the title database through
 * save3ds and leaves the ticket/seed work to custom-install-finalize on the console.
 */
class Installer(
    private val context: Context,
    private val keys: ConsoleKeys,
    private val keyFiles: Save3ds.KeysFiles,
    private val layout: SdLayout,
    private val verifyHashes: Boolean,
    private val overwriteSaves: Boolean,
    private val copyFinalizeProgram: Boolean,
    private val log: (String) -> Unit,
    private val onTitleState: (index: Int, state: TitleState, message: String?) -> Unit,
    private val onBytes: (index: Int, written: Long, total: Long) -> Unit,
    private val isCancelled: () -> Boolean,
) {
    private val save3ds = Save3ds(context)
    private val random = SecureRandom()

    fun run(titles: List<Cia>): InstallResult {
        Storage.clearTempDirs(layout)

        val needed = titles.sumOf { it.installSize() }
        val free = Storage.freeBytes(layout.root)
        if (needed > free) {
            throw InstallError(
                "espaço insuficiente: precisa de ${formatSize(needed)}, livre ${formatSize(free)}",
            )
        }

        val backup = Storage.backupDatabases(layout)
        backup?.let { log("backup do banco em ${it.absolutePath}") }

        ensureDatabases()

        val workDir = File(context.cacheDir, "title-db")
        workDir.deleteRecursively()
        workDir.mkdirs()
        log("extraindo title.db...")
        save3ds.extract(layout, keyFiles, workDir).forEach { log("save3ds: $it") }

        val done = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()

        titles.forEachIndexed { index, cia ->
            if (isCancelled()) throw InstallCancelled()
            val label = cia.displayLabel
            if (cia.usesSeed && keys.seedFor(cia.programId) == null) {
                onTitleState(index, TitleState.Failed, "seed ausente")
                failed[label] = "seed ausente"
                log("seed ausente para ${cia.titleId} (${cia.programId.toString(16)})")
                return@forEachIndexed
            }
            try {
                onTitleState(index, TitleState.Writing, null)
                installTitle(index, cia, workDir)
                onTitleState(index, TitleState.Done, null)
                done.add(label)
            } catch (cancelled: InstallCancelled) {
                throw cancelled
            } catch (e: SeedMissing) {
                onTitleState(index, TitleState.Failed, "seed ausente")
                failed[label] = "seed ausente"
            } catch (e: Exception) {
                onTitleState(index, TitleState.Failed, e.message)
                failed[label] = e.message ?: e.javaClass.simpleName
                log("falha em $label: ${e.message}")
                Storage.clearTempDirs(layout)
            }
        }

        val copied = if (done.isNotEmpty() && copyFinalizeProgram) Storage.copyFinalize(context, layout) else false
        return InstallResult(done, failed, copied, Storage.usedTitleCount(layout), backup)
    }

    private fun installTitle(index: Int, cia: Cia, workDir: File) {
        val titleId = cia.titleId
        val high = titleId.substring(0, 8)
        val low = titleId.substring(8, 16)
        val tempRoot = File(layout.root, "ci-install-temp-$titleId-${random.nextInt().toUInt().toString(16)}")
        val contentRoot = File(tempRoot, "content")
        val cmdDir = File(contentRoot, "cmd")
        val titleRoot = File(layout.titlePath, "$high/$low")
        val base3ds = "/title/$high/$low"
        val content3ds = "$base3ds/content"

        tempRoot.deleteRecursively()
        contentRoot.mkdirs()
        cmdDir.mkdirs()
        if (cia.saveSize > 0) File(tempRoot, "data").mkdirs()
        if (cia.isDlc) {
            val dirs = ((cia.contents.size - 1) / 256) + 1
            for (x in 0 until dirs) File(contentRoot, "%08x".format(x)).mkdirs()
        }

        try {
            log("instalando $titleId (${cia.displayLabel})")

            val tmdName = "00000000.tmd"
            writeEncrypted(File(contentRoot, tmdName), cia.tmd.raw, "$content3ds/$tmdName")

            val totalBytes = cia.contents.sumOf { it.size }
            var written = 0L
            for (record in cia.contents) {
                val name = "${record.id}.app"
                val outFile: File
                val path3ds: String
                if (cia.isDlc) {
                    val dirIndex = "%08x".format(record.index / 256)
                    outFile = File(contentRoot, "$dirIndex/$name")
                    path3ds = "$content3ds/$dirIndex/$name"
                } else {
                    outFile = File(contentRoot, name)
                    path3ds = "$content3ds/$name"
                }
                writeContent(index, cia, record, outFile, path3ds) { delta ->
                    written += delta
                    onBytes(index, written, totalBytes)
                }
            }

            if (cia.saveSize > 0) {
                writeSave(cia, tempRoot, base3ds)
            }

            onTitleState(index, TitleState.Finishing, null)
            writeCmd(cia, contentRoot, cmdDir, content3ds)

            val entry = buildTitleInfoEntry(cia)
            val cifinish = File(layout.root, "cifinish.bin")
            val data = Cifinish.load(cifinish)
            val seed = if (cia.usesSeed) keys.seedFor(cia.programId) ?: throw SeedMissing(cia.programId.toString(16)) else null
            data[java.lang.Long.parseUnsignedLong(titleId, 16)] = seed
            Cifinish.save(cifinish, data)

            if (titleRoot.exists()) {
                log("removendo instalação anterior em ${titleRoot.absolutePath}")
                titleRoot.deleteRecursively()
            }
            titleRoot.parentFile?.mkdirs()
            if (!tempRoot.renameTo(titleRoot)) {
                throw InstallError("não foi possível mover ${tempRoot.name} para ${titleRoot.absolutePath}")
            }

            File(workDir, titleId).writeBytes(entry)
            log("importando $titleId no title.db...")
            save3ds.import(layout, keyFiles, workDir).forEach { log("save3ds: $it") }
        } catch (e: Exception) {
            tempRoot.deleteRecursively()
            throw e
        }
    }

    private fun writeEncrypted(target: File, plain: ByteArray, path3ds: String) {
        FileOutputStream(target).use { output ->
            val ctr = CtrStream(keys.sdKey, Crypto.sdPathToIv(path3ds))
            var offset = 0
            while (offset < plain.size) {
                val length = minOf(CHUNK, plain.size - offset)
                output.write(ctr.process(plain, offset, length))
                offset += length
            }
            output.flush()
            output.fd.sync()
        }
    }

    private fun writeContent(
        titleIndex: Int,
        cia: Cia,
        record: ContentRecord,
        target: File,
        path3ds: String,
        onChunk: (Long) -> Unit,
    ) {
        val inputOffset = cia.contentOffset(record.index)
        val cbc = if (record.encrypted) {
            Cipher.getInstance("AES/CBC/NoPadding").apply {
                init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(cia.titleKey, "AES"),
                    IvParameterSpec(be16(record.index) + ByteArray(14)),
                )
            }
        } else {
            null
        }
        val ctr = CtrStream(keys.sdKey, Crypto.sdPathToIv(path3ds))
        val digest = MessageDigest.getInstance("SHA-256")

        FileOutputStream(target).use { output ->
            var position = 0L
            while (position < record.size) {
                if (isCancelled()) throw InstallCancelled()
                val length = minOf(CHUNK.toLong(), record.size - position).toInt()
                val cipherText = cia.source.readAt(inputOffset + position, length)
                if (cipherText.isEmpty()) throw CiaError("fim inesperado do CIA")
                val plain = cbc?.update(cipherText) ?: cipherText
                if (plain.isNotEmpty()) {
                    digest.update(plain)
                    output.write(ctr.process(plain))
                }
                position += cipherText.size
                onChunk(cipherText.size.toLong())
            }
            val tail = cbc?.doFinal() ?: ByteArray(0)
            if (tail.isNotEmpty()) {
                digest.update(tail)
                output.write(ctr.process(tail))
            }
            output.flush()
            output.fd.sync()
        }

        if (verifyHashes && !digest.digest().contentEquals(record.hash)) {
            target.delete()
            throw InstallError("hash não confere para ${record.id}.app")
        }
    }

    private fun writeSave(cia: Cia, tempRoot: File, base3ds: String) {
        val existing = File(File(layout.titlePath, "${cia.titleId.substring(0, 8)}/${cia.titleId.substring(8, 16)}"), "data/00000001.sav")
        val target = File(tempRoot, "data/00000001.sav")
        if (!overwriteSaves && existing.isFile) {
            log("mantendo save existente (${existing.length()} bytes)")
            existing.copyTo(target, overwrite = true)
            return
        }
        val ctr = CtrStream(keys.sdKey, Crypto.sdPathToIv("$base3ds/data/00000001.sav"))
        FileOutputStream(target).use { output ->
            val head = ctr.process(ByteArray(0x20))
            output.write(head)
            var left = cia.saveSize - 0x20
            val zeros = ByteArray(CHUNK)
            while (left > 0) {
                val length = minOf(left, zeros.size.toLong()).toInt()
                output.write(zeros, 0, length)
                left -= length
            }
            output.flush()
            output.fd.sync()
        }
    }

    private fun writeCmd(cia: Cia, contentRoot: File, cmdDir: File, content3ds: String) {
        val cmdId = cia.cmdId
        val cmdName = "%08x.cmd".format(cmdId)
        var highest = 0
        val ids = HashMap<Int, Pair<ByteArray, ByteArray>>()

        for (record in cia.contents) {
            val headerPart = cia.readDecrypted(record.index, 0x100, 0x100)
            if (headerPart.size != 0x100) throw InstallError("não foi possível ler o header de ${record.id}.app")
            val data = ByteArray(0x100 + 4 + 4)
            System.arraycopy(headerPart, 0, data, 0, 0x100)
            val indexBytes = le32(record.index.toLong())
            System.arraycopy(indexBytes, 0, data, 0x100, 4)
            val idBytes = record.id.hexToBytes().reversedArray()
            System.arraycopy(idBytes, 0, data, 0x104, 4)
            ids[record.index] = idBytes to keys.cmac(Crypto.sha256(data))
            highest = maxOf(highest, record.index)
        }

        val missing = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val missingCmac = "MISSING CONTENT!".toByteArray(Charsets.US_ASCII)
        val idsByIndex = ArrayList<ByteArray>(highest + 1)
        val cmacs = ArrayList<ByteArray>(highest + 1)
        val installed = ArrayList<ByteArray>()
        for (x in 0..highest) {
            val info = ids[x]
            if (info == null) {
                idsByIndex.add(missing)
                cmacs.add(missingCmac)
            } else {
                idsByIndex.add(info.first)
                cmacs.add(info.second)
                installed.add(info.first)
            }
        }
        installed.sortBy { it.u32le(0) }

        val header = le32(cmdId.toLong()) + le32(idsByIndex.size.toLong()) +
            le32(installed.size.toLong()) + le32(1)
        val output = ArrayList<ByteArray>()
        output.add(header)
        output.add(keys.cmac(header))
        output.addAll(idsByIndex)
        output.addAll(installed)
        output.addAll(cmacs)

        val path3ds = "$content3ds/cmd/$cmdName"
        val merged = java.io.ByteArrayOutputStream()
        output.forEach { merged.write(it) }
        writeEncrypted(File(cmdDir, cmdName), merged.toByteArray(), path3ds)
    }

    private fun buildTitleInfoEntry(cia: Cia): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(le64(cia.installSize()))
        out.write(le32(0x40))
        out.write(le16(cia.titleVersion))
        out.write(le16(cia.ncch?.version ?: 0))
        out.write(le32(if (cia.hasManual) 1 else 0))
        out.write(le32(0)) // tmd content id
        out.write(le32(cia.cmdId.toLong()))
        out.write(le32(if (cia.saveSize > 0) 1 else 0))
        out.write(cia.extDataId.copyOfRange(0, 4))
        out.write(ByteArray(4))
        out.write(le64(0x100000000L))
        val product = (cia.ncch?.productCode ?: "").toByteArray(Charsets.US_ASCII).copyOf(0x10)
        out.write(product)
        out.write(ByteArray(0x10))
        out.write(le32(random.nextInt().toLong() and 0xFFFFFFFFL))
        out.write(ByteArray(0x2C))
        return out.toByteArray()
    }

    private fun ensureDatabases() {
        if (layout.titleDb.isFile) return
        layout.dbsPath.mkdirs()
        val template = GZIPInputStream(context.assets.open("title.db.gz.bin")).use { it.readBytes() }
        log("criando title.db e import.db (${template.size} bytes)")
        writeDatabase(layout.titleDb, template, 2, "/dbs/title.db")
        writeDatabase(layout.importDb, template, 3, "/dbs/import.db")
    }

    private fun writeDatabase(target: File, template: ByteArray, counter: Long, path3ds: String) {
        val data = template.copyOf()
        val cmac = keys.cmac(
            Crypto.sha256("CTR-9DB0".toByteArray(Charsets.US_ASCII) + le32(counter) + data.copyOfRange(0x100, 0x200)),
        )
        System.arraycopy(cmac, 0, data, 0, 0x10)
        writeEncrypted(target, data, path3ds)
    }

    companion object {
        const val CHUNK = 0x200000

        fun formatSize(bytes: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unit = 0
            while (value >= 1024 && unit < units.lastIndex) {
                value /= 1024
                unit++
            }
            return if (unit == 0) "${bytes} B" else String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
        }
    }
}

/** cifinish.bin version 3, read and written exactly like custom-install. */
object Cifinish {
    private const val MAGIC = "CIFINISH"
    private const val VERSION = 3

    fun load(file: File): MutableMap<Long, ByteArray?> {
        val result = LinkedHashMap<Long, ByteArray?>()
        if (!file.isFile) return result
        val data = file.readBytes()
        if (data.size < 0x10 || String(data, 0, 8, Charsets.US_ASCII) != MAGIC) {
            throw InstallError("${file.name} corrompido. Apague o arquivo e tente de novo.")
        }
        val version = data.u32le(0x8).toInt()
        if (version != VERSION) throw InstallError("versão desconhecida de cifinish.bin: $version")
        val count = data.u32le(0xC).toInt()
        var offset = 0x10
        for (i in 0 until count) {
            if (offset + 0x20 > data.size) throw InstallError("cifinish.bin truncado")
            val magic = String(data, offset, 6, Charsets.US_ASCII)
            if (magic == "TITLE\u0000") {
                val hasSeed = data[offset + 6].toInt() != 0
                val titleId = data.u64le(offset + 8)
                val seed = data.copyOfRange(offset + 0x10, offset + 0x20)
                result[titleId] = if (hasSeed) seed else null
            }
            offset += 0x20
        }
        return result
    }

    fun save(file: File, data: Map<Long, ByteArray?>) {
        val entries = data.entries.sortedWith { a, b -> java.lang.Long.compareUnsigned(a.key, b.key) }
        val out = java.io.ByteArrayOutputStream()
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(le32(VERSION.toLong()))
        out.write(le32(entries.size.toLong()))
        for (entry in entries) {
            out.write("TITLE\u0000".toByteArray(Charsets.US_ASCII))
            out.write(if (entry.value != null) 1 else 0)
            out.write(0)
            out.write(le64(entry.key))
            out.write(entry.value ?: ByteArray(0x10))
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(out.toByteArray())
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            file.writeBytes(out.toByteArray())
            temp.delete()
        }
    }
}
