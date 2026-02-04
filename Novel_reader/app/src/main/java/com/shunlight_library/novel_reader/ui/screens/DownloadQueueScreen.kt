/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Download queue screen.
 */
package com.shunlight_library.novel_reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.entity.RegistrationQueueEntity
import com.shunlight_library.novel_reader.ui.viewmodel.DownloadQueueViewModel

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ダウンロードキュー") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            StatusSummaryRow(
                processingCount = processingQueues.size,
                errorCount = errorQueues.size,
                timeoutCount = timeoutQueues.size,
                pausedCount = pausedQueues.size,
                totalCount = queues.size
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (queues.isEmpty()) {
                EmptyQueueMessage()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(queues) { queue ->
                        QueueItem(
                            queue = queue,
                            onPause = { viewModel.pauseQueue(queue.id) },
                            onCancel = { viewModel.cancelQueue(queue.id) },
                            onRetry = { viewModel.retryQueue(queue.id) },
                            onDeleteCompleted = { viewModel.deleteCompletedQueue(queue.id) },
                            onDeleteFailed = { viewModel.deleteFailedQueue(queue.id) }
                        )
                    }
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
        RegistrationQueueEntity.STATUS_TIMEOUT -> Color(0xFFFF8C00) // オレンジ
        RegistrationQueueEntity.STATUS_PAUSED -> Color(0xFF9E9E9E) // グレー
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = queue.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = queue.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
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

            // 一時停止時のメッセージ表示
            if (queue.status == RegistrationQueueEntity.STATUS_PAUSED && !queue.error_message.isNullOrBlank()) {
                Text(
                    text = queue.error_message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9E9E9E),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // 一時停止時の進捗バー（途中まで取得済み）
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

            // タイムアウト時のメッセージ表示
            if (queue.status == RegistrationQueueEntity.STATUS_TIMEOUT && !queue.error_message.isNullOrBlank()) {
                Text(
                    text = queue.error_message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF8C00),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // タイムアウト時の進捗バー（途中まで取得済み）
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
                // 待機中: 一時停止 + 削除
                if (queue.status == RegistrationQueueEntity.STATUS_PENDING) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onPause
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("一時停止")
                    }
                }

                // 処理中: 一時停止 + 削除
                if (queue.status == RegistrationQueueEntity.STATUS_PROCESSING) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onPause
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("一時停止")
                    }
                }

                // エラー時: 再試行 + 削除
                if (queue.status == RegistrationQueueEntity.STATUS_ERROR) {
                    OutlinedButton(
                        onClick = onDeleteFailed,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRetry
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("再試行")
                    }
                }

                // タイムアウト時: 削除 + 続きから再試行
                if (queue.status == RegistrationQueueEntity.STATUS_TIMEOUT) {
                    OutlinedButton(
                        onClick = onDeleteFailed,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF8C00)
                        )
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("続きから再試行")
                    }
                }

                // 一時停止: 削除 + 続きから再開
                if (queue.status == RegistrationQueueEntity.STATUS_PAUSED) {
                    OutlinedButton(
                        onClick = onDeleteFailed,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("削除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRetry
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("続きから再開")
                    }
                }

                // 完了: 削除ボタン
                if (queue.status == RegistrationQueueEntity.STATUS_COMPLETED) {
                    OutlinedButton(
                        onClick = onDeleteCompleted,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
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
