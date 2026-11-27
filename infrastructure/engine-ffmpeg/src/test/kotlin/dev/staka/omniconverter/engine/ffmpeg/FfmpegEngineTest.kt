package dev.staka.omniconverter.engine.ffmpeg

import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.FormatCategory
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FfmpegEngineTest {
    private val dir: File = Files.createTempDirectory("ffmpeg").toFile()

    /** ffmpeg の代わりに、進捗を出して最後の引数へ書き込むシェルスクリプト */
    private fun fakeFfmpeg(exit: Int = 0, write: Boolean = true) = File(dir, "ffmpeg").apply {
        writeText(
            """
            #!/bin/sh
            echo "  Duration: 00:00:10.00, start: 0.000000" >&2
            echo "out_time_us=5000000"
            echo "out_time_us=N/A"
            for last; do :; done
            ${if (write) "echo data > \"${'$'}last\"" else ""}
            exit $exit
            """.trimIndent(),
        )
        setExecutable(true)
    }

    private fun task(from: String, to: String) =
        ConversionTask(File(dir, "in.$from").toPath(), File(dir, "out.$to").toPath(), Formats.of(from), Formats.of(to))

    @Test
    fun 進捗を通知して出力を返す() = runBlocking {
        val bin = fakeFfmpeg()
        val progress = mutableListOf<Float?>()
        val result = FfmpegEngine({ bin }, NativeProcessRunner()).convert(task("mp4", "mp3")) { progress += it.fraction }
        assertTrue(result is ConversionResult.Success)
        assertEquals(listOf<Float?>(0.5f), progress)
    }

    @Test
    fun 終了コードが0以外なら失敗() = runBlocking {
        val bin = fakeFfmpeg(exit = 1)
        val result = FfmpegEngine({ bin }, NativeProcessRunner()).convert(task("mp4", "mp3")) {}
        assertEquals(1, (result as ConversionResult.Failure).exitCode)
    }

    @Test
    fun 出力が空なら失敗() = runBlocking {
        val bin = fakeFfmpeg(write = false)
        val result = FfmpegEngine({ bin }, NativeProcessRunner()).convert(task("mp4", "mp3")) {}
        assertTrue(result is ConversionResult.Failure)
    }

    @Test
    fun 宣言したすべての組にエンコード指定がある() {
        FfmpegArgs.capabilities.forEach { FfmpegArgs.encode(it.from, it.to) }
    }

    @Test
    fun 動画から音声へは映像を捨てる() {
        assertTrue("-vn" in FfmpegArgs.encode(Formats.of("mp4"), Formats.of("mp3")))
        assertFalse("-vn" in FfmpegArgs.encode(Formats.of("wav"), Formats.of("mp3")))
    }

    @Test
    fun 音声から動画や画像へは変換しない() {
        val fromAudio = FfmpegArgs.capabilities.filter { it.from.category == FormatCategory.AUDIO }
        assertTrue(fromAudio.all { it.to.category == FormatCategory.AUDIO })
        assertTrue(FfmpegArgs.capabilities.none { it.from == it.to })
    }

    @Test
    fun 統計行の時刻からも進捗を読む() {
        val p = FfmpegProgress()
        assertNull(p.feed("frame=1 time=00:00:01.00"))
        p.feed("  Duration: 01:00:00.00, start: 0")
        assertEquals(0.25f, p.feed("frame=9 fps=30 time=00:15:00.00 bitrate=1k"))
        assertEquals(1f, p.feed("out_time_us=999999999999"))
    }
}
