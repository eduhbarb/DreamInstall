package dev.oni.dreaminstaller.core

/**
 * Decodes a SMDH icon. The pixels are RGB565 little endian but stored in 8x8 tiles, in the
 * order FBI and pyctr use, not in a linear scan.
 */
fun decodeSmdhIcon(data: ByteArray, width: Int, height: Int): IntArray? {
    val needed = width * height * 2
    if (width < 8 || height < 8 || data.size < needed) return null

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val tile = ((y shr 3) * (width shr 3) + (x shr 3)) shl 6
            val inner = (x and 1) or ((y and 1) shl 1) or ((x and 2) shl 1) or
                ((y and 2) shl 2) or ((x and 4) shl 2) or ((y and 4) shl 3)
            val offset = (tile + inner) * 2
            val value = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            val r = ((value shr 11) and 0x1F) * 255 / 31
            val g = ((value shr 5) and 0x3F) * 255 / 63
            val b = (value and 0x1F) * 255 / 31
            pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return pixels
}
