/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Shows detailed update information.
 */
package com.shunlight_library.novel_reader

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.api.NovelApiUtils
import com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateInfoScreen(
    onBack: () -> Unit,
    onNovelClick: (String) -> Unit
) {
    val repository = NovelReaderApplication.getRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }

    // 状態変数
    var updateQueue by remember { mutableStateOf<List<UpdateQueueEntity>>(emptyList()) }
    var novels by remember { mutableStateOf<Map<String, NovelDescEntity>>(emptyMap()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showErrorFixConfirmDialog by remember { mutableStateOf(false) }
    var errorNovelCount by remember { mutableStateOf(0) }
    var errorEpisodeCount by remember { mutableStateOf(0) }
    var missingEpisodeCount by remember { mutableStateOf(0) }
    var redownloadTargets by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    // 進捗表示用の変数
    var isSyncing by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf(0f) }
    var syncStep by remember { mutableStateOf("") }
    var syncMessage by remember { mutableStateOf("") }
    var currentCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    // 欠落修正オプション
    var showErrorFixOptionsDialog by remember { mutableStateOf(false) }
    var optDateFilterEnabled by remember { mutableStateOf(false) }
    var optDateStart by remember { mutableStateOf("") }
    var optDateEnd by remember { mutableStateOf("") }
    var optExcludeShort by remember { mutableStateOf(false) }
    var optExcludeCompleted by remember { mutableStateOf(false) }
    var optSelectedSubSites by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var optCheckIllustration by remember { mutableStateOf(false) }
    // データ取得
    LaunchedEffect(key1 = Unit) {
        repository.allUpdateQueue.collect { queueList ->
            updateQueue = queueList

            // N+1クエリ対策：関連する小説情報を一括取得
            val ncodes = queueList.map { it.ncode }
            val novelsList = repository.getNovelsByNcodes(ncodes)
            novels = novelsList.associateBy { it.ncode }
        }
    }
    // 欠落修正オプションダイアログ
    if (showErrorFixOptionsDialog) {
        var tempDateFilter by remember { mutableStateOf(optDateFilterEnabled) }
        var tempDateStart by remember { mutableStateOf(optDateStart) }
        var tempDateEnd by remember { mutableStateOf(optDateEnd) }
        var tempExcludeShort by remember { mutableStateOf(optExcludeShort) }
        var tempExcludeCompleted by remember { mutableStateOf(optExcludeCompleted) }
        var tempSelectedSubSites by remember { mutableStateOf(optSelectedSubSites) }
        var tempCheckIllustration by remember { mutableStateOf(optCheckIllustration) }

        AlertDialog(
            onDismissRequest = { showErrorFixOptionsDialog = false },
            title = { Text("欠落修正オプション") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(4.dp)) {
                    // 期間指定
                    Text("期間指定", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { tempDateFilter = !tempDateFilter }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = tempDateFilter, onCheckedChange = { tempDateFilter = it })
                        Text("登録日で絞り込む", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (tempDateFilter) {
                        OutlinedTextField(
                            value = tempDateStart,
                            onValueChange = { tempDateStart = it },
                            label = { Text("開始日 (yyyy-MM-dd)") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tempDateEnd,
                            onValueChange = { tempDateEnd = it },
                            label = { Text("終了日 (yyyy-MM-dd)") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            singleLine = true
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 除外設定
                    Text("除外設定", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { tempExcludeShort = !tempExcludeShort }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = tempExcludeShort, onCheckedChange = { tempExcludeShort = it })
                        Text("短編小説を除外", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { tempExcludeCompleted = !tempExcludeCompleted }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = tempExcludeCompleted, onCheckedChange = { tempExcludeCompleted = it })
                        Text("完結済みを除外", modifier = Modifier.padding(start = 8.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 媒体指定
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("媒体指定", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { tempSelectedSubSites = emptySet() }) {
                            Text("全選択", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    SubSiteConst.labels.forEach { (siteId, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val cur = tempSelectedSubSites.toMutableSet()
                                if (siteId in cur) cur.remove(siteId) else cur.add(siteId)
                                tempSelectedSubSites = cur
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = tempSelectedSubSites.isEmpty() || siteId in tempSelectedSubSites,
                                onCheckedChange = { checked ->
                                    val cur = tempSelectedSubSites.toMutableSet()
                                    if (checked) cur.add(siteId) else cur.remove(siteId)
                                    tempSelectedSubSites = cur
                                }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 特殊検知
                    Text("特殊検知", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { tempCheckIllustration = !tempCheckIllustration }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = tempCheckIllustration, onCheckedChange = { tempCheckIllustration = it })
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("挿絵エラー検知")
                            Text(
                                "挿絵タグあり・未キャッシュのエピソードを検知",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    // オプションを確定して保存
                    optDateFilterEnabled = tempDateFilter
                    optDateStart = tempDateStart
                    optDateEnd = tempDateEnd
                    optExcludeShort = tempExcludeShort
                    optExcludeCompleted = tempExcludeCompleted
                    optSelectedSubSites = tempSelectedSubSites
                    optCheckIllustration = tempCheckIllustration
                    showErrorFixOptionsDialog = false

                    // スキャン開始
                    isSyncing = true
                    syncProgress = 0f
                    syncStep = "エラーチェック"
                    syncMessage = "評価0-2の小説を検索中..."
                    errorNovelCount = 0
                    errorEpisodeCount = 0
                    missingEpisodeCount = 0
                    redownloadTargets = emptyMap()

                    val capturedDateFilter = tempDateFilter
                    val capturedDateStart = tempDateStart
                    val capturedDateEnd = tempDateEnd
                    val capturedExcludeShort = tempExcludeShort
                    val capturedExcludeCompleted = tempExcludeCompleted
                    val capturedSubSites = tempSelectedSubSites
                    val capturedCheckIllustration = tempCheckIllustration

                    scope.launch {
                        try {
                            // 評価0-2の小説を取得
                            var scanNovels = repository.getNovelsForUpdate().filter { it.rating in 0..2 }

                            // 登録日フィルター
                            if (capturedDateFilter) {
                                if (capturedDateStart.isNotEmpty()) {
                                    scanNovels = scanNovels.filter { it.registered_at >= capturedDateStart }
                                }
                                if (capturedDateEnd.isNotEmpty()) {
                                    scanNovels = scanNovels.filter { it.registered_at <= capturedDateEnd + " 23:59:59" }
                                }
                            }
                            // 短編除外
                            if (capturedExcludeShort) {
                                scanNovels = scanNovels.filter { it.noveltype != 2 }
                            }
                            // 完結除外
                            if (capturedExcludeCompleted) {
                                scanNovels = scanNovels.filter { it.end_flag != 1 }
                            }
                            // 媒体フィルター
                            if (capturedSubSites.isNotEmpty()) {
                                scanNovels = scanNovels.filter { novel ->
                                    val subSite = if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) SubSiteConst.KAKUYOMU
                                    else novel.sub_site.takeIf { it > 0 } ?: SubSiteConst.SYOSETU
                                    subSite in capturedSubSites
                                }
                            }

                            if (scanNovels.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    syncProgress = 1f
                                    syncMessage = "条件に一致する小説が見つかりませんでした"
                                    Toast.makeText(context, "条件に一致する小説が見つかりませんでした", Toast.LENGTH_SHORT).show()
                                    delay(1500)
                                    isSyncing = false
                                }
                                return@launch
                            }

                            totalCount = scanNovels.size
                            val novelErrorMap = mutableMapOf<String, List<String>>()
                            var totalErrorEps = 0
                            var totalMissingEps = 0
                            var novelsWithErrors = 0

                            // なろう作品はAPI一括取得（ncode OR検索）でgeneralAllNoをまとめて取得し、
                            // スキャン中の 1作品=1リクエスト（N+1）を回避する（全更新確認と同じ方式）
                            val syosetuScanInfoMap = HashMap<String, NovelApiUtils.NovelApiInfo>()
                            try {
                                val syosetuScanList = scanNovels.filter { it.site_type != NovelSiteAdapter.SITE_TYPE_KAKUYOMU }
                                syosetuScanInfoMap.putAll(NovelApiUtils.fetchNovelInfoBatch(syosetuScanList.filter { it.rating != 1 }.map { it.ncode }, isR18 = false))
                                syosetuScanInfoMap.putAll(NovelApiUtils.fetchNovelInfoBatch(syosetuScanList.filter { it.rating == 1 }.map { it.ncode }, isR18 = true))
                            } catch (e: Exception) {
                                Log.e("UpdateInfo", "なろう一括取得エラー（個別取得にフォールバック）: ${e.message}")
                            }

                            scanNovels.forEachIndexed { index, novel ->
                                syncProgress = 0.3f + (0.7f * index.toFloat() / scanNovels.size)
                                currentCount = index + 1
                                syncMessage = "「${novel.title}」のエラーをチェック中... (${index + 1}/${scanNovels.size})"

                                try {
                                    val episodes = repository.getEpisodesByNcode(novel.ncode).first()

                                    val errorEpisodes = episodes.filter {
                                        it.body.isEmpty() || it.e_title.isEmpty()
                                    }

                                    val generalAllNoValue: Int
                                    if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                                        // 軽量サマリー取得（全話本文DLせずに話数とメタデータのみ確認）
                                        val adapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU) as KakuyomuAdapter
                                        val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(novel.ncode)
                                        val summary = adapter.fetchUpdateSummary(workId)
                                        generalAllNoValue = summary.latestEpisodeCount
                                        val updatedNovelDesc = summary.novelDesc
                                        val updatedNovel = novel.copy(
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
                                        // 一括取得結果を優先。取れなかった作品（検索除外・通信失敗）のみ個別取得にフォールバック
                                        val info = syosetuScanInfoMap[novel.ncode.lowercase()]
                                            ?: NovelApiUtils.fetchNovelInfo(novel.ncode, novel.rating == 1)
                                        generalAllNoValue = info?.generalAllNo ?: novel.general_all_no
                                    }

                                    val isKakuyomu = novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU
                                    val missingEpisodes: List<String>
                                    if (isKakuyomu) {
                                        missingEpisodes = emptyList()
                                    } else {
                                        val episodeNumberMap = episodes.associate { ep ->
                                            (ep.episode_no.toIntOrNull() ?: 0) to ep.episode_no
                                        }
                                        val maxEpisodeNo = episodeNumberMap.keys.maxOrNull() ?: 0
                                        val checkRangeMax = maxOf(generalAllNoValue, maxEpisodeNo)
                                        missingEpisodes = (1..checkRangeMax).filter { !episodeNumberMap.containsKey(it) }.map { it.toString() }
                                    }

                                    val errorEpisodeNumbers = errorEpisodes.map { it.episode_no }

                                    // 挿絵エラー検知
                                    val illustrationErrors: List<String>
                                    if (capturedCheckIllustration) {
                                        val imgSrcRegex = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                                        illustrationErrors = episodes.filter { ep ->
                                            if (!ep.body.contains("<img", ignoreCase = true)) return@filter false
                                            imgSrcRegex.findAll(ep.body).any { it.groupValues[1].startsWith("http") }
                                        }.map { it.episode_no }
                                    } else {
                                        illustrationErrors = emptyList()
                                    }

                                    val targets = (errorEpisodeNumbers + missingEpisodes + illustrationErrors).distinct().let { list ->
                                        if (isKakuyomu) list else list.sortedBy { it.toIntOrNull() ?: 0 }
                                    }

                                    if (targets.isNotEmpty()) {
                                        novelErrorMap[novel.ncode] = targets
                                        totalErrorEps += errorEpisodes.size
                                        totalMissingEps += missingEpisodes.size
                                        novelsWithErrors++
                                    }

                                    delay(100)
                                } catch (e: Exception) {
                                    Log.e("UpdateInfo", "小説のエラーチェックに失敗: ${novel.ncode} - ${e.message}")
                                }
                            }

                            if (novelErrorMap.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    syncProgress = 1f
                                    syncMessage = "エラーや欠番は見つかりませんでした"
                                    Toast.makeText(context, "エラーや欠番は見つかりませんでした", Toast.LENGTH_SHORT).show()
                                    delay(1500)
                                    isSyncing = false
                                }
                                return@launch
                            }

                            errorNovelCount = novelsWithErrors
                            errorEpisodeCount = totalErrorEps
                            missingEpisodeCount = totalMissingEps
                            redownloadTargets = novelErrorMap

                            withContext(Dispatchers.Main) {
                                isSyncing = false
                                showErrorFixConfirmDialog = true
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                syncProgress = 1f
                                syncMessage = "エラー: ${e.message}"
                                Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_SHORT).show()
                                delay(1500)
                                isSyncing = false
                            }
                        }
                    }
                }) { Text("スキャン開始") }
            },
            dismissButton = {
                TextButton(onClick = { showErrorFixOptionsDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showErrorFixConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showErrorFixConfirmDialog = false },
            title = { Text("評価0-2の小説エラー修正") },
            text = {
                Text(
                    "エラーが見つかりました:\n" +
                            "対象小説: ${errorNovelCount}冊\n" +
                            "エラーのあるエピソード: ${errorEpisodeCount}件\n" +
                            "欠番エピソード: ${missingEpisodeCount}件\n\n" +
                            "これらのエピソードを再取得しますか？"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showErrorFixConfirmDialog = false
                    isSyncing = true
                    syncProgress = 0f
                    syncStep = "エラー修正"
                    syncMessage = "エピソードを修正中..."
                    currentCount = 0

                    val novelList = redownloadTargets.keys.toList()
                    totalCount = redownloadTargets.values.sumOf { it.size } // 総エピソード数

                    scope.launch {
                        try {
                            var processedCount = 0
                            var successCount = 0
                            var failCount = 0

                            // 各小説について処理
                            for (ncode in novelList) {
                                // 小説情報を取得
                                val novel = repository.getNovelByNcode(ncode)
                                if (novel == null) {
                                    continue
                                }

                                syncMessage = "「${novel.title}」のエピソードを修正中..."

                                // この小説の再取得対象エピソード
                                val targets = redownloadTargets[ncode] ?: continue

                                // 各エピソードを再取得
                                for (episodeNo in targets) {
                                    try {
                                        // NovelApiUtils.fetchEpisodeを使用
                                        val episode = NovelApiUtils.fetchEpisodeWithRetry(
                                            novel.ncode,
                                            episodeNo,
                                            novel.rating == 1,
                                            novel.noveltype
                                        )

                                        if (episode != null) {
                                            // データベースに保存
                                            repository.insertEpisode(episode)
                                            successCount++
                                        } else {
                                            failCount++
                                        }
                                    } catch (e: Exception) {
                                        Log.e("UpdateInfo", "エピソード取得エラー: $ncode-$episodeNo - ${e.message}")
                                        failCount++
                                    }

                                    // 進捗更新
                                    processedCount++
                                    currentCount = processedCount
                                    syncProgress = processedCount.toFloat() / totalCount
                                    syncMessage = "「${novel.title}」のエピソードを修正中... ($processedCount/$totalCount)"

                                    // APIに負担をかけないよう少し待機
                                    delay(50)
                                }

                                // 小説のtotal_ep値を更新
                                try {
                                    val updatedEpisodes = repository.getEpisodesByNcode(novel.ncode).first()
                                    val maxEpisodeNo = updatedEpisodes.mapNotNull { it.episode_no.toIntOrNull() }.maxOrNull() ?: 0

                                    if (maxEpisodeNo > novel.total_ep) {
                                        val updatedNovel = novel.copy(total_ep = maxEpisodeNo)
                                        repository.updateNovel(updatedNovel)
                                    }
                                } catch (e: Exception) {
                                    Log.e("UpdateInfo", "小説情報の更新に失敗: ${novel.ncode} - ${e.message}")
                                }
                            }

                            // 処理結果の通知
                            withContext(Dispatchers.Main) {
                                syncProgress = 1f
                                syncMessage = "修正完了: 成功${successCount}件、失敗${failCount}件"
                                Toast.makeText(
                                    context,
                                    "修正完了: 成功${successCount}件、失敗${failCount}件",
                                    Toast.LENGTH_SHORT
                                ).show()
                                delay(2000)
                                isSyncing = false
                            }
                        } catch (e: Exception) {
                            // 全体エラー処理
                            withContext(Dispatchers.Main) {
                                syncProgress = 1f
                                syncMessage = "エラー: ${e.message}"
                                Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_SHORT).show()
                                delay(1500)
                                isSyncing = false
                            }
                        }
                    }
                }) {
                    Text("修正する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorFixConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
    // 更新確認ダイアログ
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("更新確認") },
            text = { Text("すべての小説の更新をチェックしますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 更新チェック処理
                        showConfirmDialog = false
                        isRefreshing = true
                        isSyncing = true  // 同期状態をONに
                        syncProgress = 0f
                        syncStep = "更新確認"
                        syncMessage = "小説の更新をチェック中..."
                        currentCount = 0
                        totalCount = 0

                        scope.launch {
                            try {
                                // 更新対象の小説を取得
                                var novelsForUpdate = repository.getNovelsForUpdate()
                                // 短編は新規エピソードが増えないため、設定により更新確認から除外
                                // ※カクヨムの noveltype は登録時話数==1の推定値のため、短編除外は
                                //   noveltype がAPI由来で確実なSyosetu作品にのみ適用する。
                                val excludeShortUpdate = settingsStore.excludeShortFromUpdate.first()
                                if (excludeShortUpdate) {
                                    novelsForUpdate = novelsForUpdate.filter {
                                        it.site_type != NovelSiteAdapter.SITE_TYPE_SYOSETU || it.noveltype != 2
                                    }
                                }
                                // 完結作品も設定により更新確認から除外（end_flag: 1=完結）
                                val excludeCompletedUpdate = settingsStore.excludeCompletedFromUpdate.first()
                                if (excludeCompletedUpdate) {
                                    novelsForUpdate = novelsForUpdate.filter { it.end_flag != 1 }
                                }
                                totalCount = novelsForUpdate.size  // 総数を設定

                                // なろう作品はAPI一括取得（ncode OR検索）でリクエスト数を削減
                                val syosetuInfoMap = HashMap<String, NovelApiUtils.NovelApiInfo>()
                                try {
                                    val syosetuList = novelsForUpdate.filter { it.site_type != NovelSiteAdapter.SITE_TYPE_KAKUYOMU }
                                    syosetuInfoMap.putAll(NovelApiUtils.fetchNovelInfoBatch(syosetuList.filter { it.rating != 1 }.map { it.ncode }, isR18 = false))
                                    syosetuInfoMap.putAll(NovelApiUtils.fetchNovelInfoBatch(syosetuList.filter { it.rating == 1 }.map { it.ncode }, isR18 = true))
                                } catch (e: Exception) {
                                    Log.e("UpdateCheck", "なろう一括取得エラー（個別取得にフォールバック）: ${e.message}")
                                }

                                // 進捗状態の更新関数
                                val updateProgress = { count: Int, message: String ->
                                    val progress = if (totalCount > 0) count.toFloat() / totalCount else 0f
                                    syncProgress = progress
                                    currentCount = count
                                    syncMessage = message
                                }

                                // 初期プログレスを設定
                                updateProgress(0, "小説の更新を確認中...")

                                // 高速化: バッチ処理のためのグループ化
                                val batchSize = 5 // 一度に処理する小説の数
                                val novelBatches = novelsForUpdate.chunked(batchSize)

                                var processedNovels = 0
                                var workCount = 0  // 新着・更新された作品数
                                var episodeCount = 0  // 新着・更新された話数の合計

                                // 各バッチを処理
                                for (batch in novelBatches) {
                                    // 高速化: 並列処理で複数の小説を同時に処理
                                    val deferreds = batch.map { novel ->
                                        async(Dispatchers.IO) {
                                            try {
                                                if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                                                    // カクヨムの場合、HTMLスクレイピングで更新確認
                                                    val adapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU) as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
                                                    val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(novel.ncode)
                                                    val updateSummary = adapter.fetchUpdateSummary(workId)
                                                    val generalAllNo = updateSummary.latestEpisodeCount

                                                    if (generalAllNo > novel.total_ep) {
                                                        val updatedNovel = novel.copy(
                                                            general_all_no = generalAllNo,
                                                            updated_at = updateSummary.novelDesc.updated_at,
                                                            title = updateSummary.novelDesc.title,
                                                            author = updateSummary.novelDesc.author,
                                                            Synopsis = updateSummary.novelDesc.Synopsis,
                                                            main_tag = updateSummary.novelDesc.main_tag,
                                                            sub_tag = updateSummary.novelDesc.sub_tag,
                                                            last_update_date = updateSummary.novelDesc.last_update_date,
                                                            // 完結状態・作品種別は登録後も変化するため最新値で更新する
                                                            // （end_flag: 完結除外フィルタ用 / noveltype: 1話のみ作品が連載化した際の短編誤判定の自己修復）
                                                            end_flag = updateSummary.novelDesc.end_flag,
                                                            noveltype = updateSummary.novelDesc.noveltype
                                                        )
                                                        repository.updateNovel(updatedNovel)

                                                        val updateQueue = UpdateQueueEntity(
                                                            ncode = novel.ncode,
                                                            total_ep = novel.total_ep,
                                                            general_all_no = generalAllNo,
                                                            update_time = updateSummary.novelDesc.updated_at
                                                        )
                                                        repository.insertUpdateQueue(updateQueue)

                                                        workCount++

                                                        if (novel.general_all_no == 0) {
                                                            episodeCount += generalAllNo
                                                        } else {
                                                            episodeCount += (generalAllNo - novel.general_all_no)
                                                        }

                                                        return@async true
                                                    }

                                                    false
                                                } else {
                                                    // 小説家になろうの場合、一括取得の結果を優先（無ければ個別取得にフォールバック）
                                                    val info = syosetuInfoMap[novel.ncode.lowercase()]
                                                        ?: NovelApiUtils.fetchNovelInfo(novel.ncode, novel.rating == 1)

                                                    if (info != null) {
                                                        // 追加情報が取得できた場合は欠けているメタデータを補完
                                                        if (novel.userid == null || novel.noveltype == null || novel.length == null) {
                                                            val enriched = novel.copy(
                                                                userid = novel.userid ?: info.userid,
                                                                noveltype = novel.noveltype ?: info.noveltype,
                                                                length = novel.length ?: info.length
                                                            )
                                                            if (enriched != novel) {
                                                                repository.updateNovel(enriched)
                                                            }
                                                        }

                                                        // 新規エピソードのチェック
                                                        if (info.generalAllNo > novel.general_all_no) {
                                                            val updatedNovel = novel.copy(
                                                                general_all_no = info.generalAllNo,
                                                                updated_at = info.updatedAt
                                                            )
                                                            repository.updateNovel(updatedNovel)

                                                            val updateQueue = UpdateQueueEntity(
                                                                ncode = novel.ncode,
                                                                total_ep = novel.total_ep,
                                                                general_all_no = info.generalAllNo,
                                                                update_time = info.updatedAt
                                                            )
                                                            repository.insertUpdateQueue(updateQueue)

                                                            if (novel.general_all_no == 0) {
                                                                // 新着作品：全話数を加算
                                                                episodeCount += info.generalAllNo
                                                            } else {
                                                                // 更新作品：新しく追加された話数を加算
                                                                episodeCount += (info.generalAllNo - novel.general_all_no)
                                                            }

                                                            workCount++
                                                            return@async true
                                                        }

                                                        // API情報で更新がない場合は処理完了
                                                        // （改稿チェックは削除：API情報が取得できる場合は目次ページ取得不要）
                                                    }

                                                    false
                                                }
                                            } catch (e: Exception) {
                                                Log.e("UpdateCheck", "小説処理エラー: ${novel.ncode} - ${e.message}")
                                                false
                                            }
                                        }
                                    }

                                    // バッチの全処理が完了するまで待機
                                    deferreds.awaitAll()

                                    // 処理済み小説数を更新
                                    processedNovels += batch.size

                                    // ユーザーへのフィードバックを更新
                                    updateProgress(processedNovels, "小説の更新チェック中... ($processedNovels/$totalCount)")

                                }

                                // 完了メッセージを表示
                                withContext(Dispatchers.Main) {
                                    updateProgress(totalCount, "更新チェック完了")

                                    // 結果を表示
                                    val resultMessage = if (workCount > 0) {
                                        "${workCount}作品${episodeCount}話の更新が見つかりました"
                                    } else {
                                        "更新された小説はありませんでした"
                                    }

                                    Toast.makeText(context, resultMessage, Toast.LENGTH_SHORT).show()

                                    // 更新情報を再取得
                                    try {
                                        val latestQueueList = repository.getAllUpdateQueue()
                                        updateQueue = latestQueueList

                                        // N+1クエリ対策：関連する小説情報を一括取得
                                        val ncodes = latestQueueList.map { it.ncode }
                                        val novelsList = repository.getNovelsByNcodes(ncodes)
                                        novels = novelsList.associateBy { it.ncode }
                                    } catch (e: Exception) {
                                        Log.e("UpdateCheck", "更新情報再取得エラー: ${e.message}")
                                    }

                                    // 処理完了
                                    isRefreshing = false
                                    isSyncing = false  // 同期状態をOFFに
                                }
                            } catch (e: Exception) {
                                Log.e("UpdateCheck", "更新チェックエラー: ${e.message}")

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "更新チェック中にエラーが発生しました: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    isRefreshing = false
                                    isSyncing = false  // 同期状態をOFFに
                                }
                            }
                        }
                    },
                    enabled = !isSyncing
                ) {
                    Text(if (isSyncing) "同期中..." else "確認する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新着・更新情報") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 更新ボタンエリア
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "小説の更新",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRefreshing && !isSyncing
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "更新確認",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("更新確認")
                        }

                        Button(
                            onClick = {
                                // 一括更新処理を実装
                                if (updateQueue.isNotEmpty()) {
                                    isSyncing = true
                                    syncProgress = 0f
                                    syncStep = "一括更新"
                                    syncMessage = "小説のエピソードを更新中..."
                                    currentCount = 0
                                    totalCount = 0

                                    scope.launch {
                                        try {
                                            // 対象エピソード数をカウント
                                            var totalEpisodes = 0
                                            updateQueue.forEach { queue ->
                                                val episodesToDownload = queue.general_all_no - queue.total_ep
                                                if (episodesToDownload > 0) {
                                                    totalEpisodes += episodesToDownload
                                                }
                                            }

                                            totalCount = totalEpisodes

                                            // 各キューアイテムを処理
                                            var processedEpisodes = 0

                                            for (queueItem in updateQueue) {
                                                // 小説情報を取得
                                                val novel = repository.getNovelByNcode(queueItem.ncode)

                                                if (novel != null) {
                                                    // total_ep+1からgeneral_all_noまでのリストを作成
                                                    val startEpisode = novel.total_ep + 1
                                                    val endEpisode = queueItem.general_all_no

                                                    // 既に更新済みの場合はキューから削除してスキップ
                                                    if (startEpisode > endEpisode) {
                                                        repository.deleteUpdateQueueByNcode(queueItem.ncode)
                                                        Log.d("UpdateInfo", "小説「${novel.title}」は既に更新済みのためキューから削除しました")
                                                        continue
                                                    }

                                                    if (startEpisode <= endEpisode) {
                                                        syncMessage = "「${novel.title}」のエピソードを更新中..."

                                                        val episodesList = (startEpisode..endEpisode).toList()
                                                        val session = NovelUpdateCoordinator.awaitUpdateSlot(queueItem.ncode)
                                                        if (session == null) {
                                                            val skipped = episodesList.size
                                                            totalEpisodes -= skipped
                                                            if (totalEpisodes < processedEpisodes) {
                                                                totalEpisodes = processedEpisodes
                                                            }
                                                            val safeTotal = if (totalEpisodes <= 0) processedEpisodes else totalEpisodes
                                                            totalCount = safeTotal
                                                            syncProgress = if (safeTotal == 0) 1f else processedEpisodes.toFloat() / safeTotal.toFloat()
                                                            syncMessage = "「${novel.title}」は他の更新処理中のためスキップしました"
                                                            continue
                                                        }

                                                        val episodes = mutableListOf<EpisodeEntity>()
                                                        var cancelledForNovel = false
                                                        var processedForNovel = 0

                                                        try {
                                                            // カクヨムとなろう小説で処理を分岐
                                                            if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                                                                // カクヨムの場合
                                                                val adapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU) as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
                                                                val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(novel.ncode)

                                                                // 全話を事前に1回だけ取得してMapに（ループ内での都度全話ロードを防ぐ）
                                                                var episodeMap = repository.getEpisodesByNcode(novel.ncode).first()
                                                                    .associateBy { it.episode_no }
                                                                var mappingRefreshed = false

                                                                for (episodeNo in episodesList) {
                                                                    if (session.isCancelled()) {
                                                                        cancelledForNovel = true
                                                                        break
                                                                    }

                                                                    val episodeNoStr = episodeNo.toString()
                                                                    syncMessage = "「${novel.title}」の第${episodeNoStr}話を取得中..."

                                                                    try {
                                                                        var existingEpisode = episodeMap[episodeNoStr]

                                                                        // エピソード情報が見つからない場合、mapping情報が古い可能性があるため再取得
                                                                        if (existingEpisode == null && !mappingRefreshed) {
                                                                            Log.w(
                                                                                "UpdateInfo",
                                                                                "カクヨムエピソード情報が見つかりません（mapping再取得を試行）: ${queueItem.ncode}-$episodeNoStr"
                                                                            )

                                                                            try {
                                                                                // エピソード一覧とマッピング情報をアトミックに再取得
                                                                                syncMessage = "「${novel.title}」のエピソード一覧を更新中..."
                                                                                val (_, refreshedEpisodes, refreshedMappings) = adapter.fetchNovelMetadataWithEpisodeListAndMappings(novel.ncode)

                                                                                // エピソード情報をデータベースに保存（既読情報を保持）
                                                                                repository.insertEpisodesPreservingReadStatus(refreshedEpisodes)

                                                                                // マッピング情報をデータベースに保存
                                                                                val mappings = refreshedMappings.map { (episodeNoInt, kakuyomuEpisodeId) ->
                                                                                    com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity(
                                                                                        ncode = novel.ncode,
                                                                                        episode_no = episodeNoInt,
                                                                                        kakuyomu_episode_id = kakuyomuEpisodeId
                                                                                    )
                                                                                }
                                                                                repository.insertEpisodeMappings(mappings)

                                                                                Log.d(
                                                                                    "UpdateInfo",
                                                                                    "カクヨムエピソード一覧とマッピング情報を再取得完了（既読情報保持）: ${novel.ncode}, ${refreshedEpisodes.size}話, mapping: ${mappings.size}件"
                                                                                )

                                                                                // 再取得フラグを立てる（小説ごとに1回のみ）
                                                                                mappingRefreshed = true

                                                                                // Mapを再構築して以降のループで使用
                                                                                episodeMap = repository.getEpisodesByNcode(novel.ncode).first()
                                                                                    .associateBy { it.episode_no }
                                                                                existingEpisode = episodeMap[episodeNoStr]

                                                                                syncMessage = "「${novel.title}」の第${episodeNoStr}話を取得中..."
                                                                            } catch (refreshError: Exception) {
                                                                                Log.e(
                                                                                    "UpdateInfo",
                                                                                    "カクヨムエピソード一覧の再取得エラー: ${novel.ncode}",
                                                                                    refreshError
                                                                                )
                                                                            }
                                                                        }

                                                                        if (existingEpisode != null) {
                                                                            // カクヨムエピソードIDをマッピングから取得
                                                                            val kakuyomuEpisodeId = repository.getKakuyomuEpisodeId(novel.ncode, episodeNo)
                                                                                ?: existingEpisode.episode_no

                                                                            // エピソード本文を取得（再試行あり）
                                                                            var episodeBody = ""
                                                                            var retryCount = 0
                                                                            val maxRetries = 3

                                                                            while (retryCount < maxRetries) {
                                                                                episodeBody = adapter.fetchEpisodeContent(workId, kakuyomuEpisodeId)

                                                                                // 本文が空、またはエラーメッセージの場合は再試行
                                                                                if (episodeBody.isNotEmpty() && !episodeBody.startsWith("★HTMLページ読み込みエラー")) {
                                                                                    break
                                                                                }

                                                                                retryCount++
                                                                                if (retryCount < maxRetries) {
                                                                                    Log.w("UpdateInfo", "Episode body is empty or error, retrying (${retryCount}/${maxRetries}): ${queueItem.ncode}-$episodeNoStr")
                                                                                    delay(1000) // 1秒待機してから再試行
                                                                                }
                                                                            }

                                                                            if (episodeBody.isEmpty() || episodeBody.startsWith("★HTMLページ読み込みエラー")) {
                                                                                Log.e("UpdateInfo", "Failed to fetch episode body after ${maxRetries} retries: ${queueItem.ncode}-$episodeNoStr")
                                                                            }

                                                                            // エピソード情報を更新（本文を追加）
                                                                            val updatedEpisode = existingEpisode.copy(body = episodeBody)

                                                                            // 1話ずつデータベースに保存
                                                                            repository.insertEpisode(updatedEpisode)
                                                                            episodes.add(updatedEpisode)

                                                                            Log.d(
                                                                                "UpdateInfo",
                                                                                "カクヨムエピソード取得成功: ${queueItem.ncode}-$episodeNoStr (ID: $kakuyomuEpisodeId)"
                                                                            )
                                                                        } else {
                                                                            Log.e(
                                                                                "UpdateInfo",
                                                                                "カクヨムエピソード情報が見つかりません（再取得後も見つからず）: ${queueItem.ncode}-$episodeNoStr"
                                                                            )
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        Log.e(
                                                                            "UpdateInfo",
                                                                            "カクヨムエピソード取得エラー: ${queueItem.ncode}-$episodeNoStr",
                                                                            e
                                                                        )
                                                                    }

                                                                    processedEpisodes++
                                                                    processedForNovel++
                                                                    currentCount = processedEpisodes
                                                                    val safeTotal = if (totalEpisodes <= 0) processedEpisodes else totalEpisodes
                                                                    totalCount = safeTotal
                                                                    syncProgress = if (safeTotal == 0) 1f else processedEpisodes.toFloat() / safeTotal.toFloat()

                                                                    delay(100)
                                                                }
                                                            } else {
                                                                // なろう小説の場合
                                                                for (episodeNo in episodesList) {
                                                                    if (session.isCancelled()) {
                                                                        cancelledForNovel = true
                                                                        break
                                                                    }

                                                                    val episodeNoStr = episodeNo.toString()
                                                                    syncMessage = "「${novel.title}」の第${episodeNoStr}話を取得中..."

                                                                    try {
                                                                        val episode = NovelApiUtils.fetchEpisodeWithRetry(
                                                                            novel.ncode,
                                                                            episodeNoStr,
                                                                            novel.rating == 1,
                                                                            novel.noveltype
                                                                        )

                                                                        if (episode != null) {
                                                                            // 1話ずつデータベースに保存
                                                                            repository.insertEpisode(episode)
                                                                            episodes.add(episode)
                                                                            Log.d(
                                                                                "UpdateInfo",
                                                                                "エピソード取得成功: ${queueItem.ncode}-$episodeNoStr"
                                                                            )
                                                                        } else {
                                                                            Log.e(
                                                                                "UpdateInfo",
                                                                                "エピソード取得失敗: ${queueItem.ncode}-$episodeNoStr"
                                                                            )
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        Log.e(
                                                                            "UpdateInfo",
                                                                            "エピソード取得エラー: ${queueItem.ncode}-$episodeNoStr",
                                                                            e
                                                                        )
                                                                    }

                                                                    processedEpisodes++
                                                                    processedForNovel++
                                                                    currentCount = processedEpisodes
                                                                    val safeTotal = if (totalEpisodes <= 0) processedEpisodes else totalEpisodes
                                                                    totalCount = safeTotal
                                                                    syncProgress = if (safeTotal == 0) 1f else processedEpisodes.toFloat() / safeTotal.toFloat()

                                                                    delay(100)
                                                                }
                                                            }

                                                            if (!cancelledForNovel && session.isCancelled()) {
                                                                cancelledForNovel = true
                                                            }

                                                            if (cancelledForNovel) {
                                                                val remaining = episodesList.size - processedForNovel
                                                                totalEpisodes -= remaining
                                                                if (totalEpisodes < processedEpisodes) {
                                                                    totalEpisodes = processedEpisodes
                                                                }
                                                                val safeTotal = if (totalEpisodes <= 0) processedEpisodes else totalEpisodes
                                                                totalCount = safeTotal
                                                                syncProgress = if (safeTotal == 0) 1f else processedEpisodes.toFloat() / safeTotal.toFloat()
                                                                syncMessage = "「${novel.title}」の更新は中断されました"
                                                                continue
                                                            }

                                                            if (episodes.isNotEmpty()) {
                                                                // エピソードは既に1話ずつ保存済みのため、ここでは総数更新のみ行う
                                                                val updatedNovel = novel.copy(total_ep = endEpisode)
                                                                repository.updateNovel(updatedNovel)

                                                                repository.deleteUpdateQueueByNcode(queueItem.ncode)
                                                            }
                                                        } finally {
                                                            NovelUpdateCoordinator.finishUpdate(session)
                                                        }
                                                    }
                                                }
                                            }

                                            // 完了
                                            withContext(Dispatchers.Main) {
                                                syncMessage = "更新が完了しました"
                                                syncProgress = 1.0f

                                                Toast.makeText(
                                                    context,
                                                    "$processedEpisodes 件のエピソードを更新しました",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                // 少し待機してから更新状態を終了
                                                delay(1500)
                                                isSyncing = false
                                            }

                                        } catch (e: Exception) {
                                            Log.e("UpdateInfo", "一括更新エラー", e)

                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "更新中にエラーが発生しました: ${e.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                isSyncing = false
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRefreshing && !isSyncing && updateQueue.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "一括更新",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("一括更新")
                        }

                        Button(
                            onClick = { showErrorFixOptionsDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRefreshing && !isSyncing
                        ) {
                            Icon(
                                Icons.Default.BuildCircle,
                                contentDescription = "エラー修正",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("欠落修正")
                        }

                    }

                    if (isRefreshing || isSyncing) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isSyncing) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    progress = { syncProgress },
                                    modifier = Modifier.size(64.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = syncStep,
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Text(
                                    text = syncMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )

                                // テーブルごとの進捗バーを追加（totalCountが0より大きい場合のみ）
                                if (totalCount > 0) {
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // N/n（X%）形式の進捗表示
                                    val progressPercent = (currentCount.toFloat() / totalCount * 100).toInt()
                                    Text(
                                        text = "取得プログレス: $currentCount/$totalCount ($progressPercent%)",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    LinearProgressIndicator(
                                        progress = { currentCount.toFloat() / totalCount },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                    )
                                }
                            }
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "更新をチェック中...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 更新キューリスト
            if (updateQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing || isSyncing) {
                        // 更新中なので何も表示しない
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "更新情報はありません",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "「更新確認」を押して最新情報を取得してください",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "更新キュー (${updateQueue.size}件)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    HorizontalDivider()

                    LazyColumn {
                        items(updateQueue) { queueItem ->
                            val novel = novels[queueItem.ncode]
                            UpdateQueueItem(
                                queueItem = queueItem,
                                novel = novel,
                                onClick = {
                                    // 小説がnullでなければクリックを処理
                                    novel?.let { onNovelClick(queueItem.ncode) }
                                },
                                onRemove = {
                                    // キューから削除する処理
                                    scope.launch {
                                        repository.deleteUpdateQueueByNcode(queueItem.ncode)
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    // 評価0-2の小説のエラーを確認する関数

}


@Composable
fun UpdateQueueItem(
    queueItem: UpdateQueueEntity,
    novel: NovelDescEntity?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            if (novel != null) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "作者: ${novel.author}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "小説情報がありません",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = "Nコード: ${queueItem.ncode}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Update,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatDate(queueItem.update_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                // N/n（X%）形式の表示を追加（未読エピソード数）
                val unreadEpisodeCount = queueItem.general_all_no - queueItem.total_ep
                val episodeText = if (unreadEpisodeCount > 0) {
                    "全${queueItem.total_ep}話 (未取得${unreadEpisodeCount}話)"
                } else {
                    "全${queueItem.total_ep}話"
                }

                Text(
                    text = episodeText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "削除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}



// 日付表示フォーマット
private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateString
    }
}
