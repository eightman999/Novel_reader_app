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
            .ifEmpty { doc.select("h1.WorkTitle").text() }

        // 作者名: <span id="workAuthor-activityName">作者名</span>
        val author = doc.select("span#workAuthor-activityName a").text()
            .ifEmpty { doc.select("span#workAuthor-activityName").text() }
            .ifEmpty { doc.select("a#workAuthor-activityName").text() }

        // あらすじ: 複数のパターンに対応（改行も保持）
        var synopsis = ""
        var synopsisSource = ""

        // パターン1: p#introduction
        val intro1 = doc.select("p#introduction")
        if (intro1.isNotEmpty()) {
            synopsis = intro1.text()
            synopsisSource = "p#introduction"
        }

        // パターン2: div#introduction p（段落が複数ある場合）
        if (synopsis.isEmpty()) {
            val intro2 = doc.select("div#introduction p")
            if (intro2.isNotEmpty()) {
                synopsis = intro2.joinToString("\n") { it.text() }
                synopsisSource = "div#introduction p"
            }
        }

        // パターン3: ui-truncateTextButton-expandable
        if (synopsis.isEmpty()) {
            val intro3 = doc.select("p.ui-truncateTextButton-expandable")
            if (intro3.isNotEmpty()) {
                synopsis = intro3.text()
                synopsisSource = "p.ui-truncateTextButton-expandable"
            }
        }

        // パターン4: widget-introduction
        if (synopsis.isEmpty()) {
            val intro4 = doc.select("div.widget-introduction p")
            if (intro4.isNotEmpty()) {
                synopsis = intro4.joinToString("\n") { it.text() }
                synopsisSource = "div.widget-introduction p"
            }
        }

        // パターン5: catchphrase-body（キャッチコピー）
        if (synopsis.isEmpty()) {
            val intro5 = doc.select("p.catchphrase-body")
            if (intro5.isNotEmpty()) {
                synopsis = intro5.text()
                synopsisSource = "p.catchphrase-body"
            }
        }

        android.util.Log.d("KakuyomuAdapter", "あらすじ取得: source=$synopsisSource, length=${synopsis.length}")

        // タグ: 複数のセレクタパターンに対応
        val tags = mutableListOf<String>()
        var tagSource = ""

        // パターン1: ul.partialGiftWidgetTagList
        val tagList1 = doc.select("ul.partialGiftWidgetTagList li a").map { it.text().trim() }.filter { it.isNotEmpty() }
        if (tagList1.isNotEmpty()) {
            tags.addAll(tagList1)
            tagSource = "ul.partialGiftWidgetTagList li a"
        }

        // パターン2: Tags-tag クラス
        if (tags.isEmpty()) {
            val tagList2 = doc.select("li.Tags-tag a").map { it.text().trim() }.filter { it.isNotEmpty() }
            if (tagList2.isNotEmpty()) {
                tags.addAll(tagList2)
                tagSource = "li.Tags-tag a"
            }
        }

        // パターン3: Tag クラス
        if (tags.isEmpty()) {
            val tagList3 = doc.select("a.Tag").map { it.text().trim() }.filter { it.isNotEmpty() }
            if (tagList3.isNotEmpty()) {
                tags.addAll(tagList3)
                tagSource = "a.Tag"
            }
        }

        // パターン4: widget-tag クラス
        if (tags.isEmpty()) {
            val tagList4 = doc.select("ul.widget-tag li a").map { it.text().trim() }.filter { it.isNotEmpty() }
            if (tagList4.isNotEmpty()) {
                tags.addAll(tagList4)
                tagSource = "ul.widget-tag li a"
            }
        }

        // パターン5: タグリンク（/works/tag/形式）
        if (tags.isEmpty()) {
            val tagList5 = doc.select("a[href*='/works/tag/']").map { it.text().trim() }.filter { it.isNotEmpty() }
            if (tagList5.isNotEmpty()) {
                tags.addAll(tagList5)
                tagSource = "a[href*='/works/tag/']"
            }
        }

        val mainTag = tags.firstOrNull() ?: ""
        val subTag = tags.drop(1).joinToString(",")

        android.util.Log.d("KakuyomuAdapter", "タグ取得: source=$tagSource, count=${tags.size}, tags=$tags")

        // 更新日: <time datetime="2024-01-01T12:00:00Z">
        val lastUpdateElement = doc.select("time[datetime]").last()
        val lastUpdateDate = lastUpdateElement?.attr("datetime")?.take(10) ?: getCurrentDate()

        // エピソード総数
        val episodes = doc.select("ol.widget-toc-items li.widget-toc-episode")
        val totalEp = episodes.size

        // Pseudo-Ncode生成
        val pseudoNcode = PseudoNcodeGenerator.generateKakuyomuNcode(workId)

        // 取得結果のログ出力
        android.util.Log.d("KakuyomuAdapter", """
            小説情報取得完了:
            - タイトル: $title
            - 作者: $author
            - あらすじ長: ${synopsis.length}文字
            - メインタグ: $mainTag
            - サブタグ: $subTag
            - エピソード数: $totalEp
            - 更新日: $lastUpdateDate
            - Pseudo-Ncode: $pseudoNcode
        """.trimIndent())

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

        android.util.Log.d("KakuyomuAdapter", "エピソード一覧取得: ${episodeElements.size}話")

        episodeElements.forEachIndexed { index, element ->
            // エピソードID: <a href="/works/{workId}/episodes/{episodeId}">
            val episodeLink = element.select("a").attr("href")
            val episodeId = episodeLink.substringAfterLast("/")

            // エピソードタイトル: 複数のパターンに対応
            var episodeTitle = element.select("span.widget-toc-episode-titleLabel").text()
            if (episodeTitle.isEmpty()) {
                episodeTitle = element.select("span.widget-toc-episode-title").text()
            }
            if (episodeTitle.isEmpty()) {
                episodeTitle = element.select("a.widget-toc-episode-episodeTitle").text()
            }
            if (episodeTitle.isEmpty()) {
                // リンクのテキストをフォールバックとして使用
                episodeTitle = element.select("a").text()
            }
            if (episodeTitle.isEmpty()) {
                // 最後のフォールバック: "第X話"
                episodeTitle = "第${index + 1}話"
            }

            // 公開日: <time datetime="2024-01-01T12:00:00Z">
            val timeElement = element.select("time[datetime]")
            val publishedDate = if (timeElement.isNotEmpty()) {
                timeElement.attr("datetime").take(10)
            } else {
                getCurrentDate()
            }

            episodes.add(
                EpisodeEntity(
                    ncode = pseudoNcode,
                    episode_no = (index + 1).toString(),
                    body = "",  // 目次ページには本文がないため空
                    e_title = episodeTitle,
                    update_time = publishedDate,
                    is_read = false,
                    is_bookmark = false,
                    reading_rate = 0.0f
                )
            )
        }

        android.util.Log.d("KakuyomuAdapter", "エピソード一覧パース完了: ${episodes.size}話")
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
