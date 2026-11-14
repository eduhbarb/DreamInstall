package dev.oni.dreaminstaller.ui.theme

/**
 * DreamFTP's palette: near-black and purple. A different accent recolours every surface,
 * keeping each colour as bright as the default one, exactly like the 3DS app does.
 */
data class Palette(
    val accent: Int,
    val onAccent: Int,
    val highlight: Int,
    val soft: Int,
    val bg: Int,
    val blob: Int,
    val card: Int,
    val track: Int,
    val selected: Int,
    val border: Int,
    val text: Int,
    val dim: Int,
)

const val DEFAULT_ACCENT = 0xFFA855F7.toInt()

val ACCENTS: List<Pair<String, Int>> = listOf(
    "Purple" to DEFAULT_ACCENT,
    "Indigo" to 0xFF5865F2.toInt(),
    "Blue" to 0xFF3D8BFD.toInt(),
    "Teal" to 0xFF20908A.toInt(),
    "Green" to 0xFF2B9349.toInt(),
    "Amber" to 0xFFB6721C.toInt(),
    "Orange" to 0xFFCE622D.toInt(),
    "Pink" to 0xFFCD53A6.toInt(),
)

val DREAM = Palette(
    accent = DEFAULT_ACCENT,
    onAccent = 0xFF14061F.toInt(),
    highlight = 0xFFCBB0F5.toInt(),
    soft = 0xFFE6DCF5.toInt(),
    bg = 0xFF08060D.toInt(),
    blob = 0xFF160D24.toInt(),
    card = 0xFF150E21.toInt(),
    track = 0xFF1E1530.toInt(),
    selected = 0xFF2C1A45.toInt(),
    border = 0xFF2E2342.toInt(),
    text = 0xFFF4EEFC.toInt(),
    dim = 0xFF9E93B0.toInt(),
)

private data class Hsl(val h: Double, val s: Double, val l: Double)

private fun toHsl(color: Int): Hsl {
    val r = ((color shr 16) and 0xFF) / 255.0
    val g = ((color shr 8) and 0xFF) / 255.0
    val b = (color and 0xFF) / 255.0
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2.0
    if (max == min) return Hsl(0.0, 0.0, l)
    val d = max - min
    val s = if (l > 0.5) d / (2.0 - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6.0 else 0.0)
        g -> (b - r) / d + 2.0
        else -> (r - g) / d + 4.0
    }
    return Hsl(h * 60.0, s, l)
}

private fun fromHsl(h: Double, s: Double, l: Double): Int {
    val hue = ((h % 360.0) + 360.0) % 360.0
    val a = s * minOf(l, 1.0 - l)
    fun channel(n: Double): Int {
        val k = (n + hue / 30.0) % 12.0
        val t = minOf(k - 3.0, 9.0 - k).coerceIn(-1.0, 1.0)
        val v = l - a * t
        return (v * 255.0).roundToIntSafe().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(0.0) shl 16) or (channel(8.0) shl 8) or channel(4.0)
}

private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()

private fun luminance(color: Int): Double {
    fun lin(v: Int): Double {
        val c = (v and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * lin(color shr 16) + 0.7152 * lin(color shr 8) + 0.0722 * lin(color)
}

private fun withLuminance(h: Double, s: Double, target: Double): Int {
    var lo = 0.0
    var hi = 1.0
    repeat(40) {
        val mid = (lo + hi) / 2.0
        if (luminance(fromHsl(h, s, mid)) < target) lo = mid else hi = mid
    }
    return fromHsl(h, s, (lo + hi) / 2.0)
}

fun paletteFor(accent: Int): Palette {
    val base = DREAM
    if (accent == DEFAULT_ACCENT) return base
    val (h, sat, _) = toHsl(accent)
    val baseSat = toHsl(DEFAULT_ACCENT).s
    fun follow(color: Int): Int {
        val s0 = toHsl(color).s
        return withLuminance(h, s0 * minOf(sat / baseSat, 1.0), luminance(color))
    }
    return Palette(
        accent = withLuminance(h, sat, luminance(base.accent)),
        onAccent = base.onAccent,
        highlight = follow(base.highlight),
        soft = follow(base.soft),
        bg = follow(base.bg),
        blob = follow(base.blob),
        card = follow(base.card),
        track = follow(base.track),
        selected = follow(base.selected),
        border = follow(base.border),
        text = follow(base.text),
        dim = follow(base.dim),
    )
}
