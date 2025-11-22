/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Schedules automatic update worker.
 */
package com.shunlight_library.novel_reader.worker

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.work.*
import kotlinx.coroutines.guava.await
import java.util.concurrent.TimeUnit
import java.util.Calendar

class AutoUpdateScheduler(private val context: Context) {
    
    companion object {
        private const val WORK_NAME = "auto_update_work"
        private const val TAG = "AutoUpdateScheduler"
    }

    private val workManager = WorkManager.getInstance(context)

    /**
     * 自動更新を有効にし、指定時刻にスケジュール
     * @param enabled 自動更新の有効/無効
     * @param timeString 時刻文字列 (例: "03:00")
     */
    fun scheduleAutoUpdate(enabled: Boolean, timeString: String) {
        if (!enabled) {
            cancelAutoUpdate()
            return
        }

        try {
            val (hour, minute) = parseTimeString(timeString)
            val delay = calculateInitialDelay(hour, minute)
            
            Log.d(TAG, "自動更新スケジュール設定: ${timeString} (${delay}分後に開始)")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // バッテリー低下時でも実行
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AutoUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(TAG, "自動更新スケジュール登録完了")
        } catch (e: Exception) {
            Log.e(TAG, "自動更新スケジュール設定エラー", e)
        }
    }

    /**
     * 自動更新をキャンセル
     */
    fun cancelAutoUpdate() {
        workManager.cancelUniqueWork(WORK_NAME)
        Log.d(TAG, "自動更新スケジュールをキャンセル")
    }

    /**
     * 手動で更新チェックを即座に実行
     */
    fun runManualUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AutoUpdateWorker>()
            .setConstraints(constraints)
            .addTag("manual_update")
            .build()

        workManager.enqueue(workRequest)
        Log.d(TAG, "手動更新を実行")
    }

    /**
     * 現在のワーク状態を取得
     */
    fun getWorkStatus(): LiveData<List<WorkInfo>> {
        return workManager.getWorkInfosForUniqueWorkLiveData(WORK_NAME)
    }

    /**
     * バックログや実行待ちのワークがあるかチェック
     * @return ワークが実行待ち、または実行中の場合はtrue
     */
    suspend fun hasPendingWork(): Boolean {
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME).await()
            workInfos.any { workInfo ->
                workInfo.state == WorkInfo.State.ENQUEUED ||
                workInfo.state == WorkInfo.State.RUNNING ||
                workInfo.state == WorkInfo.State.BLOCKED
            }
        } catch (e: Exception) {
            Log.e(TAG, "ワーク状態の取得に失敗", e)
            false
        }
    }

    /**
     * スケジュールをリセット（バックログをクリアして再スケジュール）
     * @param enabled 自動更新の有効/無効
     * @param timeString 時刻文字列 (例: "03:00")
     */
    fun resetSchedule(enabled: Boolean, timeString: String) {
        // 既存のワークをキャンセル
        cancelAutoUpdate()

        // 再スケジュール
        scheduleAutoUpdate(enabled, timeString)

        Log.d(TAG, "スケジュールをリセットしました")
    }

    /**
     * 時刻文字列（HH:mm）をパース
     */
    private fun parseTimeString(timeString: String): Pair<Int, Int> {
        val parts = timeString.split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("時刻形式が正しくありません: $timeString")
        }
        
        val hour = parts[0].toIntOrNull() ?: throw IllegalArgumentException("時間が無効です: ${parts[0]}")
        val minute = parts[1].toIntOrNull() ?: throw IllegalArgumentException("分が無効です: ${parts[1]}")
        
        if (hour !in 0..23 || minute !in 0..59) {
            throw IllegalArgumentException("時刻の範囲が無効です: $hour:$minute")
        }
        
        return Pair(hour, minute)
    }

    /**
     * 指定時刻までの初期遅延時間（分）を計算
     */
    private fun calculateInitialDelay(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 既に今日の時刻を過ぎている場合は明日に設定
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        val delayMillis = target.timeInMillis - now.timeInMillis
        return delayMillis / (1000 * 60) // 分に変換
    }
}
