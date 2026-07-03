/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Composable screen listing episodes of a novel.
 */
package com.shunlight_library.novel_reader

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.shunlight_library.novel_reader.api.NovelApiUtils
import com.shunlight_library.novel_reader.api.NovelApiUtils.fetchEpisodeWithRetry
import com.shunlight_library.novel_reader.api.NovelApiUtils.fetchNovelInfo
import android.content.Intent
import com.shunlight_library.novel_reader.service.UpdateService

enum class UpdateType {
    UPDATE,         // 更新（新しいエピソードのみチェック）
    REDOWNLOAD,     // 再取得（すべて削除して再取得）
    FIX_ERRORS,     // エラー修正（エラーや欠番のあるエピソードのみ修正）
    CHECK_REVISION  // 改稿チェック（既存エピソードの改稿を確認）
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    ncode: String,
    onBack: () -> Unit,
    onEpisodeClick: (String, String) -> Unit, // ncode, episodeNo
    onAuthorClick: (String) -> Unit
) {
    val repository = NovelReaderApplication.getRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状態変数
    var novel by remember { mutableStateOf<NovelDescEntity?>(null) }
    var episodes by remember { mutableStateOf<List<com.shunlight_library.novel_reader.data.entity.EpisodeMeta>>(emptyList()) }
    var lastRead by remember { mutableStateOf<LastReadNovelEntity?>(null) }

    // 更新中チェック用の状態変数
    var isNovelUpdating by remember { mutableStateOf(false) }

    // 折りたたみ状態の追加
    var isDescriptionExpanded by remember { mutableStateOf(true) }

    // タグ編集用の状態変数
    var showTagEditDialog by remember { mutableStateOf(false) }
    var mainTag by remember { mutableStateOf("") }
    var subTag by remember { mutableStateOf("") }

    // ダイアログ関連の状態変数 - グローバルスコープから移動
    var showUpdateDialog by remember { mutableStateOf(false) }
    var selectedUpdateType by remember { mutableStateOf(UpdateType.UPDATE) }
    var isUpdating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0f) }
    var updateMessage by remember { mutableStateOf("") }

    // エピソード再取得の確認ダイアログ用
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }
    var tempGeneralAllNo by remember { mutableStateOf(0) }

    // エラー修正確認ダイアログ用
    var showErrorFixConfirmDialog by remember { mutableStateOf(false) }
    var errorEpisodeCount by remember { mutableStateOf(0) }
    var missingEpisodeCount by remember { mutableStateOf(0) }
    var redownloadTargets by remember { mutableStateOf<List<String>>(emptyList()) }

    var isMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteNovelDialog by remember { mutableStateOf(false) }
    var isDeletingNovel by remember { mutableStateOf(false) }

    // カクヨム目次復帰用
    var showTocRecoveryBanner by remember { mutableStateOf(false) }
    var tocRecoveryMappings by remember { mutableStateOf<List<com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity>>(emptyList()) }
    var isRecoveringToc by remember { mutableStateOf(false) }

    // データの取得
    LaunchedEffect(ncode) {
        // 小説情報の取得
        novel = repository.getNovelByNcode(ncode)

        // 削除済み作品の検出
        if (novel == null) {
            Toast.makeText(context, "この作品は削除されました。ホームに戻ります。", Toast.LENGTH_LONG).show()
            delay(1500)
            onBack()
            return@LaunchedEffect
        }

        // 最後に読んだ情報の取得
        lastRead = repository.getLastReadByNcode(ncode)

        // 初期タグ値の設定
        novel?.let {
            mainTag = it.main_tag
            subTag = it.sub_tag
        }

        // カクヨム目次復帰チェック（エピソードFlowが最初のemitをするまで待機）
        val currentNovel = novel
        if (currentNovel != null &&
            currentNovel.site_type == com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_KAKUYOMU &&
            currentNovel.general_all_no > 0) {
            delay(500) // Flowの初回emitを待つ
            if (episodes.size < currentNovel.general_all_no) {
                val mappings = withContext(Dispatchers.IO) {
                    repository.getEpisodeMappings(ncode)
                }
                if (mappings.size > episodes.size) {
                    tocRecoveryMappings = mappings
                    showTocRecoveryBanner = true
                }
            }
        }
    }

    LaunchedEffect(ncode) {
        // エピソード一覧の取得（本文を含まない軽量メタデータ — 大量話数でもメモリ効率良好）
        repository.getEpisodeMetasByNcode(ncode).collect { episodeList ->
            // エピソードリストを数値順にソート
            episodes = episodeList.sortedWith(compareBy {
                it.episode_no.toIntOrNull() ?: Int.MAX_VALUE
            })
        }
    }

    // 更新中状態を定期的にチェック
    LaunchedEffect(ncode) {
        while (true) {
            isNovelUpdating = NovelUpdateCoordinator.isUpdating(ncode)
            delay(1000) // 1秒ごとにチェック
        }
    }

    // カクヨム目次復帰：mappingテーブルからエピソードスタブを生成
    fun performTocRecovery() {
        val mappings = tocRecoveryMappings
        if (mappings.isEmpty()) return
        isRecoveringToc = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val existingNos = episodes.map { it.episode_no }.toSet()
                val stubs = mappings
                    .filter { it.episode_no.toString() !in existingNos }
                    .map { mapping ->
                        EpisodeEntity(
                            ncode = ncode,
                            episode_no = mapping.episode_no.toString(),
                            body = "",
                            e_title = "第${mapping.episode_no}話",
                            update_time = ""
                        )
                    }
                stubs.forEach { repository.insertEpisode(it) }
                val currentNovel = novel
                // 挿入後の実エピソード数（既存 + 新規スタブ）を total_ep に反映する
                val actualEpisodeCount = existingNos.size + stubs.size
                if (currentNovel != null && currentNovel.total_ep < actualEpisodeCount) {
                    repository.updateNovel(currentNovel.copy(total_ep = actualEpisodeCount))
                    novel = repository.getNovelByNcode(ncode)
                }
            }
            isRecoveringToc = false
            showTocRecoveryBanner = false
            Toast.makeText(
                context,
                "${mappings.size}件のエピソードを復元しました。「エラー修正」で本文を再取得してください。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 更新処理の開始関数
    fun startUpdateProcess() {
        showUpdateDialog = true
        selectedUpdateType = UpdateType.UPDATE
        isUpdating = false
        updateProgress = 0f
        updateMessage = ""
    }

    // 「更新」実行関数（UpdateService経由でバックグラウンド実行）
    fun performUpdate() {
        val targetNovel = novel
        if (targetNovel == null) {
            Toast.makeText(context, "小説情報が読み込まれていません", Toast.LENGTH_SHORT).show()
            return
        }

        // UpdateServiceを起動して更新処理を実行
        val intent = Intent(context, UpdateService::class.java).apply {
            action = UpdateService.ACTION_START_UPDATE
            putExtra(UpdateService.EXTRA_NCODE, targetNovel.ncode)
            putExtra(UpdateService.EXTRA_UPDATE_TYPE, UpdateService.UPDATE_TYPE_CHECK)
        }
        context.startService(intent)

        // ダイアログを閉じて通知を表示
        showUpdateDialog = false
        Toast.makeText(context, "バックグラウンドで更新処理を開始しました", Toast.LENGTH_SHORT).show()
    }

    // 「改稿チェック」実行関数（UpdateService経由でバックグラウンド実行）
    fun performCheckRevision() {
        val targetNovel = novel
        if (targetNovel == null) {
            Toast.makeText(context, "小説情報が読み込まれていません", Toast.LENGTH_SHORT).show()
            return
        }

        // カクヨムは非対応
        if (targetNovel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
            Toast.makeText(context, "カクヨムは改稿チェックに対応していません", Toast.LENGTH_SHORT).show()
            return
        }

        // 短編も非対応
        if (targetNovel.noveltype == 2) {
            Toast.makeText(context, "短編小説は改稿チェックに対応していません", Toast.LENGTH_SHORT).show()
            return
        }

        // UpdateServiceを起動して改稿チェックを実行
        val intent = Intent(context, UpdateService::class.java).apply {
            action = UpdateService.ACTION_START_UPDATE
            putExtra(UpdateService.EXTRA_NCODE, targetNovel.ncode)
            putExtra(UpdateService.EXTRA_UPDATE_TYPE, UpdateService.UPDATE_TYPE_CHECK_REVISION)
        }
        context.startService(intent)

        // ダイアログを閉じて通知を表示
        showUpdateDialog = false
        Toast.makeText(context, "バックグラウンドで改稿チェックを開始しました", Toast.LENGTH_SHORT).show()
    }

    // 旧実装（UI処理版、参考用にコメントアウト）
    /*
    fun performUpdateOld() {
        isUpdating = true
        updateProgress = 0f
        updateMessage = "APIで最新情報を確認中..."

        scope.launch {
            val targetNovel = novel ?: run {
                updateProgress = 1f
                updateMessage = "小説情報が読み込まれていません"
                Toast.makeText(context, "小説情報が読み込まれていません", Toast.LENGTH_SHORT).show()
                delay(1500)
                isUpdating = false
                showUpdateDialog = false
                return@launch
            }

            try {
                val newGeneralAllNo: Int
                val newUpdatedAt: String

                if (targetNovel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                    // カクヨムの場合、HTMLスクレイピングで更新確認
                    val kakuyomuAdapter = com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter()
                    val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(targetNovel.ncode)

                    updateProgress = 0.3f
                    updateMessage = "更新を確認中..."

                    val updateSummary = kakuyomuAdapter.fetchUpdateSummary(workId)
                    val latestEpisodeCount = updateSummary.latestEpisodeCount

                    if (latestEpisodeCount > targetNovel.total_ep) {
                        updateProgress = 0.6f
                        updateMessage = "詳細情報を取得中（メタデータのみ）..."

                        newGeneralAllNo = latestEpisodeCount
                        newUpdatedAt = updateSummary.novelDesc.updated_at

                        // 小説情報を更新
                        val updatedNovel = targetNovel.copy(
                            general_all_no = newGeneralAllNo,
                            updated_at = newUpdatedAt,
                            title = updateSummary.novelDesc.title,
                            author = updateSummary.novelDesc.author,
                            Synopsis = updateSummary.novelDesc.Synopsis,
                            main_tag = updateSummary.novelDesc.main_tag,
                            sub_tag = updateSummary.novelDesc.sub_tag,
                            last_update_date = updateSummary.novelDesc.last_update_date
                        )
                        repository.updateNovel(updatedNovel)

                        // 更新キューに追加
                        val updateQueue = UpdateQueueEntity(
                            ncode = targetNovel.ncode,
                            total_ep = targetNovel.total_ep,
                            general_all_no = newGeneralAllNo,
                            update_time = newUpdatedAt
                        )
                        repository.insertUpdateQueue(updateQueue)

                        val newEpisodeCount = newGeneralAllNo - targetNovel.total_ep

                        updateProgress = 1f
                        updateMessage = "更新を確認しました。更新情報画面から一括更新を実行してください。"
                        Toast.makeText(context, "更新を確認しました (新規${newEpisodeCount}話)\n更新情報画面から一括更新を実行してください", Toast.LENGTH_LONG).show()
                    } else {
                        updateProgress = 1f
                        updateMessage = "更新はありません"
                        Toast.makeText(context, "この小説に更新はありません", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 小説家になろうの場合、APIから取得
                    val info = fetchNovelInfo(targetNovel.ncode, targetNovel.rating == 1)

                    if (info == null) {
                        updateProgress = 1f
                        updateMessage = "APIからデータが取得できませんでした"
                        Toast.makeText(context, "APIからデータが取得できませんでした", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                        showUpdateDialog = false
                        return@launch
                    }

                    newGeneralAllNo = info.generalAllNo
                    newUpdatedAt = info.updatedAt

                    if (newGeneralAllNo > targetNovel.general_all_no) {
                        val updatedNovel = targetNovel.copy(
                            general_all_no = newGeneralAllNo,
                            updated_at = newUpdatedAt
                        )
                        repository.updateNovel(updatedNovel)

                        val updateQueue = UpdateQueueEntity(
                            ncode = targetNovel.ncode,
                            total_ep = targetNovel.total_ep,
                            general_all_no = newGeneralAllNo,
                            update_time = newUpdatedAt
                        )
                        repository.insertUpdateQueue(updateQueue)

                        updateProgress = 1f
                        updateMessage = "更新を確認しました。更新キューに追加しました。"
                        Toast.makeText(context, "更新を確認しました", Toast.LENGTH_SHORT).show()
                    } else {
                        updateProgress = 1f
                        updateMessage = "更新はありません"
                        Toast.makeText(context, "この小説に更新はありません", Toast.LENGTH_SHORT).show()
                    }
                }

                delay(1500)
                isUpdating = false
                showUpdateDialog = false
            } catch (e: Exception) {
                updateProgress = 1f
                updateMessage = "エラー: ${e.message}"
                Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_SHORT).show()
                delay(1500)
                isUpdating = false
                showUpdateDialog = false
            }
        }
    }
    */

    // 「再取得」実行関数（UpdateService経由でバックグラウンド実行）
    fun performRedownload() {
        val targetNovel = novel
        if (targetNovel == null) {
            Toast.makeText(context, "小説情報が読み込まれていません", Toast.LENGTH_SHORT).show()
            return
        }

        // UpdateServiceを起動して再取得処理を実行
        val intent = Intent(context, UpdateService::class.java).apply {
            action = UpdateService.ACTION_START_UPDATE
            putExtra(UpdateService.EXTRA_NCODE, targetNovel.ncode)
            putExtra(UpdateService.EXTRA_UPDATE_TYPE, UpdateService.UPDATE_TYPE_DOWNLOAD)
        }
        context.startService(intent)

        // ダイアログを閉じて通知を表示
        showUpdateDialog = false
        Toast.makeText(context, "バックグラウンドで再取得処理を開始しました", Toast.LENGTH_SHORT).show()
    }

    // 旧実装（UI処理版、参考用にコメントアウト）
    /*
    fun performRedownloadOld() {
        val targetNovel = novel
        if (targetNovel == null) {
            Toast.makeText(context, "小説情報が読み込まれていません", Toast.LENGTH_SHORT).show()
            return
        }

        isUpdating = true
        updateProgress = 0f
        updateMessage = "エピソードを削除中..."

        scope.launch {
            val ncode = targetNovel.ncode
            try {
                updateMessage = "他の更新処理の終了を待機しています..."
                val cancelled = NovelUpdateCoordinator.cancelAndWait(ncode)
                if (!cancelled) {
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "他の更新処理を停止できませんでした"
                        Toast.makeText(
                            context,
                            "進行中の更新を停止できませんでした。時間を置いて再度お試しください。",
                            Toast.LENGTH_SHORT
                        ).show()
                        delay(1500)
                        isUpdating = false
                        showUpdateDialog = false
                    }
                    return@launch
                }

                val session = NovelUpdateCoordinator.awaitUpdateSlot(ncode)
                if (session == null) {
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "他の更新処理を待機中です"
                        Toast.makeText(context, "他の更新処理が進行中です。時間を置いて再度お試しください。", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                        showUpdateDialog = false
                    }
                    return@launch
                }

                suspend fun handleCancellation() {
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "処理が中断されました"
                        Toast.makeText(context, "更新処理が中断されました", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                        showUpdateDialog = false
                    }
                }

                try {
                    if (session.isCancelled()) {
                        handleCancellation()
                        return@launch
                    }

                    // エピソードを削除
                    withContext(Dispatchers.IO) {
                        repository.deleteEpisodesByNcode(ncode)
                    }

                    if (session.isCancelled()) {
                        handleCancellation()
                        return@launch
                    }

                    val generalAllNoValue: Int
                    val newUpdatedAt: String

                    if (targetNovel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                        // カクヨムの場合、HTMLスクレイピングで取得
                        updateProgress = 0.3f
                        updateMessage = "HTMLスクレイピングで最新情報を確認中..."

                        val adapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU)
                        val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)

                        // 本文なしでメタデータとエピソードリストのみを取得
                        val (updatedNovelDesc, episodes) = (adapter as KakuyomuAdapter).fetchNovelMetadataWithEpisodeList(workId)

                        if (session.isCancelled()) {
                            handleCancellation()
                            return@launch
                        }

                        generalAllNoValue = episodes.size
                        newUpdatedAt = updatedNovelDesc.updated_at

                        // 小説情報を更新（カクヨムの場合はより多くの情報を更新）
                        val updatedNovel = targetNovel.copy(
                            general_all_no = generalAllNoValue,
                            total_ep = 0, // エピソードを削除したので0に
                            updated_at = newUpdatedAt,
                            title = updatedNovelDesc.title,
                            author = updatedNovelDesc.author,
                            Synopsis = updatedNovelDesc.Synopsis,
                            main_tag = updatedNovelDesc.main_tag,
                            sub_tag = updatedNovelDesc.sub_tag,
                            last_update_date = updatedNovelDesc.last_update_date
                        )
                        repository.updateNovel(updatedNovel)
                    } else {
                        // 小説家になろうの場合、APIから取得
                        updateProgress = 0.3f
                        updateMessage = "APIで最新情報を確認中..."

                        val info = fetchNovelInfo(ncode, targetNovel.rating == 1)

                        if (session.isCancelled()) {
                            handleCancellation()
                            return@launch
                        }

                        generalAllNoValue = info?.generalAllNo ?: targetNovel.general_all_no
                        newUpdatedAt = info?.updatedAt ?: targetNovel.updated_at

                        // 小説情報を更新
                        val updatedNovel = targetNovel.copy(
                            general_all_no = generalAllNoValue,
                            total_ep = 0, // エピソードを削除したので0に
                            updated_at = newUpdatedAt
                        )
                        repository.updateNovel(updatedNovel)
                    }

                    if (session.isCancelled()) {
                        handleCancellation()
                        return@launch
                    }

                    // 確認ダイアログを表示するためにUIスレッドに戻る
                    withContext(Dispatchers.Main) {
                        tempGeneralAllNo = generalAllNoValue
                        isUpdating = false
                        showUpdateDialog = false
                        showDownloadConfirmDialog = true
                    }
                } finally {
                    NovelUpdateCoordinator.finishUpdate(session)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateProgress = 1f
                    updateMessage = "エラー: ${e.message}"
                    Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_SHORT).show()
                    delay(1500)
                    isUpdating = false
                    showUpdateDialog = false
                }
            }
        }
    }
    */

    // エピソードをダウンロードする関数
    fun performDownloadEpisodes(generalAllNo: Int) {
        val targetNovel = novel
        if (targetNovel == null) {
            Toast.makeText(context, "小説情報が読み込まれていません", Toast.LENGTH_SHORT).show()
            return
        }

        isUpdating = true
        updateProgress = 0.3f
        updateMessage = "エピソードを取得中... (0/$generalAllNo)"

        scope.launch {
            val ncode = targetNovel.ncode
            try {
                updateMessage = "他の更新処理の終了を待機しています..."
                val cancelled = NovelUpdateCoordinator.cancelAndWait(ncode)
                if (!cancelled) {
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "他の更新処理を停止できませんでした"
                        Toast.makeText(
                            context,
                            "進行中の更新を停止できませんでした。時間を置いて再度お試しください。",
                            Toast.LENGTH_SHORT
                        ).show()
                        delay(1500)
                        isUpdating = false
                    }
                    return@launch
                }

                val session = NovelUpdateCoordinator.awaitUpdateSlot(ncode)
                if (session == null) {
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "他の更新処理を待機中です"
                        Toast.makeText(context, "他の更新処理が進行中です。時間を置いて再度お試しください。", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                    }
                    return@launch
                }

                suspend fun handleCancellation() {
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "処理が中断されました"
                        Toast.makeText(context, "更新処理が中断されました", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                    }
                }

                try {
                    if (session.isCancelled()) {
                        handleCancellation()
                        return@launch
                    }

                    // 更新日時
                    val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    var successCount = 0
                    var failCount = 0

                    // カクヨムの場合は専用の処理を使用
                    val isKakuyomu = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.isKakuyomuNcode(ncode)
                    
                    if (isKakuyomu) {
                        // カクヨムの場合はストリーミング方式（1話取得→保存）で再取得
                        updateMessage = "カクヨムからエピソードを取得中..."
                        val adapter = com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter()
                        val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)

                        // repository を渡して1話ずつ保存（全話メモリ蓄積を回避）
                        val result = adapter.fetchNovelWithEpisodesIncludingMappings(
                            novelId = workId,
                            repository = repository,
                            onProgress = { current, total ->
                                updateProgress = if (total > 0) current.toFloat() / total else 0f
                                updateMessage = "エピソードを取得中... ($current/$total)"
                                successCount = current
                            }
                        )
                        val mappings = result.episodeMappings

                        if (session.isCancelled()) {
                            handleCancellation()
                            return@launch
                        }

                        // マッピング情報を保存（エピソード本体は取得中に1話ずつ保存済み）
                        if (mappings.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                val mappingEntities = mappings.map { (episodeNo, kakuyomuId) ->
                                    com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity(
                                        ncode = ncode,
                                        episode_no = episodeNo,
                                        kakuyomu_episode_id = kakuyomuId
                                    )
                                }
                                repository.insertEpisodeMappings(mappingEntities)
                            }
                        }

                        updateProgress = 1.0f
                        updateMessage = "保存完了: ${successCount}話"

                        android.util.Log.d("EpisodeListScreen", "カクヨムエピソード保存完了: ${successCount}話, マッピング: ${mappings.size}件")
                    } else {
                        // 小説家になろうの場合は逐次取得
                        // エピソード番号のリスト
                        val episodeNumbers = (1..generalAllNo).toList()

                        // スクレイピングの実行
                        for ((index, episodeNo) in episodeNumbers.withIndex()) {
                            if (session.isCancelled()) {
                                handleCancellation()
                                return@launch
                            }

                            val episode = fetchEpisodeWithRetry(ncode, episodeNo.toString(), targetNovel.rating == 1, targetNovel.noveltype)

                            if (episode != null) {
                                // データベースに保存
                                withContext(Dispatchers.IO) {
                                    repository.insertEpisode(episode)
                                }
                                successCount++
                            } else {
                                failCount++
                            }

                            // サーバーに負荷をかけないように少し待機
                            delay(50)

                            // 進捗を更新
                            val progress = (index + 1).toFloat() / generalAllNo
                            updateProgress = 0.3f + (0.7f * progress)
                            updateMessage = "エピソードを取得中... (${index + 1}/$generalAllNo)"
                        }
                    }

                    if (session.isCancelled()) {
                        handleCancellation()
                        return@launch
                    }

                    // 小説のtotal_epを更新
                    val updatedNovel = targetNovel.copy(
                        total_ep = successCount,
                        general_all_no = generalAllNo,
                        updated_at = currentDate
                    )
                    repository.updateNovel(updatedNovel)

                    // 処理結果の通知
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "完了: 成功${successCount}件、失敗${failCount}件"
                        Toast.makeText(context, "完了: 成功${successCount}件、失敗${failCount}件", Toast.LENGTH_SHORT).show()
                        delay(2000)
                        isUpdating = false
                    }
                } finally {
                    NovelUpdateCoordinator.finishUpdate(session)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateProgress = 1f
                    updateMessage = "エラー: ${e.message}"
                    Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_SHORT).show()
                    delay(1500)
                    isUpdating = false
                }
            }
        }
    }

    // 更新キューに追加するだけの関数
    fun performAddToUpdateQueue(generalAllNo: Int) {
        scope.launch {
            try {
                // 更新日時
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                // 更新キューに追加
                novel?.let {
                    val updateQueue = UpdateQueueEntity(
                        ncode = it.ncode,
                        total_ep = 0, // 全て削除したので0
                        general_all_no = generalAllNo,
                        update_time = currentDate
                    )
                    repository.insertUpdateQueue(updateQueue)
                }

                // 通知
                Toast.makeText(context, "エピソードを削除し、更新キューに追加しました。エピソードを取得するには「新着・更新情報」から「一括更新」を実行してください。", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // 例外発生
                Toast.makeText(context, "更新キューへの追加でエラーが発生しました: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 「エラー修正」実行関数
    fun performErrorFix() {
        val targetNovel = novel
        if (targetNovel == null) {
            Toast.makeText(context, "小説情報が読み込まれていません", Toast.LENGTH_SHORT).show()
            return
        }

        isUpdating = true
        updateProgress = 0f
        updateMessage = "エピソードをチェック中..."

        scope.launch {
            try {
                // エピソードを取得（本文なしメタデータ — エラー判定は body_empty フラグで行う）
                novel?.let {
                    val episodesList = repository.getEpisodeMetasByNcode(it.ncode).first()

                    // エラーのあるエピソードを見つける
                    val errorEpisodes = episodesList.filter { episode ->
                        episode.body_empty == 1 || episode.e_title.isEmpty()
                    }

                    val generalAllNoValue: Int

                    if (targetNovel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                        // カクヨムの場合、軽量な更新確認APIで話数のみ取得（全話DLは不要）
                        updateProgress = 0.2f
                        updateMessage = "更新情報を確認中..."

                        val kakuyomuAdapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU) as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
                        val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(it.ncode)

                        val updateSummary = kakuyomuAdapter.fetchUpdateSummary(workId)
                        generalAllNoValue = updateSummary.latestEpisodeCount

                        // 小説情報を更新
                        val updatedNovel = it.copy(
                            general_all_no = generalAllNoValue,
                            updated_at = updateSummary.novelDesc.updated_at,
                            title = updateSummary.novelDesc.title,
                            author = updateSummary.novelDesc.author,
                            Synopsis = updateSummary.novelDesc.Synopsis,
                            main_tag = updateSummary.novelDesc.main_tag,
                            sub_tag = updateSummary.novelDesc.sub_tag,
                            last_update_date = updateSummary.novelDesc.last_update_date
                        )
                        repository.updateNovel(updatedNovel)
                    } else {
                        // 小説家になろうの場合、APIから取得
                        updateProgress = 0.2f
                        updateMessage = "APIで最新情報を確認中..."

                        val info = fetchNovelInfo(it.ncode, it.rating == 1)
                        generalAllNoValue = info?.generalAllNo ?: it.general_all_no
                    }

                    // カクヨムかどうかをチェック
                    val isKakuyomu = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.isKakuyomuNcode(it.ncode)

                    val missingEpisodes: List<String>
                    if (isKakuyomu) {
                        // カクヨムの場合は欠番チェックをしない（エピソードIDが連番でないため）
                        missingEpisodes = emptyList()
                    } else {
                        // 小説家になろうの場合は欠番チェック
                        val episodeNumberMap = episodesList.associate { episode ->
                            val numericValue = episode.episode_no.toIntOrNull() ?: 0
                            numericValue to episode.episode_no
                        }

                        val maxEpisodeNo = episodeNumberMap.keys.maxOrNull() ?: 0
                        val checkRangeMax = maxOf(generalAllNoValue, maxEpisodeNo)
                        missingEpisodes = (1..checkRangeMax).filter { epNo ->
                            !episodeNumberMap.containsKey(epNo)
                        }.map { it.toString() }
                    }

                    errorEpisodeCount = errorEpisodes.size
                    missingEpisodeCount = missingEpisodes.size

                    // エラーのあるエピソードの番号リスト（episode_noをそのまま使用）
                    val errorEpisodeNumbers = errorEpisodes.map { it.episode_no }

                    // 再取得対象の番号リスト（エラーと欠番を合わせる）
                    redownloadTargets = (errorEpisodeNumbers + missingEpisodes).distinct().let { list ->
                        // 小説家になろうの場合のみソート（数値として）
                        if (isKakuyomu) {
                            list
                        } else {
                            list.sortedBy { it.toIntOrNull() ?: 0 }
                        }
                    }

                    if (redownloadTargets.isEmpty()) {
                        // エラーも欠番もない
                        withContext(Dispatchers.Main) {
                            updateProgress = 1f
                            updateMessage = "エラーや欠番は見つかりませんでした"
                            Toast.makeText(context, "エラーや欠番は見つかりませんでした", Toast.LENGTH_SHORT).show()
                            delay(1500)
                            isUpdating = false
                            showUpdateDialog = false
                        }
                        return@launch
                    }

                    // 確認ダイアログを表示
                    withContext(Dispatchers.Main) {
                        isUpdating = false
                        showUpdateDialog = false
                        showErrorFixConfirmDialog = true
                    }
                }
            } catch (e: Exception) {
                // 例外発生
                withContext(Dispatchers.Main) {
                    updateProgress = 1f
                    updateMessage = "エラー: ${e.message}"
                    Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_SHORT).show()
                    delay(1500)
                    isUpdating = false
                    showUpdateDialog = false
                }
            }
        }
    }

    // エラー修正の実行関数
    fun executeErrorFix() {
        val targetNovel = novel
        if (targetNovel == null) {
            Toast.makeText(context, "小説情報が読み込まれていません", Toast.LENGTH_SHORT).show()
            return
        }

        isUpdating = true
        updateProgress = 0.3f
        updateMessage = "エラーまたは欠番のあるエピソードを再取得中... (0/${redownloadTargets.size})"

        scope.launch {
            try {
                updateMessage = "他の更新処理の終了を待機しています..."
                val cancelled = NovelUpdateCoordinator.cancelAndWait(targetNovel.ncode)
                if (!cancelled) {
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "他の更新処理を停止できませんでした"
                        Toast.makeText(
                            context,
                            "進行中の更新を停止できませんでした。時間を置いて再度お試しください。",
                            Toast.LENGTH_SHORT
                        ).show()
                        delay(1500)
                        isUpdating = false
                    }
                    return@launch
                }

                val session = NovelUpdateCoordinator.awaitUpdateSlot(targetNovel.ncode)
                if (session == null) {
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "他の更新処理を待機中です"
                        Toast.makeText(context, "他の更新処理が進行中です。時間を置いて再度お試しください。", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                    }
                    return@launch
                }

                suspend fun handleCancellation() {
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "処理が中断されました"
                        Toast.makeText(context, "更新処理が中断されました", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                    }
                }

                try {
                    if (session.isCancelled()) {
                        handleCancellation()
                        return@launch
                    }

                    var successCount = 0
                    var failCount = 0

                    // カクヨム判定
                    val isKakuyomu = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.isKakuyomuNcode(targetNovel.ncode)

                    // カクヨムの場合は専用処理
                    if (isKakuyomu) {
                        val adapter = com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter()
                        val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(targetNovel.ncode)

                        for ((index, episodeNoStr) in redownloadTargets.withIndex()) {
                            if (session.isCancelled()) {
                                handleCancellation()
                                return@launch
                            }

                            val episodeNo = episodeNoStr.toIntOrNull()
                            if (episodeNo == null) {
                                android.util.Log.w("EpisodeListScreen", "無効なエピソード番号: $episodeNoStr")
                                failCount++
                                continue
                            }

                            // マッピングから実際のカクヨムEpisodeIDを取得
                            val kakuyomuEpisodeId = withContext(Dispatchers.IO) {
                                repository.getKakuyomuEpisodeId(targetNovel.ncode, episodeNo)
                            }

                            if (kakuyomuEpisodeId == null) {
                                android.util.Log.w("EpisodeListScreen", "マッピングが見つかりません: episode_no=$episodeNo")
                                failCount++
                                continue
                            }

                            try {
                                // 実際のIDで本文を取得
                                val episodeBody = adapter.fetchEpisodeContent(workId, kakuyomuEpisodeId)

                                // 取得失敗・エラー本文は保存しない（エラー文の本文保存による恒久化防止）
                                if (episodeBody.isEmpty() || episodeBody.startsWith("★HTMLページ読み込みエラー")) {
                                    android.util.Log.w("EpisodeListScreen", "本文取得失敗のため保存をスキップ: episode_no=$episodeNo")
                                    failCount++
                                } else {
                                    // 既存エピソードを取得して更新
                                    val existingEpisode = withContext(Dispatchers.IO) {
                                        repository.getEpisode(targetNovel.ncode, episodeNoStr)
                                    }

                                    if (existingEpisode != null) {
                                        val updatedEpisode = existingEpisode.copy(body = episodeBody)
                                        withContext(Dispatchers.IO) {
                                            repository.insertEpisode(updatedEpisode)
                                        }
                                        successCount++
                                        android.util.Log.d("EpisodeListScreen", "カクヨムエピソード修正成功: episode_no=$episodeNo, kakuyomu_id=$kakuyomuEpisodeId")
                                    } else {
                                        android.util.Log.w("EpisodeListScreen", "既存エピソードが見つかりません: episode_no=$episodeNo")
                                        failCount++
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("EpisodeListScreen", "カクヨムエピソード取得エラー: episode_no=$episodeNo", e)
                                failCount++
                            }

                            // 進捗を更新
                            val progress = (index + 1).toFloat() / redownloadTargets.size
                            updateProgress = 0.3f + (0.7f * progress)
                            updateMessage = "エラーエピソードを再取得中... (${index + 1}/${redownloadTargets.size})"
                        }
                    } else {
                        // 小説家になろうの場合は既存の処理
                        for ((index, episodeNo) in redownloadTargets.withIndex()) {
                            if (session.isCancelled()) {
                                handleCancellation()
                                return@launch
                            }

                            val episode = fetchEpisodeWithRetry(targetNovel.ncode, episodeNo, targetNovel.rating == 1, targetNovel.noveltype)

                            if (episode != null) {
                                // データベースに保存
                                withContext(Dispatchers.IO) {
                                    repository.insertEpisode(episode)
                                }
                                successCount++
                            } else {
                                failCount++
                            }

                            // サーバーに負荷をかけないように少し待機
                            delay(50)

                            // 進捗を更新
                            val progress = (index + 1).toFloat() / redownloadTargets.size
                            updateProgress = 0.3f + (0.7f * progress)
                            updateMessage = "エラーまたは欠番のあるエピソードを再取得中... (${index + 1}/${redownloadTargets.size})"
                        }
                    }

                    if (session.isCancelled()) {
                        handleCancellation()
                        return@launch
                    }

                    // 小説のtotal_epを更新（最大話数のみ取得 — 全話本文ロードを回避）
                    val maxEpisodeNo = repository.getMainMaxEpisodeNo(targetNovel.ncode) ?: 0

                    if (maxEpisodeNo > targetNovel.total_ep) {
                        val updatedNovel = targetNovel.copy(total_ep = maxEpisodeNo)
                        repository.updateNovel(updatedNovel)
                    }

                    // 処理結果の通知
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "完了: 成功${successCount}件、失敗${failCount}件"
                        Toast.makeText(context, "完了: 成功${successCount}件、失敗${failCount}件", Toast.LENGTH_SHORT).show()
                        delay(2000)
                        isUpdating = false
                    }
                } finally {
                    NovelUpdateCoordinator.finishUpdate(session)
                }
            } catch (e: Exception) {
                // 例外発生
                withContext(Dispatchers.Main) {
                    updateProgress = 1f
                    updateMessage = "エラー: ${e.message}"
                    Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_SHORT).show()
                    delay(1500)
                    isUpdating = false
                }
            }
        }
    }

    // タグ編集ダイアログ
    if (showTagEditDialog) {
        AlertDialog(
            onDismissRequest = { showTagEditDialog = false },
            title = { Text("タグを編集") },
            text = {
                Column {
                    OutlinedTextField(
                        value = mainTag,
                        onValueChange = { mainTag = it },
                        label = { Text("メインタグ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subTag,
                        onValueChange = { subTag = it },
                        label = { Text("サブタグ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // タグを更新する処理
                        novel?.let { currentNovel ->
                            val updatedNovel = currentNovel.copy(main_tag = mainTag, sub_tag = subTag)
                            scope.launch {
                                try {
                                    repository.updateNovel(updatedNovel)
                                    novel = updatedNovel
                                    Toast.makeText(context, "タグを更新しました", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e("EpisodeListScreen", "タグ更新エラー: ${e.message}")
                                    Toast.makeText(context, "タグの更新に失敗しました", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showTagEditDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagEditDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // 更新方法選択ダイアログ
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUpdating) showUpdateDialog = false },
            title = { Text("小説更新") },
            text = {
                if (isUpdating) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(progress = updateProgress)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(updateMessage)

                        if (updateProgress > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = updateProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(updateProgress * 100).toInt()}%",
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    Column {
                        Text("更新方法を選択してください")
                        Spacer(modifier = Modifier.height(16.dp))

                        // 更新
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedUpdateType == UpdateType.UPDATE,
                                    onClick = { selectedUpdateType = UpdateType.UPDATE }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedUpdateType == UpdateType.UPDATE,
                                onClick = { selectedUpdateType = UpdateType.UPDATE }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("更新", fontWeight = FontWeight.Bold)
                                Text(
                                    "新しいエピソードがあれば更新キューに追加します",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // 再取得
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedUpdateType == UpdateType.REDOWNLOAD,
                                    onClick = { selectedUpdateType = UpdateType.REDOWNLOAD }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedUpdateType == UpdateType.REDOWNLOAD,
                                onClick = { selectedUpdateType = UpdateType.REDOWNLOAD }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("再取得", fontWeight = FontWeight.Bold)
                                Text(
                                    "すべてのエピソードを削除して再取得します",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // エラー修正
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedUpdateType == UpdateType.FIX_ERRORS,
                                    onClick = { selectedUpdateType = UpdateType.FIX_ERRORS }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedUpdateType == UpdateType.FIX_ERRORS,
                                onClick = { selectedUpdateType = UpdateType.FIX_ERRORS }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("エラー修正", fontWeight = FontWeight.Bold)
                                Text(
                                    "エラーのあるエピソードのみを再取得します",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // 改稿チェック
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedUpdateType == UpdateType.CHECK_REVISION,
                                    onClick = { selectedUpdateType = UpdateType.CHECK_REVISION }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedUpdateType == UpdateType.CHECK_REVISION,
                                onClick = { selectedUpdateType = UpdateType.CHECK_REVISION }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("改稿チェック", fontWeight = FontWeight.Bold)
                                Text(
                                    "既存エピソードの改稿を確認して再取得します（小説家になろうのみ）",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 選択したタイプに応じた処理を実行
                        when (selectedUpdateType) {
                            UpdateType.UPDATE -> performUpdate()
                            UpdateType.REDOWNLOAD -> performRedownload()
                            UpdateType.FIX_ERRORS -> performErrorFix()
                            UpdateType.CHECK_REVISION -> performCheckRevision()
                        }
                    },
                    enabled = !isUpdating
                ) {
                    Text("実行")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateDialog = false },
                    enabled = !isUpdating
                ) {
                    Text("キャンセル")
                }
            }
        )
    }

    // 再取得後の確認ダイアログ
    if (showDownloadConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadConfirmDialog = false },
            title = { Text("エピソードを取得") },
            text = {
                Text("エピソードを削除しました。エピソードを今すぐ取得しますか？（時間がかかります）")
            },
            confirmButton = {
                Button(onClick = {
                    showDownloadConfirmDialog = false
                    // エピソードを取得する処理を実行
                    performDownloadEpisodes(tempGeneralAllNo)
                }) {
                    Text("はい")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDownloadConfirmDialog = false
                    // 更新キューに追加するだけの処理を実行
                    performAddToUpdateQueue(tempGeneralAllNo)
                }) {
                    Text("更新キューに追加のみ")
                }
            }
        )
    }

    // エラー修正確認ダイアログ
    if (showErrorFixConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showErrorFixConfirmDialog = false },
            title = { Text("エラー修正") },
            text = {
                Text(
                    "エラーのあるエピソード: ${errorEpisodeCount}件\n" +
                            "欠番エピソード: ${missingEpisodeCount}件\n\n" +
                            "合計${redownloadTargets.size}件のエピソードを再取得しますか？"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showErrorFixConfirmDialog = false
                    executeErrorFix()
                }) {
                    Text("再取得する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorFixConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (showDeleteNovelDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteNovelDialog = false },
            title = { Text("小説を削除") },
            text = {
                Text("この小説と関連するデータを削除しますか？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteNovelDialog = false
                        val currentNovel = novel
                        if (currentNovel == null) {
                            Toast.makeText(context, "小説情報が読み込まれていません", Toast.LENGTH_SHORT).show()
                        } else {
                            isDeletingNovel = true
                            scope.launch {
                                try {
                                    repository.deleteNovelWithRelations(currentNovel)
                                    Toast.makeText(context, "小説を削除しました", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } catch (e: Exception) {
                                    Log.e("EpisodeListScreen", "小説削除エラー: ${e.message}", e)
                                    Toast.makeText(context, "小説の削除に失敗しました: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isDeletingNovel = false
                                }
                            }
                        }
                    }
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteNovelDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (isDeletingNovel) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("処理中") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("小説を削除しています...")
                }
            },
            confirmButton = {}
        )
    }

    // 画面の構成
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = novel?.title ?: "小説詳細",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,  // 1行に制限
                            overflow = TextOverflow.Ellipsis  // テキストが長い場合は省略記号
                        )
                        novel?.let {
                            Text(
                                "作者: ${it.author}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            novel?.let {
                                val newValue = if (it.is_favorite == 1) 0 else 1
                                scope.launch {
                                    repository.updateFavoriteStatus(it.ncode, newValue)
                                    novel = it.copy(is_favorite = newValue)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (novel?.is_favorite == 1) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (novel?.is_favorite == 1) "お気に入りから削除" else "お気に入りに追加",
                            tint = if (novel?.is_favorite == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { isMenuExpanded = true },
                            enabled = novel != null
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "その他の操作")
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("小説を削除") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    isMenuExpanded = false
                                    showDeleteNovelDialog = true
                                },
                                enabled = !isDeletingNovel
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // しおりから読む
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = lastRead != null && !isNovelUpdating) {
                                if (lastRead != null && !isNovelUpdating) {
                                    onEpisodeClick(ncode, lastRead!!.episode_no.toString())
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "しおりから読む",
                            tint = if (lastRead != null && !isNovelUpdating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "しおりから読む",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lastRead != null && !isNovelUpdating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // しおりを削除
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = lastRead != null && !isNovelUpdating) {
                                if (lastRead != null && !isNovelUpdating) {
                                    scope.launch {
                                        try {
                                            repository.deleteLastRead(ncode)
                                            lastRead = null
                                            Toast.makeText(context, "しおりを削除しました", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("EpisodeListScreen", "しおり削除エラー: ${e.message}")
                                            Toast.makeText(context, "しおりの削除に失敗しました", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.BookmarkRemove,
                            contentDescription = "しおりを削除",
                            tint = if (lastRead != null && !isNovelUpdating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "しおりを削除",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lastRead != null && !isNovelUpdating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // 小説を更新
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isNovelUpdating) {
                                if (!isNovelUpdating) {
                                    // 更新処理開始
                                    startUpdateProcess()
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "小説を更新",
                            tint = if (!isNovelUpdating) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "小説を更新",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isNovelUpdating) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // タグを編集
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isNovelUpdating) {
                                if (!isNovelUpdating) {
                                    showTagEditDialog = true
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "タグを編集",
                            tint = if (!isNovelUpdating) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "タグを編集",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isNovelUpdating) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // 作者ページを開く
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = novel != null && !isNovelUpdating) {
                                if (!isNovelUpdating) {
                                    novel?.let { novelEntity ->
                                        scope.launch {
                                            if (authorPageNeedsFetch(novelEntity)) {
                                                Toast.makeText(context, "作者情報を取得中...", Toast.LENGTH_SHORT).show()
                                            }
                                            val result = withContext(Dispatchers.IO) {
                                                resolveAuthorPageUrl(novelEntity, repository)
                                            }
                                            if (result != null) {
                                                // 新規取得したIDはローカル状態にも反映（次回はネットワーク不要）
                                                result.updatedNovel?.let { novel = it }
                                                onAuthorClick(result.url)
                                            } else {
                                                Toast.makeText(context, "作者ページを取得できませんでした", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "作者",
                            tint = if (novel != null && !isNovelUpdating) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "作者",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (novel != null && !isNovelUpdating) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // 更新中の場合はブロック画面を表示
        if (isNovelUpdating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "目次を更新中です",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "しばらくお待ちください",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "エピソードの取得・更新処理が完了するまで\n目次にはアクセスできません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // 通常の画面表示（更新中でない場合）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 小説情報のヘッダー部分 - 折りたたみ機能追加
                novel?.let { novel ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // 折りたたみボタンとタイトル
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "あらすじとタグ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (isDescriptionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isDescriptionExpanded) "折りたたむ" else "展開する"
                                )
                            }

                            // 折りたたみ部分の内容
                            if (isDescriptionExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "タイトル: ${novel.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // あらすじ
                                Text(
                                    text = "あらすじ",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = novel.Synopsis,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // タグ
                                if (novel.main_tag.isNotEmpty() || novel.sub_tag.isNotEmpty()) {
                                    Text(
                                        text = "タグ",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = buildString {
                                            append(novel.main_tag)
                                            if (novel.sub_tag.isNotEmpty()) {
                                                if (novel.main_tag.isNotEmpty()) {
                                                    append(", ")
                                                }
                                                append(novel.sub_tag)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            // 最終更新日と総話数（常に表示）
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "最終更新: ${novel.last_update_date}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "全${novel.total_ep}話",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            // 最後に読んだ情報（常に表示）
                            lastRead?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "しおり: ${it.episode_no}話 (${it.date})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // エピソード一覧のヘッダー
                Text(
                    text = "エピソード一覧",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // 目次復帰バナー（mappingテーブルにデータがあるが episodesが不足している場合）
                if (showTocRecoveryBanner) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "目次が不完全です（DB: ${episodes.size}件 / マッピング: ${tocRecoveryMappings.size}件）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "DL中断などによりエピソード一覧が保存されていません。マッピング情報から目次を再構築できます。再構築後に「エラー修正」で本文を再取得してください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { performTocRecovery() },
                                    enabled = !isRecoveringToc,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isRecoveringToc) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("目次を再構築", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showTocRecoveryBanner = false },
                                    enabled = !isRecoveringToc,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("無視", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // エピソード一覧
                if (episodes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showTocRecoveryBanner) {
                            Text(
                                text = "上のバナーから目次を再構築できます",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(episodes) { episode ->
                            val isRead = lastRead != null &&
                                    episode.episode_no.toIntOrNull()?.let { it <= lastRead!!.episode_no } ?: false

                            EpisodeItem(
                                episode = episode,
//                            isRead = isRead,
                                onClick = { onEpisodeClick(ncode, episode.episode_no) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun EpisodeItem(
    episode: com.shunlight_library.novel_reader.data.entity.EpisodeMeta,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 既読/未読アイコン - episode.is_read を使用
        Icon(
            imageVector = if (episode.is_read == 1) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (episode.is_read == 1) "既読" else "未読",
            tint = if (episode.is_read == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )

        // しおりアイコンを追加 - episode.is_bookmark を使用
        if (episode.is_bookmark == 1) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "しおり",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // エピソード情報
        Column {
            Text(
                text = "${episode.episode_no}. ${episode.e_title}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (episode.is_read == 1) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (episode.is_read == 1) FontWeight.Normal else FontWeight.Bold
            )

            if (episode.update_time.isNotEmpty()) {
                Text(
                    text = "更新: ${episode.update_time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (episode.is_read == 1) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}


// エピソードを取得する関数（スクレイピング）
// UpdateInfoScreen.kt
// エピソードを取得する関数（スクレイピング）
//private suspend fun fetchEpisode(ncode: String, episodeNo: Int, isR18: Boolean): EpisodeEntity? {
//    return withContext(Dispatchers.IO) {
//        try {
//            val baseUrl = if (isR18) {
//                "https://novel18.syosetu.com"
//            } else {
//                "https://ncode.syosetu.com"
//            }
//
//            val url = "$baseUrl/$ncode/$episodeNo/"
//
//            val doc = Jsoup.connect(url)
//                .userAgent("Mozilla/5.0")
//                .timeout(30000)
//                .get()
//
//            val title = doc.select("h1.p-novel__title.p-novel__title--rensai").text()
//            val bodyElements = doc.select("div.p-novel__body div.js-novel-text p")
//
//            // 小説ダウンロード時の不要なタグ挿入問題修正
//            // div-div間のみに区切り線を追加するよう修正
//            val body = StringBuilder()
//
//            if (bodyElements.isNotEmpty()) {
//                bodyElements.forEachIndexed { index, element ->
//                    body.append("<p>${element.html()}</p>")
//
//                    // 最後の要素でない場合にのみ改行を追加
//                    if (index < bodyElements.size - 1) {
//                        // divタグの終わりと次のdivタグの始まりを検出
//                        val currentHtml = element.html()
//                        val nextHtml = bodyElements[index + 1].html()
//
//                        // divタグの終わりと次のdivタグの始まりを検出
//                        val isCurrentDivEnd = currentHtml.trim().endsWith("</div>")
//                        val isNextDivStart = nextHtml.trim().startsWith("<div")
//
//                        if (isCurrentDivEnd && isNextDivStart) {
//                            // div-div間のみに区切り線を追加
//                            body.append("\n<p></p><p>-----</p><p></p>\n")
//                        } else {
//                            // 通常の改行のみ
//                            body.append("\n")
//                        }
//                    }
//                }
//            }
//
//            if (title.isNotEmpty() && body.isNotEmpty()) {
//                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
//
//                EpisodeEntity(
//                    ncode = ncode,
//                    episode_no = episodeNo.toString(),
//                    body = body.toString(),
//                    e_title = title,
//                    update_time = currentDate,
//                    is_read = false,
//                    is_bookmark = false
//                )
//            } else {
//                null
//            }
//        } catch (e: Exception) {
//            Log.e("UpdateInfoScreen", "エピソード取得エラー: $episodeNo", e)
//            null
//        }
//    }
//}

/** 作者ページ解決結果（userid を新規取得した場合は updatedNovel に反映済みエンティティが入る） */
private data class AuthorPageResult(val url: String, val updatedNovel: NovelDescEntity?)

/** 作者ページURLの解決にネットワークアクセスが必要かどうか */
private fun authorPageNeedsFetch(novel: NovelDescEntity): Boolean {
    val userid = novel.userid
    return when {
        novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> userid.isNullOrEmpty()
        novel.rating == 1 -> userid.isNullOrEmpty() || !userid.startsWith("x")
        else -> userid.isNullOrEmpty()
    }
}

/**
 * 作者ページURLを解決する。
 * - カクヨム: userid（スクリーンネーム）から https://kakuyomu.jp/users/{name}。
 *   未保存なら作品ページから取得してDBへ保存。
 * - なろうR18: useridにキャッシュ済みのxid（x始まり）から https://xmypage.syosetu.com/{xid}/。
 *   未取得なら作品ページからxidをスクレイプしてDBへ保存（novel18apiはuseridを返さず、
 *   数値useridではxmypageは404になるため）。
 * - なろう一般: userid から https://mypage.syosetu.com/{userid}/。未保存ならAPIから取得して保存。
 */
private suspend fun resolveAuthorPageUrl(
    novel: NovelDescEntity,
    repository: com.shunlight_library.novel_reader.data.repository.NovelRepository
): AuthorPageResult? {
    val userid = novel.userid
    return when {
        novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
            if (!userid.isNullOrEmpty()) {
                AuthorPageResult("https://kakuyomu.jp/users/$userid", null)
            } else {
                KakuyomuAdapter().fetchAuthorUserName(novel.ncode)?.let { name ->
                    val updated = novel.copy(userid = name)
                    repository.updateNovel(updated)
                    AuthorPageResult("https://kakuyomu.jp/users/$name", updated)
                }
            }
        }
        novel.rating == 1 -> {
            if (!userid.isNullOrEmpty() && userid.startsWith("x")) {
                AuthorPageResult("https://xmypage.syosetu.com/$userid/", null)
            } else {
                NovelApiUtils.fetchR18AuthorId(novel.ncode)?.let { xid ->
                    val updated = novel.copy(userid = xid)
                    repository.updateNovel(updated)
                    AuthorPageResult("https://xmypage.syosetu.com/$xid/", updated)
                }
            }
        }
        else -> {
            if (!userid.isNullOrEmpty()) {
                AuthorPageResult("https://mypage.syosetu.com/$userid/", null)
            } else {
                fetchNovelInfo(novel.ncode, isR18 = false)?.userid?.let { fetched ->
                    val updated = novel.copy(userid = fetched)
                    repository.updateNovel(updated)
                    AuthorPageResult("https://mypage.syosetu.com/$fetched/", updated)
                }
            }
        }
    }
}
