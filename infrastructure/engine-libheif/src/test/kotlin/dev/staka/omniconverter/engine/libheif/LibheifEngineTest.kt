package dev.staka.omniconverter.engine.libheif

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

class LibheifEngineTest {
    private val dir: File = Files.createTempDirectory("heif").toFile()

    /** 呼ばれた道具の名前と引数を記録し、最後の引数のファイルに書き込むスクリプト */
    private fun fakeTool(name: String) = File(dir, name).apply {
        val script = listOf(
            "#!/bin/sh",
            "echo \"$name \$*\" > \"${dir.path}/called\"",
            "for last; do :; done",
            "echo data > \"\$last\"",
        )
        writeText(script.joinToString("\n"))
        setExecutable(true)
    }

    private val engine = LibheifEngine({ fakeTool(it) }, NativeProcessRunner())

    private fun task(from: String, to: String) =
        ConversionTask(File(dir, "in.$from").toPath(), File(dir, "out.$to").toPath(), Formats.of(from), Formats.of(to))

    private fun called() = File(dir, "called").readText()

    @Test
    fun 読み込みはheif_decを使う() = runBlocking {
        assertTrue(engine.convert(task("heic", "jpg")) {} is ConversionResult.Success)
        assertTrue(called().startsWith("heif-dec -q 92"), called())
    }

    @Test
    fun AVIFへの書き出しはAオプションを付ける() = runBlocking {
        assertTrue(engine.convert(task("png", "avif")) {} is ConversionResult.Success)
        assertTrue(called().startsWith("heif-enc -A"), called())
    }

    @Test
    fun 生コマンドは先頭で道具を選ぶ() = runBlocking {
        val out = File(dir, "raw.heic").toPath()
        val ok = engine.runRaw(listOf("heif-enc", "-q", "50", "x.jpg", "-o", out.toString()), dir.toPath(), out) {}
        assertTrue(ok is ConversionResult.Success)
        val bad = engine.runRaw(listOf("rm", "-rf", "x"), dir.toPath(), out) {}
        assertTrue(bad is ConversionResult.Failure)
    }

    @Test
    fun 宣言したすべての組にコマンドがある() {
        LibheifArgs.capabilities.forEach { LibheifArgs.command(task(it.from.id, it.to.id)) }
        assertEquals(8, LibheifArgs.capabilities.size)
    }
}
