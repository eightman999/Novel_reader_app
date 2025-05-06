package com.shunlight_library.novel_reader

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateInfoScreen(
    onBack: () -> Unit,
    onNovelClick: (String) -> Unit
) {
    val repository = NovelReaderApplication.getRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 状態変数
    var updateQueue by remember { mutableStateOf<List<UpdateQueueEntity>>(emptyList()) }
    var novels by remember { mutableStateOf<Map<String, NovelDescEntity>>(emptyMap()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 進捗表示用の変数
    var isSyncing by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf(0f) }
    var syncStep by remember { mutableStateOf("") }
    var syncMessage by remember { mutableStateOf("") }
    var currentCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    val connectionSemaphore = Semaphore(3)
    // データ取得
    LaunchedEffect(key1 = Unit) {
        repository.allUpdateQueue.collect { queueList ->
            updateQueue = queueList

            // 関連する小説情報も取得
            val novelMap = mutableMapOf<String, NovelDescEntity>()
            queueList.forEach { queue ->
                repository.getNovelByNcode(queue.ncode)?.let { novel ->
                    novelMap[queue.ncode] = novel
                }
            }
            novels = novelMap
        }
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
                                var novels = repository.getNovelsForUpdate()
                                totalCount = novels.size  // 総数を設定

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
                                val novelBatches = novels.chunked(batchSize)

                                var processedNovels = 0
                                var newCount = 0
                                var updatedCount = 0

                                // 各バッチを処理
                                for (batch in novelBatches) {
                                    // 高速化: 並列処理で複数の小説を同時に処理
                                    val deferreds = batch.map { novel ->
                                        async(Dispatchers.IO) {
                                            try {
                                                // 進捗状態を更新
                                                val progressPercent = (processedNovels.toFloat() / totalCount * 100).toInt()

                                                // APIエンドポイントを選択
                                                val apiUrl = if (novel.rating == 1) {
                                                    "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=${novel.ncode}&gzip=5&json"
                                                } else {
                                                    "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=${novel.ncode}&gzip=5&json"
                                                }

                                                // APIからデータを取得
                                                try {
                                                    val connection = URL(apiUrl).openConnection() as HttpURLConnection
                                                    connection.requestMethod = "GET"
                                                    connection.connectTimeout = 5000 // タイムアウト設定を追加
                                                    connection.readTimeout = 5000

                                                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                                        // gzipファイルを解凍
                                                        val inputStream = GZIPInputStream(connection.inputStream)
                                                        val reader = BufferedReader(InputStreamReader(inputStream))
                                                        val content = StringBuilder()
                                                        var line: String?

                                                        while (reader.readLine().also { line = it } != null) {
                                                            content.append(line).append("\n")
                                                        }

                                                        // YAMLデータを解析
                                                        val yaml = Yaml()
                                                        val yamlData = yaml.load<List<Map<String, Any>>>(content.toString())

                                                        if (yamlData.size >= 2) {
                                                            val novelData = yamlData[1]
                                                            val newGeneralAllNo = novelData["general_all_no"] as Int

                                                            // データ型のキャストエラーを修正
                                                            val updatedAtObj = novelData["updated_at"]
                                                            val newUpdatedAt = when (updatedAtObj) {
                                                                is String -> updatedAtObj
                                                                is Date -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(updatedAtObj)
                                                                else -> DatabaseSyncUtils.getCurrentDateTimeString() // 現在時刻をフォールバックとして使用
                                                            }

                                                            // データベースを更新
                                                            if (newGeneralAllNo > novel.general_all_no) {
                                                                // 小説情報を更新
                                                                val updatedNovel = novel.copy(
                                                                    general_all_no = newGeneralAllNo,
                                                                    updated_at = newUpdatedAt
                                                                )
                                                                repository.updateNovel(updatedNovel)

                                                                // 更新キューに追加
                                                                val updateQueue = UpdateQueueEntity(
                                                                    ncode = novel.ncode,
                                                                    total_ep = novel.total_ep,
                                                                    general_all_no = newGeneralAllNo,
                                                                    update_time = newUpdatedAt
                                                                )
                                                                repository.insertUpdateQueue(updateQueue)

                                                                // 新規追加か更新かをカウント
                                                                if (novel.general_all_no == 0) {
                                                                    newCount++
                                                                } else {
                                                                    updatedCount++
                                                                }

                                                                true // 更新あり
                                                            } else {
                                                                false // 更新なし
                                                            }
                                                        } else {
                                                            false // データなし
                                                        }
                                                    } else {
                                                        false // HTTP通信失敗
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("UpdateCheck", "エラー: ${novel.ncode} - ${e.message}")
                                                    false // エラー発生
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
                                    val resultMessage = if (newCount > 0 || updatedCount > 0) {
                                        "新着${newCount}件・更新あり${updatedCount}件の小説が見つかりました"
                                    } else {
                                        "更新された小説はありませんでした"
                                    }

                                    Toast.makeText(context, resultMessage, Toast.LENGTH_SHORT).show()

                                    // 更新情報を再取得
                                    try {
                                        val latestQueueList = repository.getAllUpdateQueue()
                                        updateQueue = latestQueueList

                                        // 関連する小説情報も取得
                                        val novelMap = mutableMapOf<String, NovelDescEntity>()
                                        latestQueueList.forEach { queue ->
                                            repository.getNovelByNcode(queue.ncode)?.let { novel ->
                                                novelMap[queue.ncode] = novel
                                            }
                                        }
                                        novels = novelMap as List<NovelDescEntity>
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { showConfirmDialog = true },
                            modifier = Modifier.weight(1f),
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

                        Spacer(modifier = Modifier.width(16.dp))

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

                                                    if (startEpisode <= endEpisode) {
                                                        // 更新状態を更新
                                                        syncMessage = "「${novel.title}」のエピソードを更新中..."

                                                        // エピソードリストを作成
                                                        val episodesList = (startEpisode..endEpisode).toList()

                                                        // エピソードの取得とスクレイピング
                                                        val episodes = mutableListOf<EpisodeEntity>()

                                                        for (episodeNo in episodesList) {
                                                            // 進捗状況の更新
                                                            val episodeNoStr = episodeNo.toString()
                                                            syncMessage = "「${novel.title}」の第${episodeNoStr}話を取得中..."

                                                            try {
                                                                // 小説のURLを構築（通常版またはR18版）
                                                                val baseUrl = if (novel.rating == 1) {
                                                                    "https://novel18.syosetu.com"
                                                                } else {
                                                                    "https://ncode.syosetu.com"
                                                                }

                                                                val url = "$baseUrl/${queueItem.ncode}/$episodeNoStr/"

                                                                // JSoupを使用してスクレイピング
                                                                // スクレイピング部分のコード修正
                                                                withContext(Dispatchers.IO) {
                                                                    connectionSemaphore.acquire()
                                                                    try {
                                                                        val baseUrl = if (novel.rating == 1) {
                                                                            "https://novel18.syosetu.com"
                                                                        } else {
                                                                            "https://ncode.syosetu.com"
                                                                        }

                                                                        val url = "$baseUrl/${queueItem.ncode}/$episodeNoStr/"

                                                                        // リトライロジックを実装した関数を使用
                                                                        val doc = fetchWithRetry(url)

                                                                        if (doc != null) {
                                                                            try {
                                                                                // タイトルの取得（text()でタグを除去）
                                                                                var title = doc.select("h1.p-novel__title.p-novel__title--rensai").text()

                                                                                // 本文の取得 - すべてのセクション（前書き、本文、後書き）を含む
                                                                                // html()メソッドを使用してタグを保持
                                                                                val bodyElements = doc.select("div.p-novel__body div.js-novel-text p")
                                                                                val body = bodyElements.joinToString("\n<p></p><p>-----</p><p></p>\n") { "<p>${it.html()}</p>" }

                                                                                // タイトルまたは本文が空の場合はリトライ
                                                                                if (title.isEmpty() || body.isEmpty()) {
                                                                                    Log.d("UpdateInfo", "タイトルまたは本文が空のためリトライします: ${queueItem.ncode}-$episodeNoStr")

                                                                                    // リトライロジック
                                                                                    val retryDoc = fetchWithRetry(url)
                                                                                    if (retryDoc != null) {
                                                                                        // タイトルが空だった場合は再取得
                                                                                        if (title.isEmpty()) {
                                                                                            title = retryDoc.select("h1.p-novel__title.p-novel__title--rensai").text()
                                                                                        }

                                                                                        // 本文が空だった場合は再取得
                                                                                        if (body.isEmpty()) {
                                                                                            val bodyElements = doc.select("div.p-novel__body div.js-novel-text p")
                                                                                            val body = bodyElements.joinToString("\n<p></p><p>-----</p><p></p>\n") { "<p>${it.html()}</p>" }
                                                                                        }
                                                                                    }
                                                                                }

                                                                                // それでも空の場合はエラー記録して次へ
                                                                                if (title.isEmpty() || body.isEmpty()) {
                                                                                    Log.e("UpdateInfo", "リトライ後も内容の取得に失敗: ${queueItem.ncode}-$episodeNoStr")

                                                                                }

                                                                                // 更新日時の取得
                                                                                val now = Date()
                                                                                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now)

                                                                                // EpisodeEntityの作成（titleはtext()でタグ除去済み、bodyはhtml()でタグ保持）
                                                                                val episode = EpisodeEntity(
                                                                                    ncode = queueItem.ncode,
                                                                                    episode_no = episodeNoStr,
                                                                                    body = body,
                                                                                    e_title = title,
                                                                                    update_time = dateFormat
                                                                                )

                                                                                episodes.add(episode)

                                                                                // 成功ログ
                                                                                Log.d("UpdateInfo", "エピソード取得成功: ${queueItem.ncode}-$episodeNoStr")
                                                                            } catch (e: Exception) {
                                                                                // 解析エラー
                                                                                Log.e("UpdateInfo", "エピソード解析エラー: ${queueItem.ncode}-$episodeNoStr", e)
                                                                            }
                                                                        } else {
                                                                            // 取得失敗ログ
                                                                            Log.e("UpdateInfo", "エピソード取得失敗: ${queueItem.ncode}-$episodeNoStr")
                                                                        }
                                                                    } finally {
                                                                        connectionSemaphore.release() // 必ず解放
                                                                    }
                                                                }

                                                                // 進捗を更新
                                                                processedEpisodes++
                                                                currentCount = processedEpisodes
                                                                syncProgress = processedEpisodes.toFloat() / totalEpisodes

                                                            } catch (e: Exception) {
                                                                Log.e("UpdateInfo", "エピソード取得エラー: ${queueItem.ncode}-$episodeNoStr", e)
                                                                // エラーは記録するが処理は継続
                                                            }

                                                            // UIの反応性を向上させるための短い遅延
                                                            delay(100)
                                                        }

                                                        // バッチで保存
                                                        if (episodes.isNotEmpty()) {
                                                            repository.insertEpisodes(episodes)

                                                            // 小説のtotal_epを更新
                                                            val updatedNovel = novel.copy(total_ep = endEpisode)
                                                            repository.updateNovel(updatedNovel)

                                                            // 更新キューから削除
                                                            repository.deleteUpdateQueueByNcode(queueItem.ncode)
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
                            modifier = Modifier.weight(1f),
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
                    }

                    if (isRefreshing || isSyncing) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isSyncing) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    progress = syncProgress,
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
                                        progress = currentCount.toFloat() / totalCount,
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

                    Divider()

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
                            Divider()
                        }
                    }
                }
            }
        }
    }
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
// リトライロジックとランダムユーザーエージェント選択を実装した関数
private suspend fun fetchWithRetry(url: String, maxRetries: Int = 3): Document? {
    val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:54.0) Gecko/20100101 Firefox/54.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/602.3.12 (KHTML, like Gecko) Version/10.0.3 Safari/602.3.12",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3_1 like Mac OS X) AppleWebKit/603.1.30 (KHTML, like Gecko) Version/10.0 Mobile/14E304 Safari/602.1",
        "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
        "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:54.0) Gecko/20100101 Firefox/54.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/602.3.12 (KHTML, like Gecko) Version/10.0.3 Safari/602.3.12",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3_1 like Mac OS X) AppleWebKit/603.1.30 (KHTML, like Gecko) Version/10.0 Mobile/14E304 Safari/602.1"
    )

    var retryCount = 0
    var lastException: Exception? = null

    while (retryCount < maxRetries) {
        try {
            // ランダムなユーザーエージェントを選択
            val randomUserAgent = USER_AGENTS.random()

            Log.d("UpdateInfo", "接続試行 ${retryCount + 1}/$maxRetries: $url")
            Log.d("UpdateInfo", "使用中のユーザーエージェント: $randomUserAgent")

            return Jsoup.connect(url)
                .userAgent(randomUserAgent)
                .timeout(30000)  // 30秒に設定
                .maxBodySize(0)  // 無制限のボディサイズ
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .get()
        } catch (e: SocketTimeoutException) {
            lastException = e
            Log.w("UpdateInfo", "タイムアウト発生 ${retryCount + 1}/$maxRetries: $url - ${e.message}")
            retryCount++

            // 指数バックオフ（リトライ間隔を徐々に増加）
            val delayTime = 2000L * (retryCount + 1)
            Log.d("UpdateInfo", "$delayTime ミリ秒後に再試行します")
            delay(delayTime)
        } catch (e: IOException) {
            lastException = e
            Log.w("UpdateInfo", "IO例外発生 ${retryCount + 1}/$maxRetries: $url - ${e.message}")
            retryCount++

            // 指数バックオフ
            val delayTime = 2000L * (retryCount + 1)
            Log.d("UpdateInfo", "$delayTime ミリ秒後に再試行します")
            delay(delayTime)
        }
    }

    // 最大リトライ回数を超えた場合
    Log.e("UpdateInfo", "最大リトライ回数を超えました: $url", lastException)
    return null
}