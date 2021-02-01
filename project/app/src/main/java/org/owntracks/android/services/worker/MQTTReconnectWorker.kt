package org.owntracks.android.services.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.owntracks.android.injection.components.AppComponentProvider
import org.owntracks.android.services.MessageProcessor
import timber.log.Timber
import java.util.concurrent.Semaphore
import javax.inject.Inject

class MQTTReconnectWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    @JvmField
    @Inject
    var messageProcessor: MessageProcessor? = null
    override fun doWork(): Result {
        Timber.i("MQTTReconnectWorker Doing work on threadID: %s", Thread.currentThread())
        if (!messageProcessor!!.isEndpointConfigurationComplete) return Result.failure()
        // We're going to try and call messagePrcessor.reconnect() here, which may reinvoke itself on
        // a different thread. One option here was to faff around with futures, but it seems easier just to
        // Create a semaphore, pass it to the method and then just wait for it to be released, no matter where from.
        val lock = Semaphore(1)
        lock.acquireUninterruptibly()
        messageProcessor!!.reconnect(lock)
        return try {
            lock.acquire()
            if (messageProcessor!!.statefulCheckConnection()) Result.success() else Result.retry()
        } catch (e: InterruptedException) {
            Result.failure()
        }
    }

    init {
        AppComponentProvider.getAppComponent().inject(this)
    }
}