package dev.staka.omniconverter.engine.imagemagick

import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.Capability
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.Progress
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import dev.staka.omniconverter.infrastructure.process.convert
import java.io.File
import java.nio.file.Path

/** 静止画とアニメーション画像の変換。同梱した magick を使う */
class ImageMagickEngine(
    private val binary: () -> File,
    private val runner: NativeProcessRunner,
) : ConversionEngine {
    override val id = EngineId("imagemagick")
    override val capabilities: Set<Capability> = ImageMagickArgs.capabilities

    override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult =
        runner.convert(listOf(binary().path) + ImageMagickArgs.command(task), task.output.parent.toFile(), task.output)
            .also { onProgress(Progress(1f)) }

    override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
        runner.convert(listOf(binary().path) + args, workDir.toFile(), output)
}

object ImageMagickArgs {
    private val writable = listOf("jpg", "png", "webp", "gif", "bmp", "tiff", "heic", "avif", "jxl", "ico", "psd", "tga")
        .map(Formats::of)
    private val readOnly = listOf(Formats.of("dng"))
    private val readable = writable + readOnly
    private val multiFrameOutputs = setOf("gif", "webp", "tiff", "pdf")

    val capabilities: Set<Capability> = buildSet {
        readable.forEach { from ->
            (writable - from).forEach { add(Capability(from, it)) }
            add(Capability(from, Formats.of("pdf")))
        }
    }

    fun command(task: ConversionTask): List<String> {
        val input = inputFor(task)
        val options = when (task.to.id) {
            "jpg" -> listOf("-quality", "92")
            "webp" -> listOf("-quality", "85")
            "heic", "avif", "jxl" -> listOf("-quality", "80")
            "ico" -> listOf("-resize", "256x256>")
            else -> emptyList()
        }
        return listOf(input, "-auto-orient") + options + task.output.toString()
    }

    /** 1 枚しか持てない形式へは先頭のフレームだけを渡す。PSD では統合済みの画像になる */
    private fun inputFor(task: ConversionTask): String =
        if (task.to.id in multiFrameOutputs) task.input.toString() else "${task.input}[0]"
}
