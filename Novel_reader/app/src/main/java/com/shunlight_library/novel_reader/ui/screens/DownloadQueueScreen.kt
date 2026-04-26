/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Download queue screen.
 */
package com.shunlight_library.novel_reader.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.entity.RegistrationQueueEntity
import com.shunlight_library.novel_reader.ui.viewmodel.DownloadQueueViewModel
import com.shunlight_library.novel_reader.ui.viewmodel.QueueFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    onNavigateBack: () -> Unit,
    viewModel: DownloadQueueViewModel
) {
    val queues by viewModel.queues.collectAsState(initial = emptyList())
    val processingQueues by viewModel.processingQueues.collectAsState(initial = emptyList())
    val errorQueues by viewModel.errorQueues.collectAsState(initial = emptyList())
    val timeoutQueues by viewModel.timeoutQueues.collectAsState(initial = emptyList())
    val pausedQueues by viewModel.pausedQueues.collectAsState(initial = emptyList())
    val currentFilter by viewModel.filterState.collectAsState()
    val filteredQueues by viewModel.filteredQueues.collectAsState()

    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("一括削除") },
            text = { Text("「${currentFilter.label}」の${filteredQueues.size}件を削除しますか？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bulkDelete()
                        showBulkDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("キャンセル") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ダウンロード状況") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showBulkDeleteConfirm = true },
                        enabled = filteredQueues.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "一括削除")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // フィルターチップ行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QueueFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = currentFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                StatusSummaryRow(
                    processingCount = processingQueues.size,
                    errorCount = errorQueues.size,
                    timeoutCount = timeoutQueues.size,
                    pausedCount = pausedQueues.size,
                    totalCount = queues.size
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredQueues.isEmpty()) {
                EmptyQueueMessage()
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredQueues) { queue ->
                        QueueItem(
                            queue = queue,
                            onPause = { viewModel.pauseQueue(queue.id) },
                            onCancel = { viewModel.cancelQueue(queue.id) },
                            onRetry = { viewModel.retryQueue(queue.id) },
                            onDeleteCompleted = { viewModel.deleteCompletedQueue(queue.id) },
                            onDeleteFailed = { viewModel.deleteFailedQueue(queue.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
fun StatusSummaryRow(
    processingCount: Int,
    errorCount: Int,
    timeoutCount: Int,
    pausedCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "合計 $totalCount 件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (processingCount > 0) {
                StatusBadge(text = "処理中: $processingCount", color = MaterialTheme.colorScheme.primary)
            }
            if (pausedCount > 0) {
                StatusBadge(text = "一時停止: $pausedCount", color = Color(0xFF9E9E9E))
            }
            if (timeoutCount > 0) {
                StatusBadge(text = "タイムアウト: $timeoutCount", color = Color(0xFFFF8C00))
            }
            if (errorCount > 0) {
                StatusBadge(text = "エラー: $errorCount", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EmptyQueueMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "キューは空です",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "小説サイトから小説を登録すると、ここに表示されます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QueueItem(
    queue: RegistrationQueueEntity,
    onPause: () -> Unit = {},
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDeleteCompleted: () -> Unit,
    onDeleteFailed: () -> Unit = {}
) {
    val statusColor = when (queue.status) {
        RegistrationQueueEntity.STATUS_PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        RegistrationQueueEntity.STATUS_PROCESSING -> MaterialTheme.colorScheme.primary
        RegistrationQueueEntity.STATUS_ERROR -> MaterialTheme.colorScheme.error
        RegistrationQueueEntity.STATUS_COMPLETED -> MaterialTheme.colorScheme.primary
        RegistrationQueueEntity.STATUS_TIMEOUT -> Color(0xFFFF8C00)
        RegistrationQueueEntity.STATUS_PAUSED -> Color(0xFF9E9E9E)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val statusText = when (queue.status) {
        RegistrationQueueEntity.STATUS_PENDING -> "待機中"
        RegistrationQueueEntity.STATUS_PROCESSING -> "処理中"
        RegistrationQueueEntity.STATUS_ERROR -> "エラー"
        RegistrationQueueEntity.STATUS_COMPLETED -> "完了"
        RegistrationQueueEntity.STATUS_TIMEOUT -> "タイムアウト"
        RegistrationQueueEntity.STATUS_PAUSED -> "一時停止"
        else -> "不明"
    }

    val startTimeText = (queue.started_at ?: queue.created_at).let { timeStr ->
        try {
            val inputFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val outputFmt = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
            outputFmt.format(inputFmt.parse(timeStr) ?: java.util.Date())
        } catch (e: Exception) {
            timeStr
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = queue.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = queue.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "開始: $startTimeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(text = statusText, color = statusColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (queue.status == RegistrationQueueEntity.STATUS_PROCESSING && queue.total_episodes > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "エピソード取得中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${queue.current_episode} / ${queue.total_episodes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { queue.current_episode.toFloat() / queue.total_episodes.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (queue.status == RegistrationQueueEntity.STATUS_PAUSED && !queue.error_message.isNullOrBlank()) {
                Text(
                    text = queue.error_message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9E9E9E),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (queue.total_episodes > 0 && queue.current_episode > 0) {
                    LinearProgressIndicator(
                        progress = { queue.current_episode.toFloat() / queue.total_episodes.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF9E9E9E),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (queue.status == RegistrationQueueEntity.STATUS_TIMEOUT && !queue.error_message.isNullOrBlank()) {
                Text(
                    text = queue.error_message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF8C00),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (queue.total_episodes > 0 && queue.current_episode > 0) {
                    LinearProgressIndicator(
                        progress = { queue.current_episode.toFloat() / queue.total_episodes.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFF8C00),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (queue.status == RegistrationQueueEntity.STATUS_ERROR && !queue.error_message.isNullOrBlank()) {
                Text(
                    text = "エラー: ${queue.error_message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (queue.status == RegistrationQueueEntity.STATUS_PENDING) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("一時停止")
                    }
                }

                if (queue.status == RegistrationQueueEntity.STATUS_PROCESSING) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("一時停止")
                    }
                }

                if (queue.status == RegistrationQueueEntity.STATUS_ERROR) {
                    OutlinedButton(
                        onClick = onDeleteFailed,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("再試行")
                    }
                }

                if (queue.status == RegistrationQueueEntity.STATUS_TIMEOUT) {
                    OutlinedButton(
                        onClick = onDeleteFailed,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8C00))
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("続きから再試行")
                    }
                }

                if (queue.status == RegistrationQueueEntity.STATUS_PAUSED) {
                    OutlinedButton(
                        onClick = onDeleteFailed,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onRetry) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("続きから再開")
                    }
                }

                if (queue.status == RegistrationQueueEntity.STATUS_COMPLETED) {
                    OutlinedButton(
                        onClick = onDeleteCompleted,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                }
            }
        }
    }
}
