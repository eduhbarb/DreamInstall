package dev.oni.dreaminstall.core

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the bundled save3ds (built from wwylele/save3ds without FUSE) to extract and import
 * the SD title database. The binary lives in nativeLibraryDir so Android lets the app exec it.
 */
class Save3ds(private val context: Context) {

    private val binary: File by lazy {
        File(context.applicationInfo.nativeLibraryDir, "libsave3ds.so").also {
            if (!it.exists()) throw Save3dsError("libsave3ds.so não encontrada em ${it.parent}")
            it.setExecutable(true, false)
        }
    }

    fun extract(layout: SdLayout, keys: KeysFiles, workDir: File): List<String> =
        run(listOf("-x"), layout, keys, workDir)

    fun import(layout: SdLayout, keys: KeysFiles, workDir: File): List<String> =
        run(listOf("-i"), layout, keys, workDir)

    private fun run(mode: List<String>, layout: SdLayout, keys: KeysFiles, workDir: File): List<String> {
        val command = ArrayList<String>()
        command.add(binary.absolutePath)
        command.add("-b")
        command.add(keys.boot9.absolutePath)
        command.add("-m")
        command.add(keys.movable.absolutePath)
        command.add("--sd")
        command.add(layout.root.absolutePath)
        command.add("--db")
        command.add("sdtitle")
        command.add(workDir.absolutePath)
        command.addAll(mode)

        val process = ProcessBuilder(command)
            .directory(layout.root)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readLines()
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw Save3dsError("save3ds travou")
        }
        if (process.exitValue() != 0) {
            throw Save3dsError("save3ds falhou (${process.exitValue()}): ${output.takeLast(6).joinToString(" | ")}")
        }
        return output
    }

    /** The key files are copied into app storage so the native process can always open them. */
    data class KeysFiles(val boot9: File, val movable: File)

    class Save3dsError(message: String) : Exception(message)
}
