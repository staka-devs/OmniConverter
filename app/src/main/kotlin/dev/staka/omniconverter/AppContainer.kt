package dev.staka.omniconverter

import android.content.Context
import dev.staka.omniconverter.application.CancelJob
import dev.staka.omniconverter.application.ClearFinishedJobs
import dev.staka.omniconverter.application.ConversionPlanner
import dev.staka.omniconverter.application.EnqueueConversions
import dev.staka.omniconverter.application.ExecuteJob
import dev.staka.omniconverter.application.GetAvailableTargets
import dev.staka.omniconverter.application.ObserveJobs
import dev.staka.omniconverter.infrastructure.db.createJobDao
import dev.staka.omniconverter.infrastructure.db.RoomJobRepository
import dev.staka.omniconverter.infrastructure.engine.ListEngineRegistry
import dev.staka.omniconverter.infrastructure.file.SafFileGateway
import dev.staka.omniconverter.infrastructure.work.ConversionWorkerFactory
import dev.staka.omniconverter.infrastructure.work.RunningJobs
import dev.staka.omniconverter.infrastructure.work.WorkJobScheduler
import dev.staka.omniconverter.presentation.UseCases

/** 合成ルート。Infrastructure の実装をポートへ結びつける唯一の場所。ADR-0010 */
class AppContainer(context: Context) {
    private val dao = createJobDao(context)
    private val repository = RoomJobRepository(dao)
    private val running = RunningJobs()
    private val scheduler = WorkJobScheduler(context, running)

    // 並び順がエンジンの優先順になる
    private val registry = ListEngineRegistry(emptyList())
    private val planner = ConversionPlanner(registry)

    val workerFactory = ConversionWorkerFactory(
        dao = dao,
        executeJob = ExecuteJob(repository, planner, registry, SafFileGateway(context)),
        running = running,
    )

    val useCases = UseCases(
        getTargets = GetAvailableTargets(planner),
        enqueue = EnqueueConversions(repository, scheduler),
        observeJobs = ObserveJobs(repository),
        cancelJob = CancelJob(repository, scheduler),
        clearFinished = ClearFinishedJobs(repository),
    )
}
