/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Dialog displaying stored notifications.
 */
package com.shunlight_library.novel_reader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shunlight_library.novel_reader.data.AppNotification
import com.shunlight_library.novel_reader.data.NotificationStore
import com.shunlight_library.novel_reader.data.NotificationType
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
    val scope = rememberCoroutineScope()

    // 通知データを読み込み
    LaunchedEffect(key1 = Unit) {
        scope.launch {
            notifications = notificationStore.getAllNotifications()
        }
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
                    items(notifications) { notification ->
                        NotificationItem(
                            notification = notification,
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
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(notification.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
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