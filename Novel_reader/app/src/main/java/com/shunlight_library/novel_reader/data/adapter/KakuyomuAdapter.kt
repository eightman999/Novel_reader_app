/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Kakuyomu site adapter with HTML scraping.
 */
package com.shunlight_library.novel_reader.data.adapter

import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity
import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator
import com.shunlight_library.novel_reader.utils.AppLogger
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
 * カクヨムのエピソード取得結果（内部使用）
 * エピソードとマッピング情報を一緒に保持する
 */
internal data class KakuyomuEpisodeWithMapping(
    val episode: EpisodeEntity,
    val kakuyomuEpisodeId: String  // カクヨムの実際のエピソードID
)

/**
 * カクヨム小説の取得結果（マッピング情報を含む）
 * 再取得・更新処理でマッピング情報を確実に保存するために使用
 */
data class NovelWithEpisodesAndMappings(
    val novelDesc: NovelDescEntity,
    val episodes: List<EpisodeEntity>,
    val episodeMappings: Map<Int, String>  // 連番 → カクヨムEpisodeID
)

/**
 * カクヨム更新確認用の軽量サマリー
 * 更新確認ではエピソード一覧全件を取得せず、必要最小限の情報のみ返す。
 */
data class KakuyomuUpdateSummary(
    val novelDesc: NovelDescEntity,
    val latestEpisodeCount: Int
)

/**
 * カクヨム用のアダプター実装
 *
 * カクヨムには公式APIが存在しないため、HTMLスクレイピングで情報を取得する。
 * レート制限として0.1秒間隔でのアクセスを実施。
 *
 * URL構造:
 * - 作品ページ: https://kakuyomu.jp/works/{workId}
 * - エピソードページ: https://kakuyomu.jp/works/{workId}/episodes/{episodeId}
 */
class KakuyomuAdapter : NovelSiteAdapter {
    // 最後に取得したエピソードのマッピング情報を保持
    private var cachedMappings: Map<Int, String> = emptyMap()

    companion object {
        private const val BASE_URL = "https://kakuyomu.jp"
        private const val RATE_LIMIT_DELAY_MS = 500L  // 0.5秒（スクレイピング時の推奨間隔）
        @Volatile private var lastAccessTime = 0L

        // PC版User-Agentを使用（モバイル版では目次が省略される可能性があるため）
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
    
    /**
     * 最後に取得したマッピング情報を取得（呼び出し側が保存に使用）
     */
    fun getCachedMappings(): Map<Int, String> = cachedMappings

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
        val episodesWithoutBody = parseEpisodeList(workId, novelDesc.ncode, doc)

        // 各エピソードの本文を取得
        AppLogger.d("KakuyomuAdapter", "エピソード本文のダウンロード開始: ${episodesWithoutBody.size}話")
        val episodesWithBody = episodesWithoutBody.map { episode ->
            // episode.episode_no は連番（1, 2, 3...）なので、キャッシュから実際のIDを取得
            val actualEpisodeId = cachedMappings[episode.episode_no.toInt()] ?: episode.episode_no
            val episodeBody = fetchEpisodeContent(workId, actualEpisodeId)
            episode.copy(body = episodeBody)
        }

        // エピソード数の不一致をチェック
        val expectedCount = novelDesc.total_ep
        val actualCount = episodesWithBody.size

        if (expectedCount != actualCount) {
            AppLogger.e("KakuyomuAdapter", """
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
            AppLogger.d("KakuyomuAdapter", "小説とエピソード取得完了: ${novelDesc.title}, ${episodesWithBody.size}話")
        }

        Pair(novelDesc, episodesWithBody)
    }

    /**
     * 小説情報とエピソードをマッピング情報付きで取得（1話ずつ保存）
     *
     * このメソッドは再取得・更新処理で使用され、エピソードマッピング情報を
     * 確実に保存できるようにする。
     * メモリリーク防止のため、1話取得→保存→次の話、という方式で処理する。
     *
     * @param novelId カクヨムの作品IDまたはPseudo-Ncode
     * @param repository 保存先リポジトリ（nullの場合は旧方式でメモリに保持）
     * @param onProgress 進捗コールバック (current, total) -> Unit
     * @return NovelWithEpisodesAndMappings（小説情報、エピソード、マッピング情報）
     */
    suspend fun fetchNovelWithEpisodesIncludingMappings(
        novelId: String,
        repository: com.shunlight_library.novel_reader.data.repository.NovelRepository? = null,
        onProgress: ((Int, Int) -> Unit)? = null
    ): NovelWithEpisodesAndMappings = withContext(Dispatchers.IO) {
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
        val episodesWithoutBody = parseEpisodeList(workId, novelDesc.ncode, doc)

        android.util.Log.d("KakuyomuAdapter", "エピソード本文のダウンロード開始: ${episodesWithoutBody.size}話")

        // repositoryが指定されている場合は1話ずつ保存
        if (repository != null) {
            // 1話ずつ取得→保存（メモリリーク防止）
            episodesWithoutBody.forEachIndexed { index, episode ->
                val actualEpisodeId = cachedMappings[episode.episode_no.toInt()] ?: episode.episode_no
                val episodeBody = fetchEpisodeContent(workId, actualEpisodeId)
                val episodeWithBody = episode.copy(body = episodeBody)

                // 1話ずつ保存
                repository.insertEpisode(episodeWithBody)

                // 進捗通知
                onProgress?.invoke(index + 1, episodesWithoutBody.size)
            }

            // エピソード数の不一致をチェック
            val expectedCount = novelDesc.total_ep
            val actualCount = episodesWithoutBody.size

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
                android.util.Log.d("KakuyomuAdapter", "小説とエピソード取得完了（1話ずつ保存）: ${novelDesc.title}, ${episodesWithoutBody.size}話")
            }

            // マッピング情報をコピー
            val mappingsCopy = cachedMappings.toMap()
            android.util.Log.d("KakuyomuAdapter", "マッピング情報: ${mappingsCopy.size}件 (例: ${mappingsCopy.entries.take(3)})")

            // 空リストを返す（すでに保存済み）
            return@withContext NovelWithEpisodesAndMappings(novelDesc, emptyList(), mappingsCopy)
        } else {
            // 旧方式：全話をメモリに保持（後方互換性のため残す）
            val episodesWithBody = episodesWithoutBody.map { episode ->
                val actualEpisodeId = cachedMappings[episode.episode_no.toInt()] ?: episode.episode_no
                val episodeBody = fetchEpisodeContent(workId, actualEpisodeId)
                episode.copy(body = episodeBody)
            }

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

            val mappingsCopy = cachedMappings.toMap()
            android.util.Log.d("KakuyomuAdapter", "マッピング情報: ${mappingsCopy.size}件 (例: ${mappingsCopy.entries.take(3)})")

            return@withContext NovelWithEpisodesAndMappings(novelDesc, episodesWithBody, mappingsCopy)
        }
    }

    /**
     * 小説のメタデータとエピソードリスト（本文なし）のみを取得する
     *
     * このメソッドは再取得時の確認ダイアログ前に使用され、
     * 本文を取得せずにエピソード数などのメタデータのみを取得する。
     *
     * @param novelId カクヨムの作品IDまたはPseudo-Ncode
     * @return 小説メタデータとエピソードリスト（本文は空文字列）のペア
     */
    suspend fun fetchNovelMetadataWithEpisodeList(novelId: String): Pair<NovelDescEntity, List<EpisodeEntity>> = withContext(Dispatchers.IO) {
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
        val episodesWithoutBody = parseEpisodeList(workId, novelDesc.ncode, doc)

        AppLogger.d("KakuyomuAdapter", "小説メタデータとエピソードリスト取得完了（本文なし）: ${novelDesc.title}, ${episodesWithoutBody.size}話")

        Pair(novelDesc, episodesWithoutBody)
    }

    /**
     * 更新確認専用の軽量メタデータ取得。
     * 作品ページを1回取得し、必要時のみ第一話ページ目次を1回だけフォールバックで取得する。
     */
    suspend fun fetchUpdateSummary(novelId: String): KakuyomuUpdateSummary = withContext(Dispatchers.IO) {
        val workId = if (PseudoNcodeGenerator.isKakuyomuNcode(novelId)) {
            PseudoNcodeGenerator.extractKakuyomuWorkId(novelId)
        } else {
            novelId
        }

        applyRateLimit()

        val url = generateWebUrl(workId)
        val html = performHttpRequest(url)
        val doc = Jsoup.parse(html)

        val novelDesc = parseNovelInfo(doc, workId)
        val workPageCount = extractLatestEpisodeCountFromWorkPage(doc, workId)
        val latestEpisodeCount = if (workPageCount > 0) workPageCount else novelDesc.total_ep

        val normalizedNovelDesc = if (novelDesc.total_ep != latestEpisodeCount) {
            novelDesc.copy(
                total_ep = latestEpisodeCount,
                noveltype = if (latestEpisodeCount == 1) 2 else 1
            )
        } else {
            novelDesc
        }

        AppLogger.d(
            "KakuyomuAdapter",
            "更新確認サマリー取得完了: ${normalizedNovelDesc.title}, latestEpisodeCount=$latestEpisodeCount"
        )

        KakuyomuUpdateSummary(
            novelDesc = normalizedNovelDesc,
            latestEpisodeCount = latestEpisodeCount
        )
    }

    override suspend fun checkForUpdates(novelId: String, currentEpisodeCount: Int): Boolean = withContext(Dispatchers.IO) {
        val summary = fetchUpdateSummary(novelId)
        summary.latestEpisodeCount > currentEpisodeCount
    }

    private suspend fun extractLatestEpisodeCountFromWorkPage(doc: Document, workId: String): Int {
        // 1) JSONから取得（最優先）
        val (_, workData) = extractNextDataJson(doc, workId)
        val jsonCount = workData?.optInt("publicEpisodeCount") ?: 0
        if (jsonCount > 0) {
            return jsonCount
        }

        // 2) 作品ページの目次要素から取得
        val newStructureCount = doc.select("a.WorkTocSection_link__ocg9K").size
        if (newStructureCount > 0) {
            return newStructureCount
        }

        val oldStructureCount = doc.select("ol.widget-toc-items li.widget-toc-episode").size
        if (oldStructureCount > 0) {
            return oldStructureCount
        }

        // 3) 最終フォールバック: 第一話ページの目次を1回だけ取得して件数を数える
        return extractEpisodeCountFromFirstEpisodeToc(doc, workId)
    }

    private suspend fun extractEpisodeCountFromFirstEpisodeToc(workDoc: Document, workId: String): Int {
        val firstEpisodeLink = workDoc.select("a.widget-toc-episode-episodeTitle").firstOrNull()?.attr("href")
            ?: workDoc.select("a.WorkTocSection_link__ocg9K").firstOrNull()?.attr("href")
            ?: workDoc.select("a[href*='/episodes/']").firstOrNull()?.attr("href")

        if (firstEpisodeLink.isNullOrBlank()) {
            AppLogger.w("KakuyomuAdapter", "第一話リンクが取得できないため件数フォールバックをスキップ: workId=$workId")
            return 0
        }

        applyRateLimit()

        val episodeUrl = if (firstEpisodeLink.startsWith("http")) {
            firstEpisodeLink
        } else {
            "$BASE_URL$firstEpisodeLink"
        }
        val episodeHtml = performHttpRequest(episodeUrl)
        val episodeDoc = Jsoup.parse(episodeHtml)

        val tocItemsCount = episodeDoc.select("ol.widget-toc-items li.widget-toc-episode").size
        if (tocItemsCount > 0) {
            return tocItemsCount
        }

        val tocLinksCount = episodeDoc.select("a.widget-toc-episode-episodeTitle").size
        if (tocLinksCount > 0) {
            return tocLinksCount
        }

        AppLogger.w("KakuyomuAdapter", "第一話目次からエピソード件数を取得できませんでした: workId=$workId")
        return 0
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
     * @param additionalHeaders 追加のHTTPヘッダー（オプション）
     * @return HTML文字列
     * @throws Exception ネットワークエラーまたはHTTPエラー
     */
    private suspend fun performHttpRequest(
        urlString: String,
        maxRetries: Int = 3,
        additionalHeaders: Map<String, String> = emptyMap()
    ): String {
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

                // 追加のヘッダーを設定
                additionalHeaders.forEach { (key, value) ->
                    connection.setRequestProperty(key, value)
                }

                // リクエストヘッダーをログ出力
                AppLogger.d("KakuyomuAdapter", "HTTP接続: URL=$urlString")
                AppLogger.d("KakuyomuAdapter", "User-Agent: $USER_AGENT")
                additionalHeaders.forEach { (key, value) ->
                    AppLogger.d("KakuyomuAdapter", "追加ヘッダー: $key = $value")
                }

                connection.connect()
                val responseCode = connection.responseCode

                // レスポンスヘッダーをログ出力
                AppLogger.d("KakuyomuAdapter", "レスポンスコード: $responseCode")
                connection.headerFields.forEach { (key, values) ->
                    if (key != null) {
                        AppLogger.d("KakuyomuAdapter", "レスポンスヘッダー: $key = ${values.joinToString(", ")}")
                    }
                }

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

                        AppLogger.d("KakuyomuAdapter", "HTTP取得成功: $urlString (Content-Length: $contentLength, Actual: $actualLength)")

                        // HTML全文をログ出力（4000文字ずつ分割）
                        // 大量のログが出るため、デバッグ時のみ有効化
                        // logHtmlContent(urlString, htmlString)

                        return htmlString
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        AppLogger.e("KakuyomuAdapter", "404 Not Found: $urlString")
                        throw Exception("HTTP 404: ページが見つかりません")
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        AppLogger.e("KakuyomuAdapter", "403 Forbidden: $urlString")
                        throw Exception("HTTP 403: アクセスが拒否されました")
                    }
                    else -> {
                        throw Exception("HTTP error: $responseCode")
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                AppLogger.w("KakuyomuAdapter", "タイムアウト (試行 $attempt/$maxRetries): $urlString")
                if (attempt < maxRetries) {
                    delay(1000L * attempt)  // 指数バックオフ: 1秒、2秒、3秒
                }
            } catch (e: java.net.UnknownHostException) {
                lastException = e
                AppLogger.w("KakuyomuAdapter", "ネットワークエラー (試行 $attempt/$maxRetries): $urlString")
                if (attempt < maxRetries) {
                    delay(1000L * attempt)
                }
            } catch (e: java.net.ConnectException) {
                lastException = e
                AppLogger.w("KakuyomuAdapter", "接続エラー (試行 $attempt/$maxRetries): $urlString")
                if (attempt < maxRetries) {
                    delay(1000L * attempt)
                }
            } catch (e: Exception) {
                // HTTPエラーやその他のエラーは即座にスロー（再試行しない）
                AppLogger.e("KakuyomuAdapter", "HTTP取得エラー: $urlString", e)
                throw e
            } finally {
                connection?.disconnect()
            }
        }

        // 最大試行回数を超えた場合
        val exception = lastException ?: Exception("HTTP取得失敗: $urlString")
        AppLogger.e("KakuyomuAdapter", "最大再試行回数を超えました: $urlString", exception)
        throw exception
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

        AppLogger.d("KakuyomuAdapter", "あらすじ取得: source=$synopsisSource, length=${synopsis.length}")

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

        AppLogger.d("KakuyomuAdapter", "タグ取得: source=$tagSource, count=${tags.size}, tags=$tags")

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
        AppLogger.d("KakuyomuAdapter", """
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

        // 完結フラグ検出: HTMLに"完結済"や"Completed"表記があるか確認
        val isCompleted = detectKakuyomuCompletion(doc, workData)
        val endFlag = if (isCompleted) 1 else 2

        return NovelDescEntity(
            ncode = pseudoNcode,
            title = title,
            author = author,
            Synopsis = synopsis,
            main_tag = mainTag,
            sub_tag = subTag,
            rating = 2,  // カクヨムは一般のみ
            last_update_date = lastUpdateDate,  // サイト上の最終更新日
            total_ep = totalEp,
            general_all_no = 0,  // カクヨムにはこの情報がない
            userid = null,  // HTMLから抽出困難
            noveltype = if (totalEp == 1) 2 else 1,  // 1話のみなら短編、それ以外は連載
            length = null,  // HTMLから抽出困難
            updated_at = lastUpdateDate,  // サイト上の最終更新日時
            is_favorite = 0,
            site_type = NovelSiteAdapter.SITE_TYPE_KAKUYOMU,
            registered_at = getCurrentDateTime(),  // データベース登録日時
            sub_site = 0,  // カクヨムはsite_type=2で管理、sub_siteは使用しない
            end_flag = endFlag
        )
    }

    /**
     * HTMLからエピソード一覧を抽出（TOC情報のみ、本文は含まない）
     * episode_sidebarエンドポイントから優先的に取得し、失敗した場合のみ他の方法を試行
     *
     * @param workDoc 呼び出し元で取得済みの作品ページDocument（再フェッチ防止）
     */
    private suspend fun parseEpisodeList(workId: String, pseudoNcode: String, workDoc: Document): List<EpisodeEntity> {
        // 各呼び出し開始時にキャッシュをリセットして別作品の残留を防ぐ
        cachedMappings = emptyMap()

        // 呼び出し元の取得済みdocから最初のエピソードリンクを抽出（重複フェッチ防止）
        val firstEpisodeLink = workDoc.select("a.widget-toc-episode-episodeTitle").firstOrNull()?.attr("href")
            ?: workDoc.select("a.WorkTocSection_link__ocg9K").firstOrNull()?.attr("href")
            ?: workDoc.select("a[href*='/episodes/']").firstOrNull()?.attr("href")
            ?: ""

        // 方法1: episode_sidebarエンドポイントから取得（最優先、最も確実）
        val episodesWithMapping = extractEpisodesFromSidebar(workId, pseudoNcode, firstEpisodeLink)
        if (episodesWithMapping.isNotEmpty()) {
            // マッピング情報をキャッシュに保存
            cachedMappings = episodesWithMapping.associate {
                it.episode.episode_no.toInt() to it.kakuyomuEpisodeId
            }
            AppLogger.d("KakuyomuAdapter", "エピソード一覧取得成功 (episode_sidebar): ${episodesWithMapping.size}話")
            return episodesWithMapping.map { it.episode }
        }

        AppLogger.d("KakuyomuAdapter", "episode_sidebarからエピソード取得失敗、他の方法を試行")

        // 方法2: エピソードページの目次から取得（フォールバック）
        val episodesWithMappingFromToc = extractEpisodesFromToc(workId, pseudoNcode, firstEpisodeLink)
        if (episodesWithMappingFromToc.isNotEmpty()) {
            // マッピング情報をキャッシュに保存
            cachedMappings = episodesWithMappingFromToc.associate {
                it.episode.episode_no.toInt() to it.kakuyomuEpisodeId
            }
            AppLogger.d("KakuyomuAdapter", "エピソード一覧取得成功 (目次): ${episodesWithMappingFromToc.size}話")
            return episodesWithMappingFromToc.map { it.episode }
        }

        AppLogger.d("KakuyomuAdapter", "目次からエピソード取得失敗、他の方法を試行")

        // 方法3: 呼び出し元の取得済みdocを再利用（フォールバック、重複フェッチなし）
        val doc = workDoc

        AppLogger.d("KakuyomuAdapter", "=== 作品ページHTML構造調査開始 ===")
        AppLogger.d("KakuyomuAdapter", "作品URL: https://kakuyomu.jp/works/$workId")
        AppLogger.d("KakuyomuAdapter", "（取得済みdocを再利用）")

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
            AppLogger.d("KakuyomuAdapter", "作品ページセレクタ「$selector」: ${elements.size}件")
            if (selector == "a[href*='/episodes/']" && elements.size > 0) {
                val sample = elements.take(3)
                sample.forEach { link ->
                    AppLogger.d("KakuyomuAdapter", "  サンプルリンク - href: ${link.attr("href")}, class: ${link.className()}, text: ${link.text().take(50)}")
                }
            }
        }

        AppLogger.d("KakuyomuAdapter", "=== 作品ページHTML構造調査終了 ===")

        val episodes = mutableListOf<EpisodeEntity>()

        // 新しいHTML構造: WorkTocSection_link
        var episodeElements = doc.select("a.WorkTocSection_link__ocg9K")
        var isNewStructure = episodeElements.isNotEmpty()

        // 古い構造（フォールバック）
        if (!isNewStructure) {
            episodeElements = doc.select("ol.widget-toc-items li.widget-toc-episode")
        }

        AppLogger.d("KakuyomuAdapter", "エピソード一覧取得 (HTML): ${episodeElements.size}話 (新構造: $isNewStructure)")

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

            val sequentialNo = index + 1
            episodes.add(
                EpisodeEntity(
                    ncode = pseudoNcode,
                    episode_no = sequentialNo.toString(),  // 連番（他メソッドと統一）
                    body = "",
                    e_title = episodeTitle,
                    update_time = publishedDate,
                    is_read = 0,
                    is_bookmark = 0,
                    reading_rate = 0.0f
                )
            )
        }

        // 方法3 でも cachedMappings を構築（連番→カクヨムID）
        val fallbackMappings = mutableMapOf<Int, String>()
        var idx = 0
        val allElements = if (doc.select("a.WorkTocSection_link__ocg9K").isNotEmpty())
            doc.select("a.WorkTocSection_link__ocg9K")
        else
            doc.select("ol.widget-toc-items li.widget-toc-episode")
        allElements.forEach { elem ->
            val link = elem.attr("href").ifEmpty { elem.select("a").attr("href") }
            val eid = link.substringAfterLast("/")
            if (eid.isNotEmpty()) {
                idx++
                fallbackMappings[idx] = eid
            }
        }
        if (fallbackMappings.isNotEmpty()) {
            cachedMappings = fallbackMappings
        }

        AppLogger.d("KakuyomuAdapter", "エピソード一覧パース完了 (HTML方法3): ${episodes.size}話")
        return episodes
    }

    /**
     * episode_sidebarエンドポイントから全エピソード一覧を抽出
     * このエンドポイントはAJAXで目次を取得するために使用され、全エピソードが含まれる
     * 章も含まれるが、本文が空なので後で除外する
     *
     * @param workId 作品ID
     * @param pseudoNcode 疑似Ncode
     * @param firstEpisodeLink 呼び出し元で取得済みの最初のエピソードリンク（再フェッチ防止）
     * @return エピソードとマッピング情報のリスト（取得失敗時は空リスト）
     */
    private suspend fun extractEpisodesFromSidebar(workId: String, pseudoNcode: String, firstEpisodeLink: String): List<KakuyomuEpisodeWithMapping> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (firstEpisodeLink.isEmpty()) {
                AppLogger.w("KakuyomuAdapter", "最初のエピソードが見つかりません")
                return@withContext emptyList()
            }

            val firstEpisodeId = firstEpisodeLink.substringAfterLast("/")

            applyRateLimit()

            // episode_sidebarエンドポイントを取得
            val sidebarUrl = "https://kakuyomu.jp/works/$workId/episodes/$firstEpisodeId/episode_sidebar"
            AppLogger.d("KakuyomuAdapter", "episode_sidebar取得: $sidebarUrl")

            // AJAXリクエストに必要なヘッダーを設定
            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "https://kakuyomu.jp/works/$workId/episodes/$firstEpisodeId",
                "Accept" to "*/*"
            )

            val sidebarHtml = performHttpRequest(sidebarUrl, additionalHeaders = headers)
            val sidebarDoc = Jsoup.parse(sidebarHtml)

            AppLogger.d("KakuyomuAdapter", "=== episode_sidebar HTML構造調査開始 ===")
            AppLogger.d("KakuyomuAdapter", "HTML長: ${sidebarHtml.length}文字")

            // 目次リストを取得
            val tocOl = sidebarDoc.select("ol.widget-toc-items")
            AppLogger.d("KakuyomuAdapter", "目次リスト (ol.widget-toc-items): ${tocOl.size}件")

            val allLi = sidebarDoc.select("ol.widget-toc-items li")
            AppLogger.d("KakuyomuAdapter", "全てのli要素: ${allLi.size}件")

            val episodeLi = sidebarDoc.select("ol.widget-toc-items li.widget-toc-episode")
            AppLogger.d("KakuyomuAdapter", "エピソードli要素: ${episodeLi.size}件")

            val chapterLi = sidebarDoc.select("ol.widget-toc-items li.widget-toc-chapter")
            AppLogger.d("KakuyomuAdapter", "章li要素: ${chapterLi.size}件")

            AppLogger.d("KakuyomuAdapter", "=== episode_sidebar HTML構造調査終了 ===")

            // 全エピソードを抽出（章も含む）
            val episodesWithMapping = mutableListOf<KakuyomuEpisodeWithMapping>()
            val tocItems = sidebarDoc.select("ol.widget-toc-items li.widget-toc-episode")

            AppLogger.d("KakuyomuAdapter", "episode_sidebarから検出したエピソード数: ${tocItems.size}")

            tocItems.forEachIndexed { index, item ->
                try {
                    // エピソードリンク: /works/{workId}/episodes/{episodeId}
                    val link = item.select("a.widget-toc-episode-episodeTitle").attr("href")

                    if (link.isEmpty()) {
                        AppLogger.w("KakuyomuAdapter", "エピソード${index + 1}: リンクが空です")
                        return@forEachIndexed
                    }

                    val kakuyomuEpisodeId = link.substringAfterLast("/")

                    // エピソードタイトル
                    var title = item.select("span.widget-toc-episode-titleLabel").text()
                    if (title.isEmpty()) {
                        title = item.select("a.widget-toc-episode-episodeTitle").text()
                    }

                    if (title.isNotEmpty()) {
                        title = cleanupText(title)
                    }

                    // 公開日時
                    val publishedAt = item.select("time.widget-toc-episode-datePublished").attr("datetime")
                    val publishedDate = if (publishedAt.isNotEmpty()) {
                        publishedAt.take(10)
                    } else {
                        getCurrentDate()
                    }

                    val episode = EpisodeEntity(
                        ncode = pseudoNcode,
                        episode_no = (index + 1).toString(),  // 連番（1, 2, 3...）を使用
                        body = "",  // 本文は後で個別に取得
                        e_title = title,
                        update_time = publishedDate,
                        is_read = 0,
                        is_bookmark = 0,
                        reading_rate = 0.0f
                    )

                    episodesWithMapping.add(
                        KakuyomuEpisodeWithMapping(
                            episode = episode,
                            kakuyomuEpisodeId = kakuyomuEpisodeId
                        )
                    )

                    // 最初と最後のエピソードをログ出力
                    if (index < 3 || index >= tocItems.size - 3) {
                        AppLogger.d("KakuyomuAdapter", "エピソード${index + 1}: ID=$kakuyomuEpisodeId, タイトル=${title.take(30)}, 日付=$publishedDate")
                    }
                } catch (e: Exception) {
                    AppLogger.w("KakuyomuAdapter", "エピソード${index + 1}の解析エラー", e)
                }
            }

            if (episodesWithMapping.isEmpty()) {
                AppLogger.w("KakuyomuAdapter", """
                    episode_sidebarからエピソードを取得できませんでした
                    - URL: $sidebarUrl
                    - tocItems.size: ${tocItems.size}
                """.trimIndent())
            } else {
                AppLogger.d("KakuyomuAdapter", "episode_sidebarからエピソード抽出成功: ${episodesWithMapping.size}話（最初: ${episodesWithMapping.firstOrNull()?.episode?.e_title}, 最後: ${episodesWithMapping.lastOrNull()?.episode?.e_title}）")
            }

            episodesWithMapping
        } catch (e: Exception) {
            AppLogger.e("KakuyomuAdapter", "episode_sidebarからのエピソード抽出エラー", e)
            emptyList()
        }
    }

    /**
     * エピソードページの目次からエピソード一覧を抽出
     * 任意のエピソードページには完全な目次が表示されているため、そこから全エピソードを取得
     *
     * @param workId 作品ID
     * @param pseudoNcode 疑似Ncode
     * @param firstEpisodeLink 呼び出し元で取得済みの最初のエピソードリンク（再フェッチ防止）
     * @return エピソードとマッピング情報のリスト（取得失敗時は空リスト）
     */
    private suspend fun extractEpisodesFromToc(workId: String, pseudoNcode: String, firstEpisodeLink: String): List<KakuyomuEpisodeWithMapping> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (firstEpisodeLink.isEmpty()) {
                AppLogger.w("KakuyomuAdapter", "最初のエピソードが見つかりません")
                return@withContext emptyList()
            }

            AppLogger.d("KakuyomuAdapter", "最初のエピソードリンク使用: $firstEpisodeLink")

            applyRateLimit()

            // エピソードページを取得（目次が含まれる）
            val episodeUrl = if (firstEpisodeLink.startsWith("http")) firstEpisodeLink else "https://kakuyomu.jp$firstEpisodeLink"
            AppLogger.d("KakuyomuAdapter", "エピソードページを取得: $episodeUrl")

            val episodeHtml = performHttpRequest(episodeUrl)
            val episodeDoc = Jsoup.parse(episodeHtml)

            AppLogger.d("KakuyomuAdapter", "=== エピソードページHTML構造調査開始 ===")
            AppLogger.d("KakuyomuAdapter", "エピソードURL: $episodeUrl")
            AppLogger.d("KakuyomuAdapter", "HTML長: ${episodeHtml.length}文字")

            // 目次セクションの存在確認
            val tocSection = episodeDoc.select("section.widget-toc")
            AppLogger.d("KakuyomuAdapter", "目次セクション (section.widget-toc): ${tocSection.size}件")

            val tocOl = episodeDoc.select("ol.widget-toc-items")
            AppLogger.d("KakuyomuAdapter", "目次リスト (ol.widget-toc-items): ${tocOl.size}件")

            // 全てのli要素を確認
            val allLi = episodeDoc.select("ol.widget-toc-items li")
            AppLogger.d("KakuyomuAdapter", "全てのli要素: ${allLi.size}件")

            // エピソードli要素のみを確認
            val episodeLi = episodeDoc.select("ol.widget-toc-items li.widget-toc-episode")
            AppLogger.d("KakuyomuAdapter", "エピソードli要素 (li.widget-toc-episode): ${episodeLi.size}件")

            // 章li要素も確認（デバッグ用）
            val chapterLi = episodeDoc.select("ol.widget-toc-items li.widget-toc-chapter")
            AppLogger.d("KakuyomuAdapter", "章li要素 (li.widget-toc-chapter): ${chapterLi.size}件")

            // エピソードリンクの直接検索も試行
            val episodeLinks = episodeDoc.select("a.widget-toc-episode-episodeTitle")
            AppLogger.d("KakuyomuAdapter", "エピソードリンク (a.widget-toc-episode-episodeTitle): ${episodeLinks.size}件")

            AppLogger.d("KakuyomuAdapter", "=== エピソードページHTML構造調査終了 ===")

            // 目次から全エピソードを抽出
            val episodesWithMapping = mutableListOf<KakuyomuEpisodeWithMapping>()
            val tocItems = episodeDoc.select("ol.widget-toc-items li.widget-toc-episode")

            AppLogger.d("KakuyomuAdapter", "目次から検出したエピソード数: ${tocItems.size}")

            tocItems.forEachIndexed { index, item ->
                try {
                    // エピソードリンク: /works/{workId}/episodes/{episodeId}
                    val link = item.select("a.widget-toc-episode-episodeTitle").attr("href")

                    if (link.isEmpty()) {
                        AppLogger.w("KakuyomuAdapter", "エピソード${index + 1}: リンクが空です")
                        return@forEachIndexed
                    }

                    val kakuyomuEpisodeId = link.substringAfterLast("/")

                    // エピソードタイトル
                    var title = item.select("span.widget-toc-episode-titleLabel").text()
                    if (title.isEmpty()) {
                        // フォールバック: aタグのテキスト全体を取得
                        title = item.select("a.widget-toc-episode-episodeTitle").text()
                        AppLogger.d("KakuyomuAdapter", "エピソード${index + 1}: タイトルをaタグから取得")
                    }

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

                    val episode = EpisodeEntity(
                        ncode = pseudoNcode,
                        episode_no = (index + 1).toString(),  // 連番（1, 2, 3...）を使用
                        body = "",  // 本文は後で個別に取得
                        e_title = title,
                        update_time = publishedDate,
                        is_read = 0,
                        is_bookmark = 0,
                        reading_rate = 0.0f
                    )

                    episodesWithMapping.add(
                        KakuyomuEpisodeWithMapping(
                            episode = episode,
                            kakuyomuEpisodeId = kakuyomuEpisodeId
                        )
                    )

                    // 最初と最後のエピソードをログ出力
                    if (index < 3 || index >= tocItems.size - 3) {
                        AppLogger.d("KakuyomuAdapter", "エピソード${index + 1}: ID=$kakuyomuEpisodeId, タイトル=${title.take(20)}, 日付=$publishedDate")
                    }
                } catch (e: Exception) {
                    AppLogger.w("KakuyomuAdapter", "エピソード${index + 1}の解析エラー", e)
                }
            }

            if (episodesWithMapping.isEmpty()) {
                AppLogger.w("KakuyomuAdapter", """
                    目次からエピソードを取得できませんでした
                    - エピソードURL: $episodeUrl
                    - tocItems.size: ${tocItems.size}
                    - セレクタ: ol.widget-toc-items li.widget-toc-episode
                """.trimIndent())
            } else {
                AppLogger.d("KakuyomuAdapter", "目次からエピソード抽出成功: ${episodesWithMapping.size}話（最初: ${episodesWithMapping.firstOrNull()?.episode?.e_title}, 最後: ${episodesWithMapping.lastOrNull()?.episode?.e_title}）")
            }

            episodesWithMapping
        } catch (e: Exception) {
            AppLogger.e("KakuyomuAdapter", "目次からのエピソード抽出エラー", e)
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
                AppLogger.d("KakuyomuAdapter", "JSONデータが見つかりません")
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
                                                is_read = 0,
                                                is_bookmark = 0,
                                                reading_rate = 0.0f
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (episodes.isNotEmpty()) {
                        AppLogger.d("KakuyomuAdapter", "JSONからエピソード抽出成功 (tableOfContents): ${episodes.size}話")
                        return episodes
                    }
                }
            }

            // 方法2: apolloStateから直接エピソードを検索（Pascalコード参考のフォールバック）
            AppLogger.d("KakuyomuAdapter", "tableOfContents未使用、apolloStateから直接検索")
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
                                    is_read = 0,
                                    is_bookmark = 0,
                                    reading_rate = 0.0f
                                )
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.w("KakuyomuAdapter", "エピソード解析エラー: $key", e)
                    }
                }
            }

            // エピソード番号でソート（公開日時順）
            episodes.sortBy { it.update_time }

            AppLogger.d("KakuyomuAdapter", "JSONからエピソード抽出成功 (apolloState直接検索): ${episodes.size}話")
            episodes
        } catch (e: Exception) {
            AppLogger.e("KakuyomuAdapter", "JSONからのエピソード抽出エラー", e)
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
                AppLogger.d("KakuyomuAdapter", "エピソード本文取得成功 (widget-episodeBody.js-episode-body): $episodeId")
            }

            // パターン2: widget-episodeBody のみ（古い構造）
            if (episodeBody.isEmpty()) {
                val bodyElement2 = doc.select("div.widget-episodeBody")
                if (bodyElement2.isNotEmpty()) {
                    episodeBody = bodyElement2.html()
                    AppLogger.d("KakuyomuAdapter", "エピソード本文取得成功 (widget-episodeBody): $episodeId")
                }
            }

            // パターン3: js-episode-body のみ（古い構造の別パターン）
            if (episodeBody.isEmpty()) {
                val bodyElement3 = doc.select("div.js-episode-body")
                if (bodyElement3.isNotEmpty()) {
                    episodeBody = bodyElement3.html()
                    AppLogger.d("KakuyomuAdapter", "エピソード本文取得成功 (js-episode-body): $episodeId")
                }
            }

            // パターン4: 新しいHTML構造のパターン（将来的な変更に備えて）
            if (episodeBody.isEmpty()) {
                val bodyElement4 = doc.select("div[class*='EpisodeBody']")
                if (bodyElement4.isNotEmpty()) {
                    episodeBody = bodyElement4.html()
                    AppLogger.d("KakuyomuAdapter", "エピソード本文取得成功 (EpisodeBody): $episodeId")
                }
            }

            // パターン5: p要素を含むdiv（最後のフォールバック）
            if (episodeBody.isEmpty()) {
                val bodyElement5 = doc.select("div#contentMain p")
                if (bodyElement5.isNotEmpty()) {
                    episodeBody = bodyElement5.joinToString("\n") { it.outerHtml() }
                    AppLogger.d("KakuyomuAdapter", "エピソード本文取得成功 (contentMain p): $episodeId")
                }
            }

            if (episodeBody.isEmpty()) {
                AppLogger.w("KakuyomuAdapter", "エピソード本文が空です: $episodeId")
            } else {
                // エラーチェック: HTMLページ読み込みエラーの検出（Pascalコード参考）
                if (checkForLoadingError(episodeBody)) {
                    AppLogger.w("KakuyomuAdapter", "HTMLページ読み込みエラーを検出: $episodeId")
                    return@withContext "★HTMLページ読み込みエラー\n本文を正しく取得できませんでした。\n後ほど再度お試しください。"
                }

                // 本文のクリーンアップ処理を適用
                episodeBody = cleanupEpisodeBody(episodeBody)
            }

            episodeBody
        } catch (e: Exception) {
            AppLogger.e("KakuyomuAdapter", "エピソード本文取得エラー: $episodeId", e)
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
                AppLogger.w("KakuyomuAdapter", "HTMLページ読み込みエラーを検出: $episodeId")
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
            AppLogger.e("KakuyomuAdapter", "エピソード詳細取得エラー: $episodeId", e)
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
    internal fun decodeHtmlEntities(text: String): String {
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
    internal fun decodeNumericEntities(text: String): String {
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
    internal fun decodeUnicodeEscapes(text: String): String {
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
    /**
     * カクヨム作品の完結フラグを検出する
     * HTMLやJSONデータから完結済み情報を取得
     */
    private fun detectKakuyomuCompletion(doc: org.jsoup.nodes.Document, workData: org.json.JSONObject?): Boolean {
        // JSONから完結情報を確認
        workData?.let {
            val isCompleted = it.optBoolean("isCompleted", false)
            if (isCompleted) return true
            val serializeState = it.optString("serialState", "")
            if (serializeState.contains("COMPLETED", ignoreCase = true)) return true
        }
        // HTML内の完結バッジ・ラベルを検索
        val pageText = doc.text()
        if (pageText.contains("完結済")) return true
        // 完結バッジのクラス名パターン
        if (doc.select("[class*='completed'], [class*='Completed'], [class*='complete']").isNotEmpty()) {
            val badge = doc.select("[class*='completed'], [class*='Completed']").text()
            if (badge.contains("完結", ignoreCase = false)) return true
        }
        return false
    }

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
                    AppLogger.d("KakuyomuAdapter", "Next.jsデータ取得成功（指定workId一致）: $targetKey")
                    return Pair(apolloState, workData)
                }

                // 見つからない場合はWork:で始まる最初のキーを探す（フォールバック）
                apolloState?.keys()?.forEach { key ->
                    if (key.startsWith("Work:")) {
                        val workData = apolloState.getJSONObject(key)
                        AppLogger.d("KakuyomuAdapter", "Next.jsデータ取得成功（フォールバック）: $key (期待値: $targetKey)")
                        return Pair(apolloState, workData)
                    }
                }

                AppLogger.d("KakuyomuAdapter", "Work:キーが見つかりませんでした")
            } else {
                AppLogger.d("KakuyomuAdapter", "__NEXT_DATA__スクリプトが見つかりませんでした")
            }
            Pair(null, null)
        } catch (e: Exception) {
            AppLogger.e("KakuyomuAdapter", "Next.jsデータ抽出エラー", e)
            Pair(null, null)
        }
    }

    /**
     * HTML全文をログ出力（長い場合は分割して出力）
     *
     * @param url リクエストURL
     * @param html HTML文字列
     */
    private fun logHtmlContent(url: String, html: String) {
        try {
            AppLogger.d("KakuyomuAdapter", "=== HTML全文のログ出力開始: $url ===")
            AppLogger.d("KakuyomuAdapter", "HTML長: ${html.length}文字")

            // Logcatの1行の制限は約4000文字なので、4000文字ずつ分割して出力
            val chunkSize = 4000
            val chunks = (html.length + chunkSize - 1) / chunkSize  // 切り上げ

            for (i in 0 until chunks) {
                val start = i * chunkSize
                val end = minOf(start + chunkSize, html.length)
                val chunk = html.substring(start, end)
                AppLogger.d("KakuyomuAdapter", "HTML[${i + 1}/$chunks]: $chunk")
            }

            AppLogger.d("KakuyomuAdapter", "=== HTML全文のログ出力終了: $url ===")
        } catch (e: Exception) {
            AppLogger.e("KakuyomuAdapter", "HTMLログ出力エラー", e)
        }
    }
}
