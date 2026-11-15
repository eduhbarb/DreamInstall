package dev.oni.dreaminstall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoragePickTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun volume(name: String, removable: Boolean, has3ds: Boolean, root: File = File("/storage/$name")) =
        SdVolume(root = root, label = name, removable = removable, freeBytes = 0L, has3ds = has3ds)

    @Test
    fun ignoresInternalStorage() {
        val internal = volume("emulated", removable = false, has3ds = true)
        assertNull(chooseVolume(listOf(internal), savedPath = null, manual = false, id0 = null))
    }

    @Test
    fun picksTheRemovableCardWithTheConsoleFolder() {
        val internal = volume("emulated", removable = false, has3ds = true)
        val empty = volume("USB-1", removable = true, has3ds = false)
        val card = volume("USB-2", removable = true, has3ds = true)
        assertEquals(card, chooseVolume(listOf(internal, empty, card), null, false, null))
    }

    @Test
    fun prefersTheCardThatMatchesTheConsoleId0() {
        val first = volume("USB-1", removable = true, has3ds = true)
        val second = volume("USB-2", removable = true, has3ds = true)
        val id0 = "0123456789abcdef0123456789abcdef"
        File(second.root, "Nintendo 3DS/$id0").mkdirs()
        try {
            assertEquals(second, chooseVolume(listOf(first, second), null, false, id0))
        } finally {
            second.root.deleteRecursively()
        }
    }

    @Test
    fun keepsAManualChoiceWhileItIsMounted() {
        val card = volume("USB-1", removable = true, has3ds = true)
        assertEquals(card, chooseVolume(listOf(card), card.root.absolutePath, true, null))
    }

    @Test
    fun fallsBackToTheCardWhenTheManualChoiceIsGone() {
        val card = volume("USB-2", removable = true, has3ds = true)
        assertEquals(card, chooseVolume(listOf(card), "/storage/USB-1", true, null))
    }
}
