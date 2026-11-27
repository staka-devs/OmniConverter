package dev.staka.omniconverter.infrastructure

import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.domain.model.RawCommand
import dev.staka.omniconverter.infrastructure.db.toDomain
import dev.staka.omniconverter.infrastructure.db.toEntity
import dev.staka.omniconverter.infrastructure.file.SafFileGateway
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InfraCoreTest {
    private val tmp: File = Files.createTempDirectory("infra").toFile()
    private val runner = NativeProcessRunner(maxLogLines = 2)

    @Test
    fun 終了コードと末尾のログを返す() = runBlocking {
        val lines = mutableListOf<String>()
        val result = runner.run(listOf("sh", "-c", "echo a; echo b >&2; echo c; exit 3"), tmp) { lines += it }
        assertEquals(3, result.exitCode)
        assertEquals("b\nc", result.log)
        assertEquals(listOf("a", "b", "c"), lines)
    }

    @Test
    fun キャンセルするとプロセスを止める() = runBlocking {
        val marker = File(tmp, "done")
        val job = async { runner.run(listOf("sh", "-c", "sleep 5; touch done"), tmp) }
        delay(300)
        job.cancel()
        job.join()
        delay(200)
        assertTrue(job.isCancelled)
        assertFalse(marker.exists())
    }

    @Test
    fun ジョブを保存形式と相互変換できる() {
        val jobs = listOf(
            ConversionJob("a", FileRef("content://1", "x.png"), Formats.of("jpg"), options = mapOf("quality" to "90")),
            ConversionJob(
                "b", FileRef("content://2", "y.mp4"), target = null,
                rawCommand = RawCommand(EngineId("ffmpeg"), listOf("-i", "{input}", "{output}"), "mp3"),
                status = JobStatus.SUCCEEDED, progress = 1f, output = FileRef("content://3", "y.mp3"),
            ),
        )
        jobs.forEach { assertEquals(it, it.toEntity(0).toDomain()) }
    }

    @Test
    fun 表示名から作業領域の外を指せない() {
        assertEquals("_.._etc_passwd", SafFileGateway.safeName("/../etc/passwd"))
        assertEquals("hidden", SafFileGateway.safeName("..hidden"))
        assertEquals("file", SafFileGateway.safeName(".."))
    }
}
