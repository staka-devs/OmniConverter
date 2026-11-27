package dev.staka.omniconverter.engine.imagemagick

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

class ImageMagickEngineTest {
    private val dir: File = Files.createTempDirectory("magick").toFile()

    private fun task(from: String, to: String) =
        ConversionTask(File(dir, "in.$from").toPath(), File(dir, "out.$to").toPath(), Formats.of(from), Formats.of(to))

    @Test
    fun 一枚の形式へは先頭フレームだけを渡す() {
        assertEquals("${dir.path}/in.gif[0]", ImageMagickArgs.command(task("gif", "png")).first())
        assertEquals("${dir.path}/in.gif", ImageMagickArgs.command(task("gif", "webp")).first())
        assertEquals("${dir.path}/in.tiff", ImageMagickArgs.command(task("tiff", "pdf")).first())
    }

    @Test
    fun RAWは読むだけ() {
        val caps = ImageMagickArgs.capabilities
        assertTrue(caps.any { it.from.id == "dng" })
        assertTrue(caps.none { it.to.id == "dng" })
        assertTrue(caps.none { it.from == it.to })
    }

    @Test
    fun 出力に成功したら結果を返す() = runBlocking {
        val fake = File(dir, "magick").apply {
            writeText(listOf("#!/bin/sh", "for last; do :; done", "echo data > \"\$last\"").joinToString("\n"))
            setExecutable(true)
        }
        val result = ImageMagickEngine({ fake }, NativeProcessRunner()).convert(task("png", "jpg")) {}
        assertTrue(result is ConversionResult.Success)
    }
}
