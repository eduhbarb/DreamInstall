package dev.oni.dreaminstall.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val tag: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
)

class UpdateError(message: String) : Exception(message)

/** Compares dotted versions. A missing segment counts as zero, so 1.4 and 1.4.0 are equal. */
fun compareVersions(left: String, right: String): Int {
    val a = normalizeVersion(left).split('.')
    val b = normalizeVersion(right).split('.')
    val size = maxOf(a.size, b.size)
    for (index in 0 until size) {
        val x = a.getOrNull(index)?.toIntOrNull() ?: 0
        val y = b.getOrNull(index)?.toIntOrNull() ?: 0
        if (x != y) return if (x < y) -1 else 1
    }
    return 0
}

fun normalizeVersion(version: String): String = version.trim().removePrefix("v").removePrefix("V")

fun isNewer(latest: String, current: String): Boolean = compareVersions(latest, current) > 0

/** Reads the latest GitHub release. This is the only network call the app makes. */
object UpdateChecker {

    const val REPO = "eduhbarb/DreamInstall"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    fun fetch(): UpdateInfo {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "DreamInstall")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw UpdateError("HTTP $code")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            if (tag.isBlank()) throw UpdateError("release sem tag")
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apk = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }
            return UpdateInfo(
                version = normalizeVersion(tag),
                tag = tag,
                title = json.optString("name").ifBlank { tag },
                notes = json.optString("body"),
                pageUrl = json.optString("html_url").ifBlank { "https://github.com/$REPO/releases" },
                apkUrl = apk,
            )
        } finally {
            connection.disconnect()
        }
    }
}
