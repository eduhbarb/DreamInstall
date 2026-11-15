package dev.oni.dreaminstall.core

import java.io.File
import java.io.FileOutputStream

/**
 * Writes a metadata file directly on the SD card, flushes it to disk and reads it back.
 *
 * Some Android/FUSE combinations can leave a correctly sized but zero filled file when a small
 * file is written through a temporary file and renamed. The title database (written directly
 * with fsync) does not suffer from it, so every small file uses the same path here. If the read
 * back does not match, the file is deleted and written once more.
 */
fun File.writeVerified(bytes: ByteArray): Boolean {
    val dir = parentFile ?: return false
    if (!dir.isDirectory && !dir.mkdirs()) return false

    fun attempt(): Boolean = try {
        FileOutputStream(this).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
        length() == bytes.size.toLong() && readBytes().contentEquals(bytes)
    } catch (_: Exception) {
        false
    }

    if (attempt()) return true
    delete()
    return attempt()
}
