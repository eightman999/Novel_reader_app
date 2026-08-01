/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Displays recently read novels.
 */
package com.shunlight_library.novel_reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LastReadNovelWithInfo(
    val lastRead: LastReadNovelEntity,
    val novel: NovelDescEntity?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyReadNovelsScreen(
    onBack: () -> Unit,
    onNovelClick: (String, String) -> Unit // ncode, episodeNo
) {
    val repository = NovelReaderApplication.getRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsStore = remember(context) { SettingsStore(context.applicationContext) }

    // 状態変数
    var allItems by remember { mutableStateOf<List<LastReadNovelWithInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var filterSettings by remember { mutableStateOf(FilterSettings()) }
    var useSimpleListMode by remember { mutableStateOf(false) }

    // データの取得
    LaunchedEffect(Unit) {
        useSimpleListMode = settingsStore.getUseSimpleListMode()
        val lastReadNovels = repository.allLastReadNovels.first()
        val ncodes = lastReadNovels.map { it.ncode }
        val novels = repository.getNovelsByNcodes(ncodes)
        val novelMap = novels.associateBy { it.ncode }
        allItems = lastReadNovels.map { lastRead ->
            LastReadNovelWithInfo(lastRead, novelMap[lastRead.ncode])
        }
        isLoading = false
    }

    // フィルタリング適用
    val displayedItems by remember(allItems, filterSettings) {
        derivedStateOf {
            allItems.filter { item ->
                val novel = item.novel ?: return@filter true
                val unreadCount = maxOf(0, novel.total_ep - item.lastRead.episode_no)
                if (filterSettings.showUnreadOnly && unreadCount == 0) return@filter false
                if (filterSettings.showNoUnreadOnly && unreadCount > 0) return@filter false
                if (filterSettings.showCompletedOnly && novel.end_flag != 1) return@filter false
                if (filterSettings.showOngoingOnly && novel.end_flag != 2) return@filter false
                if (!filterSettings.showLongNovels && novel.noveltype == 1) return@filter false
                if (!filterSettings.showShortNovels && novel.noveltype == 2) return@filter false
                if (filterSettings.selectedSubSites.isNotEmpty()) {
                    val novelSubSite = if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) SubSiteConst.KAKUYOMU
                    else novel.sub_site.takeIf { it > 0 } ?: SubSiteConst.SYOSETU
                    if (novelSubSite !in filterSettings.selectedSubSites) return@filter false
                }
                if (filterSettings.showFavoritesOnly && novel.is_favorite != 1) return@filter false
                true
            }
        }
    }

    // フィルターダイアログ
    if (showFilterDialog) {
        var tempFilter by remember(showFilterDialog, filterSettings) { mutableStateOf(filterSettings) }
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("フィルター設定") },
            text = {
                Column(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                    // 未読フィルター
                    Text("未読フィルター", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        tempFilter = tempFilter.copy(showUnreadOnly = !tempFilter.showUnreadOnly, showNoUnreadOnly = if (!tempFilter.showUnreadOnly) false else tempFilter.showNoUnreadOnly)
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tempFilter.showUnreadOnly, onCheckedChange = { tempFilter = tempFilter.copy(showUnreadOnly = it, showNoUnreadOnly = if (it) false else tempFilter.showNoUnreadOnly) })
                        Text("未読ありのみ", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        tempFilter = tempFilter.copy(showNoUnreadOnly = !tempFilter.showNoUnreadOnly, showUnreadOnly = if (!tempFilter.showNoUnreadOnly) false else tempFilter.showUnreadOnly)
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tempFilter.showNoUnreadOnly, onCheckedChange = { tempFilter = tempFilter.copy(showNoUnreadOnly = it, showUnreadOnly = if (it) false else tempFilter.showUnreadOnly) })
                        Text("未読なしのみ", modifier = Modifier.padding(start = 8.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 完結フィルター
                    Text("完結フィルター", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        tempFilter = tempFilter.copy(showCompletedOnly = !tempFilter.showCompletedOnly, showOngoingOnly = if (!tempFilter.showCompletedOnly) false else tempFilter.showOngoingOnly)
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tempFilter.showCompletedOnly, onCheckedChange = { tempFilter = tempFilter.copy(showCompletedOnly = it, showOngoingOnly = if (it) false else tempFilter.showOngoingOnly) })
                        Text("完結済みのみ", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        tempFilter = tempFilter.copy(showOngoingOnly = !tempFilter.showOngoingOnly, showCompletedOnly = if (!tempFilter.showOngoingOnly) false else tempFilter.showCompletedOnly)
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tempFilter.showOngoingOnly, onCheckedChange = { tempFilter = tempFilter.copy(showOngoingOnly = it, showCompletedOnly = if (it) false else tempFilter.showCompletedOnly) })
                        Text("未完結のみ", modifier = Modifier.padding(start = 8.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 種別フィルター
                    Text("種別フィルター", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().clickable { tempFilter = tempFilter.copy(showLongNovels = !tempFilter.showLongNovels) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tempFilter.showLongNovels, onCheckedChange = { tempFilter = tempFilter.copy(showLongNovels = it) })
                        Text("長編を表示", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth().clickable { tempFilter = tempFilter.copy(showShortNovels = !tempFilter.showShortNovels) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tempFilter.showShortNovels, onCheckedChange = { tempFilter = tempFilter.copy(showShortNovels = it) })
                        Text("短編を表示", modifier = Modifier.padding(start = 8.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 媒体フィルター
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("媒体フィルター", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { tempFilter = tempFilter.copy(selectedSubSites = emptySet()) }) { Text("全選択", style = MaterialTheme.typography.bodySmall) }
                    }
                    SubSiteConst.labels.forEach { (siteId, label) ->
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            val cur = tempFilter.selectedSubSites.toMutableSet()
                            if (siteId in cur) cur.remove(siteId) else cur.add(siteId)
                            tempFilter = tempFilter.copy(selectedSubSites = cur)
                        }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = tempFilter.selectedSubSites.isEmpty() || siteId in tempFilter.selectedSubSites,
                                onCheckedChange = { checked ->
                                    val cur = tempFilter.selectedSubSites.toMutableSet()
                                    if (checked) cur.add(siteId) else cur.remove(siteId)
                                    tempFilter = tempFilter.copy(selectedSubSites = cur)
                                }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { filterSettings = tempFilter; showFilterDialog = false }) { Text("適用") }
            },
            dismissButton = {
                TextButton(onClick = { filterSettings = FilterSettings(); showFilterDialog = false }) { Text("リセット") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("最近読んだ小説") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "フィルター")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (displayedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(if (allItems.isEmpty()) "最近読んだ小説はありません" else "条件に一致する小説はありません")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(displayedItems, key = { it.lastRead.ncode }) { item ->
                    RecentlyReadNovelItem(
                        lastReadNovel = item.lastRead,
                        novel = item.novel,
                        useSimpleMode = useSimpleListMode,
                        onClick = { onNovelClick(item.lastRead.ncode, item.lastRead.episode_no.toString()) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun RecentlyReadNovelItem(
    lastReadNovel: LastReadNovelEntity,
    novel: NovelDescEntity?,
    useSimpleMode: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (useSimpleMode) 0.dp else 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (useSimpleMode) 0.dp else 2.dp),
        colors = if (useSimpleMode) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                 else CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (useSimpleMode) 8.dp else 16.dp)
        ) {
            if (novel != null) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "作者: ${novel.author}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "第${lastReadNovel.episode_no}話",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "最終閲覧: ${lastReadNovel.date}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (novel.total_ep > lastReadNovel.episode_no) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "未読: ${novel.total_ep - lastReadNovel.episode_no}話",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                Text(
                    text = "Nコード: ${lastReadNovel.ncode}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "エピソード: ${lastReadNovel.episode_no}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "最終閲覧: ${lastReadNovel.date}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "※小説情報が見つかりません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}