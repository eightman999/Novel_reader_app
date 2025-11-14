/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Kakuyomu site adapter with HTML scraping.
 */
package com.shunlight_library.novel_reader.data.adapter

import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * カクヨム用のアダプター実装
 *
 * カクヨムには公式APIが存在しないため、HTMLスクレイピングで情報を取得する。
 * レート制限として0.5秒間隔でのアクセスを実施。
 *
 * URL構造:
 * - 作品ページ: https://kakuyomu.jp/works/{workId}
 * - エピソードページ: https://kakuyomu.jp/works/{workId}/episodes/{episodeId}
 */
class KakuyomuAdapter : NovelSiteAdapter {
    companion object {
        private const val BASE_URL = "https://kakuyomu.jp"
        private const val RATE_LIMIT_DELAY_MS = 500L  // 0.5秒
        private var lastAccessTime = 0L

        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
    }

    override fun getSiteType(): Int = NovelSiteAdapter.SITE_TYPE_KAKUYOMU

    override fun getSiteName(): String = "カクヨム"

    override fun generateWebUrl(novelId: String): String {
        val workId = if (PseudoNcodeGenerator.isKakuyomuNcode(novelId)) {
            PseudoNcodeGenerator.extractKakuyomuWorkId(novelId)
        } else {
            novelId
        }
        return "$BASE_URL/works/$workId"
    }

    override fun generateEpisodeUrl(novelId: String, episodeId: String): String {
        val workId = if (PseudoNcodeGenerator.isKakuyomuNcode(novelId)) {
            PseudoNcodeGenerator.extractKakuyomuWorkId(novelId)
        } else {
            novelId
        }
        return "$BASE_URL/works/$workId/episodes/$episodeId"
    }

    override suspend fun fetchNovelWithEpisodes(novelId: String): Pair<NovelDescEntity, List<EpisodeEntity>> = withContext(Dispatchers.IO) {
        val workId = if (PseudoNcodeGenerator.isKakuyomuNcode(novelId)) {
            PseudoNcodeGenerator.extractKakuyomuWorkId(novelId)
        } else {
            novelId
        }

        applyRateLimit()

        val url = generateWebUrl(workId)
        val html = performHttpRequest(url)
        val doc = Jsoup.parse(html)

        // 小説情報を抽出
        val novelDesc = parseNovelInfo(doc, workId)

        // エピソード一覧を抽出
        val episodes = parseEpisodeList(doc, workId, novelDesc.ncode)

        Pair(novelDesc, episodes)
    }

    override suspend fun checkForUpdates(novelId: String, currentEpisodeCount: Int): Boolean = withContext(Dispatchers.IO) {
        val workId = if (PseudoNcodeGenerator.isKakuyomuNcode(novelId)) {
            PseudoNcodeGenerator.extractKakuyomuWorkId(novelId)
        } else {
            novelId
        }

        applyRateLimit()

        val url = generateWebUrl(workId)
        val html = performHttpRequest(url)
        val doc = Jsoup.parse(html)

        // エピソード数を取得して比較
        val episodes = doc.select("ol.widget-toc-items li.widget-toc-episode")
        episodes.size > currentEpisodeCount
    }

    override fun extractNovelIdFromUrl(url: String): String? {
        // https://kakuyomu.jp/works/1177354054887277844
        // または
        // https://kakuyomu.jp/works/1177354054887277844/episodes/xxxxx
        val workIdPattern = Regex("kakuyomu\\.jp/works/(\\d+)")
        val match = workIdPattern.find(url)
        return match?.groupValues?.get(1)
    }

    override fun isValidNovelId(novelId: String): Boolean {
        return novelId.isNotEmpty() && (novelId.toLongOrNull() != null || PseudoNcodeGenerator.isKakuyomuNcode(novelId))
    }

    /**
     * レート制限を適用（0.5秒間隔）
     */
    private suspend fun applyRateLimit() {
        val elapsed = System.currentTimeMillis() - lastAccessTime
        if (elapsed < RATE_LIMIT_DELAY_MS) {
            delay(RATE_LIMIT_DELAY_MS - elapsed)
        }
        lastAccessTime = System.currentTimeMillis()
    }

    /**
     * HTTP GETリクエストを実行してHTMLを取得
     */
    private fun performHttpRequest(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        return try {
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: $responseCode")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * HTMLから小説情報を抽出
     */
    private fun parseNovelInfo(doc: Document, workId: String): NovelDescEntity {
        // タイトル: <h1 id="workTitle">タイトル</h1>
        val title = doc.select("h1#workTitle a").text()
            .ifEmpty { doc.select("h1#workTitle").text() }

        // 作者名: <span id="workAuthor-activityName">作者名</span>
        val author = doc.select("span#workAuthor-activityName a").text()
            .ifEmpty { doc.select("span#workAuthor-activityName").text() }

        // あらすじ: <p id="introduction">あらすじ</p>
        val synopsis = doc.select("p#introduction").text()

        // タグ: <ul class="partialGiftWidgetTagList"> <li><a>タグ</a></li> ...
        val tags = doc.select("ul.partialGiftWidgetTagList li a").map { it.text() }
        val mainTag = tags.firstOrNull() ?: ""
        val subTag = tags.drop(1).joinToString(",")

        // 更新日: <time datetime="2024-01-01T12:00:00Z">
        val lastUpdateElement = doc.select("time[datetime]").last()
        val lastUpdateDate = lastUpdateElement?.attr("datetime")?.take(10) ?: getCurrentDate()

        // エピソード総数
        val episodes = doc.select("ol.widget-toc-items li.widget-toc-episode")
        val totalEp = episodes.size

        // Pseudo-Ncode生成
        val pseudoNcode = PseudoNcodeGenerator.generateKakuyomuNcode(workId)

        return NovelDescEntity(
            ncode = pseudoNcode,
            title = title,
            author = author,
            Synopsis = synopsis,
            main_tag = mainTag,
            sub_tag = subTag,
            rating = 2,  // カクヨムは一般のみ
            last_update_date = lastUpdateDate,
            total_ep = totalEp,
            general_all_no = 0,  // カクヨムにはこの情報がない
            userid = null,  // HTMLから抽出困難
            noveltype = if (totalEp == 1) 2 else 1,  // 1話のみなら短編、それ以外は連載
            length = null,  // HTMLから抽出困難
            updated_at = getCurrentDateTime(),
            is_favorite = false,
            site_type = NovelSiteAdapter.SITE_TYPE_KAKUYOMU
        )
    }

    /**
     * HTMLからエピソード一覧を抽出
     */
    private fun parseEpisodeList(doc: Document, workId: String, pseudoNcode: String): List<EpisodeEntity> {
        val episodeElements = doc.select("ol.widget-toc-items li.widget-toc-episode")
        val episodes = mutableListOf<EpisodeEntity>()

        episodeElements.forEachIndexed { index, element ->
            // エピソードID: <a href="/works/{workId}/episodes/{episodeId}">
            val episodeLink = element.select("a").attr("href")
            val episodeId = episodeLink.substringAfterLast("/")

            // エピソードタイトル: <span class="widget-toc-episode-titleLabel">タイトル</span>
            val episodeTitle = element.select("span.widget-toc-episode-titleLabel").text()

            // 公開日: <time datetime="2024-01-01T12:00:00Z">
            val publishedDate = element.select("time[datetime]").attr("datetime")?.take(10) ?: getCurrentDate()

            episodes.add(
                EpisodeEntity(
                    ncode = pseudoNcode,
                    episode_no = (index + 1).toString(),
                    e_title = episodeTitle,
                    e_contents = "",  // 目次ページには本文がないため空
                    updated_at = publishedDate,
                    is_read = false,
                    is_bookmark = false,
                    reading_rate = 0.0f
                )
            )
        }

        return episodes
    }

    /**
     * 現在日付を取得（YYYY-MM-DD形式）
     */
    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    /**
     * 現在日時を取得（YYYY-MM-DD HH:mm:ss形式）
     */
    private fun getCurrentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
