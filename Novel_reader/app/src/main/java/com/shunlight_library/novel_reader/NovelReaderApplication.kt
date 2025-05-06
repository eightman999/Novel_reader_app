package com.shunlight_library.novel_reader

import android.app.Application
import android.content.Context
import com.shunlight_library.novel_reader.data.database.NovelDatabase
import com.shunlight_library.novel_reader.data.repository.NovelRepository

class NovelReaderApplication : Application() {
    // データベースとリポジトリのlazy初期化
    val database by lazy { NovelDatabase.getDatabase(this) }
    val repository by lazy {
        NovelRepository(
            database.episodeDao(),
            database.novelDescDao(),
            database.lastReadNovelDao(),
            database.updateQueueDao()
        )
    }

    companion object {
        private lateinit var instance: NovelReaderApplication

        fun getAppContext(): Context = instance.applicationContext

        // リポジトリへの簡単なアクセス用
        fun getRepository(): NovelRepository = instance.repository
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}