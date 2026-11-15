package dev.oni.dreaminstall.core

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class TitleKind { Game, Update, Dlc, Other }

fun kindOf(titleId: String): TitleKind = when {
    titleId.startsWith("00040000") -> TitleKind.Game
    titleId.startsWith("0004000e") -> TitleKind.Update
    titleId.startsWith("0004008c") -> TitleKind.Dlc
    else -> TitleKind.Other
}

data class QueueItem(
    val uri: String,
    val fileName: String,
    val titleId: String,
    val titleName: String?,
    val kind: TitleKind,
    val size: Long,
    val installSize: Long,
    val usesSeed: Boolean,
    val programId: Long,
    val seedPresent: Boolean,
    val icon: ByteArray?,
    val state: TitleState = TitleState.Waiting,
    val message: String? = null,
) {
    val label: String get() = titleName?.takeIf { it.isNotBlank() } ?: fileName
    val needsSeed: Boolean get() = usesSeed && !seedPresent

    fun toJson(): JSONObject = JSONObject().apply {
        put("uri", uri)
        put("fileName", fileName)
        put("titleId", titleId)
        put("titleName", titleName ?: "")
        put("kind", kind.ordinal)
        put("size", size)
        put("installSize", installSize)
        put("usesSeed", usesSeed)
        put("programId", programId)
        put("seedPresent", seedPresent)
        put("icon", icon?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): QueueItem {
            val iconText = json.optString("icon")
            val icon = if (iconText.isNotEmpty()) {
                runCatching { android.util.Base64.decode(iconText, android.util.Base64.NO_WRAP) }.getOrNull()
            } else {
                null
            }
            return QueueItem(
                uri = json.getString("uri"),
                fileName = json.getString("fileName"),
                titleId = json.getString("titleId"),
                titleName = json.optString("titleName").takeIf { it.isNotEmpty() },
                kind = TitleKind.entries.getOrElse(json.optInt("kind")) { TitleKind.Other },
                size = json.optLong("size"),
                installSize = json.optLong("installSize"),
                usesSeed = json.optBoolean("usesSeed"),
                programId = json.optLong("programId"),
                seedPresent = json.optBoolean("seedPresent"),
                icon = icon,
            )
        }

        fun listToJson(items: List<QueueItem>): String {
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(text: String?): List<QueueItem> {
            if (text.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(text)
                (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }
}

class InstallRequest(
    val keys: ConsoleKeys,
    val keyFiles: Save3ds.KeysFiles,
    val layout: SdLayout,
    val titles: List<QueueItem>,
    val verifyHashes: Boolean,
    val overwriteSaves: Boolean,
    val copyFinalize: Boolean,
    val installFinalizerTitle: Boolean,
)

data class TitleProgress(
    val titleId: String,
    val name: String,
    val state: TitleState = TitleState.Waiting,
    val written: Long = 0,
    val total: Long = 0,
    val message: String? = null,
) {
    val fraction: Float
        get() = when {
            state == TitleState.Done -> 1f
            total <= 0L -> 0f
            else -> (written.toFloat() / total).coerceIn(0f, 1f)
        }
}

data class InstallState(
    val running: Boolean = false,
    val cancelling: Boolean = false,
    val titles: List<TitleProgress> = emptyList(),
    val current: Int = -1,
    val log: List<String> = emptyList(),
    val speed: Long = 0,
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val result: InstallResult? = null,
    val error: String? = null,
) {
    val overall: Float
        get() {
            if (titles.isEmpty()) return 0f
            var sum = 0f
            titles.forEachIndexed { index, title ->
                sum += if (index == current) title.fraction else if (title.state == TitleState.Done) 1f else 0f
            }
            return (sum / titles.size).coerceIn(0f, 1f)
        }

    val doneCount: Int get() = titles.count { it.state == TitleState.Done }
}

object InstallBus {
    val state = kotlinx.coroutines.flow.MutableStateFlow(InstallState())
    val cancel = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    var request: InstallRequest? = null

    fun reset(request: InstallRequest) {
        cancel.set(false)
        this.request = request
        state.value = InstallState(
            running = true,
            titles = request.titles.map { TitleProgress(it.titleId, it.label) },
            startedAt = System.currentTimeMillis(),
        )
    }
}

fun formatSize(bytes: Long): String = Installer.formatSize(bytes)

fun sizeOfCia(file: File): Long = file.length()
