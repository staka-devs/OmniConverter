package dev.staka.omniconverter.presentation

import dev.staka.omniconverter.application.CommandError
import dev.staka.omniconverter.application.DeletePreset
import dev.staka.omniconverter.application.EnqueueConversions
import dev.staka.omniconverter.application.ListEngines
import dev.staka.omniconverter.application.ObservePresets
import dev.staka.omniconverter.application.SavePreset
import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Preset
import dev.staka.omniconverter.domain.model.Progress
import dev.staka.omniconverter.domain.port.ConversionJobRepository
import dev.staka.omniconverter.domain.port.EngineRegistry
import dev.staka.omniconverter.domain.port.JobScheduler
import dev.staka.omniconverter.domain.port.PresetRepository
import dev.staka.omniconverter.presentation.command.CommandViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
class CommandViewModelTest {
    private val jobs = MutableStateFlow<List<ConversionJob>>(emptyList())
    private val presets = MutableStateFlow<List<Preset>>(emptyList())
    private val files = listOf(FileRef("u1", "a.mp4"), FileRef("u2", "b.mp4"))

    private val jobRepo = object : ConversionJobRepository {
        override fun observeAll() = jobs
        override suspend fun get(id: String) = jobs.value.find { it.id == id }
        override suspend fun upsert(jobs: List<ConversionJob>) { this@CommandViewModelTest.jobs.value += jobs }
        override suspend fun delete(id: String) {}
    }
    private val presetRepo = object : PresetRepository {
        override fun observeAll() = presets.map { it.sortedBy { p -> p.name } }
        override suspend fun save(preset: Preset) { presets.value += preset }
        override suspend fun delete(id: String) { presets.value = presets.value.filter { it.id != id } }
    }
    private val scheduler = object : JobScheduler {
        override fun schedule() {}
        override fun cancel(jobId: String) {}
    }
    private val registry = object : EngineRegistry {
        override val engines = listOf("ffmpeg", "pandoc").map { name ->
            object : ConversionEngine {
                override val id = EngineId(name)
                override val capabilities = emptySet<dev.staka.omniconverter.domain.model.Capability>()
                override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit) = ConversionResult.Failure(null, "")
                override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
                    ConversionResult.Failure(null, "")
            }
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CommandViewModel(
        ListEngines(registry), ObservePresets(presetRepo), SavePreset(presetRepo), DeletePreset(presetRepo),
        EnqueueConversions(jobRepo, scheduler),
    )

    @Test
    fun 最初のエンジンが選ばれている() {
        assertEquals(EngineId("ffmpeg"), vm().state.value.engine)
    }

    @Test
    fun 正しいコマンドなら全ファイルを登録する() {
        val vm = vm()
        vm.setFiles(files)
        vm.setText("-i {input} -vn {output}")
        vm.setExtension("mp3")
        var done = false
        vm.run { done = true }

        assertTrue(done)
        assertEquals(2, jobs.value.size)
        assertTrue(jobs.value.all { it.rawCommand?.args == listOf("-i", "{input}", "-vn", "{output}") })
        assertTrue(vm.state.value.files.isEmpty())
    }

    @Test
    fun 誤りがあれば登録せずに理由を出す() {
        val vm = vm()
        vm.setFiles(files)
        vm.setText("-i {input} out.mp3")
        vm.setExtension("mp3")
        vm.run { error("呼ばれない") }
        assertEquals(CommandError.MissingOutput, vm.state.value.error)
        assertTrue(jobs.value.isEmpty())

        vm.setText("-i {input} {output}")
        assertNull(vm.state.value.error)
    }

    @Test
    fun プリセットを保存して呼び出し削除できる() {
        val vm = vm()
        val collector = CoroutineScope(Dispatchers.Main).launch { vm.presets.collect {} }
        vm.selectEngine(EngineId("pandoc"))
        vm.setText("-f markdown -t docx -o {output} \"{input}\"")
        vm.setExtension("docx")
        vm.save("Word へ")
        val preset = vm.presets.value.single()
        assertEquals("Word へ", preset.name)

        vm.selectEngine(EngineId("ffmpeg"))
        vm.setText("")
        vm.apply(preset)
        assertEquals(EngineId("pandoc"), vm.state.value.engine)
        assertEquals("-f markdown -t docx -o {output} {input}", vm.state.value.text)
        assertEquals("docx", vm.state.value.extension)

        vm.delete(preset)
        assertTrue(vm.presets.value.isEmpty())
        collector.cancel()
    }
}
