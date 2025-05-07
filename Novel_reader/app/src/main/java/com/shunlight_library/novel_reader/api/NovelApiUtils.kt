// NovelApiUtils.kt
package com.shunlight_library.novel_reader.api

import android.util.Log
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils
import kotlinx.coroutines.Dispatchers
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
    suspend fun fetchEpisode(ncode: String, episodeNo: Int, isR18: Boolean = false): EpisodeEntity? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = if (isR18) {
                    "https://novel18.syosetu.com"
                } else {
                    "https://ncode.syosetu.com"
                }

                val url = "$baseUrl/$ncode/$episodeNo/"

                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(30000)
                    .get()

                val title = doc.select("h1.p-novel__title.p-novel__title--rensai").text()
                val bodyElements = doc.select("div.p-novel__body div.js-novel-text p")

                // 小説ダウンロード時の不要なタグ挿入問題修正
                // すべての段落間にではなく、divタグの間だけに区切り線を追加
                val body = buildString {
                    bodyElements.forEachIndexed { index, element ->
                        append("<p>${element.html()}</p>")
                        // 要素間のみに区切り線を追加（先頭または最後には追加しない）
                        if (index < bodyElements.size - 1) {
                            // </div>タグと<div>タグの間かを確認
                            val hasClosingDiv = element.html().trim().endsWith("</div>")
                            val hasOpeningDivNext = index + 1 < bodyElements.size &&
                                    bodyElements[index + 1].html().trim().startsWith("<div")

                            if (hasClosingDiv && hasOpeningDivNext) {
                                append("\n")
//                                append("\n<p></p><p>-----</p><p></p>\n")
                            } else {
                                append("\n")
                            }
                        }
                    }
                }

                if (title.isNotEmpty() && body.isNotEmpty()) {
                    val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    return@withContext EpisodeEntity(
                        ncode = ncode,
                        episode_no = episodeNo.toString(),
                        body = body,
                        e_title = title,
                        update_time = currentDate,
                        is_read = false,
                        is_bookmark = false
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "エピソード取得エラー: $episodeNo", e)
                null
            }
        }
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