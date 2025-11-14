/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Syosetu (小説家になろう) site adapter with API support.
 */
package com.shunlight_library.novel_reader.data.adapter

import com.shunlight_library.novel_reader.api.NovelApiUtils
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小説家になろう用のアダプター実装
 *
 * 既存のNovelApiUtilsを活用してAPI/HTMLスクレイピングを実行する。
 * - API: 小説情報の取得
 * - HTMLスクレイピング: エピソード本文の取得
 *
 * URL構造:
 * - 一般作品: https://ncode.syosetu.com/{ncode}/
 * - R18作品: https://novel18.syosetu.com/{ncode}/
 * - エピソード: https://ncode.syosetu.com/{ncode}/{episodeNo}/
 */
class SyosetuAdapter : NovelSiteAdapter {
    override fun getSiteType(): Int = NovelSiteAdapter.SITE_TYPE_SYOSETU

    override fun getSiteName(): String = "小説家になろう"

    override fun generateWebUrl(novelId: String): String {
        // ncodeの最初の文字でR18判定（簡易的）
        // 実際にはNovelDescEntityのratingフィールドで判定すべきだが、
        // ここではncodeのみから生成する必要があるため、一般サイトをデフォルトとする
        return "https://ncode.syosetu.com/$novelId/"
    }

    override fun generateEpisodeUrl(novelId: String, episodeId: String): String {
        return "https://ncode.syosetu.com/$novelId/$episodeId/"
    }

    override suspend fun fetchNovelWithEpisodes(novelId: String): Pair<NovelDescEntity, List<EpisodeEntity>> = withContext(Dispatchers.IO) {
        // R18判定（基本的にはURLEntityから取得すべきだが、ここでは簡易的に判定）
        // 実際の実装では、URLEntityやユーザー選択から判定する
        val isR18 = false  // デフォルトは一般

        // 小説情報を取得
        val novelDesc = NovelApiUtils.fetchNovelDetails(novelId, isR18)
            ?: throw Exception("Failed to fetch novel details for ncode: $novelId")

        // エピソード一覧を取得
        val episodes = mutableListOf<EpisodeEntity>()
        for (episodeNo in 1..novelDesc.general_all_no) {
            val episode = NovelApiUtils.fetchEpisodeWithRetry(
                ncode = novelId,
                episodeNo = episodeNo,
                isR18 = isR18,
                noveltype = novelDesc.noveltype
            )
            if (episode != null) {
                episodes.add(episode)
            } else {
                // エピソード取得失敗時は警告ログを出すが、処理は継続
                android.util.Log.w("SyosetuAdapter", "Failed to fetch episode $episodeNo for ncode: $novelId")
            }
        }

        // NovelDescEntityにsite_typeを設定
        val updatedNovelDesc = novelDesc.copy(site_type = NovelSiteAdapter.SITE_TYPE_SYOSETU)

        Pair(updatedNovelDesc, episodes)
    }

    override suspend fun checkForUpdates(novelId: String, currentEpisodeCount: Int): Boolean = withContext(Dispatchers.IO) {
        val isR18 = false  // デフォルトは一般（実際にはURLEntityから判定）

        // API情報を取得して最新のエピソード数を確認
        val apiInfo = NovelApiUtils.fetchNovelInfo(ncode = novelId, isR18 = isR18)
            ?: return@withContext false

        apiInfo.generalAllNo > currentEpisodeCount
    }

    override fun extractNovelIdFromUrl(url: String): String? {
        // NovelApiUtils.extractNcodeFromUrlを活用
        val (ncode, _) = NovelApiUtils.extractNcodeFromUrl(url)
        return ncode
    }

    override fun isValidNovelId(novelId: String): Boolean {
        // Ncodeは空でなければ有効とする（フォーマット検証は削除）
        return novelId.isNotEmpty()
    }

    /**
     * R18判定付きで小説情報とエピソード一覧を取得
     * Repository層から呼び出される際に使用
     */
    suspend fun fetchNovelWithEpisodesR18(novelId: String, isR18: Boolean): Pair<NovelDescEntity, List<EpisodeEntity>> = withContext(Dispatchers.IO) {
        // 小説情報を取得
        val novelDesc = NovelApiUtils.fetchNovelDetails(novelId, isR18)
            ?: throw Exception("Failed to fetch novel details for ncode: $novelId")

        // エピソード一覧を取得
        val episodes = mutableListOf<EpisodeEntity>()
        for (episodeNo in 1..novelDesc.general_all_no) {
            val episode = NovelApiUtils.fetchEpisodeWithRetry(
                ncode = novelId,
                episodeNo = episodeNo,
                isR18 = isR18,
                noveltype = novelDesc.noveltype
            )
            if (episode != null) {
                episodes.add(episode)
            } else {
                android.util.Log.w("SyosetuAdapter", "Failed to fetch episode $episodeNo for ncode: $novelId")
            }
        }

        // NovelDescEntityにsite_typeを設定
        val updatedNovelDesc = novelDesc.copy(site_type = NovelSiteAdapter.SITE_TYPE_SYOSETU)

        Pair(updatedNovelDesc, episodes)
    }

    /**
     * R18判定付きで更新チェック
     */
    suspend fun checkForUpdatesR18(novelId: String, currentEpisodeCount: Int, isR18: Boolean): Boolean = withContext(Dispatchers.IO) {
        val apiInfo = NovelApiUtils.fetchNovelInfo(ncode = novelId, isR18 = isR18)
            ?: return@withContext false

        apiInfo.generalAllNo > currentEpisodeCount
    }

    /**
     * URLからR18判定を含めてNcodeを抽出
     */
    fun extractNcodeWithR18FromUrl(url: String): Pair<String?, Boolean> {
        return NovelApiUtils.extractNcodeFromUrl(url)
    }
}
