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
        // 目次から取得するため、docは不要
        val episodesWithoutBody = parseEpisodeList(workId, novelDesc.ncode)

        // 各エピソードの本文を取得
        android.util.Log.d("KakuyomuAdapter", "エピソード本文のダウンロード開始: ${episodesWithoutBody.size}話")
        val episodesWithBody = episodesWithoutBody.map { episode ->
            val episodeBody = fetchEpisodeContent(workId, episode.episode_no)
            episode.copy(body = episodeBody)
        }

        // エピソード数の不一致をチェック
        val expectedCount = novelDesc.total_ep
        val actualCount = episodesWithBody.size

        if (expectedCount != actualCount) {
            android.util.Log.e("KakuyomuAdapter", """
                !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                エピソード数の不一致を検出しました！
                - 期待されるエピソード数: ${expectedCount}話
                - 実際に取得したエピソード数: ${actualCount}話
                - 不足: ${expectedCount - actualCount}話
                - 作品ID: $workId
                - タイトル: ${novelDesc.title}
                !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            """.trimIndent())
        } else {
            android.util.Log.d("KakuyomuAdapter", "小説とエピソード取得完了: ${novelDesc.title}, ${episodesWithBody.size}話")
        }

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
     * Pascalコードを参考に、完全なHTMLをバッファリングして取得
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
                        // 完全なHTMLをバッファリングして取得（Pascalコード参考）
                        val contentLength = connection.contentLength
                        val html = StringBuilder()

                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            val buffer = CharArray(8192)  // 8KBバッファ
                            var charsRead: Int

                            // 全データを読み込むまでループ（Pascalコードと同様）
                            while (reader.read(buffer).also { charsRead = it } != -1) {
                                html.append(buffer, 0, charsRead)
                            }
                        }

                        val htmlString = html.toString()
                        val actualLength = htmlString.length

                        android.util.Log.d("KakuyomuAdapter", "HTTP取得成功: $urlString (Content-Length: $contentLength, Actual: $actualLength)")

                        return htmlString
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

        // タイトルのクリーンアップ処理（HTMLエスケープ文字のデコード）
        if (title.isNotEmpty()) {
            title = cleanupText(title)
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

        // 作者名のクリーンアップ処理（HTMLエスケープ文字のデコード）
        if (author.isNotEmpty()) {
            author = cleanupText(author)
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

        // あらすじのクリーンアップ処理（HTMLエスケープ文字のデコード、改行処理など）
        if (synopsis.isNotEmpty()) {
            synopsis = cleanupSynopsis(synopsis)
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
     * エピソードページの目次から優先的に取得し、失敗した場合のみJSONやHTMLフォールバック
     */
    private suspend fun parseEpisodeList(workId: String, pseudoNcode: String): List<EpisodeEntity> {
        // 方法1: エピソードページの目次から取得（最優先、確実）
        val episodesFromToc = extractEpisodesFromToc(workId, pseudoNcode)
        if (episodesFromToc.isNotEmpty()) {
            android.util.Log.d("KakuyomuAdapter", "エピソード一覧取得成功 (目次): ${episodesFromToc.size}話")
            return episodesFromToc
        }

        android.util.Log.d("KakuyomuAdapter", "目次からエピソード取得失敗、他の方法を試行")

        // 方法2: 作品ページのHTMLから取得（フォールバック）
        val workUrl = "https://kakuyomu.jp/works/$workId"
        val workHtml = performHttpRequest(workUrl)
        val doc = Jsoup.parse(workHtml)

        android.util.Log.d("KakuyomuAdapter", "=== 作品ページHTML構造調査開始 ===")
        android.util.Log.d("KakuyomuAdapter", "作品URL: $workUrl")
        android.util.Log.d("KakuyomuAdapter", "HTML長: ${workHtml.length}文字")

        // 複数のセレクタパターンを試行
        val workTocSelectors = listOf(
            "a.WorkTocSection_link__ocg9K",
            "a[href*='/episodes/']",
            "ol.widget-toc-items li.widget-toc-episode",
            "div[class*='WorkToc']",
            "div[class*='Toc']",
            "section[class*='toc']",
            "li[class*='episode']"
        )

        workTocSelectors.forEach { selector ->
            val elements = doc.select(selector)
            android.util.Log.d("KakuyomuAdapter", "作品ページセレクタ「$selector」: ${elements.size}件")
            if (selector == "a[href*='/episodes/']" && elements.size > 0) {
                val sample = elements.take(3)
                sample.forEach { link ->
                    android.util.Log.d("KakuyomuAdapter", "  サンプルリンク - href: ${link.attr("href")}, class: ${link.className()}, text: ${link.text().take(50)}")
                }
            }
        }

        android.util.Log.d("KakuyomuAdapter", "=== 作品ページHTML構造調査終了 ===")

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

            // エピソードタイトルのクリーンアップ処理（HTMLエスケープ文字のデコード）
            episodeTitle = cleanupText(episodeTitle)

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
     * エピソードページの目次からエピソード一覧を抽出
     * 任意のエピソードページには完全な目次が表示されているため、そこから全エピソードを取得
     *
     * @param workId 作品ID
     * @param pseudoNcode 疑似Ncode
     * @return エピソードリスト（取得失敗時は空リスト）
     */
    private suspend fun extractEpisodesFromToc(workId: String, pseudoNcode: String): List<EpisodeEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            applyRateLimit()

            // 作品ページから最初のエピソードIDを取得
            val workUrl = "https://kakuyomu.jp/works/$workId"
            val workHtml = performHttpRequest(workUrl)
            val workDoc = Jsoup.parse(workHtml)

            // 最初のエピソードのリンクを取得
            val firstEpisodeLink = workDoc.select("a.widget-toc-episode-episodeTitle, a.WorkTocSection_link__ocg9K").firstOrNull()?.attr("href")
            if (firstEpisodeLink.isNullOrEmpty()) {
                android.util.Log.w("KakuyomuAdapter", "最初のエピソードが見つかりません")
                return@withContext emptyList()
            }

            applyRateLimit()

            // エピソードページを取得（目次が含まれる）
            val episodeUrl = "https://kakuyomu.jp$firstEpisodeLink"
            val episodeHtml = performHttpRequest(episodeUrl)
            val episodeDoc = Jsoup.parse(episodeHtml)

            android.util.Log.d("KakuyomuAdapter", "=== エピソードページHTML構造調査開始 ===")
            android.util.Log.d("KakuyomuAdapter", "エピソードURL: $episodeUrl")
            android.util.Log.d("KakuyomuAdapter", "HTML長: ${episodeHtml.length}文字")

            // 複数のセレクタパターンを試行
            val tocSelectors = listOf(
                "ol.widget-toc-items li.widget-toc-episode",
                "ol.widget-toc-items li",
                "ol.widget-toc-items",
                ".widget-toc-items",
                "ol[class*='toc']",
                "li[class*='toc-episode']",
                "li[class*='episode']",
                "nav ol li",
                "aside ol li"
            )

            tocSelectors.forEach { selector ->
                val elements = episodeDoc.select(selector)
                android.util.Log.d("KakuyomuAdapter", "セレクタ「$selector」: ${elements.size}件")
            }

            // aタグで目次リンクを検索
            val allLinks = episodeDoc.select("a[href*='/episodes/']")
            android.util.Log.d("KakuyomuAdapter", "全エピソードリンク（a[href*='/episodes/']）: ${allLinks.size}件")
            if (allLinks.size > 0 && allLinks.size <= 5) {
                allLinks.take(5).forEach { link ->
                    android.util.Log.d("KakuyomuAdapter", "  - href: ${link.attr("href")}, class: ${link.className()}, text: ${link.text().take(30)}")
                }
            }

            // HTMLの一部を出力（デバッグ用）
            val bodyHtml = episodeDoc.body().html()
            val tocIndex = bodyHtml.indexOf("toc", ignoreCase = true)
            if (tocIndex >= 0) {
                val snippet = bodyHtml.substring(maxOf(0, tocIndex - 100), minOf(bodyHtml.length, tocIndex + 500))
                android.util.Log.d("KakuyomuAdapter", "目次付近のHTML: ${snippet.take(300)}...")
            }

            android.util.Log.d("KakuyomuAdapter", "=== エピソードページHTML構造調査終了 ===")

            // 目次から全エピソードを抽出
            val episodes = mutableListOf<EpisodeEntity>()
            val tocItems = episodeDoc.select("ol.widget-toc-items li.widget-toc-episode")

            android.util.Log.d("KakuyomuAdapter", "目次から検出したエピソード数: ${tocItems.size}")

            tocItems.forEach { item ->
                try {
                    // エピソードリンク: /works/{workId}/episodes/{episodeId}
                    val link = item.select("a.widget-toc-episode-episodeTitle").attr("href")
                    val episodeId = link.substringAfterLast("/")

                    // エピソードタイトル
                    var title = item.select("span.widget-toc-episode-titleLabel").text()
                    if (title.isNotEmpty()) {
                        title = cleanupText(title)
                    }

                    // 公開日時
                    val publishedAt = item.select("time.widget-toc-episode-datePublished").attr("datetime")
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
                } catch (e: Exception) {
                    android.util.Log.w("KakuyomuAdapter", "エピソード解析エラー", e)
                }
            }

            if (episodes.isEmpty()) {
                android.util.Log.w("KakuyomuAdapter", """
                    目次からエピソードを取得できませんでした
                    - エピソードURL: $episodeUrl
                    - tocItems.size: ${tocItems.size}
                    - セレクタ: ol.widget-toc-items li.widget-toc-episode
                """.trimIndent())
            } else {
                android.util.Log.d("KakuyomuAdapter", "目次からエピソード抽出成功: ${episodes.size}話")
            }

            episodes
        } catch (e: Exception) {
            android.util.Log.e("KakuyomuAdapter", "目次からのエピソード抽出エラー", e)
            emptyList()
        }
    }

    /**
     * JSONデータからエピソード一覧を抽出
     * Pascalコードを参考に、ページ内のJSONから直接エピソードIDを検索
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

            // 方法1: tableOfContents から章構造とエピソード順序を取得（推奨）
            val tableOfContents = workData.optJSONObject("tableOfContents")
            if (tableOfContents != null) {
                // chaptersから章一覧を取得
                val chaptersArray = tableOfContents.optJSONArray("chapters")
                if (chaptersArray != null && chaptersArray.length() > 0) {
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
                                        var title = episodeData.optString("title", "")

                                        // タイトルのクリーンアップ処理（HTMLエスケープ文字のデコード）
                                        if (title.isNotEmpty()) {
                                            title = cleanupText(title)
                                        }

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

                    if (episodes.isNotEmpty()) {
                        android.util.Log.d("KakuyomuAdapter", "JSONからエピソード抽出成功 (tableOfContents): ${episodes.size}話")
                        return episodes
                    }
                }
            }

            // 方法2: apolloStateから直接エピソードを検索（Pascalコード参考のフォールバック）
            android.util.Log.d("KakuyomuAdapter", "tableOfContents未使用、apolloStateから直接検索")
            apolloState.keys().forEach { key ->
                if (key.startsWith("Episode:")) {
                    try {
                        val episodeData = apolloState.getJSONObject(key)
                        // このエピソードが現在の作品に属するかチェック
                        val workRef = episodeData.optJSONObject("work")
                        val workRefKey = workRef?.optString("__ref")
                        if (workRefKey == "Work:$workId") {
                            val episodeId = episodeData.optString("id")
                            var title = episodeData.optString("title", "")

                            // タイトルのクリーンアップ処理
                            if (title.isNotEmpty()) {
                                title = cleanupText(title)
                            }

                            // 公開日時を取得
                            val publishedAt = episodeData.optString("publishedAt", "")
                            val publishedDate = if (publishedAt.isNotEmpty()) {
                                publishedAt.take(10)
                            } else {
                                getCurrentDate()
                            }

                            episodes.add(
                                EpisodeEntity(
                                    ncode = pseudoNcode,
                                    episode_no = episodeId,
                                    body = "",
                                    e_title = title,
                                    update_time = publishedDate,
                                    is_read = false,
                                    is_bookmark = false,
                                    reading_rate = 0.0f
                                )
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("KakuyomuAdapter", "エピソード解析エラー: $key", e)
                    }
                }
            }

            // エピソード番号でソート（公開日時順）
            episodes.sortBy { it.update_time }

            android.util.Log.d("KakuyomuAdapter", "JSONからエピソード抽出成功 (apolloState直接検索): ${episodes.size}話")
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
            } else {
                // エラーチェック: HTMLページ読み込みエラーの検出（Pascalコード参考）
                if (checkForLoadingError(episodeBody)) {
                    android.util.Log.w("KakuyomuAdapter", "HTMLページ読み込みエラーを検出: $episodeId")
                    return@withContext "★HTMLページ読み込みエラー\n本文を正しく取得できませんでした。\n後ほど再度お試しください。"
                }

                // 本文のクリーンアップ処理を適用
                episodeBody = cleanupEpisodeBody(episodeBody)
            }

            episodeBody
        } catch (e: Exception) {
            android.util.Log.e("KakuyomuAdapter", "エピソード本文取得エラー: $episodeId", e)
            ""
        }
    }

    /**
     * 個別エピソードページからエピソード情報（タイトル、章タイトル、本文）を取得
     * 目次ページから取得できない場合のフォールバック用
     * Pascalコードの ParsePage 関数を参考
     *
     * @param workId 作品ID
     * @param episodeId エピソードID
     * @param episodeNo エピソード番号（フォールバック用）
     * @return Triple<章タイトル, エピソードタイトル, 本文>
     */
    suspend fun fetchEpisodeDetails(
        workId: String,
        episodeId: String,
        episodeNo: Int
    ): Triple<String, String, String> = withContext(Dispatchers.IO) {
        try {
            applyRateLimit()

            val url = generateEpisodeUrl(workId, episodeId)
            val html = performHttpRequest(url)
            val doc = Jsoup.parse(html)

            // 章タイトルを取得（複数のパターン）
            var chapterTitle = ""

            // パターン1: chapterTitle level1（Pascalコード参考）
            val chapterElement1 = doc.select("p.chapterTitle.level1 span")
            if (chapterElement1.isNotEmpty()) {
                chapterTitle = chapterElement1.text()
            }

            // パターン2: chapterTitle level2（Pascalコード参考）
            if (chapterTitle.isEmpty()) {
                val chapterElement2 = doc.select("p.chapterTitle.level2 span")
                if (chapterElement2.isNotEmpty()) {
                    chapterTitle = chapterElement2.text()
                }
            }

            // エピソードタイトルを取得（複数のパターン、Pascalコード参考）
            var episodeTitle = ""

            // パターン1: header#contentMain-header（最優先、Pascalコード参考）
            val titleElement1 = doc.select("header#contentMain-header")
            if (titleElement1.isNotEmpty()) {
                episodeTitle = titleElement1.text()
            }

            // パターン2: widget-episodeTitle
            if (episodeTitle.isEmpty()) {
                val titleElement2 = doc.select("p.widget-episodeTitle")
                if (titleElement2.isNotEmpty()) {
                    episodeTitle = titleElement2.text()
                }
            }

            // パターン3: h1タグ（第2フォールバック、Pascalコード参考）
            if (episodeTitle.isEmpty()) {
                val titleElement3 = doc.select("h1").firstOrNull()
                if (titleElement3 != null) {
                    episodeTitle = titleElement3.text()
                }
            }

            // パターン4: 最後のフォールバック（Pascalコード参考）
            if (episodeTitle.isEmpty()) {
                episodeTitle = "第${episodeNo}話"
            }

            // エピソード本文を取得（既存のロジックを使用）
            var episodeBody = ""

            val bodyElement1 = doc.select("div.widget-episodeBody.js-episode-body")
            if (bodyElement1.isNotEmpty()) {
                episodeBody = bodyElement1.html()
            }

            if (episodeBody.isEmpty()) {
                val bodyElement2 = doc.select("div.widget-episodeBody")
                if (bodyElement2.isNotEmpty()) {
                    episodeBody = bodyElement2.html()
                }
            }

            if (episodeBody.isEmpty()) {
                val bodyElement3 = doc.select("div.js-episode-body")
                if (bodyElement3.isNotEmpty()) {
                    episodeBody = bodyElement3.html()
                }
            }

            // エラーチェック
            if (checkForLoadingError(episodeBody)) {
                android.util.Log.w("KakuyomuAdapter", "HTMLページ読み込みエラーを検出: $episodeId")
                episodeBody = "★HTMLページ読み込みエラー"
            } else {
                // 本文のクリーンアップ処理を適用
                episodeBody = cleanupEpisodeBody(episodeBody)
            }

            // テキストのクリーンアップ
            chapterTitle = cleanupText(chapterTitle)
            episodeTitle = cleanupText(episodeTitle)

            Triple(chapterTitle, episodeTitle, episodeBody)
        } catch (e: Exception) {
            android.util.Log.e("KakuyomuAdapter", "エピソード詳細取得エラー: $episodeId", e)
            Triple("", "第${episodeNo}話", "")
        }
    }

    /**
     * HTMLページ読み込みエラーをチェック
     * Pascalコードの SERRSTR チェックを参考
     *
     * @param html HTML文字列
     * @return true: エラーを検出、false: 正常
     */
    private fun checkForLoadingError(html: String): Boolean {
        // Pascalコードの SERRSTR = '<div class="dots-indicator" id="LoadingEpisode">'
        // 本文の先頭（最初の10文字以内）にこの文字列があればエラーと判定
        val errorIndicator = "<div class=\"dots-indicator\" id=\"LoadingEpisode\">"
        val checkLength = minOf(html.length, 200)  // 最初の200文字をチェック
        val prefix = html.take(checkLength)
        return prefix.contains(errorIndicator)
    }

    /**
     * エピソード本文のクリーンアップ処理
     * - HTMLエスケープ文字のデコード
     * - Unicodeエスケープ文字のデコード
     * - 改行タグの処理
     * - 余計なタグの除去
     *
     * @param html 生のHTML本文
     * @return クリーンアップされた本文
     */
    private fun cleanupEpisodeBody(html: String): String {
        var cleaned = html

        // 1. 改行タグを実際の改行に変換（<br />、<br>、<br/>）
        cleaned = cleaned.replace(Regex("<br\\s*/?>"), "\n")

        // 2. HTMLエスケープ文字のデコード（Jsoupが自動的に処理する部分もあるが、念のため）
        cleaned = decodeHtmlEntities(cleaned)

        // 3. Unicodeエスケープ文字のデコード（&#xxxx; 形式）
        cleaned = decodeNumericEntities(cleaned)

        // 4. Unicodeエスケープ文字のデコード（\uxxxx 形式）
        cleaned = decodeUnicodeEscapes(cleaned)

        // 5. 余計なタグの除去（空の <p> タグなど）
        cleaned = cleaned.replace(Regex("<p[^>]*>\\s*</p>"), "")

        // 6. 各行の先頭にある半角空白を除去
        cleaned = cleaned.lines().joinToString("\n") { line ->
            line.trimStart(' ')
        }

        return cleaned
    }

    /**
     * HTMLエスケープ文字をデコード
     * Pascalコードの Restore2RealChar 関数を参考
     */
    private fun decodeHtmlEntities(text: String): String {
        var decoded = text
        // 基本的なHTMLエスケープ文字（Jsoupで処理されない場合に備えて）
        decoded = decoded.replace("&lt;", "<")
        decoded = decoded.replace("&gt;", ">")
        decoded = decoded.replace("&quot;", "\"")
        decoded = decoded.replace("&nbsp;", " ")
        decoded = decoded.replace("&yen;", "\\")
        decoded = decoded.replace("&brvbar;", "|")
        decoded = decoded.replace("&copy;", "©")
        // &amp; は最後に処理（他のエスケープ文字に影響しないように）
        decoded = decoded.replace("&amp;", "&")
        return decoded
    }

    /**
     * 数値エスケープ文字をデコード（&#xxxx; 形式）
     * 10進数: &#1234;
     * 16進数: &#x4E00;
     * Pascalコードの Restore2RealChar 関数を参考
     */
    private fun decodeNumericEntities(text: String): String {
        var decoded = text

        // 16進数形式: &#x????;
        val hexPattern = Regex("&#x([0-9A-Fa-f]+);")
        decoded = hexPattern.replace(decoded) { matchResult ->
            try {
                val codePoint = matchResult.groupValues[1].toInt(16)
                String(Character.toChars(codePoint))
            } catch (e: Exception) {
                "？"  // デコード失敗時は？に置換
            }
        }

        // 10進数形式: &#????;
        val decPattern = Regex("&#([0-9]+);")
        decoded = decPattern.replace(decoded) { matchResult ->
            try {
                val codePoint = matchResult.groupValues[1].toInt(10)
                String(Character.toChars(codePoint))
            } catch (e: Exception) {
                "？"  // デコード失敗時は？に置換
            }
        }

        return decoded
    }

    /**
     * Unicodeエスケープ文字をデコード（\uxxxx 形式）
     * Pascalコードの Restore2RealChar 関数を参考
     */
    private fun decodeUnicodeEscapes(text: String): String {
        val pattern = Regex("\\\\u([0-9A-Fa-f]{4})")
        return pattern.replace(text) { matchResult ->
            try {
                val codePoint = matchResult.groupValues[1].toInt(16)
                String(Character.toChars(codePoint))
            } catch (e: Exception) {
                "？"  // デコード失敗時は？に置換
            }
        }
    }

    /**
     * 一般的なテキストのクリーンアップ処理
     * タイトル、作者名、エピソードタイトルなどに使用
     * - HTMLエスケープ文字のデコード
     * - Unicodeエスケープ文字のデコード
     * - 前後の空白を除去
     *
     * @param text 生のテキスト
     * @return クリーンアップされたテキスト
     */
    private fun cleanupText(text: String): String {
        var cleaned = text

        // 1. HTMLエスケープ文字のデコード
        cleaned = decodeHtmlEntities(cleaned)

        // 2. Unicodeエスケープ文字のデコード（&#xxxx; 形式）
        cleaned = decodeNumericEntities(cleaned)

        // 3. Unicodeエスケープ文字のデコード（\uxxxx 形式）
        cleaned = decodeUnicodeEscapes(cleaned)

        // 4. 前後の空白を除去
        cleaned = cleaned.trim()

        return cleaned
    }

    /**
     * あらすじのクリーンアップ処理
     * - HTMLエスケープ文字のデコード
     * - Unicodeエスケープ文字のデコード
     * - "…続きを読む"などの不要なテキストを除去
     * - エスケープされた引用符の復元
     *
     * @param text 生のあらすじテキスト
     * @return クリーンアップされたあらすじ
     */
    private fun cleanupSynopsis(text: String): String {
        var cleaned = text

        // 1. あらすじ部分の"が\"とエスケープされているため"に戻す（Pascalコード参考）
        cleaned = cleaned.replace("\\\"", "\"")

        // 2. 改行が\n表記となっている場合は実際の改行に変換（Pascalコード参考）
        cleaned = cleaned.replace("\\n", "\n")

        // 3. HTMLエスケープ文字のデコード
        cleaned = decodeHtmlEntities(cleaned)

        // 4. Unicodeエスケープ文字のデコード（&#xxxx; 形式）
        cleaned = decodeNumericEntities(cleaned)

        // 5. Unicodeエスケープ文字のデコード（\uxxxx 形式）
        cleaned = decodeUnicodeEscapes(cleaned)

        // 6. 前書きの最後にある"…続きを読む"を削除する（Pascalコード参考）
        cleaned = cleaned.replace("…続きを読む", "")

        // 7. 前後の空白を除去
        cleaned = cleaned.trim()

        return cleaned
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
