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
import com.shunlight_library.novel_reader.api.NovelApiUtils.fetchEpisodeWithRetry
import com.shunlight_library.novel_reader.api.NovelApiUtils.fetchNovelInfo
import android.content.Intent
import com.shunlight_library.novel_reader.service.UpdateService

enum class UpdateType {
    UPDATE,      // 更新（新しいエピソードのみチェック）
    REDOWNLOAD,  // 再取得（すべて削除して再取得）
    FIX_ERRORS   // エラー修正（エラーや欠番のあるエピソードのみ修正）
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
    var episodes by remember { mutableStateOf<List<EpisodeEntity>>(emptyList()) }
    var lastRead by remember { mutableStateOf<LastReadNovelEntity?>(null) }

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
    }

    LaunchedEffect(ncode) {
        // エピソード一覧の取得（Flow型なのでLaunchedEffectで直接collect可能）
        repository.getEpisodesByNcode(ncode).collect { episodeList ->
            // エピソードリストを数値順にソート
            episodes = episodeList.sortedWith(compareBy {
                it.episode_no.toIntOrNull() ?: Int.MAX_VALUE
            })
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

                    val hasUpdate = kakuyomuAdapter.checkForUpdates(workId, targetNovel.total_ep)

                    if (hasUpdate) {
                        updateProgress = 0.6f
                        updateMessage = "詳細情報を取得中（マッピング情報含む）..."

                        // マッピング情報を含めて取得（改善版メソッド使用）
                        val result = kakuyomuAdapter.fetchNovelWithEpisodesIncludingMappings(workId)
                        val updatedNovelDesc = result.novelDesc
                        val episodes = result.episodes
                        val mappings = result.episodeMappings

                        newGeneralAllNo = episodes.size
                        newUpdatedAt = updatedNovelDesc.updated_at

                        // 小説情報を更新
                        val updatedNovel = targetNovel.copy(
                            general_all_no = newGeneralAllNo,
                            updated_at = newUpdatedAt,
                            title = updatedNovelDesc.title,
                            author = updatedNovelDesc.author,
                            Synopsis = updatedNovelDesc.Synopsis,
                            main_tag = updatedNovelDesc.main_tag,
                            sub_tag = updatedNovelDesc.sub_tag,
                            last_update_date = updatedNovelDesc.last_update_date
                        )
                        repository.updateNovel(updatedNovel)

                        // 既存エピソードを取得
                        val existingEpisodes = repository.getEpisodesByNcode(targetNovel.ncode).first()
                        val existingEpisodeNos = existingEpisodes.map { it.episode_no }.toSet()

                        // 新しいエピソードのみをフィルタリング
                        val newEpisodes = episodes.filter { it.episode_no !in existingEpisodeNos }

                        if (newEpisodes.isNotEmpty()) {
                            updateProgress = 0.8f
                            updateMessage = "新しいエピソードとマッピングを保存中... (${newEpisodes.size}話)"

                            // 新しいエピソードに対応するマッピングのみを抽出
                            val newMappings = mappings.filter { (episodeNo, _) ->
                                newEpisodes.any { it.episode_no == episodeNo.toString() }
                            }

                            // 新しいエピソードとマッピングを保存
                            withContext(Dispatchers.IO) {
                                repository.insertKakuyomuEpisodesWithMappings(newEpisodes, newMappings)
                            }

                            android.util.Log.d("EpisodeListScreen", "カクヨム更新: 新規エピソード${newEpisodes.size}話, マッピング${newMappings.size}件を保存")
                        }

                        // 更新キューに追加
                        val updateQueue = UpdateQueueEntity(
                            ncode = targetNovel.ncode,
                            total_ep = targetNovel.total_ep,
                            general_all_no = newGeneralAllNo,
                            update_time = newUpdatedAt
                        )
                        repository.insertUpdateQueue(updateQueue)

                        updateProgress = 1f
                        updateMessage = "更新を確認しました。新規${newEpisodes.size}話を保存し、更新キューに追加しました。"
                        Toast.makeText(context, "更新を確認しました (新規${newEpisodes.size}話)", Toast.LENGTH_SHORT).show()
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
                        // カクヨムの場合は一括取得（マッピング情報付き）
                        updateMessage = "カクヨムからエピソードを取得中..."
                        val adapter = com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter()
                        val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)

                        // マッピング情報を含めて取得（改善版メソッド使用）
                        val result = adapter.fetchNovelWithEpisodesIncludingMappings(workId)
                        val episodes = result.episodes
                        val mappings = result.episodeMappings

                        if (session.isCancelled()) {
                            handleCancellation()
                            return@launch
                        }

                        // エピソードとマッピングを一括保存（改善版メソッド使用）
                        updateMessage = "エピソードとマッピングを保存中... (${episodes.size}話)"
                        withContext(Dispatchers.IO) {
                            repository.insertKakuyomuEpisodesWithMappings(episodes, mappings)
                        }

                        successCount = episodes.size
                        updateProgress = 1.0f
                        updateMessage = "保存完了: ${episodes.size}話"

                        android.util.Log.d("EpisodeListScreen", "カクヨムエピソード一括保存完了: ${successCount}話, マッピング: ${mappings.size}件")
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
                // エピソードを取得
                novel?.let {
                    val episodesList = repository.getEpisodesByNcode(it.ncode).first()

                    // エラーのあるエピソードを見つける
                    val errorEpisodes = episodesList.filter { episode ->
                        episode.body.isEmpty() || episode.e_title.isEmpty()
                    }

                    val generalAllNoValue: Int

                    if (targetNovel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                        // カクヨムの場合、HTMLスクレイピングで取得
                        updateProgress = 0.2f
                        updateMessage = "HTMLスクレイピングで最新情報を確認中..."

                        val adapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU)
                        val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(it.ncode)

                        val (updatedNovelDesc, allEpisodes) = adapter.fetchNovelWithEpisodes(workId)
                        generalAllNoValue = allEpisodes.size

                        // 小説情報を更新
                        val updatedNovel = it.copy(
                            general_all_no = generalAllNoValue,
                            updated_at = updatedNovelDesc.updated_at,
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

                    // 小説のtotal_epを更新
                    val updatedEpisodes = repository.getEpisodesByNcode(targetNovel.ncode).first()
                    val maxEpisodeNo = updatedEpisodes.mapNotNull { episode -> episode.episode_no.toIntOrNull() }.maxOrNull() ?: 0

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
                                val newValue = !it.is_favorite
                                scope.launch {
                                    repository.updateFavoriteStatus(it.ncode, newValue)
                                    novel = it.copy(is_favorite = newValue)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (novel?.is_favorite == true) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (novel?.is_favorite == true) "お気に入りから削除" else "お気に入りに追加",
                            tint = if (novel?.is_favorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                            .clickable(enabled = lastRead != null) {
                                if (lastRead != null) {
                                    onEpisodeClick(ncode, lastRead!!.episode_no.toString())
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "しおりから読む",
                            tint = if (lastRead != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "しおりから読む",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lastRead != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // しおりを削除
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = lastRead != null) {
                                if (lastRead != null) {
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
                            tint = if (lastRead != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "しおりを削除",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lastRead != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // 小説を更新
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                // 更新処理開始
                                startUpdateProcess()
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "小説を更新")
                        Text("小説を更新", style = MaterialTheme.typography.labelSmall)
                    }

                    // タグを編集
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showTagEditDialog = true
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "タグを編集")
                        Text("タグを編集", style = MaterialTheme.typography.labelSmall)
                    }

                    // 作者ページを開く
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = novel != null) {
                                novel?.let { novelEntity ->
                                    val userId = novelEntity.userid
                                    if (userId != null) {
                                        val url = if (novelEntity.rating == 1) {
                                            "https://xmypage.syosetu.com/$userId/"
                                        } else {
                                            "https://mypage.syosetu.com/$userId/"
                                        }
                                        onAuthorClick(url)
                                    } else {
                                        Toast.makeText(context, "すでになろうにいません", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "作者")
                        Text("作者", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    ) { innerPadding ->
        // 小説の基本情報表示
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

            // エピソード一覧
            if (episodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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

// EpisodeListScreen.kt - EpisodeItem の修正

@Composable
fun EpisodeItem(
    episode: EpisodeEntity,
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
            imageVector = if (episode.is_read) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (episode.is_read) "既読" else "未読",
            tint = if (episode.is_read) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )

        // しおりアイコンを追加 - episode.is_bookmark を使用
        if (episode.is_bookmark) {
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
                color = if (episode.is_read) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (episode.is_read) FontWeight.Normal else FontWeight.Bold
            )

            if (episode.update_time.isNotEmpty()) {
                Text(
                    text = "更新: ${episode.update_time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (episode.is_read) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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