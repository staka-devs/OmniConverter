package dev.staka.omniconverter.engine.ffmpeg

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.infrastructure.process.BinaryLocator
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/** 同梱した本物の ffmpeg を端末で動かし、代表的な変換が通ることを確かめる */
@RunWith(AndroidJUnit4::class)
class FfmpegDeviceIT {
    companion object {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val dir = File(context.cacheDir, "ffmpeg-it").apply { deleteRecursively(); mkdirs() }
        private val engine = FfmpegEngine({ BinaryLocator(context).locate("ffmpeg") }, NativeProcessRunner())
        private val sample = File(dir, "sample.mp4")

        /** 2 秒のテスト映像とサイン波を ffmpeg 自身で作る */
        @BeforeClass
        @JvmStatic
        fun createSample() = runBlocking {
            val args = listOf(
                "-y", "-f", "lavfi", "-i", "testsrc=duration=2:size=160x120:rate=10",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", sample.path,
            )
            check(engine.runRaw(args, dir.toPath(), sample.toPath()) {} is ConversionResult.Success) { "サンプルを作れません" }
        }
    }

    private fun convert(from: File, to: String): File = runBlocking {
        val out = File(dir, "out-${System.nanoTime()}.$to")
        val fromFormat = Formats.detect(from.name)!!
        var last = 0f
        val result = engine.convert(ConversionTask(from.toPath(), out.toPath(), fromFormat, Formats.of(to))) { p ->
            p.fraction?.let { last = it }
        }
        if (result is ConversionResult.Failure) fail("${from.name} → $to が失敗: ${result.log}")
        assertTrue(out.length() > 0)
        assertTrue(last > 0.5f, "進捗が届いていません: $last")
        out
    }

    @Test
    fun バージョンを表示できる() = runBlocking {
        val result = engine.runRaw(listOf("-version"), dir.toPath(), File(dir, "none").toPath()) {}
        assertTrue(result.log.contains("--enable-libx264"), result.log)
    }

    @Test
    fun 動画から音声を取り出す() {
        convert(sample, "mp3")
    }

    @Test
    fun 動画を別の形式にする() {
        convert(sample, "webm")
        convert(sample, "mkv")
    }

    @Test
    fun 動画からアニメーション画像を作る() {
        convert(sample, "gif")
    }

    @Test
    fun 音声どうしで変換する() {
        val wav = convert(sample, "wav")
        convert(wav, "flac")
        convert(wav, "opus")
    }
}
