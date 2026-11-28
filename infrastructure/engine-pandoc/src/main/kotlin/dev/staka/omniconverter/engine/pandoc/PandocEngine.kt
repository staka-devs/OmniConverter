package dev.staka.omniconverter.engine.pandoc

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

/**
 * 文書形式の変換。同梱した pandoc を使う。
 * PDF は出力しない。HTML を経由して MuPDF が組むよう、変換経路の探索に任せる
 */
class PandocEngine(
    private val binary: () -> File,
    private val runner: NativeProcessRunner,
) : ConversionEngine {
    override val id = EngineId("pandoc")
    override val capabilities: Set<Capability> = PandocArgs.capabilities

    override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult {
        val dir = task.output.parent.toFile()
        return runner.convert(listOf(binary().path) + PandocArgs.command(task), dir, task.output, environment(dir))
            .also { onProgress(Progress(1f)) }
    }

    override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
        runner.convert(listOf(binary().path) + args, workDir.toFile(), output, environment(workDir.toFile()))

    /** アプリのプロセスには HOME も一時ディレクトリも無いので、作業ディレクトリを渡す */
    private fun environment(dir: File) = mapOf("HOME" to dir.path, "TMPDIR" to dir.path, "LANG" to "C.UTF-8")
}

object PandocArgs {
    private val readers = mapOf(
        "md" to "markdown", "html" to "html", "docx" to "docx", "odt" to "odt", "epub" to "epub",
        "rst" to "rst", "org" to "org", "tex" to "latex", "ipynb" to "ipynb", "rtf" to "rtf",
    )

    /** 並び順が経路探索の優先順になる。HTML を先に置き、PDF へは HTML を経由させる */
    private val writers = linkedMapOf(
        "html" to "html5", "md" to "markdown", "docx" to "docx", "odt" to "odt", "epub" to "epub3",
        "rst" to "rst", "org" to "org", "tex" to "latex", "ipynb" to "ipynb", "pptx" to "pptx",
        "rtf" to "rtf", "txt" to "plain",
    )

    val capabilities: Set<Capability> = buildSet {
        readers.keys.forEach { from ->
            writers.keys.filter { it != from }.forEach { add(Capability(Formats.of(from), Formats.of(it))) }
        }
    }

    fun command(task: ConversionTask): List<String> {
        val reader = readers[task.from.id] ?: error("pandoc は ${task.from.id} を読めません")
        val writer = writers[task.to.id] ?: error("pandoc は ${task.to.id} を出力できません")
        val singleFileHtml = if (task.to.id == "html") listOf("--embed-resources") else emptyList()
        return listOf("-f", reader, "-t", writer, "--standalone") + singleFileHtml +
            listOf("-o", task.output.toString(), task.input.toString())
    }
}
