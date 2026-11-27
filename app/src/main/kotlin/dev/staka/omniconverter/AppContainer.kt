package dev.staka.omniconverter

import android.content.Context
import dev.staka.omniconverter.application.CancelJob
import dev.staka.omniconverter.application.ClearFinishedJobs
import dev.staka.omniconverter.application.DeletePreset
import dev.staka.omniconverter.application.ConversionPlanner
import dev.staka.omniconverter.application.EnqueueConversions
import dev.staka.omniconverter.application.ExecuteJob
import dev.staka.omniconverter.application.GetAvailableTargets
import dev.staka.omniconverter.application.ListEngines
import dev.staka.omniconverter.application.ObserveJobs
import dev.staka.omniconverter.application.ObserveOutputDir
import dev.staka.omniconverter.application.ObservePresets
import dev.staka.omniconverter.application.RetainSharedFiles
import dev.staka.omniconverter.application.SetOutputDir
import dev.staka.omniconverter.application.SavePreset
import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.infrastructure.db.Storage
import dev.staka.omniconverter.infrastructure.db.RoomJobRepository
import dev.staka.omniconverter.infrastructure.engine.ListEngineRegistry
import dev.staka.omniconverter.infrastructure.file.SafFileGateway
import dev.staka.omniconverter.infrastructure.settings.PrefsSettingsRepository
import dev.staka.omniconverter.infrastructure.work.ConversionWorkerFactory
import dev.staka.omniconverter.infrastructure.work.RunningJobs
import dev.staka.omniconverter.infrastructure.work.WorkJobScheduler
import dev.staka.omniconverter.presentation.UseCases
import kotlinx.coroutines.flow.first

/** 合成ルート。Infrastructure の実装をポートへ結びつける唯一の場所。ADR-0010 */
class AppContainer(context: Context, engines: List<ConversionEngine>) {
    private val storage = Storage(context)
    private val settings = PrefsSettingsRepository(context)
    private val files = SafFileGateway(context) { settings.outputDir().first() }
    private val dao = storage.jobDao
    private val repository = RoomJobRepository(dao)
    private val running = RunningJobs()
    private val scheduler = WorkJobScheduler(context, running)

    // 並び順がエンジンの優先順になる
    private val registry = ListEngineRegistry(engines)
    private val planner = ConversionPlanner(registry)

    val workerFactory = ConversionWorkerFactory(
        dao = dao,
        executeJob = ExecuteJob(repository, planner, registry, files),
        running = running,
    )

    val useCases = UseCases(
        getTargets = GetAvailableTargets(planner),
        enqueue = EnqueueConversions(repository, scheduler),
        observeJobs = ObserveJobs(repository),
        cancelJob = CancelJob(repository, scheduler),
        clearFinished = ClearFinishedJobs(repository, files),
        listEngines = ListEngines(registry),
        observePresets = ObservePresets(storage.presets),
        savePreset = SavePreset(storage.presets),
        deletePreset = DeletePreset(storage.presets),
        retainShared = RetainSharedFiles(files),
        observeOutputDir = ObserveOutputDir(settings),
        setOutputDir = SetOutputDir(settings),
    )
}
