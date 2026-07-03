/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * WebNovelReader (別アプリ) のバックアップZIPからデータを復元するマネージャ。
 *
 * 対応形式:
 *   webnovelreader.zip
 *     ├─ info                       … エピソード総数（テキスト）
 *     ├─ webnovel.db                … メタデータ用SQLite (fetch_target / episode テーブル)
 *     └─ ep_data/{fetch_target_id}/{episode._id}
 *                                    … 各エピソードHTML（入れ子ZIP。展開すると生HTML1ファイル）
 *
 * fetch_target = 作品、episode = 話メタデータ。本文はDBには無く ep_data の入れ子ZIP内の
 * 生HTML（なろう/カクヨムのページそのもの）に格納されているため、Jsoupで本文を抽出して
 * 内部Roomデータベースへ取り込む。
 */
package com.shunlight_library.novel_reader.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.SettingsStore
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import com.shunlight_library.novel_reader.data.sync.ImprovedDatabaseSyncManager.SyncProgress
import com.shunlight_library.novel_reader.data.sync.ImprovedDatabaseSyncManager.SyncProgressCallback
import com.shunlight_library.novel_reader.data.sync.ImprovedDatabaseSyncManager.SyncResult
import com.shunlight_library.novel_reader.data.sync.ImprovedDatabaseSyncManager.SyncStep
import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class WebNovelReaderImportManager(private val context: Context) {

    companion object {
        private const val TAG = "WebNovelReaderImport"
        private const val EMBEDDED_DB_NAME = "webnovel.db"

        /**
         * 先頭バイトがZIPマジック (PK\x03\x04) かどうかを判定する。
         * DatabaseSyncActivity がファイル形式を自動判別するために使用。
         */
        fun looksLikeZip(header: ByteArray): Boolean =
            header.size >= 4 &&
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
    }

    private val repository: NovelRepository = NovelReaderApplication.getRepository()

    // 進捗スロットリング
    private var lastUpdateTime = 0L
    private val minUpdateIntervalMs = 400L

    /** サイト判別結果 */
    private data class NovelSource(
        val ncode: String,
        val siteType: Int,   // 1=なろう, 2=カクヨム
        val rating: Int,     // 1=R18, 2=一般
        val subSite: Int,    // 0=不明, 1=なろう
        val workId: String?  // カクヨムの数値作品ID（なろうはnull）
    )

    /** fetch_target 1件の情報 */
    private data class TargetInfo(
        val id: Int,
        val url: String,
        val rawTitle: String,
        val groupName: String,
        val userName: String,
        val tags: String,
        val updateDate: String,
        val source: NovelSource?
    )

    /**
     * WebNovelReader のバックアップZIPから復元する。
     * @return 取り込み結果（[ImprovedDatabaseSyncManager.SyncResult] を共用）
     */
    suspend fun importFromZip(
        uri: Uri,
        callback: SyncProgressCallback? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        var outerTemp: File? = null
        var dbTemp: File? = null
        var zip: ZipFile? = null
        var db: SQLiteDatabase? = null

        try {
            // 1) 外側ZIPを一時ファイルへコピー（ランダムアクセスのため ZipFile を使う）
            update(callback, SyncStep.PREPARING, "バックアップZIPを読み込み中...", 0.02f)
            outerTemp = copyUriToTemp(uri)
                ?: return@withContext fail(callback, "ZIPファイルをコピーできませんでした")
            zip = try {
                ZipFile(outerTemp)
            } catch (e: Exception) {
                return@withContext fail(callback, "ZIPファイルを開けませんでした: ${e.message}")
            }

            // 2) 埋め込み webnovel.db を取り出して開く
            val dbEntry = zip.getEntry(EMBEDDED_DB_NAME)
                ?: return@withContext fail(
                    callback,
                    "$EMBEDDED_DB_NAME が見つかりません（対応していないZIP形式です）"
                )
            update(callback, SyncStep.CHECKING_COMPATIBILITY, "メタデータDBを展開中...", 0.05f)
            dbTemp = File(context.cacheDir, "wnr_embedded_${System.currentTimeMillis()}.db")
            zip.getInputStream(dbEntry).use { input ->
                FileOutputStream(dbTemp).use { output -> input.copyTo(output) }
            }
            db = try {
                SQLiteDatabase.openDatabase(dbTemp.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) {
                return@withContext fail(callback, "webnovel.db を開けませんでした: ${e.message}")
            }

            if (!tableExists(db, "fetch_target") || !tableExists(db, "episode")) {
                return@withContext fail(
                    callback,
                    "webnovel.db の形式が不正です（fetch_target / episode テーブルが必要）"
                )
            }

            val preserve = SettingsStore(context).getDatabaseSyncSettings().preserveExistingEpisodes

            // 3) 作品一覧を読み込み
            val targets = readTargets(db)
            val grandTotalEpisodes = countAllEpisodes(db)
            val totalNovels = targets.size

            var novelCount = 0
            var episodeCount = 0
            var skippedNovels = 0

            for ((index, target) in targets.withIndex()) {
                val source = target.source
                if (source == null) {
                    skippedNovels++
                    Log.w(TAG, "非対応サイトのためスキップ: ${target.url}")
                    continue
                }

                try {
                    episodeCount += importOneNovel(
                        zip = zip,
                        db = db,
                        target = target,
                        source = source,
                        preserve = preserve,
                        callback = callback,
                        novelIndex = index,
                        totalNovels = totalNovels,
                        cumulativeEpisodes = episodeCount,
                        grandTotalEpisodes = grandTotalEpisodes
                    )
                    novelCount++
                } catch (e: Exception) {
                    Log.e(TAG, "作品「${target.rawTitle}」(${source.ncode})の取り込み中にエラー", e)
                    skippedNovels++
                }
            }

            val message = buildString {
                append("復元が完了しました: 作品${novelCount}件、エピソード${episodeCount}件")
                if (skippedNovels > 0) append("（非対応・失敗 ${skippedNovels}件をスキップ）")
            }
            update(callback, SyncStep.COMPLETED, message, 1.0f)

            val result = SyncResult(
                success = true,
                novelDescsCount = novelCount,
                episodesCount = episodeCount,
                lastReadCount = 0
            )
            callback?.onComplete(result)
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "ZIP復元中に予期しないエラー", e)
            return@withContext fail(callback, "予期しないエラー: ${e.message}")
        } finally {
            try { db?.close() } catch (_: Exception) {}
            try { zip?.close() } catch (_: Exception) {}
            dbTemp?.delete()
            outerTemp?.delete()
        }
    }

    /** 1作品分（メタデータ＋全エピソード本文）を取り込む。戻り値は取り込んだエピソード数。 */
    private suspend fun importOneNovel(
        zip: ZipFile,
        db: SQLiteDatabase,
        target: TargetInfo,
        source: NovelSource,
        preserve: Boolean,
        callback: SyncProgressCallback?,
        novelIndex: Int,
        totalNovels: Int,
        cumulativeEpisodes: Int,
        grandTotalEpisodes: Int
    ): Int {
        val ftid = target.id

        // 話数（no>=1）と最大話番号を把握
        var maxNo = 0
        var episodeRows = 0
        db.rawQuery(
            "SELECT COUNT(*), COALESCE(MAX(no), 0) FROM episode WHERE fetch_target_id=? AND no>=1",
            arrayOf(ftid.toString())
        ).use { c -> if (c.moveToFirst()) { episodeRows = c.getInt(0); maxNo = c.getInt(1) } }

        // 短編（no=0 のみ本文を持つ）フォールバック用
        val isSingle = episodeRows == 0

        // no=0（作品トップページ）の _id を取得しメタデータを補完（なろうのみ）
        var indexId: Long = -1
        db.rawQuery(
            "SELECT _id FROM episode WHERE fetch_target_id=? AND no=0 LIMIT 1",
            arrayOf(ftid.toString())
        ).use { c -> if (c.moveToFirst()) indexId = c.getLong(0) }

        var author = target.userName
        var synopsis = ""
        var userid: String? = null
        if (source.siteType == 1 && indexId >= 0) {
            readNestedHtml(zip, ftid, indexId)?.let { html ->
                val meta = parseSyosetuIndexMeta(html)
                if (author.isBlank()) author = meta.author
                synopsis = meta.synopsis
                userid = meta.userid
            }
        }

        val title = cleanTitle(target.rawTitle, source.siteType)
        val dateStr = normalizeDate(target.updateDate)
        val now = nowString()
        val totalEp = if (isSingle) 1 else maxNo
        val noveltype = if (totalEp <= 1) 2 else 1 // 1話のみ=短編

        val novel = NovelDescEntity(
            ncode = source.ncode,
            title = title,
            author = author,
            Synopsis = synopsis,
            main_tag = "",
            sub_tag = target.tags,
            rating = source.rating,
            last_update_date = dateStr,
            total_ep = totalEp,
            general_all_no = totalEp,
            userid = userid,
            noveltype = noveltype,
            length = null,
            updated_at = dateStr,
            registered_at = now,
            is_favorite = 0,
            site_type = source.siteType,
            sub_site = source.subSite,
            end_flag = 0
        )
        // 既存レコードがあれば お気に入り/完結フラグ 等のローカル値を保持
        val existing = repository.getNovelByNcode(source.ncode)
        repository.insertNovel(
            if (existing != null) novel.copy(
                is_favorite = existing.is_favorite,
                end_flag = if (existing.end_flag != 0) existing.end_flag else novel.end_flag,
                sub_site = if (existing.sub_site != 0) existing.sub_site else novel.sub_site
            ) else novel
        )

        // エピシード本文を取り込み
        val batchSize = 20
        val batch = mutableListOf<EpisodeEntity>()
        val mappingBatch = mutableListOf<EpisodeMappingEntity>()
        var imported = 0

        val whereClause = if (isSingle) "fetch_target_id=? AND no=0" else "fetch_target_id=? AND no>=1"
        db.rawQuery(
            "SELECT _id, no, title, is_read, url FROM episode WHERE $whereClause ORDER BY no",
            arrayOf(ftid.toString())
        ).use { c ->
            val idxId = c.getColumnIndexOrThrow("_id")
            val idxNo = c.getColumnIndexOrThrow("no")
            val idxTitle = c.getColumnIndexOrThrow("title")
            val idxRead = c.getColumnIndexOrThrow("is_read")
            val idxUrl = c.getColumnIndexOrThrow("url")

            while (c.moveToNext()) {
                val epId = c.getLong(idxId)
                val rawNo = c.getInt(idxNo)
                val episodeNo = if (isSingle) 1 else rawNo
                val eTitle = c.getString(idxTitle) ?: ""
                val isRead = if (c.getInt(idxRead) == 1) 1 else 0
                val epUrl = c.getString(idxUrl) ?: ""

                val html = readNestedHtml(zip, ftid, epId)
                val body = when {
                    html == null -> ""
                    source.siteType == 2 -> extractKakuyomuBody(html)
                    else -> extractSyosetuBody(html)
                }

                batch.add(
                    EpisodeEntity(
                        ncode = source.ncode,
                        episode_no = episodeNo.toString(),
                        body = body,
                        e_title = eTitle,
                        update_time = now,
                        is_read = isRead,
                        is_bookmark = 0,
                        reading_rate = if (isRead == 1) 1.0f else 0f
                    )
                )
                // カクヨムは episode_mapping（話番号→エピソードID）も再構築
                if (source.siteType == 2) {
                    extractKakuyomuEpisodeId(epUrl)?.let { kid ->
                        mappingBatch.add(
                            EpisodeMappingEntity(source.ncode, episodeNo, kid)
                        )
                    }
                }
                imported++

                if (batch.size >= batchSize) {
                    flushBatch(batch, mappingBatch, preserve, source.siteType)
                    val fractionInNovel = imported.toFloat() / (episodeRows.coerceAtLeast(1))
                    reportProgress(
                        callback, novelIndex, totalNovels, fractionInNovel,
                        cumulativeEpisodes + imported, grandTotalEpisodes,
                        source.ncode, title
                    )
                }
            }
        }
        if (batch.isNotEmpty()) {
            flushBatch(batch, mappingBatch, preserve, source.siteType)
        }

        reportProgress(
            callback, novelIndex, totalNovels, 1.0f,
            cumulativeEpisodes + imported, grandTotalEpisodes, source.ncode, title
        )
        Log.d(TAG, "作品「$title」(${source.ncode}) を${imported}話取り込みました")
        return imported
    }

    private suspend fun flushBatch(
        batch: MutableList<EpisodeEntity>,
        mappingBatch: MutableList<EpisodeMappingEntity>,
        preserve: Boolean,
        siteType: Int
    ) {
        repository.insertEpisodes(batch, preserve)
        if (siteType == 2 && mappingBatch.isNotEmpty()) {
            repository.insertEpisodeMappings(mappingBatch.toList())
        }
        batch.clear()
        mappingBatch.clear()
    }

    // ---- fetch_target 読み込み ---------------------------------------------

    private fun readTargets(db: SQLiteDatabase): List<TargetInfo> {
        val list = mutableListOf<TargetInfo>()
        db.rawQuery(
            "SELECT _id, url, title, group_name, user_name, tags, update_date FROM fetch_target ORDER BY _id",
            null
        ).use { c ->
            val idxId = c.getColumnIndexOrThrow("_id")
            val idxUrl = c.getColumnIndexOrThrow("url")
            val idxTitle = c.getColumnIndexOrThrow("title")
            val idxGroup = c.getColumnIndexOrThrow("group_name")
            val idxUser = c.getColumnIndexOrThrow("user_name")
            val idxTags = c.getColumnIndexOrThrow("tags")
            val idxDate = c.getColumnIndexOrThrow("update_date")
            while (c.moveToNext()) {
                val url = c.getString(idxUrl) ?: ""
                list.add(
                    TargetInfo(
                        id = c.getInt(idxId),
                        url = url,
                        rawTitle = c.getString(idxTitle) ?: "",
                        groupName = c.getString(idxGroup) ?: "",
                        userName = c.getString(idxUser) ?: "",
                        tags = c.getString(idxTags) ?: "",
                        updateDate = c.getString(idxDate) ?: "",
                        source = parseSource(url)
                    )
                )
            }
        }
        return list
    }

    private fun countAllEpisodes(db: SQLiteDatabase): Int {
        return db.rawQuery("SELECT COUNT(*) FROM episode WHERE no>=1", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    // ---- URL → サイト判別 ---------------------------------------------------

    private fun parseSource(url: String): NovelSource? {
        val u = url.trim()
        return when {
            u.contains("ncode.syosetu.com") -> {
                val nc = extractSyosetuNcode(u) ?: return null
                NovelSource(nc.lowercase(Locale.ROOT), 1, 2, 1, null)
            }
            u.contains("novel18.syosetu.com") -> {
                val nc = extractSyosetuNcode(u) ?: return null
                NovelSource(nc.lowercase(Locale.ROOT), 1, 1, 0, null)
            }
            u.contains("kakuyomu.jp/works/") -> {
                val workId = Regex("kakuyomu\\.jp/works/(\\d+)").find(u)?.groupValues?.get(1)
                    ?: return null
                val nc = try {
                    PseudoNcodeGenerator.generateKakuyomuNcode(workId)
                } catch (e: Exception) {
                    return null
                }
                NovelSource(nc, 2, 2, 0, workId)
            }
            // syosetu.org(ハーメルン)等 未対応サイトは null
            else -> null
        }
    }

    private fun extractSyosetuNcode(url: String): String? =
        Regex("syosetu\\.com/(n[0-9A-Za-z]+)").find(url)?.groupValues?.get(1)

    private fun extractKakuyomuEpisodeId(url: String): String? =
        Regex("/episodes/(\\d+)").find(url)?.groupValues?.get(1)

    // ---- HTML本文抽出 -------------------------------------------------------

    /** なろう本文抽出（NovelApiUtils.fetchEpisode と同じ `div.p-novel__body > div` 方式） */
    private fun extractSyosetuBody(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            val children = doc.select("div.p-novel__body > div")
            if (children.isNotEmpty()) {
                buildString {
                    children.forEachIndexed { i, el ->
                        append(el.outerHtml())
                        if (i < children.size - 1) append("\n<hr>\n")
                    }
                }
            } else {
                // 旧構造フォールバック
                (doc.selectFirst("#novel_honbun")
                    ?: doc.selectFirst("div.p-novel__text")
                    ?: doc.selectFirst("div.js-novel-text"))?.outerHtml() ?: ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "なろう本文抽出に失敗", e)
            ""
        }
    }

    /** カクヨム本文抽出（KakuyomuAdapter.fetchEpisodeContent と同じセレクタ優先順位） */
    private fun extractKakuyomuBody(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            val el = doc.selectFirst("div.widget-episodeBody.js-episode-body")
                ?: doc.selectFirst("div.widget-episodeBody")
                ?: doc.selectFirst("div.js-episode-body")
            val body = el?.html() ?: ""
            // 読み込みエラーページ本文は保存しない
            if (body.take(200).contains("<div class=\"dots-indicator\" id=\"LoadingEpisode\">")) "" else body
        } catch (e: Exception) {
            Log.w(TAG, "カクヨム本文抽出に失敗", e)
            ""
        }
    }

    private data class SyosetuMeta(val author: String, val synopsis: String, val userid: String?)

    /** なろう作品トップページ(no=0)から 作者名/あらすじ/作者ID を補完抽出 */
    private fun parseSyosetuIndexMeta(html: String): SyosetuMeta {
        return try {
            val doc = Jsoup.parse(html)
            val authorEl = doc.selectFirst("div.p-novel__author a")
                ?: doc.selectFirst("div.p-novel__author")
            val author = authorEl?.text()?.removePrefix("作者：")?.trim() ?: ""
            val userid = doc.selectFirst("div.p-novel__author a")
                ?.absUrl("href")
                ?.let { Regex("mypage\\.syosetu\\.com/(\\d+)").find(it)?.groupValues?.get(1) }
            val synopsis = (doc.selectFirst("#novel_ex")
                ?: doc.selectFirst("div.p-novel__summary"))
                ?.wholeText()?.trim() ?: ""
            SyosetuMeta(author, synopsis, userid)
        } catch (e: Exception) {
            SyosetuMeta("", "", null)
        }
    }

    // ---- 入れ子ZIP読み出し --------------------------------------------------

    /**
     * `ep_data/{ftid}/{epId}` を読み出す。この中身自体が単一エントリのZIPなので、
     * 展開して生HTML文字列を返す。存在しない/読めない場合はnull。
     */
    private fun readNestedHtml(zip: ZipFile, ftid: Int, epId: Long): String? {
        val entry = zip.getEntry("ep_data/$ftid/$epId") ?: return null
        val nestedBytes = try {
            zip.getInputStream(entry).use { it.readBytes() }
        } catch (e: Exception) {
            return null
        }
        // 入れ子ZIPを展開（最初のエントリが生HTML）
        return try {
            ZipInputStream(ByteArrayInputStream(nestedBytes)).use { zis ->
                zis.nextEntry ?: return null
                zis.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            // 万一ZIPでなければ平文として扱う
            runCatching { nestedBytes.toString(Charsets.UTF_8) }.getOrNull()
        }
    }

    // ---- ユーティリティ -----------------------------------------------------

    private fun cleanTitle(raw: String, siteType: Int): String {
        var t = raw.trim()
        if (siteType == 2) t = t.removeSuffix(" - カクヨム").trim()
        return t
    }

    private fun normalizeDate(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return nowString()
        return s.replace('/', '-')
    }

    private fun nowString(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table)
        ).use { it.count > 0 }

    private suspend fun copyUriToTemp(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, "wnr_import_${System.currentTimeMillis()}.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: return@withContext null
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "ZIPコピー中にエラー", e)
            null
        }
    }

    private fun reportProgress(
        callback: SyncProgressCallback?,
        novelIndex: Int,
        totalNovels: Int,
        fractionInNovel: Float,
        currentEpisodes: Int,
        totalEpisodes: Int,
        ncode: String,
        title: String
    ) {
        val base = (novelIndex + fractionInNovel.coerceIn(0f, 1f)) / totalNovels.coerceAtLeast(1)
        val progress = (0.05f + 0.9f * base).coerceIn(0f, 0.99f)
        update(
            callback,
            SyncStep.SYNCING_EPISODES,
            "復元中 [${novelIndex + 1}/$totalNovels] $title",
            progress,
            ncode = ncode,
            title = title,
            currentCount = currentEpisodes,
            totalCount = totalEpisodes
        )
    }

    private fun update(
        callback: SyncProgressCallback?,
        step: SyncStep,
        message: String,
        progress: Float,
        ncode: String = "",
        title: String = "",
        currentCount: Int = 0,
        totalCount: Int = 0
    ) {
        val now = System.currentTimeMillis()
        val force = step == SyncStep.COMPLETED || step == SyncStep.ERROR ||
            step == SyncStep.PREPARING || step == SyncStep.CHECKING_COMPATIBILITY
        if (!force && now - lastUpdateTime < minUpdateIntervalMs) return
        lastUpdateTime = now
        callback?.onProgressUpdate(
            SyncProgress(
                step = step,
                message = message,
                progress = progress,
                currentNcode = ncode,
                currentTitle = title,
                currentCount = currentCount,
                totalCount = totalCount
            )
        )
    }

    private fun fail(callback: SyncProgressCallback?, message: String): SyncResult {
        Log.e(TAG, message)
        callback?.onProgressUpdate(
            SyncProgress(step = SyncStep.ERROR, message = message, progress = 0f)
        )
        val result = SyncResult(
            success = false,
            novelDescsCount = 0,
            episodesCount = 0,
            lastReadCount = 0,
            errorMessage = message
        )
        callback?.onComplete(result)
        return result
    }
}
