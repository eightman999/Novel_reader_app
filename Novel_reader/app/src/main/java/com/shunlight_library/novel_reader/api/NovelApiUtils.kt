// NovelApiUtils.kt
package com.shunlight_library.novel_reader.api

import android.util.Log
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream

/**
 * 小説関連のAPI処理を行うユーティリティクラス
 */
object NovelApiUtils {
    private const val TAG = "NovelApiUtils"

    /**
     * APIから小説情報を取得する
     * @param ncode 小説のNコード
     * @param isR18 R18小説かどうか
     * @return Pair(総エピソード数, 更新日時) 取得に失敗した場合は (-1, "")
     */
    // NovelApiUtils.kt の fetchNovelInfo 関数を修正
    suspend fun fetchNovelInfo(ncode: String, isR18: Boolean = false, apiUrl: String? = null): Pair<Int, String> {
        if (ncode.isEmpty()) return Pair(-1, "")

        return withContext(Dispatchers.IO) {
            try {
                // API URLの構築
                val actualApiUrl = apiUrl ?: if (isR18) {
                    "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5&json"
                } else {
                    "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5&json"
                }

                val connection = URL(actualApiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")  // User-Agentを追加

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
                        val newGeneralAllNo = novelData["general_all_no"] as Int

                        // 更新日時の取得
                        val updatedAtObj = novelData["updated_at"]
                        val newUpdatedAt = when (updatedAtObj) {
                            is String -> updatedAtObj
                            is Date -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(updatedAtObj)
                            else -> DatabaseSyncUtils.getCurrentDateTimeString()
                        }

                        Pair(newGeneralAllNo, newUpdatedAt)
                    } else {
                        Pair(-1, "")
                    }
                } else {
                    Pair(-1, "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "API取得エラー: ${e.message}", e)
                Pair(-1, "")
            }
        }
    }

    /**
     * 小説情報を取得してNovelDescEntityを作成する
     * @param ncode 小説のNコード
     * @param isR18 R18小説かどうか
     * @return 取得した小説情報、または取得できなかった場合はnull
     */
    suspend fun fetchNovelDetails(ncode: String, isR18: Boolean = false): NovelDescEntity? {
        return withContext(Dispatchers.IO) {
            try {
                // API URLの構築
                val apiUrl = if (isR18) {
                    "https://api.syosetu.com/novel18api/api/?of=t-n-u-w-s-k-g-ga-e-l-ua&ncode=$ncode&gzip=5&json"
                } else {
                    "https://api.syosetu.com/novelapi/api/?of=t-n-u-w-s-k-g-ga-e-l-ua&ncode=$ncode&gzip=5&json"
                }

                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

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
                        val keyword = novelData["keyword"] as? String ?: ""

                        // キーワードから最初のタグをメインタグ、残りをサブタグとして扱う
                        val tags = keyword.split(" ")
                        val mainTag = if (tags.isNotEmpty()) tags[0] else ""
                        val subTag = if (tags.size > 1) tags.subList(1, tags.size).joinToString(" ") else ""

                        // 現在の日時を取得
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
                            last_update_date = currentDate,
                            total_ep = 0, // 初期値は0、後で更新処理で正確な値が設定される
                            general_all_no = generalAllNo,
                            updated_at = currentDate
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
            }
        }
    }

    /**
     * エピソードを取得する
     * @param ncode 小説のNコード
     * @param episodeNo エピソード番号
     * @param isR18 R18小説かどうか
     * @return 取得したエピソード、または取得できなかった場合はnull
     */
    // NovelApiUtils.kt の fetchEpisode 関数を修正
    // NovelApiUtils.kt の fetchEpisode 関数を修正
    suspend fun fetchEpisode(ncode: String, episodeNo: Int, isR18: Boolean = false): EpisodeEntity? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = if (isR18) {
                    "https://novel18.syosetu.com"
                } else {
                    "https://ncode.syosetu.com"
                }
                val url = "$baseUrl/$ncode/$episodeNo/"

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

                var doc = connection.get()

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

                // タイトルと本文を取得
                val title = doc.select("h1.p-novel__title.p-novel__title--rensai").text()
                val bodyElements = doc.select("div.p-novel__body > div")
                val body = StringBuilder()

                if (bodyElements.isNotEmpty()) {
                    bodyElements.forEachIndexed { index, element ->
                        body.append(element.outerHtml())
                        // 最後の要素でなければ<hr>を追加
                        if (index < bodyElements.size - 1) {
                            body.append("\n<hr>\n")
                        }
                    }
                }

                if (title.isNotEmpty() && body.isNotEmpty()) {
                    val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    EpisodeEntity(
                        ncode = ncode,
                        episode_no = episodeNo.toString(),
                        body = body.toString(),
                        e_title = title,
                        update_time = currentDate,
                        is_read = false,
                        is_bookmark = false
                    )
                } else {
                    Log.e(TAG, "タイトルまたは本文が空です: title=${title.isNotEmpty()}, body=${body.isNotEmpty()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "エピソード取得エラー: $episodeNo", e)
                null
            }
        }
    }

    // リダイレクトと複数回試行用のfetchEpisodeWithRetry関数を追加
    suspend fun fetchEpisodeWithRetry(ncode: String, episodeNo: Int, isR18: Boolean = false, maxRetries: Int = 3): EpisodeEntity? {
        for (attempt in 1..maxRetries) {
            try {
                val episode = fetchEpisode(ncode, episodeNo, isR18)
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
     * URLからncodeとR18フラグを抽出する
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
     * 重複確認 - 既に小説が登録されているかどうかをチェックする
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
}