/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Factory for creating and managing site-specific adapters.
 */
package com.shunlight_library.novel_reader.data.adapter

import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator

/**
 * サイト別アダプターのファクトリークラス
 *
 * - サイト種別からアダプターを取得
 * - URLからサイトを自動判定してアダプターを取得
 * - Ncodeからサイトを判定してアダプターを取得
 *
 * シングルトンパターンでアダプターインスタンスを管理し、
 * 無駄なインスタンス生成を防ぐ。
 */
object NovelSiteAdapterFactory {
    // アダプターインスタンスをキャッシュ
    private val adapters = mapOf(
        NovelSiteAdapter.SITE_TYPE_SYOSETU to SyosetuAdapter(),
        NovelSiteAdapter.SITE_TYPE_KAKUYOMU to KakuyomuAdapter()
    )

    /**
     * サイト種別からアダプターを取得
     *
     * @param siteType サイト種別 (SITE_TYPE_SYOSETU または SITE_TYPE_KAKUYOMU)
     * @return 対応するアダプター
     * @throws IllegalArgumentException 未対応のサイト種別の場合
     */
    fun getAdapter(siteType: Int): NovelSiteAdapter {
        return adapters[siteType]
            ?: throw IllegalArgumentException("Unsupported site type: $siteType")
    }

    /**
     * Ncodeからサイトを判定してアダプターを取得
     *
     * - "K"で始まる → カクヨム
     * - それ以外 → 小説家になろう
     *
     * @param ncode 小説のNcode（またはPseudo-Ncode）
     * @return 対応するアダプター
     */
    fun getAdapterByNcode(ncode: String): NovelSiteAdapter {
        return when {
            PseudoNcodeGenerator.isKakuyomuNcode(ncode) -> getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU)
            else -> getAdapter(NovelSiteAdapter.SITE_TYPE_SYOSETU)
        }
    }

    /**
     * URLからサイトを判定してアダプターと小説IDを取得
     *
     * @param url 小説サイトのURL
     * @return Pair<アダプター, 小説ID> または null（判定できない場合）
     */
    fun getAdapterByUrl(url: String): Pair<NovelSiteAdapter, String>? {
        // 小説家になろう: https://ncode.syosetu.com/{ncode}/
        // または https://novel18.syosetu.com/{ncode}/
        if (url.contains("syosetu.com")) {
            val adapter = getAdapter(NovelSiteAdapter.SITE_TYPE_SYOSETU)
            val novelId = adapter.extractNovelIdFromUrl(url)
            return if (novelId != null) {
                Pair(adapter, novelId)
            } else {
                null
            }
        }

        // カクヨム: https://kakuyomu.jp/works/{workId}
        if (url.contains("kakuyomu.jp")) {
            val adapter = getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU)
            val novelId = adapter.extractNovelIdFromUrl(url)
            return if (novelId != null) {
                Pair(adapter, novelId)
            } else {
                null
            }
        }

        return null
    }

    /**
     * URLがサポート対象のサイトかどうかを判定
     *
     * @param url 判定対象のURL
     * @return サポート対象の場合 true
     */
    fun isSupportedUrl(url: String): Boolean {
        return url.contains("syosetu.com") || url.contains("kakuyomu.jp")
    }

    /**
     * 利用可能な全アダプターを取得
     *
     * @return アダプターのリスト
     */
    fun getAllAdapters(): List<NovelSiteAdapter> {
        return adapters.values.toList()
    }

    /**
     * サイト種別の一覧を取得
     *
     * @return サイト種別のリスト
     */
    fun getSupportedSiteTypes(): List<Int> {
        return adapters.keys.toList()
    }

    /**
     * サイト名の一覧を取得
     *
     * @return サイト名のリスト（表示用）
     */
    fun getSupportedSiteNames(): List<String> {
        return adapters.values.map { it.getSiteName() }
    }
}
