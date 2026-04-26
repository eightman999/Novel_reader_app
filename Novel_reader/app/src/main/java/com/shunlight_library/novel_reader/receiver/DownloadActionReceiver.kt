/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * BroadcastReceiver for handling notification actions.
 */
package com.shunlight_library.novel_reader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.shunlight_library.novel_reader.worker.AutoUpdateWorker
import java.util.concurrent.TimeUnit

class DownloadActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DownloadActionReceiver"
        const val ACTION_DOWNLOAD_ALL = "com.shunlight_library.novel_reader.ACTION_DOWNLOAD_ALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            ACTION_DOWNLOAD_ALL -> {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<AutoUpdateWorker>()
                    .setConstraints(constraints)
                    .addTag("notification_bulk_update")
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
                Log.d(TAG, "Enqueued AutoUpdateWorker for bulk update via WorkManager")
            }
        }
    }
}
