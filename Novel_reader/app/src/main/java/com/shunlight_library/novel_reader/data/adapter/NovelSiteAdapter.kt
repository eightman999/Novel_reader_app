/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Interface for site-specific novel data adapters.
 */
package com.shunlight_library.novel_reader.data.adapter

import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity

/**
 * 小説サイト固有のデータ取得ロジックを実装するアダプターインターフェース
 *
 * 各小説サイトは、このインターフェースを実装した専用アダプターを持つ。
 * - 小説家になろう: SyosetuAdapter
 * - カクヨム: KakuyomuAdapter
 *
 * アダプターパターンを使用することで、サイト固有のロジックを分離し、
 * 新しい小説サイトの追加を容易にする。
 */
interface NovelSiteAdapter {
    companion object {
        /**
         * サイト種別定数
         */
        const val SITE_TYPE_SYOSETU = 1  // 小説家になろう
        const val SITE_TYPE_KAKUYOMU = 2  // カクヨム
    }

    /**
     * サイト種別を取得
     *
     * @return サイト種別 (SITE_TYPE_SYOSETU または SITE_TYPE_KAKUYOMU)
     */
    fun getSiteType(): Int

    /**
     * サイト名を取得
     *
     * @return サイト名（表示用）
     */
    fun getSiteName(): String

    /**
     * 小説の閲覧URLを生成
     *
     * @param novelId 小説ID（Syosetu: ncode、Kakuyomu: workId）
     * @return 小説のWebページURL
     */
    fun generateWebUrl(novelId: String): String

    /**
     * エピソードの閲覧URLを生成
     *
     * @param novelId 小説ID
     * @param episodeId エピソードID（Syosetu: episodeNo、Kakuyomu: episodeId）
     * @return エピソードのWebページURL
     */
    fun generateEpisodeUrl(novelId: String, episodeId: String): String

    /**
     * 小説の基本情報とエピソード一覧を取得
     *
     * サイトのAPI/HTMLから小説のメタデータと全エピソード情報を取得する。
     * 初回登録時や強制更新時に使用。
     *
     * @param novelId 小説ID
     * @return Pair<NovelDescEntity, List<EpisodeEntity>> 小説情報とエピソードリスト
     * @throws Exception サイトへのアクセスやパースに失敗した場合
     */
    suspend fun fetchNovelWithEpisodes(novelId: String): Pair<NovelDescEntity, List<EpisodeEntity>>

    /**
     * 更新チェックを実行
     *
     * 現在のエピソード数と比較して更新があるかを確認する。
     * fetchNovelWithEpisodes より軽量な実装が望ましい。
     *
     * @param novelId 小説ID
     * @param currentEpisodeCount 現在保存されているエピソード数
     * @return 更新がある場合 true
     * @throws Exception サイトへのアクセスやパースに失敗した場合
     */
    suspend fun checkForUpdates(novelId: String, currentEpisodeCount: Int): Boolean

    /**
     * URLから小説IDを抽出
     *
     * @param url サイトのURL（小説ページまたはエピソードページ）
     * @return 小説ID（抽出できない場合は null）
     */
    fun extractNovelIdFromUrl(url: String): String?

    /**
     * 小説IDの妥当性を検証
     *
     * @param novelId 検証対象の小説ID
     * @return 妥当な場合 true
     */
    fun isValidNovelId(novelId: String): Boolean
}
