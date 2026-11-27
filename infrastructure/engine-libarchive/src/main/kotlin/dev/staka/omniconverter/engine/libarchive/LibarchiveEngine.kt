package dev.staka.omniconverter.engine.libarchive

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

/** アーカイブの詰め替え。bsdtar の @入力 指定で、展開せずに別形式へ書き直す */
class LibarchiveEngine(
    private val binary: () -> File,
    private val runner: NativeProcessRunner,
) : ConversionEngine {
    override val id = EngineId("libarchive")
    override val capabilities: Set<Capability> = LibarchiveArgs.capabilities

    override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult {
        val args = listOf("-c", "-f", task.output.toString()) + LibarchiveArgs.format(task.to.id) + "@${task.input}"
        return runner.convert(listOf(binary().path) + args, task.output.parent.toFile(), task.output)
            .also { onProgress(Progress(1f)) }
    }

    override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
        runner.convert(listOf(binary().path) + args, workDir.toFile(), output)
}

object LibarchiveArgs {
    private val writable = listOf("zip", "tar", "tar.gz", "tar.xz", "tar.bz2", "tar.zst", "7z", "iso", "cpio").map(Formats::of)
    // rar は読み込みだけに対応する
    private val readable = writable + Formats.of("rar")

    val capabilities: Set<Capability> = buildSet {
        readable.forEach { from -> (writable - from).forEach { add(Capability(from, it)) } }
    }

    fun format(to: String): List<String> = when (to) {
        "zip" -> listOf("--format", "zip")
        "tar" -> listOf("--format", "pax")
        "tar.gz" -> listOf("--format", "pax", "--gzip")
        "tar.xz" -> listOf("--format", "pax", "--xz")
        "tar.bz2" -> listOf("--format", "pax", "--bzip2")
        "tar.zst" -> listOf("--format", "pax", "--zstd")
        "7z" -> listOf("--format", "7zip")
        "iso" -> listOf("--format", "iso9660")
        "cpio" -> listOf("--format", "newc")
        else -> error("libarchive は $to を出力できません")
    }
}
