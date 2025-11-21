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
import com.shunlight_library.novel_reader.service.UpdateService

/**
 * プッシュ通知のアクションボタンを処理するBroadcastReceiver
 */
class DownloadActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DownloadActionReceiver"
        const val ACTION_DOWNLOAD_ALL = "com.shunlight_library.novel_reader.ACTION_DOWNLOAD_ALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            ACTION_DOWNLOAD_ALL -> {
                // UpdateServiceを起動してバルク更新を実行
                val serviceIntent = Intent(context, UpdateService::class.java).apply {
                    action = UpdateService.ACTION_START_UPDATE
                    putExtra(UpdateService.EXTRA_UPDATE_TYPE, UpdateService.UPDATE_TYPE_BULK_UPDATE)
                }
                context.startService(serviceIntent)
                Log.d(TAG, "Started UpdateService for bulk update")
            }
        }
    }
}
