package com.shunlight_library.novel_reader

import android.provider.ContactsContract.CommonDataKinds.Website.URL
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
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

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
    onEpisodeClick: (String, String) -> Unit // ncode, episodeNo
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
    var redownloadTargets by remember { mutableStateOf<List<Int>>(emptyList()) }

    // データの取得
    LaunchedEffect(ncode) {
        // 小説情報の取得
        novel = repository.getNovelByNcode(ncode)

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

    // 「更新」実行関数
    fun performUpdate() {
        isUpdating = true
        updateProgress = 0f
        updateMessage = "APIで最新情報を確認中..."

        scope.launch {
            try {
                val (newGeneralAllNo, newUpdatedAt) = fetchNovelInfo(novel?.ncode ?: "")

                if (newGeneralAllNo == -1) {
                    // APIからデータが取得できなかった
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "APIからデータが取得できませんでした"
                        Toast.makeText(context, "APIからデータが取得できませんでした", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                        showUpdateDialog = false
                    }
                    return@launch
                }

                // 更新があるか確認
                if (novel != null && newGeneralAllNo > novel!!.general_all_no) {
                    // 小説情報を更新
                    val updatedNovel = novel!!.copy(
                        general_all_no = newGeneralAllNo,
                        updated_at = newUpdatedAt
                    )
                    repository.updateNovel(updatedNovel)

                    // 更新キューに追加
                    val updateQueue = UpdateQueueEntity(
                        ncode = novel!!.ncode,
                        total_ep = novel!!.total_ep,
                        general_all_no = newGeneralAllNo,
                        update_time = newUpdatedAt
                    )
                    repository.insertUpdateQueue(updateQueue)

                    // 通知
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "更新を確認しました。更新キューに追加しました。"
                        Toast.makeText(context, "更新を確認しました", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                        showUpdateDialog = false
                    }
                } else {
                    // 更新なし
                    withContext(Dispatchers.Main) {
                        updateProgress = 1f
                        updateMessage = "更新はありません"
                        Toast.makeText(context, "この小説に更新はありません", Toast.LENGTH_SHORT).show()
                        delay(1500)
                        isUpdating = false
                        showUpdateDialog = false
                    }
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

    // 「再取得」実行関数
    fun performRedownload() {
        isUpdating = true
        updateProgress = 0f
        updateMessage = "エピソードを削除中..."

        scope.launch {
            try {
                // エピソードを削除
                withContext(Dispatchers.IO) {
                    novel?.let {
                        repository.deleteEpisodesByNcode(it.ncode)
                    }
                }

                updateProgress = 0.3f
                updateMessage = "APIで最新情報を確認中..."

                val (newGeneralAllNo, newUpdatedAt) = fetchNovelInfo(novel?.ncode ?: "")

                var generalAllNoValue = newGeneralAllNo
                if (generalAllNoValue == -1) {
                    // APIから取得失敗時は既存の値を使用
                    generalAllNoValue = novel?.general_all_no ?: 0
                }

                // 小説情報を更新
                novel?.let { currentNovel ->
                    val updatedNovel = currentNovel.copy(
                        general_all_no = generalAllNoValue,
                        total_ep = 0, // エピソードを削除したので0に
                        updated_at = newUpdatedAt
                    )
                    repository.updateNovel(updatedNovel)
                }

                // 確認ダイアログを表示するためにUIスレッドに戻る
                withContext(Dispatchers.Main) {
                    tempGeneralAllNo = generalAllNoValue
                    isUpdating = false
                    showUpdateDialog = false
                    showDownloadConfirmDialog = true
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

    // エピソードをダウンロードする関数
    fun performDownloadEpisodes(generalAllNo: Int) {
        isUpdating = true
        updateProgress = 0.3f
        updateMessage = "エピソードを取得中... (0/$generalAllNo)"

        scope.launch {
            try {
                // 更新日時
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                var successCount = 0
                var failCount = 0

                // エピソード番号のリスト
                val episodeNumbers = (1..generalAllNo).toList()

                // スクレイピングの実行
                for ((index, episodeNo) in episodeNumbers.withIndex()) {
                    novel?.let {
                        val episode = fetchEpisode(it.ncode, episodeNo, it.rating == 1)

                        if (episode != null) {
                            // データベースに保存
                            withContext(Dispatchers.IO) {
                                repository.insertEpisode(episode)
                            }
                            successCount++
                        } else {
                            failCount++
                        }
                    }

                    // サーバーに負荷をかけないように少し待機
                    delay(50) // 1秒待機

                    // 進捗を更新
                    val progress = (index + 1).toFloat() / generalAllNo
                    updateProgress = 0.3f + (0.7f * progress)
                    updateMessage = "エピソードを取得中... (${index + 1}/$generalAllNo)"
                }

                // 小説のtotal_epを更新
                novel?.let {
                    val updatedNovel = it.copy(
                        total_ep = successCount,
                        general_all_no = generalAllNo
                    )
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

                    // APIで最新情報を取得して、総エピソード数を確認
                    updateProgress = 0.2f
                    updateMessage = "APIで最新情報を確認中..."

                    val (newGeneralAllNo, _) = fetchNovelInfo(it.ncode)

                    var generalAllNoValue = newGeneralAllNo
                    if (generalAllNoValue == -1) {
                        // APIから取得失敗時は既存の値を使用
                        generalAllNoValue = it.general_all_no
                    }

                    // エピソードの番号リスト（IntとStringの両方を持つ）
                    val episodeNumberMap = episodesList.associate { episode ->
                        val numericValue = episode.episode_no.toIntOrNull() ?: 0
                        numericValue to episode.episode_no
                    }

                    // 最大エピソード番号を取得
                    val maxEpisodeNo = episodeNumberMap.keys.maxOrNull() ?: 0

                    // 欠番リスト（1から最大の番号の範囲で）
                    val checkRangeMax = maxOf(generalAllNoValue, maxEpisodeNo)
                    val missingEpisodes = (1..checkRangeMax).filter { epNo ->
                        !episodeNumberMap.containsKey(epNo)
                    }

                    errorEpisodeCount = errorEpisodes.size
                    missingEpisodeCount = missingEpisodes.size

                    // エラーのあるエピソードの番号リスト
                    val errorEpisodeNumbers = errorEpisodes.mapNotNull { episode -> episode.episode_no.toIntOrNull() }

                    // 再取得対象の番号リスト（エラーと欠番を合わせる）
                    redownloadTargets = (errorEpisodeNumbers + missingEpisodes).distinct().sorted()

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
        isUpdating = true
        updateProgress = 0.3f
        updateMessage = "エラーまたは欠番のあるエピソードを再取得中... (0/${redownloadTargets.size})"

        scope.launch {
            try {
                var successCount = 0
                var failCount = 0

                // スクレイピングの実行
                novel?.let {
                    for ((index, episodeNo) in redownloadTargets.withIndex()) {
                        val episode = fetchEpisode(it.ncode, episodeNo, it.rating == 1)

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
                        delay(50) // 1秒待機

                        // 進捗を更新
                        val progress = (index + 1).toFloat() / redownloadTargets.size
                        updateProgress = 0.3f + (0.7f * progress)
                        updateMessage = "エラーまたは欠番のあるエピソードを再取得中... (${index + 1}/${redownloadTargets.size})"
                    }

                    // 小説のtotal_epを更新
                    val updatedEpisodes = repository.getEpisodesByNcode(it.ncode).first()
                    val maxEpisodeNo = updatedEpisodes.mapNotNull { episode -> episode.episode_no.toIntOrNull() }.maxOrNull() ?: 0

                    if (maxEpisodeNo > it.total_ep) {
                        val updatedNovel = it.copy(total_ep = maxEpisodeNo)
                        repository.updateNovel(updatedNovel)
                    }
                }

                // 処理結果の通知
                withContext(Dispatchers.Main) {
                    updateProgress = 1f
                    updateMessage = "完了: 成功${successCount}件、失敗${failCount}件"
                    Toast.makeText(context, "完了: 成功${successCount}件、失敗${failCount}件", Toast.LENGTH_SHORT).show()
                    delay(2000)
                    isUpdating = false
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

    // 画面の構成
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(novel?.title ?: "小説詳細")
                        novel?.let {
                            Text(
                                "作者: ${it.author}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
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
                            isRead = isRead,
                            onClick = { onEpisodeClick(ncode, episode.episode_no) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeItem(
    episode: EpisodeEntity,
    isRead: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 既読/未読アイコン
        Icon(
            imageVector = if (isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isRead) "既読" else "未読",
            tint = if (isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // エピソード情報
        Column {
            Text(
                text = "${episode.episode_no}. ${episode.e_title}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold
            )

            if (episode.update_time.isNotEmpty()) {
                Text(
                    text = "更新: ${episode.update_time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// API呼び出し関数: APIから小説情報を取得
private suspend fun fetchNovelInfo(ncode: String): Pair<Int, String> {
    if (ncode.isEmpty()) return Pair(-1, "")

    return withContext(Dispatchers.IO) {
        try {
            // API URLの構築（R18判定は呼び出し側で対応）
            val apiUrl = "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5&json"

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
            Log.e("EpisodeListScreen", "API取得エラー: ${e.message}", e)
            Pair(-1, "")
        }
    }
}

// エピソードを取得する関数（スクレイピング）
private suspend fun fetchEpisode(ncode: String, episodeNo: Int, isR18: Boolean): EpisodeEntity? {
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
            val body = bodyElements.joinToString("\n<p></p><p>-----</p><p></p>\n") { "<p>${it.html()}</p>" }

            if (title.isNotEmpty() && body.isNotEmpty()) {
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                EpisodeEntity(
                    ncode = ncode,
                    episode_no = episodeNo.toString(),
                    body = body,
                    e_title = title,
                    update_time = currentDate
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("EpisodeListScreen", "エピソード取得エラー: $episodeNo", e)
            null
        }
    }
}