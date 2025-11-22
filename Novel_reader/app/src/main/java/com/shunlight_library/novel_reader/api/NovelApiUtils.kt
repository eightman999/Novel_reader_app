/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Utility methods for accessing the novel API.
 */
// NovelApiUtils.kt
package com.shunlight_library.novel_reader.api

import android.util.Log
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils
import com.shunlight_library.novel_reader.utils.ImageCacheUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.HttpStatusException
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.text.Charsets

/**
 * 小説関連のAPI処理を行うユーティリティクラス
 */
object NovelApiUtils {
    private const val TAG = "NovelApiUtils"

    // レート制限設定
    private const val API_RATE_LIMIT_MS = 150L  // 0.15秒間隔
    private var lastApiAccessTime = 0L

    private val IMAGE_EXTENSION_REGEX =
        ".*\\.(jpe?g|png|gif|bmp|webp|avif)(\\?.*)?$".toRegex(RegexOption.IGNORE_CASE)
    private val API_USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    )

    /**
     * APIレート制限を適用する
     * 前回のAPIアクセスから指定時間経過していない場合は待機する
     */
    private suspend fun applyApiRateLimit() {
        val elapsed = System.currentTimeMillis() - lastApiAccessTime
        if (elapsed < API_RATE_LIMIT_MS) {
            delay(API_RATE_LIMIT_MS - elapsed)
        }
        lastApiAccessTime = System.currentTimeMillis()
    }

    data class NovelApiInfo(
        val generalAllNo: Int,
        val updatedAt: String,
        val userid: String?,
        val noveltype: Int?,
        val length: Int?
    )

    /**
     * なろう小説APIから小説情報を取得する
     *
     * APIレスポンス形式: YAML（gzip圧縮）
     * 形式例:
     * ---
     * -
     *   allcount: 1
     * -
     *   title: タイトル
     *   userid: 123456
     *   general_all_no: 総エピソード数
     *   updated_at: "2025-11-04 18:35:37"
     *   noveltype: 1 (1=連載, 2=短編)
     *   length: 文字数
     *
     * @param ncode 小説のNコード
     * @param isR18 R18小説かどうか（true=novel18.syosetu.com, false=ncode.syosetu.com）
     * @param apiUrl カスタムAPI URL（nullの場合はデフォルトのAPIを使用）
     * @param maxRetries 最大再試行回数
     * @param retryDelayMillis 再試行時の待機時間（ミリ秒）
     * @return NovelApiInfo（取得失敗時はnull）
     */
    suspend fun fetchNovelInfo(
        ncode: String,
        isR18: Boolean = false,
        apiUrl: String? = null,
        maxRetries: Int = 3,
        retryDelayMillis: Long = 1000L
    ): NovelApiInfo? {
        if (ncode.isEmpty()) return null

        val apiCandidates = if (apiUrl != null) {
            listOf(apiUrl)
        } else {
            // なろう小説APIはデフォルトでYAML形式を返す（gzip圧縮付き）
            listOf(
                if (isR18) {
                    "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua-u-nt-l&ncode=$ncode&gzip=5"
                } else {
                    "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua-u-nt-l&ncode=$ncode&gzip=5"
                }
            )
        }

        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            for (candidateUrl in apiCandidates) {
                val userAgent = API_USER_AGENTS.random()
                try {
                    val result = requestNovelInfo(candidateUrl, userAgent)
                    if (result != null) {
                        return result
                    }

                    Log.w(
                        TAG,
                        "API応答から必要なデータを取得できませんでした (attempt=$attempt/$maxRetries, url=$candidateUrl)"
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    if (!isRetryableNetworkException(e)) {
                        Log.e(TAG, "API取得エラー(非再試行): ${e.message}", e)
                        return null
                    }

                    lastException = e
                    Log.w(
                        TAG,
                        "API取得エラー (attempt=$attempt/$maxRetries, url=$candidateUrl): ${e.message}",
                        e
                    )
                }
            }

            if (attempt < maxRetries) {
                val backoff = retryDelayMillis * attempt
                Log.d(TAG, "fetchNovelInfo 再試行まで ${backoff}ms 待機します (attempt=$attempt/$maxRetries)")
                delay(backoff)
            }
        }

        lastException?.let { Log.e(TAG, "API取得エラー: ${it.message}", it) }
        return null
    }

    private suspend fun requestNovelInfo(url: String, userAgent: String): NovelApiInfo? {
        // レート制限を適用
        applyApiRateLimit()

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept-Encoding", "gzip")
                    instanceFollowRedirects = true
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP ${connection.responseCode} 応答: $url")
                    return@withContext null
                }

                // URLにgzipパラメータが含まれている、またはContent-Encodingヘッダーにgzipが含まれている場合はGZIP解凍
                val useGzip = url.contains("gzip=", ignoreCase = true) ||
                              connection.contentEncoding?.contains("gzip", ignoreCase = true) == true

                Log.d(TAG, "GZIP使用: $useGzip (URL: $url, Content-Encoding: ${connection.contentEncoding})")

                val inputStream = try {
                    if (useGzip) {
                        try {
                            GZIPInputStream(connection.inputStream)
                        } catch (gzipError: Exception) {
                            // GZIP解凍に失敗した場合、非圧縮として再試行
                            Log.w(TAG, "GZIP解凍エラー、非圧縮として再試行: ${gzipError.message}")
                            connection.inputStream
                        }
                    } else {
                        connection.inputStream
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "InputStream取得エラー: ${e.message}", e)
                    throw e
                }

                inputStream.buffered().use { bufferedStream ->
                    // レスポンス内容を文字列として読み込む
                    val responseContent = InputStreamReader(bufferedStream, Charsets.UTF_8).use { reader ->
                        reader.readText()
                    }

                    // レスポンス内容をログ出力（デバッグ用）
                    Log.d(TAG, "API Response (first 500 chars): ${responseContent.take(500)}")

                    // レスポンスが空の場合
                    if (responseContent.isBlank()) {
                        Log.w(TAG, "APIレスポンスが空です: $url")
                        return@withContext null
                    }

                    // YAMLパース
                    // なろう小説APIはYAML形式でデータを返す
                    // 形式:
                    // ---
                    // -
                    //   allcount: 1
                    // -
                    //   title: タイトル
                    //   userid: 123456
                    //   story: |        # | = リテラルスカラー（改行保持）
                    //     複数行の
                    //     あらすじ...
                    //   story: >        # > = 折り畳みスカラー（改行を空白に）
                    //     複数行のテキストが
                    //     1行にまとめられる
                    //   general_all_no: 100
                    //   updated_at: "2025-11-04 18:35:37"
                    //   noveltype: 1
                    //   length: 123456
                    //
                    // SnakeYAMLは「|」と「>」の両方を自動処理
                    try {
                        val yaml = Yaml()
                        val yamlData = yaml.load<List<Map<String, Any>>>(responseContent)

                        Log.d(TAG, "YAML解析成功: ${yamlData?.size ?: 0} 要素")

                        // データが2要素未満の場合（メタデータと小説情報）
                        if (yamlData == null || yamlData.size < 2) {
                            Log.w(TAG, "APIレスポンスに小説情報が含まれていません")
                            return@withContext null
                        }

                        // 1番目はメタデータ（allcount）、2番目が小説情報
                        val novelData = yamlData[1]

                        // 必要なフィールドを取得
                        val generalAllNo = (novelData["general_all_no"] as? Number)?.toInt()
                        val updatedAt = novelData["updated_at"]?.toString()
                        val userid = novelData["userid"]?.toString()
                        val noveltype = (novelData["noveltype"] as? Number)?.toInt()
                        val length = (novelData["length"] as? Number)?.toInt()

                        if (generalAllNo == null) {
                            Log.w(TAG, "general_all_no フィールドが見つかりません")
                            return@withContext null
                        }

                        val normalizedUpdatedAt = updatedAt?.takeIf { it.isNotBlank() }
                            ?: DatabaseSyncUtils.getCurrentDateTimeString()

                        NovelApiInfo(
                            generalAllNo = generalAllNo,
                            updatedAt = normalizedUpdatedAt,
                            userid = userid,
                            noveltype = noveltype,
                            length = length
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "YAMLパースエラー: ${e.message}\nレスポンス内容: ${responseContent.take(1000)}", e)
                        throw e
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun isRetryableNetworkException(exception: Exception): Boolean {
        return exception is SocketTimeoutException ||
            exception is ConnectException ||
            exception is UnknownHostException ||
            exception is SocketException ||
            exception is SSLException
    }

    /**
     * 小説情報を取得してNovelDescEntityを作成する
     * @param ncode 小説のNコード
     * @param isR18 R18小説かどうか
     * @return 取得した小説情報、または取得できなかった場合はnull
     */
    suspend fun fetchNovelDetails(ncode: String, isR18: Boolean = false): NovelDescEntity? {
        // レート制限を適用
        applyApiRateLimit()

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                // API URLの構築（YAML形式、gzip圧縮）
                val apiUrl = if (isR18) {
                    "https://api.syosetu.com/novel18api/api/?of=t-n-u-w-s-k-g-ga-e-l-ua-nt&ncode=$ncode&gzip=5"
                } else {
                    "https://api.syosetu.com/novelapi/api/?of=t-n-u-w-s-k-g-ga-e-l-ua-nt&ncode=$ncode&gzip=5"
                }

                connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = GZIPInputStream(connection.inputStream)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        content.append(line).append("\n")
                    }

                    val yaml = Yaml()
                    val yamlData = yaml.load<List<Map<String, Any>>>(content.toString())

                    if (yamlData.size >= 2) {
                        val novelData = yamlData[1]

                        // 必要なデータを取得
                        val title = novelData["title"] as String
                        val author = novelData["writer"] as String
                        val synopsis = novelData["story"] as? String ?: ""
                        val generalAllNo = novelData["general_all_no"] as Int
                        val userid = novelData["userid"]?.toString()
                        val noveltype = (novelData["noveltype"] as? Int)
                        val length = (novelData["length"] as? Int)
                        val keyword = novelData["keyword"] as? String ?: ""

                        // APIから取得した小説の最終更新日時（サイト上の更新日時）
                        val updatedAt = novelData["updated_at"] as? String ?: ""

                        // キーワードから最初のタグをメインタグ、残りをサブタグとして扱う
                        val tags = keyword.split(" ")
                        val mainTag = if (tags.isNotEmpty()) tags[0] else ""
                        val subTag = if (tags.size > 1) tags.subList(1, tags.size).joinToString(" ") else ""

                        // 現在の日時を取得（データベース登録日時として使用）
                        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                        // レーティング（R18なら1、それ以外なら2）
                        val rating = if (isR18) 1 else 2

                        return@withContext NovelDescEntity(
                            ncode = ncode,
                            title = title,
                            author = author,
                            Synopsis = synopsis,
                            main_tag = mainTag,
                            sub_tag = subTag,
                            rating = rating,
                            last_update_date = updatedAt,  // サイト上の最終更新日
                            total_ep = 0, // 初期値は0、後で更新処理で正確な値が設定される
                            general_all_no = generalAllNo,
                            userid = userid,
                            noveltype = noveltype,
                            length = length,
                            updated_at = updatedAt,  // サイト上の最終更新日時
                            registered_at = currentDate  // データベース登録日時
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "小説詳細取得エラー: ${e.message}", e)
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * エピソードを取得する
     * @param ncode 小説のNコード（カクヨムの場合はPseudo-Ncode）
     * @param episodeNo エピソード番号（カクヨムの場合はエピソードID文字列）
     * @param isR18 R18小説かどうか
     * @return 取得したエピソード、または取得できなかった場合はnull
     */
    // NovelApiUtils.kt の fetchEpisode 関数を修正
    // NovelApiUtils.kt の fetchEpisode 関数を修正
    suspend fun fetchEpisode(
        ncode: String,
        episodeNo: String,
        isR18: Boolean = false,
        noveltype: Int? = null
    ): EpisodeEntity? {
        // レート制限を適用（なろう小説の場合のみ有効、カクヨムは独自のレート制限を持つ）
        if (!com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.isKakuyomuNcode(ncode)) {
            applyApiRateLimit()
        }

        return withContext(Dispatchers.IO) {
            try {
                // カクヨム小説の場合は KakuyomuAdapter を使用
                if (com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.isKakuyomuNcode(ncode)) {
                    val adapter = com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter()
                    val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
                    
                    // episodeNoは連番（1, 2, 3...）なので、マッピングテーブルから実際のIDを取得
                    val repository = com.shunlight_library.novel_reader.NovelReaderApplication.getRepository()
                    val episodeNoInt = episodeNo.toIntOrNull()
                    val kakuyomuEpisodeId = if (episodeNoInt != null) {
                        repository.getKakuyomuEpisodeId(ncode, episodeNoInt) ?: episodeNo
                    } else {
                        episodeNo
                    }

                    // エピソード本文を取得
                    val body = adapter.fetchEpisodeContent(workId, kakuyomuEpisodeId)

                    // タイトル取得のためエピソードページをパース（簡易版）
                    val url = adapter.generateEpisodeUrl(workId, kakuyomuEpisodeId)
                    val doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                        .timeout(30000)
                        .get()

                    // タイトル取得: 複数のパターンに対応（文献準拠）
                    val title = doc.select("header#contentMain-header").text()
                        .ifEmpty { doc.select("h1").first()?.text() ?: "" }
                        .ifEmpty { "第${episodeNo}話" }

                    val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    return@withContext if (body.isNotEmpty()) {
                        EpisodeEntity(
                            ncode = ncode,
                            episode_no = episodeNo,
                            body = body,
                            e_title = title,
                            update_time = currentDate,
                            is_read = false,
                            is_bookmark = false
                        )
                    } else {
                        Log.e(TAG, "カクヨムエピソード本文が空です: workId=$workId, episodeId=$kakuyomuEpisodeId")
                        null
                    }
                }

                // 小説家になろうの場合は既存のロジックを使用
                val baseUrl = if (isR18) {
                    "https://novel18.syosetu.com"
                } else {
                    "https://ncode.syosetu.com"
                }
                // episodeNoをIntに変換（小説家になろうは数値のみ）
                val episodeNoInt = episodeNo.toIntOrNull() ?: 1
                val url = if (noveltype == 2) {
                    "$baseUrl/$ncode/"
                } else {
                    "$baseUrl/$ncode/$episodeNoInt/"
                }

                // ユーザーエージェントをランダムに設定（検出回避用）
                val userAgents = listOf(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Safari/605.1.15",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Safari/537.36"
                )
                val randomUserAgent = userAgents.random()

                // Jsoupのコネクションを設定
                val connection = Jsoup.connect(url)
                    .userAgent(randomUserAgent)
                    .timeout(30000)
                    .followRedirects(true)

                if (isR18 ||
                    url.contains("novel18.syosetu.com") ||
                    url.contains("noc.syosetu.com") ||
                    url.contains("mid.syosetu.com") ||
                    url.contains("mnlt.syosetu.com")) {
                    // R18サイト用のCookieを設定
                    connection.cookie("over18", "yes")
                }

                var doc = try {
                    connection.get()
                } catch (e: org.jsoup.HttpStatusException) {
                    // 404エラーかつepisodeNo == 1の場合、短編小説の可能性があるため話数なしURLを試す
                    if (e.statusCode == 404 && episodeNo == "1" && noveltype != 2) {
                        Log.d(TAG, "404エラーを検出。短編小説として話数なしURLを試します: $baseUrl/$ncode/")
                        val fallbackConnection = Jsoup.connect("$baseUrl/$ncode/")
                            .userAgent(randomUserAgent)
                            .timeout(30000)
                            .followRedirects(true)

                        if (isR18 ||
                            url.contains("novel18.syosetu.com") ||
                            url.contains("noc.syosetu.com") ||
                            url.contains("mid.syosetu.com") ||
                            url.contains("mnlt.syosetu.com")) {
                            fallbackConnection.cookie("over18", "yes")
                        }

                        fallbackConnection.get()
                    } else {
                        throw e
                    }
                }

                // レスポンスが年齢確認ページかチェック
                val htmlContent = doc.html()
                if (htmlContent.contains("年齢確認") || htmlContent.contains("Age Verification") ||
                    doc.location().contains("ageauth")) {

                    Log.d(TAG, "年齢確認ページを検出しました。Enterリンクを探します")

                    // "Enter"リンクを探す
                    val enterLink = doc.select("a:contains(Enter)").firstOrNull() // firstOrNull() を使用
                    if (enterLink != null) { // nullチェック
                        val nextUrl = enterLink.absUrl("href")
                        Log.d(TAG, "Enterリンクが見つかりました。次のURLに進みます: $nextUrl")

                        // "Enter"リンクにアクセス
                        doc = Jsoup.connect(nextUrl)
                            .userAgent(randomUserAgent)
                            .timeout(30000)
                            .cookie("over18", "yes") // R18サイトの場合、再度Cookieが必要な場合がある
                            .get()
                    } else {
                        Log.d(TAG, "Enterリンクが見つかりませんでした")
                    }
                }

                // タイトルを取得（連載小説と短編小説の両方に対応）
                val title = doc.select("h1.p-novel__title.p-novel__title--rensai").text()
                    .ifEmpty { doc.select("h1.p-novel__title").text() }

                val miteminLinkMap = mutableMapOf<String, String>()

                // 画像をローカルキャッシュに置換
                val imgElements = doc.select("div.p-novel__body img")
                for (img in imgElements) {
                    var srcUrl = img.absUrl("src")
                    if (srcUrl.isBlank()) {
                        srcUrl = img.absUrl("data-src")
                    }
                    if (srcUrl.isBlank()) {
                        srcUrl = img.absUrl("data-original")
                    }

                    val anchorElement = img.parents().firstOrNull { it.tagName() == "a" }
                    val linkedUrl = anchorElement?.absUrl("href")?.takeIf { it.isNotBlank() }

                    val resolvedUrl = resolveImageSource(srcUrl, linkedUrl, randomUserAgent)
                    if (!resolvedUrl.isNullOrEmpty()) {
                        ImageCacheUtils.downloadAndCacheImage(resolvedUrl)?.let { localUri ->
                            img.attr("src", localUri)

                            anchorElement?.attr("href", localUri)

                            if (!linkedUrl.isNullOrEmpty()) {
                                miteminLinkMap[linkedUrl] = localUri
                            }
                            if (srcUrl.isNotBlank()) {
                                miteminLinkMap[srcUrl] = localUri
                            }
                            miteminLinkMap[resolvedUrl] = localUri
                        }
                    }
                }

                // 本文内のミテミンリンクをローカルキャッシュへ置換
                for (anchor in doc.select("a[href*='mitemin.net']")) {
                    val rawHref = anchor.attr("href")
                    val absoluteHref = anchor.absUrl("href").takeIf { it.isNotBlank() } ?: rawHref

                    val replacement = miteminLinkMap[absoluteHref]
                        ?: miteminLinkMap[rawHref]
                        ?: run {
                            if (absoluteHref.contains("mitemin.net")) {
                                fetchMiteminOriginalImage(absoluteHref, randomUserAgent)?.let { directUrl ->
                                    val cached = miteminLinkMap[directUrl]
                                    if (cached != null) {
                                        cached
                                    } else {
                                        ImageCacheUtils.downloadAndCacheImage(directUrl)?.also { localUri ->
                                            miteminLinkMap[absoluteHref] = localUri
                                            if (rawHref.isNotBlank()) {
                                                miteminLinkMap[rawHref] = localUri
                                            }
                                            miteminLinkMap[directUrl] = localUri
                                        }
                                    }
                                }
                            } else {
                                null
                            }
                        }

                    if (!replacement.isNullOrEmpty()) {
                        anchor.attr("href", replacement)
                    }
                }

                val bodyElements = doc.select("div.p-novel__body > div")
                var bodyHtml = StringBuilder()
                
                if (bodyElements.isNotEmpty()) {
                    bodyElements.forEachIndexed { index, element ->
                        bodyHtml.append(element.outerHtml())
                        // 最後の要素でなければ<hr>を追加
                        if (index < bodyElements.size - 1) {
                            bodyHtml.append("\n<hr>\n")
                        }
                    }
                }

                var bodyString = bodyHtml.toString()
                if (bodyString.isNotEmpty()) {
                    miteminLinkMap.forEach { (remoteUrl, localUri) ->
                        if (remoteUrl.isNotBlank() && localUri.isNotBlank()) {
                            bodyString = bodyString.replace(remoteUrl, localUri)
                        }
                    }
                }

                if (title.isNotEmpty() && bodyString.isNotEmpty()) {
                    val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    EpisodeEntity(
                        ncode = ncode,
                        episode_no = episodeNo.toString(),
                        body = bodyString,
                        e_title = title,
                        update_time = currentDate,
                        is_read = false,
                        is_bookmark = false
                    )
                } else {
                    Log.e(TAG, "タイトルまたは本文が空です: title=${title.isNotEmpty()}, body=${bodyString.isNotEmpty()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "エピソード取得エラー: $episodeNo", e)
                null
            }
        }
    }

    private fun resolveImageSource(defaultSrc: String, linkedUrl: String?, userAgent: String): String? {
        if (defaultSrc.isBlank()) return null

        if (hasImageExtension(defaultSrc)) {
            return defaultSrc
        }

        if (!linkedUrl.isNullOrEmpty() && linkedUrl.contains("mitemin.net")) {
            fetchMiteminOriginalImage(linkedUrl, userAgent)?.let { return it }
        }

        return defaultSrc
    }

    private fun hasImageExtension(url: String): Boolean {
        return IMAGE_EXTENSION_REGEX.matches(url)
    }

    private fun fetchMiteminOriginalImage(pageUrl: String, userAgent: String): String? {
        return try {
            val pageDoc = Jsoup.connect(pageUrl)
                .userAgent(userAgent)
                .timeout(30000)
                .get()

            pageDoc.selectFirst("td.imageview a[href]")
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "ミテミン画像の取得に失敗しました: ${e.message}", e)
            null
        }
    }

    /**
     * リダイレクトに対応しつつエピソード取得を複数回試行する
     * ネットワークエラー時には指定回数まで再試行を行う
     */
    suspend fun fetchEpisodeWithRetry(
        ncode: String,
        episodeNo: String,
        isR18: Boolean = false,
        noveltype: Int? = null,
        maxRetries: Int = 3
    ): EpisodeEntity? {
        for (attempt in 1..maxRetries) {
            try {
                val episode = fetchEpisode(ncode, episodeNo, isR18, noveltype)
                if (episode != null) {
                    return episode
                }
                Log.d(TAG, "試行 $attempt/$maxRetries 失敗しました。再試行します...")
                delay(1000) // 1秒待機してから再試行
            } catch (e: Exception) {
                Log.e(TAG, "試行 $attempt/$maxRetries 中にエラー発生: ${e.message}")
                if (attempt == maxRetries) throw e
                delay(1000) // 1秒待機してから再試行
            }
        }
        return null
    }
    /**
     * URLからncodeとR18フラグを正規表現で解析して抽出する
     * @param url 小説のURL
     * @return Pair(ncode, isR18)、取得できなかった場合は (null, false)
     */
    fun extractNcodeFromUrl(url: String): Pair<String?, Boolean> {
        val pattern = "https://(ncode|novel18)\\.syosetu\\.com/([^/]+)/?.*".toRegex()
        val matchResult = pattern.find(url)

        return if (matchResult != null) {
            val domain = matchResult.groupValues[1]
            val ncode = matchResult.groupValues[2]
            val isR18 = domain == "novel18"
            Pair(ncode, isR18)
        } else {
            Pair(null, false)
        }
    }

    /**
     * 重複確認 - 既に小説が登録されているかをリポジトリでチェックする
     * @param repository リポジトリインスタンス
     * @param ncode 確認するNコード
     * @return 既に登録されている場合はtrue、そうでない場合はfalse
     */
    suspend fun isNovelAlreadyRegistered(
        repository: com.shunlight_library.novel_reader.data.repository.NovelRepository,
        ncode: String
    ): Boolean {
        return repository.getNovelByNcode(ncode) != null
    }

    /**
     * エピソード情報（改稿日時を含む）
     */
    data class EpisodeRevisionInfo(
        val episodeNo: Int,
        val title: String,
        val updateTime: String  // yyyy-MM-dd HH:mm:ss形式
    )

    /**
     * なろう小説の目次ページから全エピソードの改稿情報を取得する
     * 
     * @param ncode 小説のNコード
     * @param isR18 R18小説かどうか
     * @param noveltype 小説種別（1=連載、2=短編）
     * @return エピソード改稿情報のリスト（取得失敗時は空リスト）
     */
    suspend fun fetchEpisodeRevisionsFromToc(
        ncode: String,
        isR18: Boolean = false,
        noveltype: Int? = null
    ): List<EpisodeRevisionInfo> = withContext(Dispatchers.IO) {
        // 短編小説は除外
        if (noveltype == 2) {
            Log.d(TAG, "短編小説のため改稿チェックをスキップ: $ncode")
            return@withContext emptyList()
        }

        val baseUrl = if (isR18) {
            "https://novel18.syosetu.com"
        } else {
            "https://ncode.syosetu.com"
        }

        val allEpisodes = mutableListOf<EpisodeRevisionInfo>()
        var currentPage = 1
        var hasMorePages = true

        val userAgent = API_USER_AGENTS.random()

        try {
            while (hasMorePages) {
                val tocUrl = if (currentPage == 1) {
                    "$baseUrl/$ncode/"
                } else {
                    "$baseUrl/$ncode/?p=$currentPage"
                }

                // レート制限を適用（各ページ取得前）
                applyApiRateLimit()

                Log.d(TAG, "目次ページを取得中: $tocUrl")

                val doc = try {
                    val connection = Jsoup.connect(tocUrl)
                        .userAgent(userAgent)
                        .timeout(30000)
                        .followRedirects(true)

                    if (isR18) {
                        connection.cookie("over18", "yes")
                    }

                    connection.get()
                } catch (e: HttpStatusException) {
                    if (e.statusCode == 404) {
                        Log.d(TAG, "ページが見つかりません（最終ページに到達）: $tocUrl")
                        hasMorePages = false
                        break
                    } else {
                        throw e
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "目次ページ取得エラー: $tocUrl", e)
                    hasMorePages = false
                    break
                }

                // エピソードリストを解析
                // なろう小説の目次構造（2025年版）:
                // <div class="p-eplist">
                //   <div class="p-eplist__sublist">
                //     <a href="/ncode/1/" class="p-eplist__subtitle">第1話 タイトル</a>
                //     <div class="p-eplist__update">
                //       2024/01/01 12:00
                //       <span title="2024/01/02 15:00 改稿">（<u>改</u>）</span>
                //     </div>
                //   </div>
                // </div>
                val episodeElements = doc.select("div.p-eplist div.p-eplist__sublist")

                if (episodeElements.isEmpty()) {
                    Log.d(TAG, "エピソードが見つかりません。最終ページに到達: page=$currentPage")
                    hasMorePages = false
                    break
                }

                for (element in episodeElements) {
                    try {
                        // エピソード番号を取得（URLから抽出）
                        val episodeLink = element.select("a.p-eplist__subtitle").attr("href")
                        val episodeNoMatch = Regex("/(\\d+)/?$").find(episodeLink)
                        val episodeNo = episodeNoMatch?.groupValues?.get(1)?.toIntOrNull()

                        if (episodeNo == null) {
                            Log.w(TAG, "エピソード番号が取得できません: $episodeLink")
                            continue
                        }

                        // エピソードタイトルを取得
                        val title = element.select("a.p-eplist__subtitle").text()

                        // 更新日時を取得
                        val updateElement = element.select("div.p-eplist__update")
                        var updateTimeStr = updateElement.text()
                            .replace("（改）", "")
                            .replace("（改稿）", "")
                            .replace(Regex("\\(\\s*<u>改</u>\\s*\\)"), "")  // HTMLタグ付き改稿マークを除去
                            .trim()

                        // 日時フォーマットを変換: "2024/01/01 12:00" -> "2024-01-01 12:00:00"
                        val updateTime = try {
                            val inputFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val date = inputFormat.parse(updateTimeStr)
                            if (date != null) {
                                outputFormat.format(date)
                            } else {
                                DatabaseSyncUtils.getCurrentDateTimeString()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "日時パースエラー: $updateTimeStr", e)
                            DatabaseSyncUtils.getCurrentDateTimeString()
                        }

                        allEpisodes.add(
                            EpisodeRevisionInfo(
                                episodeNo = episodeNo,
                                title = title,
                                updateTime = updateTime
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "エピソード情報解析エラー", e)
                        continue
                    }
                }

                Log.d(TAG, "目次ページ page=$currentPage: ${episodeElements.size}エピソード取得")

                // 100エピソード未満の場合は最終ページ
                if (episodeElements.size < 100) {
                    hasMorePages = false
                } else {
                    currentPage++
                    // サーバー負荷軽減のため待機
                    delay(500)
                }
            }

            Log.d(TAG, "目次から取得完了: ${allEpisodes.size}エピソード")
            allEpisodes
        } catch (e: Exception) {
            Log.e(TAG, "目次取得エラー: $ncode", e)
            emptyList()
        }
    }
}