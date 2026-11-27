package dev.staka.omniconverter.engine.imagemagick

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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

/** 本物の magick で PNG を各形式へ書き出し、PNG へ戻して大きさが保たれることを確かめる */
@RunWith(AndroidJUnit4::class)
class ImageMagickDeviceIT {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "magick-it").apply { mkdirs() }
    private val magick = BinaryLocator(context).locate("magick")
    private val engine = ImageMagickEngine({ magick }, NativeProcessRunner())

    private fun samplePng(): File = File(dir, "sample.png").apply {
        val bmp = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun convert(from: File, to: String): File = runBlocking {
        val out = File(dir, "out-${System.nanoTime()}.$to")
        val task = ConversionTask(from.toPath(), out.toPath(), Formats.detect(from.name)!!, Formats.of(to))
        val result = engine.convert(task) {}
        if (result is ConversionResult.Failure) fail("${from.name} → $to が失敗: ${result.log}")
        out
    }

    @Test
    fun 委譲先がすべて組み込まれている() = runBlocking {
        val log = NativeProcessRunner().run(listOf(magick.path, "-version"), dir).log
        listOf("heic", "jng", "jpeg", "jxl", "lcms", "png", "raw", "tiff", "webp", "zstd").forEach {
            assertTrue(log.contains(it), "$it が無い: $log")
        }
    }

    @Test
    fun 各形式へ書き出して読み戻す() {
        val png = samplePng()
        for (to in listOf("jpg", "webp", "gif", "bmp", "tiff", "heic", "avif", "jxl", "tga", "psd", "ico")) {
            val back = convert(convert(png, to), "png")
            val bmp = BitmapFactory.decodeFile(back.path) ?: fail("$to から PNG に戻せない")
            assertEquals(40, bmp.width, to)
            assertEquals(30, bmp.height, to)
        }
    }

    @Test
    fun 画像からPDFを作る() {
        val pdf = convert(samplePng(), "pdf")
        assertEquals("%PDF", pdf.readBytes().copyOfRange(0, 4).decodeToString())
    }
}
