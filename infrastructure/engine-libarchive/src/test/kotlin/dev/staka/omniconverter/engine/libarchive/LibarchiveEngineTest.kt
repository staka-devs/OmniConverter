package dev.staka.omniconverter.engine.libarchive

import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibarchiveEngineTest {
    private val dir: File = Files.createTempDirectory("archive").toFile()

    /** 引数を記録し、-f の次の引数へ書き込むスクリプト */
    private val fake = File(dir, "bsdtar").apply {
        val script = listOf(
            "#!/bin/sh",
            "echo \"\$*\" > \"${dir.path}/called\"",
            "echo data > \"\$3\"",
        )
        writeText(script.joinToString("\n"))
        setExecutable(true)
    }

    private val engine = LibarchiveEngine({ fake }, NativeProcessRunner())

    @Test
    fun 入力を展開せずに別形式へ書き直す() = runBlocking {
        val input = File(dir, "a.zip")
        val output = File(dir, "a.tar.gz")
        val task = ConversionTask(input.toPath(), output.toPath(), Formats.of("zip"), Formats.of("tar.gz"))
        assertTrue(engine.convert(task) {} is ConversionResult.Success)
        assertEquals("-c -f ${output.path} --format pax --gzip @${input.path}", File(dir, "called").readText().trim())
    }

    @Test
    fun rarは読むだけで書かない() {
        val caps = LibarchiveArgs.capabilities
        assertTrue(caps.any { it.from.id == "rar" })
        assertTrue(caps.none { it.to.id == "rar" })
        caps.forEach { LibarchiveArgs.format(it.to.id) }
    }
}
