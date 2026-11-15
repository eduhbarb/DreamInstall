package dev.oni.dreaminstaller.core

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.system.Os
import android.util.Log
import java.io.File

data class SdVolume(
    val root: File,
    val label: String,
    val removable: Boolean,
    val freeBytes: Long,
    val has3ds: Boolean,
)

data class SdLayout(
    val root: File,
    val id0: String,
    val id1: String,
) {
    val id1Path: File get() = File(root, "Nintendo 3DS/$id0/$id1")
    val dbsPath: File get() = File(id1Path, "dbs")
    val titleDb: File get() = File(dbsPath, "title.db")
    val importDb: File get() = File(dbsPath, "import.db")
    val titlePath: File get() = File(id1Path, "title")
}

object Storage {

    fun isAllFilesGranted(context: Context): Boolean = Environment.isExternalStorageManager()

    fun volumes(context: Context): List<SdVolume> {
        val manager = context.getSystemService(StorageManager::class.java)
        val result = ArrayList<SdVolume>()
        for (volume in manager.storageVolumes) {
            val dir = runCatching { volume.directory }.getOrNull() ?: continue
            if (volume.state != Environment.MEDIA_MOUNTED) continue
            if (!dir.isDirectory || !dir.canRead()) continue
            val free = runCatching {
                val stat = Os.statvfs(dir.absolutePath)
                stat.f_bavail * stat.f_frsize
            }.getOrDefault(0L)
            result.add(
                SdVolume(
                    root = dir,
                    label = volume.getDescription(context),
                    removable = volume.isRemovable,
                    freeBytes = free,
                    has3ds = File(dir, "Nintendo 3DS").isDirectory,
                ),
            )
        }
        return result.sortedWith(compareByDescending<SdVolume> { it.has3ds }.thenBy { it.label })
    }

    fun freeBytes(path: File): Long = runCatching {
        val stat = Os.statvfs(path.absolutePath)
        stat.f_bavail * stat.f_frsize
    }.getOrDefault(0L)

    fun resolveLayout(root: File, keys: ConsoleKeys): SdLayout {
        val id0 = keys.id0Hex
        val consoleDir = File(root, "Nintendo 3DS/$id0")
        if (!consoleDir.isDirectory) {
            throw InstallError("id0 $id0 não existe em ${root.absolutePath}. Use este cartão no console pelo menos uma vez.")
        }
        val id1s = consoleDir.listFiles()
            ?.filter { it.isDirectory && it.name.length == 32 && it.name.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
            ?.map { it.name }
            ?: emptyList()
        if (id1s.isEmpty()) throw InstallError("diretório id1 não encontrado para id0 $id0")
        if (id1s.size > 1) throw InstallError("há ${id1s.size} diretórios id1 para este id0")
        return SdLayout(root, id0, id1s.first())
    }

    fun usedTitleCount(layout: SdLayout): Int {
        var count = 0
        val high = File(layout.titlePath, "00040000")
        high.listFiles()?.forEach { entry ->
            if (entry.isDirectory && entry.name.length == 8) count++
        }
        return count
    }

    fun backupDatabases(layout: SdLayout): File? {
        if (!layout.titleDb.exists() && !layout.importDb.exists()) return null
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val dir = File(layout.root, "ci-backup/$stamp")
        dir.mkdirs()
        if (layout.titleDb.exists()) layout.titleDb.copyTo(File(dir, "title.db"), overwrite = true)
        if (layout.importDb.exists()) layout.importDb.copyTo(File(dir, "import.db"), overwrite = true)
        return dir
    }

    fun copyFinalize(context: Context, layout: SdLayout): FinalizerStatus {
        val threeDsx = syncAsset(
            context,
            "DreamInstallFinalize.3dsx",
            File(layout.root, "3ds/DreamInstallFinalize.3dsx"),
        )
        val cia = syncAsset(
            context,
            "DreamInstallFinalize.cia",
            File(layout.root, "cias/DreamInstallFinalize.cia"),
        )
        return FinalizerStatus(threeDsx, cia)
    }

    /** Copies an asset only when the file is missing or different, with fsync and verification. */
    private fun syncAsset(context: Context, assetName: String, target: File): Boolean {
        return runCatching {
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            if (target.isFile && target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) {
                return false
            }
            if (!target.writeVerified(bytes)) {
                throw InstallError("não foi possível gravar $assetName")
            }
            true
        }.onFailure {
            Log.w("DreamInstaller", "falha ao copiar $assetName", it)
        }.getOrDefault(false)
    }

    /**
     * Looks for the console key dumps where custom-install's PC GUI looks:
     * SD/gm9/out, SD/boot9strap and the SD root.
     */
    fun findConsoleFiles(root: File): FoundKeys {
        fun find(name: String): File? {
            for (dir in listOf(listOf("gm9", "out"), listOf("boot9strap"), emptyList())) {
                val file = resolveCaseInsensitive(root, dir + name)
                if (file != null && file.isFile) return file
            }
            return null
        }
        return FoundKeys(find("boot9.bin"), find("movable.sed"), find("seeddb.bin"))
    }

    private fun resolveCaseInsensitive(root: File, segments: List<String>): File? {
        var current: File = root
        for (segment in segments) {
            val direct = File(current, segment)
            current = if (direct.exists()) {
                direct
            } else {
                current.listFiles()?.firstOrNull { it.name.equals(segment, ignoreCase = true) } ?: return null
            }
        }
        return current
    }

    fun clearTempDirs(layout: SdLayout) {
        layout.root.listFiles()?.forEach { entry ->
            if (entry.isDirectory && entry.name.startsWith("ci-install-temp-")) entry.deleteRecursively()
        }
    }
}

class InstallError(message: String) : Exception(message)

/** Key files found on the SD card, in the same places custom-install checks on a PC. */
data class FoundKeys(
    val boot9: File?,
    val movable: File?,
    val seeddb: File?,
) {
    val any: Boolean get() = boot9 != null || movable != null || seeddb != null
    val names: List<String>
        get() = buildList {
            if (boot9 != null) add("boot9.bin")
            if (movable != null) add("movable.sed")
            if (seeddb != null) add("seeddb.bin")
        }
}

/** Which DreamInstallFinalize files were written to the SD this session. */
data class FinalizerStatus(
    val threeDsx: Boolean,
    val cia: Boolean,
)
