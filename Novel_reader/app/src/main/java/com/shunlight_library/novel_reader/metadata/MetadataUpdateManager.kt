package com.shunlight_library.novel_reader.metadata

import android.util.Log
import com.shunlight_library.novel_reader.api.NovelApiUtils
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class MetadataUpdateResult(
    val targets: Int,
    val updated: Int,
    val hadError: Boolean = false
)

object MetadataUpdateManager {
    private const val TAG = "NovelReaderApp"

    suspend fun updateMissingMetadata(
        repository: NovelRepository,
        onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): MetadataUpdateResult {
        return withContext(Dispatchers.IO) {
            try {
                val novels = repository.allNovels.first()
                val targets = novels.filter { it.needsMetadataSupplement() }

                onProgress(0, targets.size)

                var updatedCount = 0

                targets.forEachIndexed { index, novel ->
                    processNovel(repository, novel)?.let { afterImproved ->
                        if (afterImproved) {
                            updatedCount++
                        }
                    }

                    onProgress(index + 1, targets.size)
                }

                MetadataUpdateResult(targets.size, updatedCount)
            } catch (e: Exception) {
                Log.e(TAG, "メタデータのバッチ更新中にエラーが発生しました: ${e.message}", e)
                MetadataUpdateResult(0, 0, hadError = true)
            }
        }
    }

    private suspend fun processNovel(
        repository: NovelRepository,
        novel: NovelDescEntity
    ): Boolean? {
        val beforeCount = novel.metadataCount()
        val info = NovelApiUtils.fetchNovelInfo(novel.ncode, novel.rating == 1)
        if (info != null) {
            val updatedNovel = novel.copy(
                userid = novel.userid ?: info.userid,
                noveltype = novel.noveltype ?: info.noveltype,
                length = novel.length ?: info.length
            )
            val afterCount = updatedNovel.metadataCount()

            Log.w(
                TAG,
                "小説 ${novel.ncode} のメタデータが不足していたため取得しました (再取得前: ${beforeCount}項目, 再取得後: ${afterCount}項目)"
            )

            if (afterCount > beforeCount || updatedNovel != novel) {
                repository.updateNovel(updatedNovel)
            }

            return afterCount > beforeCount
        }

        return null
    }

    private fun NovelDescEntity.needsMetadataSupplement(): Boolean {
        return userid == null || noveltype == null || length == null
    }

    private fun NovelDescEntity.metadataCount(): Int {
        return listOf(userid, noveltype, length).count { it != null }
    }
}
