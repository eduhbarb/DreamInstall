package dev.oni.dreaminstaller.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Parses the synthetic CIAs generated for the on-device end-to-end run. */
class SyntheticCiaTest {

    private fun resource(name: String): File {
        val bytes = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()
        val file = File.createTempFile("fixture-", ".bin")
        file.deleteOnExit()
        file.writeBytes(bytes)
        return file
    }

    private fun keys(): ConsoleKeys = ConsoleKeys.of(
        javaClass.classLoader!!.getResourceAsStream("boot9.bin")!!.readBytes(),
        javaClass.classLoader!!.getResourceAsStream("movable.sed")!!.readBytes(),
        javaClass.classLoader!!.getResourceAsStream("seeddb.bin")!!.readBytes(),
    )

    @Test
    fun gameMetadata() {
        val keys = keys()
        Cia.open(ByteSource.fromFile(resource("game.cia")), keys).use { cia ->
            assertEquals("000400000ff40a00", cia.titleId)
            assertEquals("CTR-TEST", cia.productCode)
            assertEquals("Dream Test", cia.titleName)
            assertNotNull(cia.iconRgb565)
            assertFalse(cia.usesSeed)
            assertEquals(0x40000L, cia.installSize())
            assertEquals(0x2000L, cia.saveSize)
            assertEquals(1, cia.contents.size)
            assertEquals(0, cia.contents[0].index)
        }
    }

    @Test
    fun seedMetadata() {
        val keys = keys()
        Cia.open(ByteSource.fromFile(resource("seed.cia")), keys).use { cia ->
            assertEquals("000400000ff40b00", cia.titleId)
            assertEquals("CTR-TEST", cia.productCode)
            assertEquals("Dream Seed", cia.titleName)
            assertTrue(cia.usesSeed)
            assertEquals(0x30000L, cia.installSize())
        }
    }
}
