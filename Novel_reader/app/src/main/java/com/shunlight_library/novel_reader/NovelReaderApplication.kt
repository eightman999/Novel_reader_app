/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * データベースとリポジトリの初期化を担当するアプリケーションクラス。
 */
package com.shunlight_library.novel_reader

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.*
import com.shunlight_library.novel_reader.data.database.NovelDatabase
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import com.shunlight_library.novel_reader.worker.UpdateCheckWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * アプリケーション全体で使用するシングルトンを初期化するクラス。
 * Room データベースとリポジトリへの参照を提供し、
 * WorkManager を利用して自動更新のスケジュールを設定する。
 */
class NovelReaderApplication : Application() {
    /** Room データベースを遅延初期化したインスタンス */
    val database by lazy { NovelDatabase.getDatabase(this) }

    /** データベースから生成されるリポジトリ */
    val repository by lazy {
        NovelRepository(
            database.episodeDao(),
            database.novelDescDao(),
            database.lastReadNovelDao(),
            database.updateQueueDao(),
            database.urlEntityDao(),
            database.imageCacheDao()
        )
    }

    companion object {
        private const val TAG = "NovelReaderApp"
        private lateinit var instance: NovelReaderApplication

        fun getAppContext(): Context = instance.applicationContext

        // リポジトリへの簡単なアクセス用
        fun getRepository(): NovelRepository = instance.repository

        // アプリケーションスコープへのアクセス用
        fun getApplicationScope(): CoroutineScope = instance.applicationScope
    }

    /** 自動更新ワークを管理する WorkManager インスタンス */
    private lateinit var workManager: WorkManager

    /** アプリケーション全体で使用する構造化されたCoroutineScope */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // WorkManagerの初期化
        workManager = WorkManager.getInstance(this)

        // アプリ起動時に自動更新スケジュールを設定
        setupUpdateSchedule()

    }

    /**
     * 自動更新スケジュールを設定する
     */
    private fun setupUpdateSchedule() {
        // 構造化されたCoroutineScopeで設定値を読み込んでスケジュールする
        applicationScope.launch(Dispatchers.IO) {
            val settingsStore = SettingsStore(this@NovelReaderApplication)
            val enabled = settingsStore.autoUpdateEnabled.first()
            val timeString = settingsStore.autoUpdateTime.first()

            scheduleUpdateWork(enabled, timeString)
        }
    }

    /**
     * 自動更新スケジュールを設定する
     * @param enabled 自動更新を有効にするかどうか
     * @param timeString 自動更新を実行する時間（HH:MM形式）
     */
    fun scheduleUpdateWork(enabled: Boolean, timeString: String) {
        // 既存のワークをキャンセル
        workManager.cancelUniqueWork("novel_daily_update")

        if (!enabled) {
            Log.d(TAG, "自動更新が無効化されています")
            return
        }

        try {
            // 時間文字列をパース
            val (hour, minute) = parseUpdateTime(timeString) ?: run {
                val settingsStore = SettingsStore(this)
                val fallbackTime = settingsStore.defaultAutoUpdateTime
                val fallbackParsed = parseUpdateTime(fallbackTime)

                if (fallbackParsed == null) {
                    Log.e(TAG, "自動更新時刻の解析に失敗しました: $timeString (fallback=$fallbackTime)")
                    return
                }

                Log.w(
                    TAG,
                    "自動更新時刻の形式が不正です: '$timeString'。デフォルト値 '$fallbackTime' を使用します"
                )
                fallbackParsed
            }

            // 次回の実行時間を計算
            val now = Calendar.getInstance()
            val scheduledTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // 指定時間が今日すでに過ぎていれば、明日に設定
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // 初回実行までの遅延時間をミリ秒で計算
            val initialDelay = scheduledTime.timeInMillis - now.timeInMillis

            val hourMinute = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
            Log.d(
                TAG,
                "次回の自動更新: $hourMinute, ${initialDelay / (60 * 60 * 1000)}時間${(initialDelay / (60 * 1000)) % 60}分後"
            )

            // WorkManagerによる定期実行の設定
            val dailyUpdateRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                24, TimeUnit.HOURS // 24時間ごとに実行
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS) // 初回実行までの遅延
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED) // ネットワーク接続が必要
                        .build()
                )
                .build()

            // ユニークワークとして登録（既存の同名ワークは置き換え）
            workManager.enqueueUniquePeriodicWork(
                "novel_daily_update",
                ExistingPeriodicWorkPolicy.REPLACE,
                dailyUpdateRequest
            )
        } catch (e: Exception) {
            Log.e(TAG, "自動更新スケジュール設定エラー: ${e.message}", e)
        }
    }

    private fun parseUpdateTime(timeString: String): Pair<Int, Int>? {
        val parts = timeString.split(":")
            .map { it.trim() }
        if (parts.size != 2) {
            return null
        }

        val hour = parts[0].toIntOrNull()
        val minute = parts[1].toIntOrNull()

        if (hour == null || minute == null) {
            return null
        }

        if (hour !in 0..23 || minute !in 0..59) {
            return null
        }

        return hour to minute
    }

    /**
     * アプリケーション終了時のクリーンアップ
     * 注: onTerminate()は通常のアプリ終了では呼ばれないが、適切なリソース管理のため定義
     */
    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }

}
