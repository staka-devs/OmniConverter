package dev.staka.omniconverter.infrastructure

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.staka.omniconverter.application.ConversionPlanner
import dev.staka.omniconverter.application.ExecuteJob
import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.Capability
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.domain.model.Progress
import dev.staka.omniconverter.infrastructure.db.OmniDatabase
import dev.staka.omniconverter.infrastructure.db.RoomJobRepository
import dev.staka.omniconverter.infrastructure.engine.ListEngineRegistry
import dev.staka.omniconverter.infrastructure.file.SafFileGateway
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import dev.staka.omniconverter.infrastructure.work.ConversionWorker
import dev.staka.omniconverter.infrastructure.work.ConversionWorkerFactory
import dev.staka.omniconverter.infrastructure.work.RunningJobs
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 実機またはエミュレータ上で、プロセス実行、ファイル入出力、ワーカーを結合して確かめる */
@RunWith(AndroidJUnit4::class)
class DeviceIT {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val files = SafFileGateway(context)
    private val created = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        created.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun 端末のシェルを実行して終了コードを受け取る() = runBlocking {
        val result = NativeProcessRunner().run(listOf("/system/bin/sh", "-c", "echo hi; exit 7"), context.cacheDir)
        assertEquals(7, result.exitCode)
        assertEquals("hi", result.log)
    }

    @Test
    fun キャンセルで端末のプロセスも止まる() = runBlocking {
        val marker = File(context.cacheDir, "marker-${System.nanoTime()}")
        val job = async { NativeProcessRunner().run(listOf("/system/bin/sh", "-c", "sleep 3; touch ${marker.path}"), context.cacheDir) }
        delay(300)
        job.cancel()
        job.join()
        delay(3500)
        assertFalse(marker.exists())
    }

    @Test
    fun 書き出したファイルを取り込み直せる() = runBlocking {
        val src = Files.createTempFile(context.cacheDir.toPath(), "it", ".txt").also { it.toFile().writeText("こんにちは") }
        val exported = files.export(src, "it-${System.nanoTime()}.txt").also { created += Uri.parse(it.uri) }

        val workDir = files.newWorkDir()
        val imported = files.importToWorkspace(exported, workDir)
        assertEquals("こんにちは", imported.toFile().readText())
        assertTrue(imported.startsWith(workDir))

        files.deleteWorkDir(workDir)
        assertFalse(Files.exists(workDir))
    }

    @Test
    fun 共有の写しは変換後に消し写し以外は消さない(): Unit = runBlocking {
        val src = Files.createTempFile(context.cacheDir.toPath(), "share", ".txt").also { it.toFile().writeText("共有") }
        val shared = files.export(src, "share-${System.nanoTime()}.txt").also { created += Uri.parse(it.uri) }

        val kept = files.retain(shared)
        val keptFile = File(Uri.parse(kept.uri).path!!)
        assertEquals("共有", keptFile.readText())
        assertEquals(shared.name, kept.name)

        val outsideInbox = File(context.filesDir, "keep-me.txt").apply { writeText("x") }
        files.release(dev.staka.omniconverter.domain.model.FileRef(Uri.fromFile(outsideInbox).toString(), outsideInbox.name))
        assertTrue(outsideInbox.exists())
        files.release(shared)

        files.release(kept)
        assertFalse(keptFile.exists())
        outsideInbox.delete()
    }

    @Test
    fun ワーカーが待機中のジョブを最後まで処理する() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, OmniDatabase::class.java).build()
        try {
            val repo = RoomJobRepository(db.jobDao())
            val registry = ListEngineRegistry(listOf(UpperCaseEngine()))
            val factory = ConversionWorkerFactory(
                db.jobDao(),
                ExecuteJob(repo, ConversionPlanner(registry), registry, files),
                RunningJobs(),
            )

            val src = Files.createTempFile(context.cacheDir.toPath(), "src", ".txt").also { it.toFile().writeText("abc") }
            val source = files.export(src, "worker-${System.nanoTime()}.txt").also { created += Uri.parse(it.uri) }
            repo.upsert(listOf(ConversionJob("j1", source, Formats.of("md")), ConversionJob("j2", source, Formats.of("md"))))

            if (android.os.Build.VERSION.SDK_INT >= 33) {
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                    .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
            }
            val worker = TestListenableWorkerBuilder<ConversionWorker>(context).setWorkerFactory(factory).build()
            assertTrue(worker.doWork() is ListenableWorker.Result.Success)

            assertTrue(awaitNotification(ConversionWorker.DONE_NOTIFICATION_ID), "完了の通知がありません")

            listOf("j1", "j2").forEach { id ->
                val job = repo.get(id)!!
                assertEquals(JobStatus.SUCCEEDED, job.status, job.error)
                val uri = Uri.parse(job.output!!.uri).also { created += it }
                val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                assertEquals("ABC", text)
            }
        } finally {
            db.close()
        }
    }

    /** 通知の登録は非同期なので、最大 5 秒待つ */
    private suspend fun awaitNotification(id: Int): Boolean {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        repeat(50) {
            if (manager.activeNotifications.any { it.id == id }) return true
            delay(100)
        }
        return false
    }

    /** txt を大文字にして md として出す */
    private class UpperCaseEngine : ConversionEngine {
        override val id = EngineId("upper")
        override val capabilities = setOf(Capability(Formats.of("txt"), Formats.of("md")))
        override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult {
            task.output.toFile().writeText(task.input.toFile().readText().uppercase())
            return ConversionResult.Success(task.output, "")
        }
        override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
            ConversionResult.Failure(null, "unsupported")
    }
}
