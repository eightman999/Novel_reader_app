/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Activity UI for database synchronization.
 */
package com.shunlight_library.novel_reader.ui

import android.app.Activity
import com.shunlight_library.novel_reader.utils.AppLogger
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.sync.DatabaseExportManager
import com.shunlight_library.novel_reader.data.sync.EncryptedBackupManager
import com.shunlight_library.novel_reader.data.sync.ImprovedDatabaseSyncManager
import com.shunlight_library.novel_reader.data.sync.WebNovelReaderImportManager
import com.shunlight_library.novel_reader.ui.theme.Novel_readerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

private enum class BackupAction { EXPORT, RESTORE }

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
    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<DatabaseExportManager.ExportResult?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupResult by remember { mutableStateOf<EncryptedBackupManager.Result?>(null) }
    var backupAction by remember { mutableStateOf<BackupAction?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var backupConfirm by remember { mutableStateOf("") }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

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
                    AppLogger.d(DatabaseSyncActivity.TAG, "取得した永続的なアクセス権限: $path")
                    addLog("データベースファイルを選択しました: $path")
                    Toast.makeText(context, "データベースファイルを選択しました", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    AppLogger.e(DatabaseSyncActivity.TAG, "権限取得エラー: ${e.message}", e)
                    Toast.makeText(context, "アクセス権限の取得中にエラーが発生しました", Toast.LENGTH_SHORT).show()
                    addLog("エラー: アクセス権限の取得に失敗しました")
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            addLog("データベース書き出しを開始します ($timestamp)")
            isExporting = true
            exportResult = null
            scope.launch {
                val manager = DatabaseExportManager(context)
                val result = manager.exportDatabase(uri)
                exportResult = result
                isExporting = false

                if (result.success) {
                    val sizeInKb = result.bytesCopied / 1024f
                    val message = "書き出し完了: ${String.format(Locale.getDefault(), "%.1f", sizeInKb)}KB"
                    addLog("完了: $message")
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = result.errorMessage ?: "不明なエラー"
                    addLog("エラー: 書き出しに失敗しました - $errorMsg")
                    Toast.makeText(context, "書き出しに失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            addLog("書き出しをキャンセルしました")
        }
    }

    val encryptedExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = backupPassword.toCharArray()
        backupPassword = ""
        backupConfirm = ""
        backupAction = null
        if (uri != null) {
            backupBusy = true
            backupResult = null
            addLog("暗号化バックアップを書き出しています")
            scope.launch {
                val result = EncryptedBackupManager(context).export(uri, password)
                backupResult = result
                backupBusy = false
                addLog(if (result.success) "完了: 暗号化バックアップ (${result.imageCount}画像)" else "エラー: ${result.errorMessage}")
            }
        } else {
            password.fill('\u0000')
        }
    }

    val encryptedRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            backupPassword = ""
            backupAction = BackupAction.RESTORE
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
            val callback = object : ImprovedDatabaseSyncManager.SyncProgressCallback {
                // M16: 同期処理は Dispatchers.IO 上で動くため、Compose State 更新は Main に戻す
                override fun onProgressUpdate(progress: ImprovedDatabaseSyncManager.SyncProgress) {
                    scope.launch(Dispatchers.Main.immediate) {
                        syncProgress = progress.progress
                        syncStep = progress.step.name
                        syncMessage = progress.message
                        currentCount = progress.currentCount
                        totalCount = progress.totalCount

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
                }

                override fun onComplete(result: ImprovedDatabaseSyncManager.SyncResult) {
                    scope.launch(Dispatchers.Main.immediate) {
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
            }

            try {
                // ファイル形式を自動判別（先頭バイトがZIPマジックなら WebNovelReader バックアップ）
                val isZip = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(selectedUri!!)?.use { input ->
                            val header = ByteArray(4)
                            val read = input.read(header)
                            read >= 4 && WebNovelReaderImportManager.looksLikeZip(header)
                        } ?: false
                    } catch (e: Exception) {
                        false
                    }
                }

                if (isZip) {
                    addLog("WebNovelReader バックアップ(ZIP)として復元します")
                    WebNovelReaderImportManager(context).importFromZip(selectedUri!!, callback)
                } else {
                    addLog("SQLiteデータベースとして復元します")
                    ImprovedDatabaseSyncManager(context).syncFromExternalDb(selectedUri!!, callback)
                }
            } catch (e: Exception) {
                AppLogger.e(DatabaseSyncActivity.TAG, "同期処理中にエラーが発生しました", e)
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
                .verticalScroll(rememberScrollState())
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
                        text = "対応形式: 本アプリの.dbバックアップ / 旧形式SQLite(novel_status.db等) / WebNovelReaderバックアップ(.zip)。形式は自動判別します。",
                        style = MaterialTheme.typography.bodySmall
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
                            enabled = selectedUri != null && !isSyncing && !isExporting && !backupBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("同期を開始")
                        }
                    }
                }
            }

            // データベース書き出しセクション
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
                        text = "データベース書き出し",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "現在の内部データベースのみを平文で書き出します。画像は含まれません。Google ドライブへの保存には下の暗号化バックアップを使ってください。",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (isExporting) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("書き出し中...")
                        }
                    } else {
                        exportResult?.let { result ->
                            if (result.success) {
                                Text(
                                    text = "直近の書き出し: ${String.format(Locale.getDefault(), "%.1fKB", result.bytesCopied / 1024f)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "書き出しに失敗しました: ${result.errorMessage ?: "不明なエラー"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val defaultName = "novel_database_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.db"
                            exportLauncher.launch(defaultName)
                        },
                        enabled = !isSyncing && !isExporting && !backupBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("データベースを書き出す")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("暗号化バックアップ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "小説DBと画像キャッシュをパスワードで暗号化して保存します。保存先で Google ドライブを選べます。復元は現在のDBへマージします。設定は対象外です。パスワードを忘れると復元できません。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (backupBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("処理中...")
                        }
                    }
                    backupResult?.let { result ->
                        Text(
                            if (result.success) "完了: 画像${result.imageCount}件" else "失敗: ${result.errorMessage ?: "不明なエラー"}",
                            color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = {
                            backupPassword = ""
                            backupConfirm = ""
                            backupAction = BackupAction.EXPORT
                        },
                        enabled = !isSyncing && !isExporting && !backupBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("暗号化バックアップを書き出す") }
                    OutlinedButton(
                        onClick = { encryptedRestoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        enabled = !isSyncing && !isExporting && !backupBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("暗号化バックアップを復元") }
                }
            }

            // ログ表示セクション
            // ログ表示セクションの改善

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 260.dp)
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

    backupAction?.let { action ->
        AlertDialog(
            onDismissRequest = {
                backupAction = null
                backupPassword = ""
                backupConfirm = ""
                pendingRestoreUri = null
            },
            title = { Text(if (action == BackupAction.EXPORT) "バックアップ用パスワード" else "復元用パスワード") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (action == BackupAction.EXPORT) "12文字以上のパスワードを設定してください。忘れると復元できません。"
                        else "作成時のパスワードを入力してください。復元データは現在のDBへマージされます。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("パスワード") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    if (action == BackupAction.EXPORT) {
                        OutlinedTextField(
                            value = backupConfirm,
                            onValueChange = { backupConfirm = it },
                            label = { Text("パスワードを再入力") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = if (action == BackupAction.EXPORT) backupPassword.length >= 12 && backupPassword == backupConfirm
                        else backupPassword.isNotEmpty() && pendingRestoreUri != null,
                    onClick = {
                        if (action == BackupAction.EXPORT) {
                            val name = "novel_reader_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.nrbackup"
                            encryptedExportLauncher.launch(name)
                        } else {
                            val source = pendingRestoreUri ?: return@TextButton
                            val password = backupPassword.toCharArray()
                            backupAction = null
                            backupPassword = ""
                            backupConfirm = ""
                            pendingRestoreUri = null
                            backupBusy = true
                            backupResult = null
                            addLog("暗号化バックアップを復元しています")
                            scope.launch {
                                val result = EncryptedBackupManager(context).restore(source, password)
                                backupResult = result
                                backupBusy = false
                                addLog(if (result.success) "完了: 暗号化バックアップ復元 (${result.imageCount}画像)" else "エラー: ${result.errorMessage}")
                            }
                        }
                    }
                ) { Text("続行") }
            },
            dismissButton = {
                TextButton(onClick = {
                    backupAction = null
                    backupPassword = ""
                    backupConfirm = ""
                    pendingRestoreUri = null
                }) { Text("キャンセル") }
            }
        )
    }
}
