package dev.staka.omniconverter.application

import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.domain.model.Progress
import dev.staka.omniconverter.domain.model.RawCommand
import dev.staka.omniconverter.domain.port.ConversionJobRepository
import dev.staka.omniconverter.domain.port.EngineRegistry
import dev.staka.omniconverter.domain.port.FileGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/** ジョブ 1 件を実行する。取り込み、変換、書き出し、状態の更新までを受け持つ */
class ExecuteJob(
    private val repository: ConversionJobRepository,
    private val planner: ConversionPlanner,
    private val registry: EngineRegistry,
    private val files: FileGateway,
) {
    suspend operator fun invoke(jobId: String) {
        val job = repository.get(jobId) ?: return
        if (job.status != JobStatus.QUEUED) return
        val running = job.copy(status = JobStatus.RUNNING, progress = 0f)
        save(running)

        val workDir = files.newWorkDir()
        try {
            val finished = coroutineScope {
                // StateFlow は値を間引くので、保存が進捗の通知に追いつかなくても溜まらない
                val progress = MutableStateFlow<Float?>(null)
                val writer = launch { progress.filterNotNull().collect { save(running.copy(progress = it)) } }
                try {
                    run(running, workDir) { progress.value = it }
                } finally {
                    writer.cancel()
                }
            }
            save(finished)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { save(running.copy(status = JobStatus.CANCELLED, progress = null)) }
            throw e
        } catch (e: Exception) {
            save(running.copy(status = JobStatus.FAILED, progress = null, error = e.message ?: e.toString()))
        } finally {
            withContext(NonCancellable) {
                files.deleteWorkDir(workDir)
                files.release(job.source)
            }
        }
    }

    private suspend fun run(job: ConversionJob, workDir: Path, onProgress: (Float) -> Unit): ConversionJob {
        val input = files.importToWorkspace(job.source, workDir)
        val (result, extension) = job.rawCommand?.let { runRaw(it, input, workDir, onProgress) to it.outputExtension }
            ?: (runPlan(job, input, workDir, onProgress) to job.target!!.extensions.first())

        return when (result) {
            is ConversionResult.Success -> {
                val output = files.export(result.output, "${baseName(job.source.name)}.$extension")
                job.copy(status = JobStatus.SUCCEEDED, progress = 1f, output = output)
            }
            is ConversionResult.Failure -> job.copy(status = JobStatus.FAILED, progress = null, error = tail(result.log))
        }
    }

    private suspend fun runPlan(job: ConversionJob, input: Path, workDir: Path, onProgress: (Float) -> Unit): ConversionResult {
        val from = Formats.detect(job.source.name) ?: error("形式を判定できません: ${job.source.name}")
        val target = job.target!!
        val plan = planner.plan(from, target) ?: error("${from.id} から ${target.id} へは変換できません")

        var current = input
        var result: ConversionResult = ConversionResult.Failure(null, "")
        plan.steps.forEachIndexed { i, step ->
            val out = workDir.resolve("step$i.${step.to.extensions.first()}")
            val options = if (i == plan.steps.lastIndex) job.options else emptyMap()
            result = step.engine.convert(ConversionTask(current, out, step.from, step.to, options)) { p: Progress ->
                onProgress((i + (p.fraction ?: 0f)) / plan.steps.size)
            }
            if (result is ConversionResult.Failure) return result
            current = (result as ConversionResult.Success).output
        }
        return result
    }

    private suspend fun runRaw(command: RawCommand, input: Path, workDir: Path, onProgress: (Float) -> Unit): ConversionResult {
        val engine = registry.engines.firstOrNull { it.id == command.engine } ?: error("エンジンがありません: ${command.engine.value}")
        val output = workDir.resolve("out.${command.outputExtension}")
        val args = substitute(command.args, input, output)
        return engine.runRaw(args, workDir, output) { p -> p.fraction?.let(onProgress) }
    }

    private suspend fun save(job: ConversionJob) = repository.upsert(listOf(job))

    companion object {
        fun substitute(args: List<String>, input: Path, output: Path): List<String> =
            args.map { it.replace("{input}", input.toString()).replace("{output}", output.toString()) }

        fun baseName(name: String): String {
            val ext = Formats.detect(name)?.extensions?.firstOrNull { name.lowercase().endsWith(".$it") }
            return if (ext != null) name.dropLast(ext.length + 1) else name.substringBeforeLast('.')
        }

        private fun tail(log: String) = log.lines().takeLast(20).joinToString("\n")
    }
}
