package ru.mtproxymanager.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import ru.mtproxymanager.MtProxyApplication
import ru.mtproxymanager.util.Redactor
import java.util.concurrent.TimeUnit

private const val TAG = "MtProxyWork"
private const val MAX_ATTEMPTS = 3

class SourcesRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork() = runSafely {
        (applicationContext as MtProxyApplication).container.repository.refreshSources()
    }
}

class RecheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork() = runSafely {
        val repository = (applicationContext as MtProxyApplication).container.repository
        repository.checkAll(repository.snapshot())
    }
}

private suspend fun CoroutineWorker.runSafely(block: suspend () -> Unit): androidx.work.ListenableWorker.Result =
    try {
        block()
        androidx.work.ListenableWorker.Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, Redactor.redact("Фоновая задача не выполнена: $e"))
        if (runAttemptCount < MAX_ATTEMPTS) {
            androidx.work.ListenableWorker.Result.retry()
        } else {
            androidx.work.ListenableWorker.Result.failure()
        }
    }

object BackgroundWork {
    private const val SOURCES_WORK = "sources-refresh"
    private const val RECHECK_WORK = "proxy-recheck"

    fun schedule(context: Context, refreshHours: Int) {
        val manager = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        manager.enqueueUniquePeriodicWork(
            SOURCES_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SourcesRefreshWorker>(refreshHours.toLong(), TimeUnit.HOURS)
                .setConstraints(network)
                .build(),
        )

        manager.enqueueUniquePeriodicWork(
            RECHECK_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build(),
        )
    }
}
