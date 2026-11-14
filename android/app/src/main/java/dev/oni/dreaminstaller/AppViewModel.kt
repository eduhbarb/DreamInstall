package dev.oni.dreaminstaller

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.oni.dreaminstaller.core.ByteSource
import dev.oni.dreaminstaller.core.Cia
import dev.oni.dreaminstaller.core.ConsoleKeys
import dev.oni.dreaminstaller.core.InstallBus
import dev.oni.dreaminstaller.core.InstallRequest
import dev.oni.dreaminstaller.core.KeyError
import dev.oni.dreaminstaller.core.QueueItem
import dev.oni.dreaminstaller.core.Save3ds
import dev.oni.dreaminstaller.core.SdLayout
import dev.oni.dreaminstaller.core.SdVolume
import dev.oni.dreaminstaller.core.Storage
import dev.oni.dreaminstaller.core.TitleKind
import dev.oni.dreaminstaller.core.TitleState
import dev.oni.dreaminstaller.core.kindOf
import dev.oni.dreaminstaller.service.InstallService
import dev.oni.dreaminstaller.ui.theme.DEFAULT_ACCENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class KeyStatus(
    val boot9: Boolean = false,
    val movable: Boolean = false,
    val seeddb: Boolean = false,
    val valid: Boolean = false,
    val error: String? = null,
)

enum class KeyKind { Boot9, Movable, SeedDb }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("dreaminstaller", Context.MODE_PRIVATE)
    private val keyDir = File(app.filesDir, "keys").apply { mkdirs() }

    val accent = MutableStateFlow(prefs.getInt("accent", DEFAULT_ACCENT))
    val verifyHashes = MutableStateFlow(prefs.getBoolean("verify", true))
    val overwriteSaves = MutableStateFlow(prefs.getBoolean("overwrite", false))
    val copyFinalize = MutableStateFlow(prefs.getBoolean("finalize", true))

    val keys = MutableStateFlow<ConsoleKeys?>(null)
    val keyFiles = MutableStateFlow<Save3ds.KeysFiles?>(null)
    val keyStatus = MutableStateFlow(KeyStatus())

    val volumes = MutableStateFlow<List<SdVolume>>(emptyList())
    val selectedRoot = MutableStateFlow<File?>(null)
    val layout = MutableStateFlow<SdLayout?>(null)
    val layoutError = MutableStateFlow<String?>(null)

    val queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    val install = InstallBus.state

    init {
        loadKeys()
        queue.value = QueueItem.listFromJson(prefs.getString("queue", null))
        refreshVolumes()
        viewModelScope.launch {
            install.collect { state ->
                if (state.running) {
                    val current = queue.value
                    val titles = state.titles
                    if (titles.size == current.size) {
                        queue.value = current.mapIndexed { index, item ->
                            val progress = titles[index]
                            item.copy(state = progress.state, message = progress.message)
                        }
                    }
                } else if (state.result != null) {
                    val result = state.result
                    val done = result.done.toSet()
                    val failed = result.failed
                    queue.value = queue.value.map { item ->
                        when {
                            item.titleName in done || item.label in done -> item.copy(state = TitleState.Done, message = null)
                            failed.containsKey(item.label) -> item.copy(state = TitleState.Failed, message = failed[item.label])
                            else -> item
                        }
                    }
                    persistQueue()
                }
            }
        }
    }

    fun setAccent(value: Int) {
        accent.value = value
        prefs.edit().putInt("accent", value).apply()
    }

    fun setVerify(value: Boolean) {
        verifyHashes.value = value
        prefs.edit().putBoolean("verify", value).apply()
    }

    fun setOverwriteSaves(value: Boolean) {
        overwriteSaves.value = value
        prefs.edit().putBoolean("overwrite", value).apply()
    }

    fun setCopyFinalize(value: Boolean) {
        copyFinalize.value = value
        prefs.edit().putBoolean("finalize", value).apply()
    }

    fun show(text: String?) {
        message.value = text
    }

    fun consumeMessage() {
        message.value = null
    }

    fun openAllFilesSettings() {
        val app = getApplication<Application>()
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${app.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { app.startActivity(intent) }.onFailure {
            runCatching { app.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }
        }
    }

    fun refreshVolumes() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val found = withContext(Dispatchers.IO) { Storage.volumes(app) }
            volumes.value = found
            val savedPath = prefs.getString("sd", null)
            val saved = found.firstOrNull { it.root.absolutePath == savedPath }
            val chosen = saved ?: found.firstOrNull { it.has3ds } ?: found.firstOrNull()
            if (chosen != null) {
                selectedRoot.value = chosen.root
                prefs.edit().putString("sd", chosen.root.absolutePath).apply()
            }
            updateLayout()
            if (chosen != null) scanCardForKeys(overwrite = false)
        }
    }

    fun selectRoot(root: File) {
        selectedRoot.value = root
        prefs.edit().putString("sd", root.absolutePath).apply()
        updateLayout()
        scanCardForKeys(overwrite = false)
    }

    /**
     * Copies boot9.bin, movable.sed and seeddb.bin from the SD card when they sit in the
     * places custom-install's PC GUI checks (SD/gm9/out, SD/boot9strap, SD root).
     */
    fun scanCardForKeys(overwrite: Boolean) {
        val root = selectedRoot.value ?: return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val found = withContext(Dispatchers.IO) { Storage.findConsoleFiles(root) }
            if (!found.any) {
                if (overwrite) show(app.getString(R.string.keys_not_found))
                return@launch
            }
            val imported = withContext(Dispatchers.IO) {
                val list = mutableListOf<String>()
                fun tryCopy(source: File?, name: String) {
                    if (source == null) return
                    val target = File(keyDir, name)
                    if (!overwrite && target.isFile) return
                    val bytes = runCatching { source.readBytes() }.getOrNull() ?: return
                    val valid = when (name) {
                        "boot9.bin" -> bytes.size == 0x10000
                        "movable.sed" -> bytes.size == 0x120 || bytes.size == 0x140
                        else -> bytes.size >= 0x10
                    }
                    if (!valid) return
                    if (target.isFile && target.readBytes().contentEquals(bytes)) return
                    target.writeBytes(bytes)
                    list.add(name)
                }
                tryCopy(found.boot9, "boot9.bin")
                tryCopy(found.movable, "movable.sed")
                tryCopy(found.seeddb, "seeddb.bin")
                list
            }
            if (imported.isNotEmpty()) {
                loadKeys()
                show(app.getString(R.string.keys_found, imported.joinToString(", ")))
            } else if (overwrite) {
                show(app.getString(R.string.keys_not_found))
            }
        }
    }

    fun setKey(kind: KeyKind, uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val bytes = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw KeyError("não foi possível abrir o arquivo")
                }
                val name = when (kind) {
                    KeyKind.Boot9 -> "boot9.bin"
                    KeyKind.Movable -> "movable.sed"
                    KeyKind.SeedDb -> "seeddb.bin"
                }
                when (kind) {
                    KeyKind.Boot9 -> if (bytes.size != 0x10000) {
                        throw KeyError("boot9.bin precisa ter 0x10000 bytes (tem 0x${bytes.size.toString(16)})")
                    }
                    KeyKind.Movable -> if (bytes.size != 0x120 && bytes.size != 0x140) {
                        throw KeyError("movable.sed precisa ter 0x120 bytes (tem 0x${bytes.size.toString(16)})")
                    }
                    KeyKind.SeedDb -> if (bytes.isEmpty()) throw KeyError("seeddb.bin vazio")
                }
                withContext(Dispatchers.IO) { File(keyDir, name).writeBytes(bytes) }
                loadKeys()
            } catch (e: Exception) {
                show(e.message ?: app.getString(R.string.error_invalid_key))
            }
        }
    }

    fun clearKeys() {
        keyDir.listFiles()?.forEach { it.delete() }
        keys.value = null
        keyFiles.value = null
        keyStatus.value = KeyStatus()
        updateLayout()
        refreshQueueSeeds()
    }

    private fun loadKeys() {
        val boot9 = File(keyDir, "boot9.bin")
        val movable = File(keyDir, "movable.sed")
        val seeddb = File(keyDir, "seeddb.bin")
        val status = KeyStatus(boot9 = boot9.isFile, movable = movable.isFile, seeddb = seeddb.isFile)
        if (boot9.isFile && movable.isFile) {
            try {
                val loaded = ConsoleKeys.load(boot9, movable, seeddb.takeIf { it.isFile })
                keys.value = loaded
                keyFiles.value = Save3ds.KeysFiles(boot9, movable)
                keyStatus.value = status.copy(valid = true)
            } catch (e: Exception) {
                keys.value = null
                keyFiles.value = null
                keyStatus.value = status.copy(error = e.message)
            }
        } else {
            keys.value = null
            keyFiles.value = null
            keyStatus.value = status
        }
        updateLayout()
        refreshQueueSeeds()
    }

    private fun refreshQueueSeeds() {
        val loaded = keys.value
        queue.value = queue.value.map { item ->
            val present = loaded?.seedFor(item.programId) != null
            item.copy(seedPresent = present)
        }
        persistQueue()
    }

    private fun updateLayout() {
        val loaded = keys.value
        val root = selectedRoot.value
        layout.value = null
        layoutError.value = null
        if (loaded == null || root == null) return
        try {
            layout.value = Storage.resolveLayout(root, loaded)
        } catch (e: Exception) {
            layoutError.value = e.message
        }
    }

    fun addCias(uris: List<Uri>) {
        val loaded = keys.value ?: run {
            show(getApplication<Application>().getString(R.string.error_keys))
            return
        }
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            val app = getApplication<Application>()
            var added = 0
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    runCatching {
                        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    var source: ByteSource? = null
                    try {
                        source = ByteSource.fromUri(app, uri)
                        val cia = Cia.open(source, loaded)
                        try {
                            val titleId = cia.titleId
                            if (queue.value.none { it.titleId == titleId }) {
                                val item = QueueItem(
                                    uri = uri.toString(),
                                    fileName = cia.fileName,
                                    titleId = titleId,
                                    titleName = cia.titleName,
                                    kind = kindOf(titleId),
                                    size = source.size,
                                    installSize = cia.installSize(),
                                    usesSeed = cia.usesSeed,
                                    programId = cia.programId,
                                    seedPresent = !cia.usesSeed || loaded.seedFor(cia.programId) != null,
                                    icon = cia.iconRgb565,
                                )
                                queue.update { it + item }
                                added++
                            }
                        } finally {
                            cia.close()
                        }
                    } catch (e: Exception) {
                        show(e.message ?: e.javaClass.simpleName)
                    } finally {
                        if (source != null) runCatching { source.close() }
                    }
                }
            }
            persistQueue()
            busy.value = false
            if (added == 0 && uris.isNotEmpty()) {
                // nothing new; keep any error message already shown
            }
        }
    }

    fun removeItem(item: QueueItem) {
        queue.update { it.filterNot { q -> q.uri == item.uri } }
        persistQueue()
    }

    fun clearQueue() {
        queue.value = emptyList()
        persistQueue()
    }

    private fun persistQueue() {
        prefs.edit().putString("queue", QueueItem.listToJson(queue.value)).apply()
    }

    fun startInstall() {
        val app = getApplication<Application>()
        val loaded = keys.value ?: return show(app.getString(R.string.error_keys))
        val files = keyFiles.value ?: return show(app.getString(R.string.error_keys))
        val root = selectedRoot.value ?: return show(app.getString(R.string.error_sd))
        val items = queue.value
        if (items.isEmpty()) return show(app.getString(R.string.queue_empty))
        if (items.any { it.needsSeed }) {
            show(app.getString(R.string.error_seeddb))
        }
        val layout = try {
            Storage.resolveLayout(root, loaded)
        } catch (e: Exception) {
            return show(e.message)
        }
        InstallBus.reset(
            InstallRequest(
                keys = loaded,
                keyFiles = files,
                layout = layout,
                titles = items,
                verifyHashes = verifyHashes.value,
                overwriteSaves = overwriteSaves.value,
                copyFinalize = copyFinalize.value,
            ),
        )
        InstallService.start(app)
    }

    fun cancelInstall() {
        InstallService.cancel(getApplication())
    }

    fun dismissResult() {
        install.update { it.copy(result = null, error = null) }
    }

    fun updateSeedState() {
        refreshQueueSeeds()
    }
}
