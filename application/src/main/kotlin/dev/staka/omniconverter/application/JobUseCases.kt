package dev.staka.omniconverter.application

import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Format
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.domain.model.RawCommand
import dev.staka.omniconverter.domain.port.ConversionJobRepository
import dev.staka.omniconverter.domain.port.FileGateway
import dev.staka.omniconverter.domain.port.JobScheduler
import dev.staka.omniconverter.domain.port.SettingsRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** 選んだファイルすべてに共通して変換できる形式 */
class GetAvailableTargets(private val planner: ConversionPlanner) {
    operator fun invoke(sources: List<FileRef>): List<Format> {
        val froms = sources.map { Formats.detect(it.name) ?: return emptyList() }.distinct()
        if (froms.isEmpty()) return emptyList()
        val common = froms.map { planner.targets(it) }.reduce { acc, s -> acc intersect s }
        return Formats.all.filter { it in common }
    }
}

class EnqueueConversions(
    private val repository: ConversionJobRepository,
    private val scheduler: JobScheduler,
) {
    suspend operator fun invoke(sources: List<FileRef>, target: Format, options: Map<String, String> = emptyMap()) =
        enqueue(sources.map { ConversionJob(UUID.randomUUID().toString(), it, target, options) })

    suspend operator fun invoke(sources: List<FileRef>, command: RawCommand) =
        enqueue(sources.map { ConversionJob(UUID.randomUUID().toString(), it, target = null, rawCommand = command) })

    private suspend fun enqueue(jobs: List<ConversionJob>) {
        repository.upsert(jobs)
        scheduler.schedule()
    }
}

class ObserveJobs(private val repository: ConversionJobRepository) {
    operator fun invoke(): Flow<List<ConversionJob>> = repository.observeAll()
}

class CancelJob(
    private val repository: ConversionJobRepository,
    private val scheduler: JobScheduler,
) {
    suspend operator fun invoke(id: String) {
        val job = repository.get(id) ?: return
        if (job.status == JobStatus.QUEUED || job.status == JobStatus.RUNNING) {
            scheduler.cancel(id)
            repository.upsert(listOf(job.copy(status = JobStatus.CANCELLED)))
        }
    }
}

/** 終わったジョブを一覧から消す。実行前に取り消したジョブの写しもここで消す */
class ClearFinishedJobs(
    private val repository: ConversionJobRepository,
    private val files: FileGateway,
) {
    suspend operator fun invoke(jobs: List<ConversionJob>) {
        jobs.filter { it.status != JobStatus.QUEUED && it.status != JobStatus.RUNNING }.forEach {
            repository.delete(it.id)
            files.release(it.source)
        }
    }
}

/** 他のアプリから共有されたファイルを、変換が終わるまで読めるよう手元に写す */
class RetainSharedFiles(private val files: FileGateway) {
    suspend operator fun invoke(sources: List<FileRef>): List<FileRef> = sources.map { files.retain(it) }
}

class ObserveOutputDir(private val settings: SettingsRepository) {
    operator fun invoke(): Flow<FileRef?> = settings.outputDir()
}

class SetOutputDir(private val settings: SettingsRepository) {
    suspend operator fun invoke(dir: FileRef?) = settings.setOutputDir(dir)
}
