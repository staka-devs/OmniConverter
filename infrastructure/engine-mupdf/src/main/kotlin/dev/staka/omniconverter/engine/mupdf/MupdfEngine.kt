package dev.staka.omniconverter.engine.mupdf

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

/** PDF、XPS、EPUB、CBZ、HTML の変換。同梱した mutool を使う */
class MupdfEngine(
    private val binary: () -> File,
    private val runner: NativeProcessRunner,
) : ConversionEngine {
    override val id = EngineId("mupdf")
    override val capabilities: Set<Capability> = MupdfArgs.capabilities

    override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult =
        runner.convert(listOf(binary().path) + MupdfArgs.command(task), task.output.parent.toFile(), task.output)
            .also { onProgress(Progress(1f)) }

    /** 上級モードでは mutool の副コマンドから指定する。例: convert -o {output} {input} */
    override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
        runner.convert(listOf(binary().path) + args, workDir.toFile(), output)
}

object MupdfArgs {
    private val readable = listOf("pdf", "xps", "epub", "cbz", "html").map(Formats::of)
    private val writable = listOf("pdf", "cbz", "png", "svg", "html", "txt").map(Formats::of)
    // 決まったページを持たない入力は、A4 に組んでから書き出す
    private val reflowable = setOf("epub", "html")

    val capabilities: Set<Capability> = buildSet {
        readable.forEach { from -> (writable - from).forEach { add(Capability(from, it)) } }
    }

    fun command(task: ConversionTask): List<String> {
        val layout = if (task.from.id in reflowable) listOf("-W", "595", "-H", "842", "-S", "11") else emptyList()
        // shortcut: 1 件の変換は 1 ファイルを出すので、PNG と SVG は先頭ページだけ。全ページが要るなら CBZ を選ぶ
        val (format, options, pages) = when (task.to.id) {
            "pdf" -> Triple("pdf", null, null)
            "cbz" -> Triple("cbz", "resolution=150", null)
            "png" -> Triple("png", "resolution=150", "1")
            "svg" -> Triple("svg", null, "1")
            "html" -> Triple("html", null, null)
            "txt" -> Triple("text", null, null)
            else -> error("mupdf は ${task.to.id} を出力できません")
        }
        return listOf("convert") + layout + listOf("-F", format) +
            (options?.let { listOf("-O", it) } ?: emptyList()) +
            listOf("-o", task.output.toString(), task.input.toString()) + listOfNotNull(pages)
    }
}
