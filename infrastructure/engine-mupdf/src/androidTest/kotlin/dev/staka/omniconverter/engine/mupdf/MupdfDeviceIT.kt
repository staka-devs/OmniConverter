package dev.staka.omniconverter.engine.mupdf

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** 本物の mutool で、PDF と HTML を各形式へ書き出す */
@RunWith(AndroidJUnit4::class)
class MupdfDeviceIT {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "mupdf-it").apply { mkdirs() }
    private val engine = MupdfEngine({ BinaryLocator(context).locate("mutool") }, NativeProcessRunner())

    /** Android の PdfDocument で 2 ページの PDF を作る */
    private fun samplePdf(): File = File(dir, "sample.pdf").apply {
        val doc = PdfDocument()
        repeat(2) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(300, 200, i + 1).create())
            page.canvas.drawText("Page ${i + 1} OmniConverter", 20f, 50f, Paint().apply { textSize = 18f })
            doc.finishPage(page)
        }
        outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    private fun convert(from: File, to: String): File = runBlocking {
        val out = File(dir, "out-${System.nanoTime()}.$to")
        val task = ConversionTask(from.toPath(), out.toPath(), Formats.detect(from.name)!!, Formats.of(to))
        val result = engine.convert(task) {}
        if (result is ConversionResult.Failure) fail("${from.name} → $to が失敗: ${result.log}")
        out
    }

    @Test
    fun PDFの文字を取り出す() {
        val text = convert(samplePdf(), "txt").readText()
        assertTrue(text.contains("Page 1 OmniConverter") && text.contains("Page 2"), text)
    }

    @Test
    fun PDFの先頭ページを画像にする() {
        val bmp = BitmapFactory.decodeFile(convert(samplePdf(), "png").path)
        // 72 dpi の 300x200 ポイントを 150 dpi で描く
        assertEquals(625, bmp.width)
    }

    @Test
    fun PDFをSVGとHTMLとCBZにする() {
        val pdf = samplePdf()
        assertTrue(convert(pdf, "svg").readText().contains("<svg"))
        assertTrue(convert(pdf, "html").readText().contains("OmniConverter"))
        assertEquals("PK", convert(pdf, "cbz").readBytes().copyOfRange(0, 2).decodeToString())
    }

    @Test
    fun 日本語のHTMLをPDFにする() {
        val html = File(dir, "doc.html").apply {
            writeText("<html><body><h1>変換テスト</h1><p>日本語の本文です。</p></body></html>")
        }
        val pdf = convert(html, "pdf")
        assertEquals("%PDF", pdf.readBytes().copyOfRange(0, 4).decodeToString())
        assertTrue(convert(pdf, "txt").readText().contains("日本語の本文"))
    }
}
