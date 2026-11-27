package dev.staka.omniconverter.engine.pandoc

import dev.staka.omniconverter.application.ConversionPlanner
import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.Capability
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.Progress
import dev.staka.omniconverter.domain.port.EngineRegistry
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PandocEngineTest {
    private val dir: File = Files.createTempDirectory("pandoc").toFile()

    private fun task(from: String, to: String) =
        ConversionTask(File(dir, "in.$from").toPath(), File(dir, "out.$to").toPath(), Formats.of(from), Formats.of(to))

    @Test
    fun 読み手と書き手を明示する() {
        assertEquals(
            listOf("-f", "markdown", "-t", "docx", "--standalone", "-o", "${dir.path}/out.docx", "${dir.path}/in.md"),
            PandocArgs.command(task("md", "docx")),
        )
        assertTrue("--embed-resources" in PandocArgs.command(task("docx", "html")))
    }

    @Test
    fun 宣言したすべての組にコマンドがある() {
        PandocArgs.capabilities.forEach { PandocArgs.command(task(it.from.id, it.to.id)) }
        assertTrue(PandocArgs.capabilities.none { it.to.id == "pdf" })
    }

    @Test
    fun MarkdownからPDFへはHTMLを経由する() {
        val pdfFromHtml = object : ConversionEngine {
            override val id = EngineId("mupdf")
            override val capabilities = setOf(
                Capability(Formats.of("html"), Formats.of("pdf")),
                Capability(Formats.of("epub"), Formats.of("pdf")),
            )
            override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit) = ConversionResult.Failure(null, "")
            override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
                ConversionResult.Failure(null, "")
        }
        val pandoc = PandocEngine({ File("pandoc") }, NativeProcessRunner())
        val planner = ConversionPlanner(object : EngineRegistry {
            override val engines = listOf(pandoc, pdfFromHtml)
        })
        val plan = planner.plan(Formats.of("md"), Formats.of("pdf"))!!
        assertEquals(listOf("html", "pdf"), plan.steps.map { it.to.id })
    }

    @Test
    fun HOMEと一時ディレクトリを渡す() = runBlocking {
        val fake = File(dir, "pandoc").apply {
            val script = listOf(
                "#!/bin/sh",
                "while [ \"\$1\" != \"-o\" ]; do shift; done",
                "echo \"\$HOME \$TMPDIR \$LANG\" > \"\$2\"",
            )
            writeText(script.joinToString("\n"))
            setExecutable(true)
        }
        val t = task("md", "html")
        assertTrue(PandocEngine({ fake }, NativeProcessRunner()).convert(t) {} is ConversionResult.Success)
        assertEquals("${dir.path} ${dir.path} C.UTF-8", t.output.toFile().readText().trim())
    }
}
