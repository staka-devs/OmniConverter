package dev.staka.omniconverter.presentation

import dev.staka.omniconverter.application.CancelJob
import dev.staka.omniconverter.application.ClearFinishedJobs
import dev.staka.omniconverter.application.ConversionPlanner
import dev.staka.omniconverter.application.EnqueueConversions
import dev.staka.omniconverter.application.GetAvailableTargets
import dev.staka.omniconverter.application.ObserveJobs
import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.Capability
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.domain.model.Progress
import dev.staka.omniconverter.domain.port.ConversionJobRepository
import dev.staka.omniconverter.domain.port.EngineRegistry
import dev.staka.omniconverter.domain.port.JobScheduler
import dev.staka.omniconverter.presentation.convert.ConvertViewModel
import dev.staka.omniconverter.presentation.queue.QueueViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val repo = MemoryRepository()
    private val scheduler = RecordingScheduler()
    private val planner = ConversionPlanner(object : EngineRegistry {
        override val engines = listOf(StubEngine("png>jpg", "png>webp", "jpg>png"))
    })

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val settings = MemorySettings()
    private fun convertVm() = ConvertViewModel(
        GetAvailableTargets(planner), EnqueueConversions(repo, scheduler),
        dev.staka.omniconverter.application.RetainSharedFiles(NoFiles),
        dev.staka.omniconverter.application.ObserveOutputDir(settings),
        dev.staka.omniconverter.application.SetOutputDir(settings),
    )

    @Test
    fun 共有されたファイルは写しを選択に入れる() {
        val vm = convertVm()
        vm.receiveShared(listOf(FileRef("content://share/1", "a.png")))
        assertEquals(listOf("kept:content://share/1"), vm.state.value.files.map { it.uri })
        assertEquals(listOf("jpg", "webp"), vm.state.value.targets.map { it.id })
    }

    @Test
    fun 出力先を選び既定に戻せる() {
        val vm = convertVm()
        val collector = CoroutineScope(Dispatchers.Main).launch { vm.outputDir.collect {} }
        vm.chooseOutputDir(FileRef("content://tree/x", "写真"))
        assertEquals("写真", vm.outputDir.value?.name)
        vm.chooseOutputDir(null)
        assertNull(vm.outputDir.value)
        collector.cancel()
    }

    @Test
    fun ファイルを選ぶと変換先が並ぶ() {
        val vm = convertVm()
        vm.setFiles(listOf(FileRef("u1", "a.png")))
        assertEquals(listOf("jpg", "webp"), vm.state.value.targets.map { it.id })
        assertNull(vm.state.value.selected)
    }

    @Test
    fun ファイルを足し外しても選んだ形式を保つ() {
        val vm = convertVm()
        val a = FileRef("u1", "a.png")
        vm.setFiles(listOf(a))
        vm.select(Formats.of("webp"))
        vm.addFiles(listOf(FileRef("u2", "b.png"), a))
        assertEquals(listOf("u1", "u2"), vm.state.value.files.map { it.uri })
        vm.remove(a)
        assertEquals(listOf("u2"), vm.state.value.files.map { it.uri })
        assertEquals("webp", vm.state.value.selected?.id)
    }

    @Test
    fun 変換するとジョブが登録され選択が消える() {
        val vm = convertVm()
        vm.setFiles(listOf(FileRef("u1", "a.png"), FileRef("u2", "b.png")))
        vm.select(Formats.of("jpg"))
        var enqueued = false
        vm.convert { enqueued = true }

        assertTrue(enqueued)
        assertEquals(1, scheduler.scheduled)
        assertEquals(listOf("a.png", "b.png"), repo.state.value.values.map { it.source.name })
        assertTrue(repo.state.value.values.all { it.target?.id == "jpg" })
        assertTrue(vm.state.value.files.isEmpty())
    }

    @Test
    fun 変換先を選ぶまで変換しない() {
        val vm = convertVm()
        vm.setFiles(listOf(FileRef("u1", "a.png")))
        vm.convert { error("呼ばれない") }
        assertTrue(repo.state.value.isEmpty())
    }

    @Test
    fun キューでキャンセルと完了の消去ができる() {
        val vm = QueueViewModel(ObserveJobs(repo), CancelJob(repo, scheduler), ClearFinishedJobs(repo, NoFiles))
        val src = FileRef("u", "a.png")
        repo.state.value = listOf(
            ConversionJob("run", src, Formats.of("jpg"), status = JobStatus.RUNNING),
            ConversionJob("done", src, Formats.of("jpg"), status = JobStatus.SUCCEEDED),
        ).associateBy { it.id }
        val collector = CoroutineScope(Dispatchers.Main).launch { vm.jobs.collect {} }

        vm.clearFinished()
        assertEquals(setOf("run"), repo.state.value.keys)
        vm.cancel("run")
        assertEquals(listOf("run"), scheduler.cancelled)
        assertEquals(JobStatus.CANCELLED, repo.state.value["run"]!!.status)
        collector.cancel()
    }
}

private class StubEngine(vararg pairs: String) : ConversionEngine {
    override val id = EngineId("stub")
    override val capabilities = pairs.map { p -> p.split(">").let { Capability(Formats.of(it[0]), Formats.of(it[1])) } }.toSet()
    override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit) = ConversionResult.Failure(null, "")
    override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
        ConversionResult.Failure(null, "")
}

private class MemoryRepository : ConversionJobRepository {
    val state = MutableStateFlow<Map<String, ConversionJob>>(emptyMap())
    override fun observeAll() = state.map { it.values.toList() }
    override suspend fun get(id: String) = state.value[id]
    override suspend fun upsert(jobs: List<ConversionJob>) {
        state.value = state.value + jobs.associateBy { it.id }
    }
    override suspend fun delete(id: String) {
        state.value = state.value - id
    }
}

/** 写しを作らないファイル操作。キューの消去で呼ばれる */
private object NoFiles : dev.staka.omniconverter.domain.port.FileGateway {
    override suspend fun importToWorkspace(source: FileRef, workDir: Path) = error("unused")
    override suspend fun export(file: Path, name: String) = error("unused")
    override suspend fun newWorkDir() = error("unused")
    override suspend fun deleteWorkDir(dir: Path) {}
    override suspend fun retain(source: FileRef) = source.copy(uri = "kept:${source.uri}")
    override suspend fun release(source: FileRef) {}
}

private class MemorySettings : dev.staka.omniconverter.domain.port.SettingsRepository {
    private val dir = MutableStateFlow<FileRef?>(null)
    override fun outputDir() = dir
    override suspend fun setOutputDir(dir: FileRef?) {
        this.dir.value = dir
    }
}

private class RecordingScheduler : JobScheduler {
    var scheduled = 0
    val cancelled = mutableListOf<String>()
    override fun schedule() { scheduled++ }
    override fun cancel(jobId: String) { cancelled += jobId }
}
