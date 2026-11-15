package dev.oni.dreaminstaller.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected pixels come from pyctr's tiled SMDH decoder for the same bytes. */
class SmdhIconTest {

    @Test
    fun decodesTiledRgb565() {
        val data = javaClass.classLoader!!.getResourceAsStream("smdh_icon.bin")!!.readBytes()
        val pixels = decodeSmdhIcon(data, 48, 48)!!
        val expected = longArrayOf(
            0xFF397D08, 0xFF29C218, 0xFF080CCD, 0xFF08007B, 0xFF7BF241, 0xFFEE0C31,
            0xFFF6484A, 0xFF52BA08, 0xFFFFEA73, 0xFFAC5D8B, 0xFF206952, 0xFF52F631,
            0xFF8BBA62, 0xFFC504D5, 0xFFB440D5, 0xFF73FF08, 0xFFBD8518, 0xFF10DA8B,
            0xFFC5558B, 0xFF185DE6, 0xFF183010, 0xFFBD3C7B, 0xFF73F6E6, 0xFF009194,
            0xFFD5BE18, 0xFF8B3462, 0xFF7BD608, 0xFF521094, 0xFFE6F639, 0xFF9C6500,
            0xFFDE7900, 0xFF41CAD5, 0xFF18FFD5, 0xFF7B91B4, 0xFFD5AA94, 0xFF0091FF,
            0xFFCDEAEE, 0xFFAC24B4, 0xFF411410, 0xFFAC2429, 0xFFEE287B, 0xFF83A1EE,
            0xFF106120, 0xFF9C8D29, 0xFFD53839, 0xFF837183, 0xFF18EAEE, 0xFFEE1808,
            0xFF29408B, 0xFFB4A594, 0xFFF6F68B, 0xFF4A9D6A, 0xFF20814A, 0xFFAC4010,
            0xFF20E2DE, 0xFF527DF6, 0xFF416939, 0xFF5281C5, 0xFF18E200, 0xFF4A6562,
            0xFF6AB262, 0xFFCD71C5, 0xFF529DDE, 0xFF9CBA10, 0xFFA40420, 0xFF62AEA4,
            0xFFE6A17B, 0xFF94C64A, 0xFF9475F6, 0xFFD5185A, 0xFF8B1400, 0xFF1044EE,
            0xFFCD18F6, 0xFFFF9120, 0xFFD5489C, 0xFF102883, 0xFF5A816A, 0xFF4A694A,
            0xFF3110D5, 0xFFAC7DD5, 0xFFEE5D20, 0xFF83CACD, 0xFFA4D2AC, 0xFF5ACE4A,
            0xFF316D08, 0xFF29BA52, 0xFF83DEB4, 0xFF200439, 0xFFBD99B4, 0xFFB440CD,
            0xFFF66D08, 0xFF941831, 0xFFEE08B4, 0xFF62D6BD, 0xFF7B2818, 0xFF9C7900,
        )
        for (i in expected.indices) {
            assertEquals("pixel $i", expected[i].toInt(), pixels[i])
        }
    }

    @Test
    fun rejectsShortData() {
        assertEquals(null, decodeSmdhIcon(ByteArray(10), 48, 48))
    }
}
