package dev.oni.dreaminstaller

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.oni.dreaminstaller.core.ByteSource
import dev.oni.dreaminstaller.core.Cia
import dev.oni.dreaminstaller.core.ConsoleKeys
import dev.oni.dreaminstaller.core.Installer
import dev.oni.dreaminstaller.core.Save3ds
import dev.oni.dreaminstaller.core.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Runs the real installer against the synthetic fixtures, using the same boot9/movable.sed/CIA
 * that custom-install used on a PC. The tree it produces is copied to external files so the
 * host can compare it with the upstream output.
 */
@RunWith(AndroidJUnit4::class)
class InstallE2ETest {

    @Test
    fun installsSyntheticTitles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val assets = instrumentation.context.assets
        val work = File(context.cacheDir, "e2e").apply {
            deleteRecursively()
            mkdirs()
        }
        for (name in FIXTURES) {
            assets.open(name).use { input -> File(work, name).outputStream().use { input.copyTo(it) } }
        }

        val boot9 = File(work, "boot9.bin")
        val movable = File(work, "movable.sed")
        val seeddb = File(work, "seeddb.bin")
        val keys = ConsoleKeys.load(boot9, movable, seeddb)

        val root = File(work, "sd").apply { mkdirs() }
        File(root, "Nintendo 3DS/${keys.id0Hex}/" + "0".repeat(32)).mkdirs()
        val layout = Storage.resolveLayout(root, keys)

        val titles = listOf("game.cia", "seed.cia").map { name ->
            Cia.open(ByteSource.fromFile(File(work, name)), keys)
        }

        val logs = mutableListOf<String>()
        var states = 0
        val installer = Installer(
            context = context,
            keys = keys,
            keyFiles = Save3ds.KeysFiles(boot9, movable),
            layout = layout,
            verifyHashes = true,
            overwriteSaves = false,
            copyFinalizeProgram = true,
            installFinalizerTitle = true,
            log = { logs.add(it) },
            onTitleState = { _, _, _ -> states++ },
            onBytes = { _, _, _ -> },
            isCancelled = { false },
        )

        val result = try {
            installer.run(titles)
        } finally {
            titles.forEach { it.close() }
        }

        val report = buildString {
            appendLine("done=${result.done}")
            appendLine("failed=${result.failed}")
            appendLine("states=$states")
            appendLine("finalizer3dsx=${result.finalizer3dsxCopied} cia=${result.finalizerCiaCopied}")
            appendLine("titles=${result.usedTitleCount}")
            appendLine("--- log ---")
            logs.forEach { appendLine(it) }
        }

        val external = File(context.externalMediaDirs.first(), "e2e-result")
        external.deleteRecursively()
        external.mkdirs()
        root.copyRecursively(external, overwrite = true)
        File(external, "report.txt").writeText(report)
        File(external, "manifest.txt").writeText(manifest(root))

        assertTrue(report, result.failed.isEmpty())
        assertEquals(2, result.done.size)
        assertTrue(layout.titleDb.length() > 0)
        assertTrue(File(layout.root, "cifinish.bin").exists())
        assertTrue(result.finalizer3dsxCopied && result.finalizerCiaCopied)
        assertTrue(result.finalizerTitleInstalled)
        assertTrue(File(layout.root, "3ds/DreamInstallFinalize.3dsx").isFile)
        assertTrue(File(layout.root, "cias/DreamInstallFinalize.cia").isFile)
        assertTrue(File(layout.titlePath, "00040000/0ff4d100").isDirectory)
        assertTrue(File(context.cacheDir, "title-db/000400000ff4d100").isFile)
        val names = File(layout.root, "dreamfinalize-names.txt").readText()
        assertTrue(names.contains("000400000ff40a00"))
        assertTrue(names.contains("000400000ff40b00"))
        assertTrue(names.contains("Dream Test"))
        assertTrue(names.contains("Dream Seed"))
    }

    private fun manifest(root: File): String {
        val lines = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(root).invariantSeparatorsPath
            if (rel.startsWith("ci-backup/") || rel.endsWith(".keep") || rel.contains("/dbs/")) return@forEach
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            lines.add("$rel ${digest.joinToString("") { "%02x".format(it) }}")
        }
        return lines.sorted().joinToString("\n", postfix = "\n")
    }

    companion object {
        private val FIXTURES = listOf("boot9.bin", "movable.sed", "seeddb.bin", "game.cia", "seed.cia")
    }
}
