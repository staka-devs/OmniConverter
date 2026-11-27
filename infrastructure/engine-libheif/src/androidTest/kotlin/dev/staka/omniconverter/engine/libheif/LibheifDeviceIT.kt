package dev.staka.omniconverter.engine.libheif

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
import kotlin.test.fail

/** 本物の heif-enc と heif-dec で、PNG から HEIC と AVIF を作って読み戻す */
@RunWith(AndroidJUnit4::class)
class LibheifDeviceIT {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "heif-it").apply { mkdirs() }
    private val engine = LibheifEngine({ BinaryLocator(context).locate(it) }, NativeProcessRunner())

    private fun samplePng(): File = File(dir, "sample.png").apply {
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
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
    fun HEICを作って読み戻す() {
        val png = convert(convert(samplePng(), "heic"), "png")
        val bmp = BitmapFactory.decodeFile(png.path)
        assertEquals(64, bmp.width)
        assertEquals(48, bmp.height)
    }

    @Test
    fun AVIFを作ってJPEGで読み戻す() {
        val jpg = convert(convert(samplePng(), "avif"), "jpg")
        assertEquals(64, BitmapFactory.decodeFile(jpg.path).width)
    }
}
