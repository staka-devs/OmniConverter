package dev.staka.omniconverter.engine.libheif

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

/** HEIC と AVIF の読み書き。heif-dec と heif-enc を使う */
class LibheifEngine(
    private val binary: (String) -> File,
    private val runner: NativeProcessRunner,
) : ConversionEngine {
    override val id = EngineId("libheif")
    override val capabilities: Set<Capability> = LibheifArgs.capabilities

    override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult {
        val (tool, args) = LibheifArgs.command(task)
        return runner.convert(listOf(binary(tool).path) + args, task.output.parent.toFile(), task.output)
            .also { onProgress(Progress(1f)) }
    }

    /** 上級モードでは、先頭の引数で heif-enc か heif-dec を選ぶ */
    override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit): ConversionResult {
        val tool = args.firstOrNull()?.takeIf { it in LibheifArgs.tools }
            ?: return ConversionResult.Failure(null, "先頭に ${LibheifArgs.tools.joinToString(" か ")} を指定してください")
        return runner.convert(listOf(binary(tool).path) + args.drop(1), workDir.toFile(), output)
    }
}

object LibheifArgs {
    val tools = listOf("heif-enc", "heif-dec")
    private val heif = listOf("heic", "avif").map(Formats::of)
    private val raster = listOf("jpg", "png").map(Formats::of)

    val capabilities: Set<Capability> = buildSet {
        heif.forEach { h -> raster.forEach { r -> add(Capability(h, r)); add(Capability(r, h)) } }
    }

    fun command(task: ConversionTask): Pair<String, List<String>> = when (task.to.id) {
        "heic" -> "heif-enc" to listOf("-q", "90", task.input.toString(), "-o", task.output.toString())
        "avif" -> "heif-enc" to listOf("-A", "-q", "80", task.input.toString(), "-o", task.output.toString())
        "jpg" -> "heif-dec" to listOf("-q", "92", task.input.toString(), task.output.toString())
        "png" -> "heif-dec" to listOf(task.input.toString(), task.output.toString())
        else -> error("libheif は ${task.to.id} を出力できません")
    }
}
