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
import org.json.JSONObject
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
        private const val RATE_LIMIT_DELAY_MS = 1000L  // 1秒（スクレイピング時の推奨間隔）
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

        // エピソード一覧を抽出（本文なし）
        val episodesWithoutBody = parseEpisodeList(doc, workId, novelDesc.ncode)

        // 各エピソードの本文を取得
        android.util.Log.d("KakuyomuAdapter", "エピソード本文のダウンロード開始: ${episodesWithoutBody.size}話")
        val episodesWithBody = episodesWithoutBody.map { episode ->
            val episodeBody = fetchEpisodeContent(workId, episode.episode_no)
            episode.copy(body = episodeBody)
        }

        android.util.Log.d("KakuyomuAdapter", "小説とエピソード取得完了: ${novelDesc.title}, ${episodesWithBody.size}話")
        Pair(novelDesc, episodesWithBody)
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
        // JSONから取得を試みる
        val (_, workData) = extractNextDataJson(doc, workId)
        var episodeCount = workData?.optInt("publicEpisodeCount") ?: 0

        // JSONから取得できない場合はHTML
        if (episodeCount == 0) {
            // 新しいHTML構造
            var episodes = doc.select("a.WorkTocSection_link__ocg9K")
            if (episodes.isEmpty()) {
                // 古い構造（フォールバック）
                episodes = doc.select("ol.widget-toc-items li.widget-toc-episode")
            }
            episodeCount = episodes.size
        }

        episodeCount > currentEpisodeCount
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
        if (novelId.isEmpty()) return false
        if (PseudoNcodeGenerator.isKakuyomuNcode(novelId)) return true

        // BigIntegerで数値かどうかを判定（Long型の範囲外も対応）
        return try {
            java.math.BigInteger(novelId)
            true
        } catch (e: NumberFormatException) {
            false
        }
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
     * HTTP GETリクエストを実行してHTMLを取得（再試行対応）
     *
     * @param urlString リクエストURL
     * @param maxRetries 最大再試行回数（デフォルト: 3回）
     * @return HTML文字列
     * @throws Exception ネットワークエラーまたはHTTPエラー
     */
    private suspend fun performHttpRequest(urlString: String, maxRetries: Int = 3): String {
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.connect()
                val responseCode = connection.responseCode

                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val html = connection.inputStream.bufferedReader().use { it.readText() }
                        android.util.Log.d("KakuyomuAdapter", "HTTP取得成功: $urlString")
                        return html
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        android.util.Log.e("KakuyomuAdapter", "404 Not Found: $urlString")
                        throw Exception("HTTP 404: ページが見つかりません")
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        android.util.Log.e("KakuyomuAdapter", "403 Forbidden: $urlString")
                        throw Exception("HTTP 403: アクセスが拒否されました")
                    }
                    else -> {
                        throw Exception("HTTP error: $responseCode")
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                android.util.Log.w("KakuyomuAdapter", "タイムアウト (試行 $attempt/$maxRetries): $urlString")
                if (attempt < maxRetries) {
                    delay(1000L * attempt)  // 指数バックオフ: 1秒、2秒、3秒
                }
            } catch (e: java.net.UnknownHostException) {
                lastException = e
                android.util.Log.w("KakuyomuAdapter", "ネットワークエラー (試行 $attempt/$maxRetries): $urlString")
                if (attempt < maxRetries) {
                    delay(1000L * attempt)
                }
            } catch (e: java.net.ConnectException) {
                lastException = e
                android.util.Log.w("KakuyomuAdapter", "接続エラー (試行 $attempt/$maxRetries): $urlString")
                if (attempt < maxRetries) {
                    delay(1000L * attempt)
                }
            } catch (e: Exception) {
                // HTTPエラーやその他のエラーは即座にスロー（再試行しない）
                android.util.Log.e("KakuyomuAdapter", "HTTP取得エラー: $urlString", e)
                throw e
            } finally {
                connection?.disconnect()
            }
        }

        // 最大試行回数を超えた場合
        android.util.Log.e("KakuyomuAdapter", "最大再試行回数を超えました: $urlString", lastException)
        throw lastException ?: Exception("HTTP取得失敗: $urlString")
    }

    /**
     * HTMLから小説情報を抽出
     */
    private fun parseNovelInfo(doc: Document, workId: String): NovelDescEntity {
        // Next.jsのJSONデータを取得（最優先）
        val (apolloState, workData) = extractNextDataJson(doc, workId)

        // タイトル: 複数のパターンに対応
        var title = workData?.optString("title") ?: ""
        if (title.isEmpty()) {
            // 新しいHTML構造: h1 with Heading classes
            title = doc.select("h1.Heading_heading__lQ85n a").text()
                .ifEmpty { doc.select("h1.Heading_heading__lQ85n").text() }
                // 古い構造（フォールバック）
                .ifEmpty { doc.select("h1#workTitle a").text() }
                .ifEmpty { doc.select("h1#workTitle").text() }
                .ifEmpty { doc.select("h1.WorkTitle").text() }
        }

        // 作者名: 複数のパターンに対応（JSON参照を解決）
        var author = ""
        if (workData != null && apolloState != null) {
            val authorRef = workData.optJSONObject("author")
            if (authorRef != null) {
                val refKey = authorRef.optString("__ref")
                if (refKey.isNotEmpty()) {
                    val authorData = apolloState.optJSONObject(refKey)
                    author = authorData?.optString("activityName") ?: ""
                    if (author.isEmpty()) {
                        author = authorData?.optString("name") ?: ""
                    }
                }
            }
        }

        if (author.isEmpty()) {
            // 新しいHTML構造: partialGiftWidgetActivityName
            author = doc.select("div.partialGiftWidgetActivityName a").text()
                .ifEmpty { doc.select("div.partialGiftWidgetActivityName").text() }
                // Typography with ActivityName
                .ifEmpty { doc.select("div.Typography_fontWeight-bold__jDh15 div.partialGiftWidgetActivityName a").text() }
                // 古い構造（フォールバック）
                .ifEmpty { doc.select("span#workAuthor-activityName a").text() }
                .ifEmpty { doc.select("span#workAuthor-activityName").text() }
                .ifEmpty { doc.select("a#workAuthor-activityName").text() }
        }

        // あらすじ: 複数のパターンに対応（改行も保持）
        var synopsis = workData?.optString("introduction") ?: ""
        var synopsisSource = if (synopsis.isNotEmpty()) "JSON" else ""

        // 新しいHTML構造: CollapseTextWithKakuyomuLinks
        if (synopsis.isEmpty()) {
            val intro0 = doc.select("div.CollapseTextWithKakuyomuLinks_collapseText__XSlmz")
            if (intro0.isNotEmpty()) {
                synopsis = intro0.html().replace("<br>", "\n").let { Jsoup.parse(it).text() }
                synopsisSource = "div.CollapseTextWithKakuyomuLinks"
            }
        }

        // パターン1: p#introduction
        if (synopsis.isEmpty()) {
            val intro1 = doc.select("p#introduction")
            if (intro1.isNotEmpty()) {
                synopsis = intro1.text()
                synopsisSource = "p#introduction"
            }
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

        // JSONからタグを取得（最優先）
        workData?.optJSONArray("tagLabels")?.let { tagArray ->
            for (i in 0 until tagArray.length()) {
                tags.add(tagArray.getString(i))
            }
            tagSource = "JSON"
        }

        // パターン1: ul.partialGiftWidgetTagList
        if (tags.isEmpty()) {
            val tagList1 = doc.select("ul.partialGiftWidgetTagList li a").map { it.text().trim() }.filter { it.isNotEmpty() }
            if (tagList1.isNotEmpty()) {
                tags.addAll(tagList1)
                tagSource = "ul.partialGiftWidgetTagList li a"
            }
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

        // エピソード総数: JSONまたはHTML
        var totalEp = workData?.optInt("publicEpisodeCount") ?: 0
        if (totalEp == 0) {
            // 新しいHTML構造: WorkTocSection_link
            val newEpisodes = doc.select("a.WorkTocSection_link__ocg9K")
            totalEp = newEpisodes.size

            // 古い構造（フォールバック）
            if (totalEp == 0) {
                val oldEpisodes = doc.select("ol.widget-toc-items li.widget-toc-episode")
                totalEp = oldEpisodes.size
            }
        }

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
     * HTMLからエピソード一覧を抽出（TOC情報のみ、本文は含まない）
     * JSONデータから優先的に取得し、失敗した場合のみHTMLフォールバック
     */
    private fun parseEpisodeList(doc: Document, workId: String, pseudoNcode: String): List<EpisodeEntity> {
        // まずJSONデータからエピソード一覧を取得
        val episodesFromJson = extractEpisodesFromJson(doc, workId, pseudoNcode)
        if (episodesFromJson.isNotEmpty()) {
            android.util.Log.d("KakuyomuAdapter", "エピソード一覧取得成功 (JSON): ${episodesFromJson.size}話")
            return episodesFromJson
        }

        // JSONから取得できない場合はHTMLフォールバック
        android.util.Log.d("KakuyomuAdapter", "JSONからエピソード取得失敗、HTMLフォールバックを試行")

        val episodes = mutableListOf<EpisodeEntity>()

        // 新しいHTML構造: WorkTocSection_link
        var episodeElements = doc.select("a.WorkTocSection_link__ocg9K")
        var isNewStructure = episodeElements.isNotEmpty()

        // 古い構造（フォールバック）
        if (!isNewStructure) {
            episodeElements = doc.select("ol.widget-toc-items li.widget-toc-episode")
        }

        android.util.Log.d("KakuyomuAdapter", "エピソード一覧取得 (HTML): ${episodeElements.size}話 (新構造: $isNewStructure)")

        episodeElements.forEachIndexed { index, element ->
            // エピソードID: <a href="/works/{workId}/episodes/{episodeId}">
            val episodeLink = if (isNewStructure) {
                element.attr("href")
            } else {
                element.select("a").attr("href")
            }
            val episodeId = episodeLink.substringAfterLast("/")

            // エピソードタイトル: 複数のパターンに対応
            var episodeTitle = ""

            if (isNewStructure) {
                // 新しい構造: WorkTocSection_title内のdiv
                episodeTitle = element.select("div.WorkTocSection_title__H2007 div").text()
                    .ifEmpty { element.select("div.WorkTocSection_title__H2007").text() }
                    // Typography classを持つdiv
                    .ifEmpty { element.select("div.Typography_lineHeight-1s__3iKaG div").text() }
                    // リンク全体のテキスト
                    .ifEmpty { element.text() }
            } else {
                // 古い構造
                episodeTitle = element.select("span.widget-toc-episode-titleLabel").text()
                    .ifEmpty { element.select("span.widget-toc-episode-title").text() }
                    .ifEmpty { element.select("a.widget-toc-episode-episodeTitle").text() }
                    .ifEmpty { element.select("a").text() }
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

            // episode_noには実際のカクヨムエピソードIDを格納
            // これによりURLの生成が正確になる
            episodes.add(
                EpisodeEntity(
                    ncode = pseudoNcode,
                    episode_no = episodeId,  // カクヨムの実際のエピソードIDを使用
                    body = "",  // TOCページには本文がないため空（後でfetchEpisodeContentで取得）
                    e_title = episodeTitle,
                    update_time = publishedDate,
                    is_read = false,
                    is_bookmark = false,
                    reading_rate = 0.0f
                )
            )
        }

        android.util.Log.d("KakuyomuAdapter", "エピソード一覧パース完了 (HTML): ${episodes.size}話")
        return episodes
    }

    /**
     * JSONデータからエピソード一覧を抽出
     *
     * @param doc HTMLドキュメント
     * @param workId 作品ID
     * @param pseudoNcode 疑似Ncode
     * @return エピソードリスト（取得失敗時は空リスト）
     */
    private fun extractEpisodesFromJson(doc: Document, workId: String, pseudoNcode: String): List<EpisodeEntity> {
        return try {
            val (apolloState, workData) = extractNextDataJson(doc, workId)
            if (apolloState == null || workData == null) {
                android.util.Log.d("KakuyomuAdapter", "JSONデータが見つかりません")
                return emptyList()
            }

            val episodes = mutableListOf<EpisodeEntity>()

            // tableOfContents から章構造とエピソード順序を取得
            val tableOfContents = workData.optJSONObject("tableOfContents")
            if (tableOfContents == null) {
                android.util.Log.d("KakuyomuAdapter", "tableOfContentsが見つかりません")
                return emptyList()
            }

            // chaptersから章一覧を取得
            val chaptersArray = tableOfContents.optJSONArray("chapters")
            if (chaptersArray == null || chaptersArray.length() == 0) {
                android.util.Log.d("KakuyomuAdapter", "chaptersが見つかりません")
                return emptyList()
            }

            // 各章を処理
            for (i in 0 until chaptersArray.length()) {
                val chapter = chaptersArray.getJSONObject(i)

                // エピソード一覧を取得（episodesフィールド）
                val episodesArray = chapter.optJSONArray("episodes")
                if (episodesArray != null) {
                    for (j in 0 until episodesArray.length()) {
                        val episodeRef = episodesArray.getJSONObject(j)
                        val refKey = episodeRef.optString("__ref")

                        if (refKey.isNotEmpty()) {
                            // 参照を解決してエピソードデータを取得
                            val episodeData = apolloState.optJSONObject(refKey)
                            if (episodeData != null) {
                                val episodeId = episodeData.optString("id")
                                val title = episodeData.optString("title", "")

                                // 公開日時を取得
                                val publishedAt = episodeData.optString("publishedAt", "")
                                val publishedDate = if (publishedAt.isNotEmpty()) {
                                    publishedAt.take(10)  // YYYY-MM-DD部分を取得
                                } else {
                                    getCurrentDate()
                                }

                                episodes.add(
                                    EpisodeEntity(
                                        ncode = pseudoNcode,
                                        episode_no = episodeId,
                                        body = "",  // 本文は後で個別に取得
                                        e_title = title,
                                        update_time = publishedDate,
                                        is_read = false,
                                        is_bookmark = false,
                                        reading_rate = 0.0f
                                    )
                                )
                            }
                        }
                    }
                }
            }

            android.util.Log.d("KakuyomuAdapter", "JSONからエピソード抽出成功: ${episodes.size}話")
            episodes
        } catch (e: Exception) {
            android.util.Log.e("KakuyomuAdapter", "JSONからのエピソード抽出エラー", e)
            emptyList()
        }
    }

    /**
     * 個別エピソードの本文を取得
     *
     * @param workId 作品ID
     * @param episodeId エピソードID
     * @return エピソード本文HTML（取得失敗時は空文字列）
     */
    suspend fun fetchEpisodeContent(workId: String, episodeId: String): String = withContext(Dispatchers.IO) {
        try {
            applyRateLimit()

            val url = generateEpisodeUrl(workId, episodeId)
            val html = performHttpRequest(url)
            val doc = Jsoup.parse(html)

            // エピソード本文を抽出: 複数のパターンに対応
            var episodeBody = ""

            // パターン1: widget-episodeBody と js-episode-body の両方のクラスを持つdiv（最優先、文献準拠）
            val bodyElement1 = doc.select("div.widget-episodeBody.js-episode-body")
            if (bodyElement1.isNotEmpty()) {
                episodeBody = bodyElement1.html()
                android.util.Log.d("KakuyomuAdapter", "エピソード本文取得成功 (widget-episodeBody.js-episode-body): $episodeId")
            }

            // パターン2: widget-episodeBody のみ（古い構造）
            if (episodeBody.isEmpty()) {
                val bodyElement2 = doc.select("div.widget-episodeBody")
                if (bodyElement2.isNotEmpty()) {
                    episodeBody = bodyElement2.html()
                    android.util.Log.d("KakuyomuAdapter", "エピソード本文取得成功 (widget-episodeBody): $episodeId")
                }
            }

            // パターン3: js-episode-body のみ（古い構造の別パターン）
            if (episodeBody.isEmpty()) {
                val bodyElement3 = doc.select("div.js-episode-body")
                if (bodyElement3.isNotEmpty()) {
                    episodeBody = bodyElement3.html()
                    android.util.Log.d("KakuyomuAdapter", "エピソード本文取得成功 (js-episode-body): $episodeId")
                }
            }

            // パターン4: 新しいHTML構造のパターン（将来的な変更に備えて）
            if (episodeBody.isEmpty()) {
                val bodyElement4 = doc.select("div[class*='EpisodeBody']")
                if (bodyElement4.isNotEmpty()) {
                    episodeBody = bodyElement4.html()
                    android.util.Log.d("KakuyomuAdapter", "エピソード本文取得成功 (EpisodeBody): $episodeId")
                }
            }

            // パターン5: p要素を含むdiv（最後のフォールバック）
            if (episodeBody.isEmpty()) {
                val bodyElement5 = doc.select("div#contentMain p")
                if (bodyElement5.isNotEmpty()) {
                    episodeBody = bodyElement5.joinToString("\n") { it.outerHtml() }
                    android.util.Log.d("KakuyomuAdapter", "エピソード本文取得成功 (contentMain p): $episodeId")
                }
            }

            if (episodeBody.isEmpty()) {
                android.util.Log.w("KakuyomuAdapter", "エピソード本文が空です: $episodeId")
            }

            episodeBody
        } catch (e: Exception) {
            android.util.Log.e("KakuyomuAdapter", "エピソード本文取得エラー: $episodeId", e)
            ""
        }
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

    /**
     * Next.jsのJSONデータを抽出
     *
     * @param doc HTMLドキュメント
     * @param workId 作品ID（正しいWork:キーを探すために使用）
     * @return Pair<apolloState, workData> - apolloStateは参照解決用、workDataは作品情報
     */
    private fun extractNextDataJson(doc: Document, workId: String): Pair<JSONObject?, JSONObject?> {
        return try {
            // <script id="__NEXT_DATA__" type="application/json">...</script> を取得
            val scriptElement = doc.select("script#__NEXT_DATA__").firstOrNull()
            if (scriptElement != null) {
                val jsonText = scriptElement.html()
                val rootJson = JSONObject(jsonText)

                // props.pageProps.__APOLLO_STATE__.Work:workId の階層を探索
                val pageProps = rootJson.optJSONObject("props")?.optJSONObject("pageProps")
                val apolloState = pageProps?.optJSONObject("__APOLLO_STATE__")

                // 正しいWork:workIdのキーを優先的に探す
                val targetKey = "Work:$workId"
                if (apolloState?.has(targetKey) == true) {
                    val workData = apolloState.getJSONObject(targetKey)
                    android.util.Log.d("KakuyomuAdapter", "Next.jsデータ取得成功（指定workId一致）: $targetKey")
                    return Pair(apolloState, workData)
                }

                // 見つからない場合はWork:で始まる最初のキーを探す（フォールバック）
                apolloState?.keys()?.forEach { key ->
                    if (key.startsWith("Work:")) {
                        val workData = apolloState.getJSONObject(key)
                        android.util.Log.d("KakuyomuAdapter", "Next.jsデータ取得成功（フォールバック）: $key (期待値: $targetKey)")
                        return Pair(apolloState, workData)
                    }
                }

                android.util.Log.d("KakuyomuAdapter", "Work:キーが見つかりませんでした")
            } else {
                android.util.Log.d("KakuyomuAdapter", "__NEXT_DATA__スクリプトが見つかりませんでした")
            }
            Pair(null, null)
        } catch (e: Exception) {
            android.util.Log.e("KakuyomuAdapter", "Next.jsデータ抽出エラー", e)
            Pair(null, null)
        }
    }
}
