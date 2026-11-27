package dev.staka.omniconverter.engine.mupdf

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

class MupdfEngineTest {
    private val dir: File = Files.createTempDirectory("mupdf").toFile()

    private fun task(from: String, to: String) =
        ConversionTask(File(dir, "in.$from").toPath(), File(dir, "out.$to").toPath(), Formats.of(from), Formats.of(to))

    @Test
    fun HTMLはA4に組んでからPDFにする() {
        assertEquals(
            listOf("convert", "-W", "595", "-H", "842", "-S", "11", "-F", "pdf", "-o", "${dir.path}/out.pdf", "${dir.path}/in.html"),
            MupdfArgs.command(task("html", "pdf")),
        )
    }

    @Test
    fun PNGは先頭ページだけを書き出す() {
        val args = MupdfArgs.command(task("pdf", "png"))
        assertEquals("1", args.last())
        assertTrue("resolution=150" in args)
    }

    @Test
    fun 宣言したすべての組にコマンドがある() {
        MupdfArgs.capabilities.forEach { MupdfArgs.command(task(it.from.id, it.to.id)) }
        assertTrue(MupdfArgs.capabilities.none { it.from == it.to })
    }

    @Test
    fun 出力ができれば成功() = runBlocking {
        // -o の次の引数へ書き込む mutool の代わり
        val fake = File(dir, "mutool").apply {
            val script = listOf(
                "#!/bin/sh",
                "while [ \"\$1\" != \"-o\" ]; do shift; done",
                "echo data > \"\$2\"",
            )
            writeText(script.joinToString("\n"))
            setExecutable(true)
        }
        val result = MupdfEngine({ fake }, NativeProcessRunner()).convert(task("pdf", "txt")) {}
        assertTrue(result is ConversionResult.Success)
    }
}
