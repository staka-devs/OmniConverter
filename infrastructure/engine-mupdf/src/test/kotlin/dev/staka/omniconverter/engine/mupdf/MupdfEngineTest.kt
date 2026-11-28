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

    /**
     * -o の次の引数へ書き込む mutool の代わり。
     * [numberedPages] なら本物と同じく、名前の %d をページ番号 1 に置き換えて書く
     */
    private fun fakeMutool(numberedPages: Boolean) = File(dir, if (numberedPages) "mutool-paged" else "mutool").apply {
        val target = if (numberedPages) "\"\$(echo \"\$2\" | sed 's/%d/1/')\"" else "\"\$2\""
        val script = listOf(
            "#!/bin/sh",
            "while [ \"\$1\" != \"-o\" ]; do shift; done",
            "echo written > $target",
        )
        writeText(script.joinToString("\n"))
        setExecutable(true)
    }

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
    fun ページ番号付きの先頭ページを求められた名前に移す() = runBlocking {
        val fake = fakeMutool(numberedPages = true)
        val t = task("pdf", "png")
        val result = MupdfEngine({ fake }, NativeProcessRunner()).convert(t) {}
        assertTrue(result is ConversionResult.Success)
        assertEquals("written", t.output.toFile().readText().trim())
        assertTrue(!File(dir, "mutool-page1.png").exists())
    }

    @Test
    fun 出力ができれば成功() = runBlocking {
        val fake = fakeMutool(numberedPages = false)
        val result = MupdfEngine({ fake }, NativeProcessRunner()).convert(task("pdf", "txt")) {}
        assertTrue(result is ConversionResult.Success)
    }
}
