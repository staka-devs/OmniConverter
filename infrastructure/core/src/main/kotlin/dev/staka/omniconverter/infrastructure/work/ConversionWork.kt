package dev.staka.omniconverter.infrastructure.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.staka.omniconverter.application.ExecuteJob
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.domain.port.JobScheduler
import dev.staka.omniconverter.infrastructure.core.R
import dev.staka.omniconverter.infrastructure.db.JobDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** 実行中ジョブのコルーチン。個別キャンセルに使う */
class RunningJobs {
    private val jobs = ConcurrentHashMap<String, Job>()
    fun put(id: String, job: Job) { jobs[id] = job }
    fun remove(id: String) { jobs.remove(id) }
    fun cancel(id: String) { jobs[id]?.cancel() }
}

/** 待機中のジョブがなくなるまで処理する。同時に動かすのは 1 つだけ。ADR-0009 */
class ConversionWorker(
    context: Context,
    params: WorkerParameters,
    private val dao: JobDao,
    private val executeJob: ExecuteJob,
    private val running: RunningJobs,
    private val parallelism: Int,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())
        dao.requeueRunning()
        val semaphore = Semaphore(parallelism)
        val processed = mutableListOf<String>()
        coroutineScope {
            while (true) {
                val ids = dao.queuedIds()
                if (ids.isEmpty()) break
                processed += ids
                ids.map { id ->
                    launch { semaphore.withPermit { executeJob(id) } }
                        .also { job -> running.put(id, job); job.invokeOnCompletion { running.remove(id) } }
                }.joinAll()
            }
        }
        notifyFinished(processed)
        return Result.success()
    }

    /** キューが空になったら、成功と失敗の件数を知らせる。押すとアプリを開く */
    private suspend fun notifyFinished(ids: List<String>) {
        if (ids.isEmpty()) return
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val statuses = ids.mapNotNull { dao.get(it)?.status }
        val succeeded = statuses.count { it == JobStatus.SUCCEEDED.name }
        val failed = statuses.count { it == JobStatus.FAILED.name }

        val context = applicationContext
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(DONE_CHANNEL, context.getString(R.string.done_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, DONE_CHANNEL)
            .setContentTitle(context.getString(R.string.done_title))
            .setContentText(context.getString(R.string.done_text, succeeded, failed))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(DONE_NOTIFICATION_ID, notification)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.conversion_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle(applicationContext.getString(R.string.conversion_running))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val UNIQUE_NAME = "conversion-queue"
        private const val CHANNEL = "conversion"
        private const val NOTIFICATION_ID = 1
        private const val DONE_CHANNEL = "conversion-done"
        private const val DONE_NOTIFICATION_ID = 2
    }
}

class ConversionWorkerFactory(
    private val dao: JobDao,
    private val executeJob: ExecuteJob,
    private val running: RunningJobs,
    // shortcut: 並列数は固定。端末ごとに調整したくなったら設定画面から渡す
    private val parallelism: Int = 2,
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == ConversionWorker::class.java.name) {
            ConversionWorker(appContext, workerParameters, dao, executeJob, running, parallelism)
        } else {
            null
        }
}

class WorkJobScheduler(private val context: Context, private val running: RunningJobs) : JobScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // 実行中のワーカーは新しく積まれたジョブも拾うので、APPEND_OR_REPLACE で取りこぼしだけ防ぐ
        WorkManager.getInstance(context).enqueueUniqueWork(ConversionWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun cancel(jobId: String) = running.cancel(jobId)
}
