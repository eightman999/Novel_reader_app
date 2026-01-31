/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * データベースとリポジトリの初期化を担当するアプリケーションクラス。
 */
package com.shunlight_library.novel_reader

import android.app.Application
import android.content.Context
import com.shunlight_library.novel_reader.data.database.NovelDatabase
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import kotlinx.coroutines.*

/**
 * アプリケーション全体で使用するシングルトンを初期化するクラス。
 * Room データベースとリポジトリへの参照を提供する。
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
            database.imageCacheDao(),
            database.episodeMappingDao(),
            database.registrationQueueDao(),
            database.tempEpisodeDao()
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

    /** アプリケーション全体で使用する構造化されたCoroutineScope */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 新規登録キューの監視を開始
        com.shunlight_library.novel_reader.manager.RegistrationQueueManager.startMonitoring()
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
