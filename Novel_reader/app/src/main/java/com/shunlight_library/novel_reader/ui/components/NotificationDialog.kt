/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Dialog displaying stored notifications.
 */
package com.shunlight_library.novel_reader.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.shunlight_library.novel_reader.data.ErrorLog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shunlight_library.novel_reader.data.AppNotification
import com.shunlight_library.novel_reader.data.NotificationStore
import com.shunlight_library.novel_reader.data.NotificationType
import com.shunlight_library.novel_reader.data.ErrorLogStore
import com.shunlight_library.novel_reader.data.EmailUtils
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDialog(
    notificationStore: NotificationStore,
    onDismiss: () -> Unit
) {
    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    var selectedRelease by remember { mutableStateOf<AppNotification?>(null) }
    var selectedDownloadDetails by remember { mutableStateOf<AppNotification?>(null) }
    var showErrorLogDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 通知データを読み込み
    LaunchedEffect(key1 = Unit) {
        scope.launch {
            notifications = notificationStore.getAllNotifications()
        }
    }

    // リリース詳細ダイアログ
    selectedRelease?.let { release ->
        ReleaseDetailDialog(
            release = release,
            onDismiss = { selectedRelease = null }
        )
    }

    // ダウンロード詳細ダイアログ
    selectedDownloadDetails?.let { notification ->
        DownloadDetailDialog(
            notification = notification,
            onDismiss = { selectedDownloadDetails = null }
        )
    }

    // エラーログダイアログ
    if (showErrorLogDialog) {
        ErrorLogDialog(
            errorLogStore = ErrorLogStore(context),
            onDismiss = { showErrorLogDialog = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("通知")

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // エラーログボタン
                    TextButton(
                        onClick = { showErrorLogDialog = true }
                    ) {
                        Text("エラーログ", fontSize = 12.sp)
                    }

                    // 全て既読にするボタン
                    if (notifications.any { !it.isRead }) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    notificationStore.markAllAsRead()
                                    notifications = notificationStore.getAllNotifications()
                                }
                            }
                        ) {
                            Text("全て既読", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "通知はありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            onClick = {
                                // リリース通知の場合、詳細ダイアログを表示
                                if (notification.releaseBody != null) {
                                    selectedRelease = notification
                                }
                                // ダウンロード詳細がある場合、詳細ダイアログを表示
                                else if (notification.downloadDetails != null && notification.downloadDetails.isNotEmpty()) {
                                    selectedDownloadDetails = notification
                                }
                            },
                            onMarkAsRead = {
                                if (!notification.isRead) {
                                    scope.launch {
                                        notificationStore.markAsRead(notification.id)
                                        notifications = notificationStore.getAllNotifications()
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    notificationStore.deleteNotification(notification.id)
                                    notifications = notificationStore.getAllNotifications()
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        },
        dismissButton = {
            if (notifications.isNotEmpty()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            notificationStore.clearAllNotifications()
                            notifications = emptyList()
                        }
                    }
                ) {
                    Text("全て削除")
                }
            }
        }
    )
}

@Composable
fun NotificationItem(
    notification: AppNotification,
    onClick: () -> Unit,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(notification.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = notification.releaseBody != null || notification.downloadDetails != null) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 通知タイプアイコン
                    Icon(
                        when (notification.type) {
                            NotificationType.UPDATE -> Icons.Default.Refresh
                            NotificationType.ERROR -> Icons.Default.Error
                            NotificationType.INFO -> Icons.Default.Info
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when (notification.type) {
                            NotificationType.UPDATE -> MaterialTheme.colorScheme.primary
                            NotificationType.ERROR -> MaterialTheme.colorScheme.error
                            NotificationType.INFO -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // 日時
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 内容
            Text(
                text = notification.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            // アクションボタン
            if (!notification.isRead || true) { // 常に表示（削除ボタンのため）
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!notification.isRead) {
                        TextButton(
                            onClick = onMarkAsRead
                        ) {
                            Text("既読", fontSize = 12.sp)
                        }
                    }
                    
                    TextButton(
                        onClick = onDelete
                    ) {
                        Text("削除", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailDialog(
    release: AppNotification,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = release.releaseName ?: release.releaseTagName ?: "リリース詳細",
                    style = MaterialTheme.typography.titleLarge
                )
                if (!release.releaseTagName.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = release.releaseTagName!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (!release.releasePublishedAt.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "公開日時: ${formatPublishedDate(release.releasePublishedAt!!)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(scrollState)
            ) {
                if (!release.releaseBody.isNullOrEmpty()) {
                    MarkdownText(
                        markdown = release.releaseBody!!,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "リリースノートはありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        confirmButton = {
            if (!release.releaseUrl.isNullOrEmpty()) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl))
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("GitHubで開く")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

/**
 * ISO 8601形式の日時を読みやすい形式に変換
 */
private fun formatPublishedDate(isoDate: String): String {
    if (isoDate.isEmpty()) return "不明"
    return try {
        val instant = java.time.Instant.parse(isoDate)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        isoDate
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailDialog(
    notification: AppNotification,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "ダウンロード詳細",
                    style = MaterialTheme.typography.titleLarge
                )
                val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(notification.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(scrollState)
            ) {
                notification.downloadDetails?.forEach { novelInfo ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = novelInfo.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            if (novelInfo.successEpisodes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "✓ 成功: ${novelInfo.successEpisodes.size}話",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                novelInfo.successEpisodes.forEach { ep ->
                                    Text(
                                        text = "  - ${ep.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            if (novelInfo.failedEpisodes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "✗ 失敗: ${novelInfo.failedEpisodes.size}話",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                novelInfo.failedEpisodes.forEach { ep ->
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        Text(
                                            text = "  - ${ep.title}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        ep.error?.let { error ->
                                            Text(
                                                text = "    エラー: $error",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (notification.downloadDetails == null || notification.downloadDetails.isEmpty()) {
                    Text(
                        text = "ダウンロード詳細はありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLogDialog(
    errorLogStore: ErrorLogStore,
    onDismiss: () -> Unit
) {
    var errorLogs by remember { mutableStateOf<List<com.shunlight_library.novel_reader.data.ErrorLog>>(emptyList()) }
    var selectedLog by remember { mutableStateOf<com.shunlight_library.novel_reader.data.ErrorLog?>(null) }
    var showErrorDetails by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        errorLogs = errorLogStore.getAllErrorLogs()
    }

    if (showErrorDetails && selectedLog != null) {
        ErrorLogDetailDialog(
            errorLog = selectedLog!!,
            errorLogStore = errorLogStore,
            onDismiss = { showErrorDetails = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("エラーログ")
                if (errorLogs.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                EmailUtils.sendErrorLogByEmail(context, errorLogs, errorLogStore)
                            }
                        }
                    ) {
                        Text("メール送信", fontSize = 12.sp)
                    }
                }
            }
        },
        text = {
            if (errorLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "エラーログはありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(errorLogs, key = { it.id }) { log ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLog = log; showErrorDetails = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                                Text(
                                    text = dateFormat.format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = log.novelTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${log.episodeTitle ?: "第${log.episodeNo}話"} - ${log.errorType}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        },
        dismissButton = {
            if (errorLogs.isNotEmpty()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            errorLogStore.clearAllErrorLogs()
                            errorLogs = emptyList()
                        }
                    }
                ) {
                    Text("全て削除", fontSize = 12.sp)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLogDetailDialog(
    errorLog: com.shunlight_library.novel_reader.data.ErrorLog,
    errorLogStore: ErrorLogStore,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "エラー詳細",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(scrollState)
            ) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                Text(
                    text = "日時: ${dateFormat.format(Date(errorLog.timestamp))}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "小説名: ${errorLog.novelTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Nコード: ${errorLog.ncode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "エピソード: ${errorLog.episodeTitle ?: "第${errorLog.episodeNo}話"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "エラー種類: ${errorLog.errorType}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "エラーメッセージ: ${errorLog.errorMessage}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                errorLog.stackTrace?.let { trace ->
                    Text(
                        text = "スタックトレース:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        scope.launch {
                            errorLogStore.deleteErrorLog(errorLog.id)
                        }
                        onDismiss()
                    }
                ) {
                    Text("削除")
                }
                TextButton(onClick = onDismiss) {
                    Text("閉じる")
                }
            }
        }
    )
}