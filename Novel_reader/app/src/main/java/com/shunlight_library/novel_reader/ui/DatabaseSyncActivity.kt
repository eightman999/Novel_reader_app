package com.shunlight_library.novel_reader.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.shunlight_library.novel_reader.data.sync.ImprovedDatabaseSyncManager
import com.shunlight_library.novel_reader.ui.theme.Novel_readerTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.background

class DatabaseSyncActivity : ComponentActivity() {

    companion object {
        const val TAG = "DatabaseSyncActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Novel_readerTheme {
                DatabaseSyncScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseSyncScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状態変数
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf(0f) }
    var syncStep by remember { mutableStateOf("") }
    var syncMessage by remember { mutableStateOf("") }
    var syncResult by remember { mutableStateOf<ImprovedDatabaseSyncManager.SyncResult?>(null) }
    var logMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    // 追加の状態変数
    var currentCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    // ログメッセージに関する状態変数を修正
    val maxLogMessages = 20 // 最大ログ表示数

    // ログ追加用のヘルパー関数
    fun addLog(message: String) {
        logMessages = (logMessages + message).takeLast(maxLogMessages)
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = uri.toString()
                selectedUri = uri

                val contentResolver = context.contentResolver
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                try {
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d(DatabaseSyncActivity.TAG, "取得した永続的なアクセス権限: $path")
                    addLog("データベースファイルを選択しました: $path")
                    Toast.makeText(context, "データベースファイルを選択しました", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(DatabaseSyncActivity.TAG, "権限取得エラー: ${e.message}", e)
                    Toast.makeText(context, "アクセス権限の取得中にエラーが発生しました", Toast.LENGTH_SHORT).show()
                    addLog("エラー: アクセス権限の取得に失敗しました")
                }
            }
        }
    }

    fun startSync() {
        if (selectedUri == null) {
            Toast.makeText(context, "データベースファイルを選択してください", Toast.LENGTH_SHORT).show()
            return
        }

        isSyncing = true
        syncProgress = 0f
        syncStep = "準備中"
        syncMessage = "同期を開始しています..."
        syncResult = null
        addLog("同期を開始しています...")

        scope.launch {
            val syncManager = ImprovedDatabaseSyncManager(context)

            try {
                syncManager.syncFromExternalDb(
                    selectedUri!!,
                    object : ImprovedDatabaseSyncManager.SyncProgressCallback {
                        override fun onProgressUpdate(progress: ImprovedDatabaseSyncManager.SyncProgress) {
                            syncProgress = progress.progress
                            syncStep = progress.step.name
                            syncMessage = progress.message
                            currentCount = progress.currentCount
                            totalCount = progress.totalCount

                            // ログメッセージの改善
                            val logMsg = buildString {
                                append("${progress.step}: ${progress.message}")

                                if (progress.currentNcode.isNotEmpty() && progress.currentTitle.isNotEmpty()) {
                                    append(" - [${progress.currentNcode}] ${progress.currentTitle}")
                                }

                                if (progress.totalCount > 0) {
                                    val percent = (progress.currentCount.toFloat() / progress.totalCount * 100).toInt()
                                    append(" ($percent%)")
                                }
                            }

                            addLog(logMsg)
                        }

                        override fun onComplete(result: ImprovedDatabaseSyncManager.SyncResult) {
                            syncResult = result
                            isSyncing = false

                            if (result.success) {
                                val successMsg = "同期が完了しました: 小説${result.novelDescsCount}件、" +
                                        "エピソード${result.episodesCount}件、履歴${result.lastReadCount}件"
                                addLog("完了: $successMsg")
                            } else {
                                val errorMsg = "同期に失敗しました: ${result.errorMessage}"
                                addLog("エラー: $errorMsg")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(DatabaseSyncActivity.TAG, "同期処理中にエラーが発生しました", e)
                syncResult = ImprovedDatabaseSyncManager.SyncResult(
                    success = false,
                    novelDescsCount = 0,
                    episodesCount = 0,
                    lastReadCount = 0,
                    errorMessage = "予期しないエラー: ${e.message}"
                )
                isSyncing = false
                addLog("重大なエラー: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("データベース同期") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // データベースファイル選択セクション
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "外部データベース",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = selectedUri?.toString() ?: "ファイルが選択されていません",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                type = "*/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            filePickerLauncher.launch(intent)
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("データベースファイルを選択")
                    }
                }
            }

            // 同期アクションセクション
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "データベース同期",
                        style = MaterialTheme.typography.titleMedium
                    )

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

                                Text(
                                    text = "テーブル進捗: $currentCount / $totalCount",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = currentCount.toFloat() / totalCount,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                )

                                Text(
                                    text = "${(currentCount.toFloat() / totalCount * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )

                        if (syncResult != null) {
                            if (syncResult!!.success) {
                                Text(
                                    text = "同期完了！",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = "小説: ${syncResult!!.novelDescsCount}件\n" +
                                            "エピソード: ${syncResult!!.episodesCount}件\n" +
                                            "閲覧履歴: ${syncResult!!.lastReadCount}件",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "同期失敗",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )

                                Text(
                                    text = syncResult!!.errorMessage ?: "不明なエラー",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Button(
                            onClick = { startSync() },
                            enabled = selectedUri != null && !isSyncing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("同期を開始")
                        }
                    }
                }
            }

            // ログ表示セクション
            // ログ表示セクションの改善

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "同期ログ",
                            style = MaterialTheme.typography.titleMedium
                        )

                        // ログクリアボタンを追加
                        TextButton(
                            onClick = { logMessages = emptyList() },
                            enabled = logMessages.isNotEmpty()
                        ) {
                            Text("クリア")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            logMessages.forEach { message ->
                                // メッセージの種類に応じて色分けを行う
                                val color = when {
                                    message.contains("エラー") -> MaterialTheme.colorScheme.error
                                    message.contains("完了") -> MaterialTheme.colorScheme.primary
                                    message.contains("SYNCING") -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = color,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}