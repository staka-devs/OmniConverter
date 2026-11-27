package dev.staka.omniconverter.infrastructure

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.infrastructure.db.OmniDatabase
import dev.staka.omniconverter.infrastructure.db.RoomJobRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class JobRepositoryIT {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OmniDatabase::class.java).build()
    private val dao = db.jobDao()
    private var now = 1000L
    private val repo = RoomJobRepository(dao) { now }

    @After
    fun close() = db.close()

    private fun job(id: String, status: JobStatus = JobStatus.QUEUED) =
        ConversionJob(id, FileRef("content://$id", "$id.png"), Formats.of("jpg"), status = status)

    @Test
    fun 一括登録した順に並ぶ() = runTest {
        repo.upsert(listOf(job("c"), job("a"), job("b")))
        assertEquals(listOf("c", "a", "b"), repo.observeAll().first().map { it.id })
        assertEquals(listOf("c", "a", "b"), dao.queuedIds())
    }

    @Test
    fun 更新しても登録順は変わらない() = runTest {
        repo.upsert(listOf(job("a"), job("b")))
        now = 9999
        repo.upsert(listOf(job("a", JobStatus.SUCCEEDED)))
        assertEquals(listOf("a", "b"), repo.observeAll().first().map { it.id })
        assertEquals(JobStatus.SUCCEEDED, repo.get("a")!!.status)
    }

    @Test
    fun 実行中のまま残ったジョブを待機に戻す() = runTest {
        repo.upsert(listOf(job("a", JobStatus.RUNNING).copy(progress = 0.4f), job("b", JobStatus.FAILED)))
        dao.requeueRunning()
        assertEquals(JobStatus.QUEUED, repo.get("a")!!.status)
        assertNull(repo.get("a")!!.progress)
        assertEquals(JobStatus.FAILED, repo.get("b")!!.status)
    }

    @Test
    fun 削除できる() = runTest {
        repo.upsert(listOf(job("a")))
        repo.delete("a")
        assertNull(repo.get("a"))
    }
}
