package dev.staka.omniconverter.application

import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.Capability
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.Progress
import dev.staka.omniconverter.domain.port.ConversionJobRepository
import dev.staka.omniconverter.domain.port.EngineRegistry
import dev.staka.omniconverter.domain.port.FileGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.nio.file.Files
import java.nio.file.Path

/** "a>b" 形式で能力を宣言し、入力に自分の ID を追記して出力するエンジン */
class FakeEngine(name: String, vararg pairs: String, private val fail: Boolean = false) : ConversionEngine {
    override val id = EngineId(name)
    override val capabilities = pairs.map { p ->
        val (a, b) = p.split(">")
        Capability(Formats.of(a), Formats.of(b))
    }.toSet()
    val rawCalls = mutableListOf<List<String>>()

    override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult {
        onProgress(Progress(0.5f))
        if (fail) return ConversionResult.Failure(1, "boom")
        Files.writeString(task.output, Files.readString(task.input) + ">" + id.value)
        return ConversionResult.Success(task.output, "")
    }

    override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit): ConversionResult {
        rawCalls += args
        Files.writeString(output, "raw")
        return ConversionResult.Success(output, "")
    }
}

class FakeRegistry(override val engines: List<ConversionEngine>) : EngineRegistry

class FakeRepository : ConversionJobRepository {
    val jobs = MutableStateFlow<Map<String, ConversionJob>>(emptyMap())
    val history = mutableListOf<ConversionJob>()
    override fun observeAll(): Flow<List<ConversionJob>> = jobs.map { it.values.toList() }
    override suspend fun get(id: String) = jobs.value[id]
    override suspend fun upsert(jobs: List<ConversionJob>) {
        history += jobs
        this.jobs.value = this.jobs.value + jobs.associateBy { it.id }
    }
    override suspend fun delete(id: String) {
        jobs.value = jobs.value - id
    }
}

/** URI をファイル内容とみなし、書き出したファイルを [exported] に残す */
class FakeFiles : FileGateway {
    val root: Path = Files.createTempDirectory("omni-test")
    val exported = mutableMapOf<String, String>()
    val deleted = mutableListOf<Path>()
    override suspend fun importToWorkspace(source: FileRef, workDir: Path): Path =
        workDir.resolve(source.name).also { Files.writeString(it, source.uri) }
    override suspend fun export(file: Path, name: String): FileRef {
        exported[name] = Files.readString(file)
        return FileRef("out://$name", name)
    }
    override suspend fun newWorkDir(): Path = Files.createTempDirectory(root, "job")
    override suspend fun deleteWorkDir(dir: Path) {
        deleted.add(dir)
    }
    val released = mutableListOf<FileRef>()
    override suspend fun retain(source: FileRef) = source.copy(uri = "kept://${source.uri}")
    override suspend fun release(source: FileRef) {
        released.add(source)
    }
}
