/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * BroadcastReceiver for handling "Download All" action from notifications
 */
package com.shunlight_library.novel_reader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shunlight_library.novel_reader.service.UpdateService

/**
 * 通知からの「すべてダウンロード」アクション処理を行うBroadcastReceiver
 *
 * 自動更新の通知に表示される「すべてダウンロード」ボタンをタップすると、
 * このレシーバーが起動され、UpdateServiceを使って一括ダウンロードを実行する。
 */
class DownloadAllReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DOWNLOAD_ALL = "com.shunlight_library.novel_reader.ACTION_DOWNLOAD_ALL"
        private const val TAG = "DownloadAllReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DOWNLOAD_ALL) {
            Log.d(TAG, "ダウンロールボタンがタップされました")

            // UpdateServiceを起動して一括ダウンロードを実行
            val serviceIntent = Intent(context, UpdateService::class.java).apply {
                action = UpdateService.ACTION_START_UPDATE
                putExtra(UpdateService.EXTRA_UPDATE_TYPE, UpdateService.UPDATE_TYPE_BULK_UPDATE)
            }
            context.startService(serviceIntent)

            Log.d(TAG, "UpdateServiceで一括ダウンロードを開始しました")
        }
    }
}
