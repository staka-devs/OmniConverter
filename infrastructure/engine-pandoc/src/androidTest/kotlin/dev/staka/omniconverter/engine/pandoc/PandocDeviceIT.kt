package dev.staka.omniconverter.engine.pandoc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.infrastructure.process.BinaryLocator
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** 公式の静的バイナリの pandoc が端末で動き、日本語の文書を変換できることを確かめる */
@RunWith(AndroidJUnit4::class)
class PandocDeviceIT {
    /** x86_64 の古いエミュレータの ARM 変換は、静的バイナリのシグナル設定に対応しない。実機では起きない。ADR-0011 */
    @org.junit.Before
    fun requireNativeArm() {
        val translated = android.os.Build.SUPPORTED_ABIS[0].startsWith("x86") && android.os.Build.VERSION.SDK_INT < 33
        org.junit.Assume.assumeFalse("API 32 以前の ARM 変換では静的バイナリが動かない", translated)
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "pandoc-it").apply { mkdirs() }
    private val engine = PandocEngine({ BinaryLocator(context).locate("pandoc") }, NativeProcessRunner())

    private val markdown = File(dir, "doc.md").apply {
        writeText("# 見出し\n\n本文の**強調**です。\n\n- 項目一\n- 項目二\n")
    }

    private fun convert(from: File, to: String): File = runBlocking {
        val out = File(dir, "out-${System.nanoTime()}.$to")
        val task = ConversionTask(from.toPath(), out.toPath(), Formats.detect(from.name)!!, Formats.of(to))
        val result = engine.convert(task) {}
        if (result is ConversionResult.Failure) fail("${from.name} → $to が失敗: ${result.log}")
        out
    }

    @Test
    fun MarkdownをHTMLにする() {
        val html = convert(markdown, "html").readText()
        assertTrue(html.contains("<strong>強調</strong>"), html)
    }

    @Test
    fun MarkdownをWordにしてMarkdownに戻す() {
        val docx = convert(markdown, "docx")
        ZipFile(docx).use { assertTrue(it.getEntry("word/document.xml") != null) }
        val back = convert(docx, "md").readText()
        assertTrue(back.contains("見出し") && back.contains("**強調**"), back)
    }

    @Test
    fun 静的バイナリが端末で起動する() = runBlocking {
        val pandoc = BinaryLocator(context).locate("pandoc")
        val env = mapOf("HOME" to dir.path, "TMPDIR" to dir.path)
        val result = NativeProcessRunner().run(listOf(pandoc.path, "--version"), dir, env)
        assertEquals(0, result.exitCode, result.log)
        assertTrue(result.log.startsWith("pandoc 3.14"), result.log)
    }
}
