/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Settings hub screen — lists setting categories.
 */
package com.shunlight_library.novel_reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.navigation.Screen

private data class SettingsCategory(
    val icon: String,
    val title: String,
    val subtitle: String,
    val destination: Screen,
    val developerOnly: Boolean = false
)

private val allCategories = listOf(
    SettingsCategory("🎨", "表示・フォント", "テーマ、フォント、文字サイズ、向き、リスト表示", Screen.SettingsDisplay),
    SettingsCategory("📖", "読書設定", "背景色、フォント色、スワイプ・タップ操作", Screen.SettingsReading),
    SettingsCategory("🌐", "ネットワーク", "自己サーバーアクセス設定", Screen.SettingsNetwork),
    SettingsCategory("🔄", "自動更新", "更新時刻、自動ダウンロード", Screen.SettingsAutoUpdate),
    SettingsCategory("💾", "ストレージ・DB", "画像保存先、データベース同期・整合性", Screen.SettingsStorage),
    SettingsCategory("🛠", "開発者オプション", "エラーログ追跡、通知履歴、診断情報", Screen.SettingsDeveloper, developerOnly = true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMenuScreen(
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    developerUnlocked: Boolean = false
) {
    val visibleCategories = remember(developerUnlocked) {
        allCategories.filter { !it.developerOnly || developerUnlocked }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(visibleCategories) { category ->
                SettingsCategoryRow(
                    icon = category.icon,
                    title = category.title,
                    subtitle = category.subtitle,
                    onClick = { onNavigate(category.destination) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .size(40.dp)
                .wrapContentSize(Alignment.Center)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
