package dev.staka.omniconverter.application

import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.domain.model.RawCommand
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecuteJobTest {
    private val repo = FakeRepository()
    private val files = FakeFiles()

    private fun execute(vararg engines: FakeEngine): ExecuteJob {
        val registry = FakeRegistry(engines.toList())
        return ExecuteJob(repo, ConversionPlanner(registry), registry, files)
    }

    @Test
    fun 二段の変換を実行して書き出す() = runTest {
        repo.upsert(listOf(ConversionJob("j", FileRef("src", "note.md"), Formats.of("pdf"))))
        execute(FakeEngine("pandoc", "md>html"), FakeEngine("mupdf", "html>pdf"))("j")

        val job = repo.get("j")!!
        assertEquals(JobStatus.SUCCEEDED, job.status)
        assertEquals("note.pdf", job.output?.name)
        assertEquals("src>pandoc>mupdf", files.exported["note.pdf"])
        assertEquals(1, files.deleted.size)
        assertEquals(listOf("src"), files.released.map { it.uri })
    }

    @Test
    fun 共有されたファイルは手元に写してから登録する() = runTest {
        val kept = RetainSharedFiles(files)(listOf(FileRef("content://share/1", "a.png")))
        assertEquals(listOf("kept://content://share/1"), kept.map { it.uri })
        assertEquals("a.png", kept.single().name)
    }

    @Test
    fun エンジンの失敗はジョブの失敗になる() = runTest {
        repo.upsert(listOf(ConversionJob("j", FileRef("src", "a.png"), Formats.of("jpg"))))
        execute(FakeEngine("im", "png>jpg", fail = true))("j")

        val job = repo.get("j")!!
        assertEquals(JobStatus.FAILED, job.status)
        assertEquals("boom", job.error)
        assertTrue(files.exported.isEmpty())
    }

    @Test
    fun 変換経路が無ければ失敗する() = runTest {
        repo.upsert(listOf(ConversionJob("j", FileRef("src", "a.png"), Formats.of("mp3"))))
        execute(FakeEngine("im", "png>jpg"))("j")
        assertEquals(JobStatus.FAILED, repo.get("j")!!.status)
    }

    @Test
    fun 生コマンドの置き換えと拡張子() = runTest {
        val ffmpeg = FakeEngine("ffmpeg")
        val raw = RawCommand(EngineId("ffmpeg"), listOf("-i", "{input}", "-vn", "{output}"), "mp3")
        repo.upsert(listOf(ConversionJob("j", FileRef("src", "clip.mp4"), target = null, rawCommand = raw)))
        execute(ffmpeg)("j")

        assertEquals(JobStatus.SUCCEEDED, repo.get("j")!!.status)
        assertEquals("clip.mp3", repo.get("j")!!.output?.name)
        val args = ffmpeg.rawCalls.single()
        assertTrue(args[1].endsWith("clip.mp4") && args[3].endsWith("out.mp3"))
    }

    @Test
    fun 待機中でないジョブは実行しない() = runTest {
        repo.upsert(listOf(ConversionJob("j", FileRef("src", "a.png"), Formats.of("jpg"), status = JobStatus.CANCELLED)))
        execute(FakeEngine("im", "png>jpg"))("j")
        assertEquals(JobStatus.CANCELLED, repo.get("j")!!.status)
    }

    @Test
    fun 複合拡張子を除いた名前で書き出す() {
        assertEquals("backup", ExecuteJob.baseName("backup.tar.gz"))
        assertEquals("photo", ExecuteJob.baseName("photo.HEIC"))
        assertEquals("a.b", ExecuteJob.baseName("a.b.unknown"))
    }

    @Test
    fun 引数の置き換え() {
        val args = ExecuteJob.substitute(listOf("x={input}", "{output}"), Path.of("i"), Path.of("o"))
        assertEquals(listOf("x=i", "o"), args)
    }
}
