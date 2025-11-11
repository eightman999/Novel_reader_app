/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Screen showing novel list with search and filters.
 */
package com.shunlight_library.novel_reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.api.NovelApiUtils
import kotlinx.coroutines.launch

// 並び替え条件を定義する列挙型
enum class SortField(val displayName: String) {
    NCODE("作品ID"),
    TITLE("タイトル"),
    AUTHOR("作者"),
    TOTAL_EP("総話数"),
    UNREAD_COUNT("未読数"),
    LENGTH("文字数"),
    LAST_UPDATE_DATE("更新日")
}

// 並び替え方向を定義する列挙型
enum class SortDirection {
    ASCENDING, DESCENDING
}

// 検索対象を定義する列挙型
enum class SearchField(val displayName: String) {
    NCODE("作品ID"),
    TITLE("タイトル"),
    AUTHOR("作者")
}

// フィルター設定のデータクラス
// フィルター設定のデータクラス
data class FilterSettings(
    val minRating: Int = 0,
    val maxRating: Int = 5,  // 最高レーティングを追加
    val hideRating5WithNoEpisodes: Boolean = false,
    val showCompleted: Boolean = true,
    val showOngoing: Boolean = true,
    val showFavoritesOnly: Boolean = false,
    val showLongNovels: Boolean = true,
    val showShortNovels: Boolean = true
)

// 小説と既読情報を組み合わせたデータクラス
data class NovelWithReadInfo(
    val novel: NovelDescEntity,
    val lastRead: LastReadNovelEntity?,
    val unreadCount: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun NovelListScreen(
    onBack: () -> Unit,
    onNovelClick: (String) -> Unit
) {
    val repository = NovelReaderApplication.getRepository()
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsStore = remember(context) { SettingsStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 表示設定の状態変数
    var showTitle by remember { mutableStateOf(true) }
    var showAuthor by remember { mutableStateOf(true) }
    var showSynopsis by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(true) }
    var showRating by remember { mutableStateOf(false) }
    var showUpdateDate by remember { mutableStateOf(true) }
    var showEpisodeCount by remember { mutableStateOf(true) }

    // 並び替えとフィルタリングの状態変数
    var sortField by remember { mutableStateOf(SortField.LAST_UPDATE_DATE) }
    var sortDirection by remember { mutableStateOf(SortDirection.DESCENDING) }
    var filterSettings by remember { mutableStateOf(FilterSettings()) }

    // 検索関連の状態変数
    var searchText by remember { mutableStateOf("") }
    var searchField by remember { mutableStateOf(SearchField.TITLE) }
    var isSearching by remember { mutableStateOf(false) }

    // ダイアログ表示状態
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSearchFieldDialog by remember { mutableStateOf(false) }

    // 小説リストの状態
    var novelList by remember { mutableStateOf<List<NovelDescEntity>>(emptyList()) }
    var lastReadMap by remember { mutableStateOf<Map<String, LastReadNovelEntity>>(emptyMap()) }

    val listState = rememberLazyListState()

    // 設定読み込み完了フラグ
    var settingsLoaded by remember { mutableStateOf(false) }

    // 設定を保存する関数
    val saveCurrentSettings: suspend () -> Unit = {
        val settings = NovelListFilterSettings(
            sortField = sortField.name,
            sortDirection = sortDirection.name,
            minRating = filterSettings.minRating,
            maxRating = filterSettings.maxRating,
            hideRating5WithNoEpisodes = filterSettings.hideRating5WithNoEpisodes,
            showCompleted = filterSettings.showCompleted,
            showOngoing = filterSettings.showOngoing,
            showFavoritesOnly = filterSettings.showFavoritesOnly,
            showLongNovels = filterSettings.showLongNovels,
            showShortNovels = filterSettings.showShortNovels
        )
        settingsStore.saveNovelListFilterSettings(settings)
    }


    val allNovels by remember(novelList, lastReadMap) {
        derivedStateOf {
            novelList.map { novel ->
                val lastRead = lastReadMap[novel.ncode]
                val unreadCount = if (lastRead != null) {
                    maxOf(0, novel.total_ep - lastRead.episode_no)
                } else {
                    novel.total_ep
                }
                NovelWithReadInfo(novel, lastRead, unreadCount)

            }
        }
    }

    val displayedNovels by remember(
        allNovels,
        filterSettings,
        searchText,
        searchField,
        sortField,
        sortDirection
    ) {
        derivedStateOf {
            val filtered = allNovels.filter { novelWithInfo ->
                val novel = novelWithInfo.novel

                if (filterSettings.hideRating5WithNoEpisodes &&
                    novel.rating == 5 && novel.total_ep == 0) {
                    return@filter false
                }

                if (novel.rating < filterSettings.minRating || novel.rating > filterSettings.maxRating) {
                    return@filter false
                }

                if (filterSettings.showFavoritesOnly && !novel.is_favorite) {
                    return@filter false
                }

                if (!filterSettings.showLongNovels && novel.noveltype == 1) {
                    return@filter false
                }
                if (!filterSettings.showShortNovels && novel.noveltype == 2) {
                    return@filter false
                }

                if (searchText.isNotEmpty()) {
                    when (searchField) {
                        SearchField.NCODE -> if (!novel.ncode.contains(searchText, ignoreCase = true)) {
                            return@filter false
                        }

                        SearchField.TITLE -> if (!novel.title.contains(searchText, ignoreCase = true)) {
                            return@filter false
                        }

                        SearchField.AUTHOR -> if (!novel.author.contains(searchText, ignoreCase = true)) {
                            return@filter false
                        }
                    }
                }

                true
            }

            when (sortField) {
                SortField.NCODE -> if (sortDirection == SortDirection.ASCENDING) {
                    filtered.sortedBy { it.novel.ncode }
                } else {
                    filtered.sortedByDescending { it.novel.ncode }
                }

                SortField.TITLE -> if (sortDirection == SortDirection.ASCENDING) {
                    filtered.sortedBy { it.novel.title }
                } else {
                    filtered.sortedByDescending { it.novel.title }
                }

                SortField.AUTHOR -> if (sortDirection == SortDirection.ASCENDING) {
                    filtered.sortedBy { it.novel.author }
                } else {
                    filtered.sortedByDescending { it.novel.author }
                }

                SortField.TOTAL_EP -> if (sortDirection == SortDirection.ASCENDING) {
                    filtered.sortedBy { it.novel.total_ep }
                } else {
                    filtered.sortedByDescending { it.novel.total_ep }
                }

                SortField.UNREAD_COUNT -> if (sortDirection == SortDirection.ASCENDING) {
                    filtered.sortedBy { it.unreadCount }
                } else {
                    filtered.sortedByDescending { it.unreadCount }
                }

                SortField.LENGTH -> if (sortDirection == SortDirection.ASCENDING) {
                    filtered.sortedBy { it.novel.length ?: 0 }
                } else {
                    filtered.sortedByDescending { it.novel.length ?: 0 }
                }

                SortField.LAST_UPDATE_DATE -> if (sortDirection == SortDirection.ASCENDING) {
                    filtered.sortedBy { it.novel.last_update_date }
                } else {
                    filtered.sortedByDescending { it.novel.last_update_date }
                }
            }
        }
    }

    // 設定の読み込み（初回のみ）
    LaunchedEffect(key1 = Unit) {
        // 表示設定の取得
        val displaySettings = settingsStore.getDisplaySettings()
        showTitle = displaySettings.showTitle
        showAuthor = displaySettings.showAuthor
        showSynopsis = displaySettings.showSynopsis
        showTags = displaySettings.showTags
        showRating = displaySettings.showRating
        showUpdateDate = displaySettings.showUpdateDate
        showEpisodeCount = displaySettings.showEpisodeCount
        
        // フィルター設定の取得
        val savedFilterSettings = settingsStore.getNovelListFilterSettings()
        sortField = try {
            SortField.valueOf(savedFilterSettings.sortField)
        } catch (e: IllegalArgumentException) {
            SortField.LAST_UPDATE_DATE
        }
        sortDirection = try {
            SortDirection.valueOf(savedFilterSettings.sortDirection)
        } catch (e: IllegalArgumentException) {
            SortDirection.DESCENDING
        }
        filterSettings = FilterSettings(
            minRating = savedFilterSettings.minRating,
            maxRating = savedFilterSettings.maxRating,
            hideRating5WithNoEpisodes = savedFilterSettings.hideRating5WithNoEpisodes,
            showCompleted = savedFilterSettings.showCompleted,
            showOngoing = savedFilterSettings.showOngoing,
            showFavoritesOnly = savedFilterSettings.showFavoritesOnly,
            showLongNovels = savedFilterSettings.showLongNovels,
            showShortNovels = savedFilterSettings.showShortNovels
        )

        settingsLoaded = true
    }

    // 最終既読情報の取得
    LaunchedEffect(settingsLoaded) {
        if (!settingsLoaded) return@LaunchedEffect
        repository.allLastReadNovels.collect { lastReadList ->
            lastReadMap = lastReadList.associateBy { it.ncode }
        }
    }

    // 小説データの取得
    LaunchedEffect(settingsLoaded) {
        if (!settingsLoaded) return@LaunchedEffect
        repository.allNovels.collect { novelsList ->
            novelList = novelsList
        }
    }

    // フィルターや並び替え設定が変更されたら自動保存
    LaunchedEffect(sortField, sortDirection, filterSettings, settingsLoaded) {
        if (settingsLoaded) {
            saveCurrentSettings()
        }
    }

    // 並び替えダイアログ
    if (showSortDialog) {
        var tempSortField by remember(showSortDialog, sortField) { mutableStateOf(sortField) }
        var tempSortDirection by remember(showSortDialog, sortDirection) { mutableStateOf(sortDirection) }

        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("並び替え") },
            text = {
                Column(modifier = Modifier.padding(8.dp)) {
                    // 並び替えフィールドの選択
                    Text("並び替え項目", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    SortField.values().forEach { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempSortField = field }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempSortField == field,
                                onClick = { tempSortField = field }
                            )
                            Text(
                                text = field.displayName,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 昇順/降順の選択
                    Text("並び順", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempSortDirection = SortDirection.ASCENDING }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tempSortDirection == SortDirection.ASCENDING,
                            onClick = { tempSortDirection = SortDirection.ASCENDING }
                        )
                        Text(
                            text = "昇順 (A→Z, 小→大)",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempSortDirection = SortDirection.DESCENDING }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tempSortDirection == SortDirection.DESCENDING,
                            onClick = { tempSortDirection = SortDirection.DESCENDING }
                        )
                        Text(
                            text = "降順 (Z→A, 大→小)",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    sortField = tempSortField
                    sortDirection = tempSortDirection
                    showSortDialog = false
                    if (settingsLoaded) {
                        scope.launch { saveCurrentSettings() }
                    }
                }) {
                    Text("適用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // フィルターダイアログ
    // フィルターダイアログ
    if (showFilterDialog) {
        var tempFilterSettings by remember(showFilterDialog, filterSettings) { mutableStateOf(filterSettings) }

        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("フィルター設定") },
            text = {
                Column(modifier = Modifier.padding(8.dp)) {
                    // 最低レーティング
                    Text("最低レーティング: ${tempFilterSettings.minRating}",
                        style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = tempFilterSettings.minRating.toFloat(),
                        onValueChange = {
                            // 最低値は最高値を超えないようにする
                            val newMinRating = minOf(it.toInt(), tempFilterSettings.maxRating)
                            tempFilterSettings = tempFilterSettings.copy(minRating = newMinRating)
                        },
                        valueRange = 0f..5f,
                        steps = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0")
                        Text("1")
                        Text("2")
                        Text("3")
                        Text("4")
                        Text("5")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 最高レーティング
                    Text("最高レーティング: ${tempFilterSettings.maxRating}",
                        style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = tempFilterSettings.maxRating.toFloat(),
                        onValueChange = {
                            // 最高値は最低値を下回らないようにする
                            val newMaxRating = maxOf(it.toInt(), tempFilterSettings.minRating)
                            tempFilterSettings = tempFilterSettings.copy(maxRating = newMaxRating)
                        },
                        valueRange = 0f..5f,
                        steps = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0")
                        Text("1")
                        Text("2")
                        Text("3")
                        Text("4")
                        Text("5")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // チェックボックス式のフィルター
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempFilterSettings = tempFilterSettings.copy(
                                    hideRating5WithNoEpisodes = !tempFilterSettings.hideRating5WithNoEpisodes
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tempFilterSettings.hideRating5WithNoEpisodes,
                            onCheckedChange = { checked ->
                                tempFilterSettings = tempFilterSettings.copy(
                                    hideRating5WithNoEpisodes = checked
                                )
                            }
                        )
                        Text(
                            text = "評価5かつエピソード0の作品を非表示",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // お気に入りのみ表示チェックボックス
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempFilterSettings = tempFilterSettings.copy(
                                    showFavoritesOnly = !tempFilterSettings.showFavoritesOnly
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tempFilterSettings.showFavoritesOnly,
                            onCheckedChange = { checked ->
                                tempFilterSettings = tempFilterSettings.copy(
                                    showFavoritesOnly = checked
                                )
                            }
                        )
                        Text(
                            text = "お気に入りのみ表示",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // 長編を表示
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempFilterSettings = tempFilterSettings.copy(
                                    showLongNovels = !tempFilterSettings.showLongNovels
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tempFilterSettings.showLongNovels,
                            onCheckedChange = { checked ->
                                tempFilterSettings = tempFilterSettings.copy(showLongNovels = checked)
                            }
                        )
                        Text(
                            text = "長編を表示",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // 短編を表示
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempFilterSettings = tempFilterSettings.copy(
                                    showShortNovels = !tempFilterSettings.showShortNovels
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tempFilterSettings.showShortNovels,
                            onCheckedChange = { checked ->
                                tempFilterSettings = tempFilterSettings.copy(showShortNovels = checked)
                            }
                        )
                        Text(
                            text = "短編を表示",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    filterSettings = tempFilterSettings
                    showFilterDialog = false
                    if (settingsLoaded) {
                        scope.launch { saveCurrentSettings() }
                    }
                }) {
                    Text("適用")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    // すべてのフィルターをリセット
                    filterSettings = FilterSettings()
                    showFilterDialog = false
                    if (settingsLoaded) {
                        scope.launch { saveCurrentSettings() }
                    }
                }) {
                    Text("リセット")
                }
            }
        )
    }
    // 検索フィールド選択ダイアログ
    if (showSearchFieldDialog) {
        AlertDialog(
            onDismissRequest = { showSearchFieldDialog = false },
            title = { Text("検索対象を選択") },
            text = {
                Column(modifier = Modifier.padding(8.dp)) {
                    SearchField.values().forEach { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchField = field
                                    showSearchFieldDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = searchField == field,
                                onClick = {
                                    searchField = field
                                    showSearchFieldDialog = false
                                }
                            )
                            Text(
                                text = field.displayName,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("小説一覧") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    actions = {
                        // 現在の並び替え方向を表示するアイコン
                        IconButton(onClick = {
                            // 並び替え方向を切り替える
                            sortDirection = if (sortDirection == SortDirection.ASCENDING) {
                                SortDirection.DESCENDING
                            } else {
                                SortDirection.ASCENDING
                            }
                            if (settingsLoaded) {
                                scope.launch { saveCurrentSettings() }
                            }
                        }) {
                            Icon(
                                if (sortDirection == SortDirection.ASCENDING)
                                    Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = if (sortDirection == SortDirection.ASCENDING)
                                    "昇順" else "降順"
                            )
                        }

                        // 並び替えボタン
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "並び替え")
                        }

                        // フィルターボタン
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "フィルター")
                        }
                    }
                )

                // 検索バー
                if (isSearching) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("${searchField.displayName}を検索...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboardController?.hide() }
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "検索")
                            },
                            trailingIcon = {
                                Row {
                                    if (searchText.isNotEmpty()) {
                                        IconButton(onClick = { searchText = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                                        }
                                    }

                                    // 検索対象選択ボタン
                                    IconButton(onClick = { showSearchFieldDialog = true }) {
                                        Icon(Icons.Default.ArrowDropDown,
                                            contentDescription = "検索対象を選択")
                                    }
                                }
                            }
                        )

                        // 検索バーを閉じるボタン
                        IconButton(onClick = {
                            isSearching = false
                            searchText = ""
                            keyboardController?.hide()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "検索を閉じる")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // 検索FAB（検索バーが表示されていない場合のみ表示）
            if (!isSearching) {
                FloatingActionButton(
                    onClick = { isSearching = true },
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Search, contentDescription = "検索")
                }
            }
        }
    ) { innerPadding ->
        if (displayedNovels.isEmpty()) {
            // 小説がない場合
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (searchText.isNotEmpty()) {
                    Text("「${searchText}」に一致する${searchField.displayName}が見つかりません")
                } else if (allNovels.isEmpty()) {
                    Text("小説が登録されていません")
                } else {
                    Text("フィルター条件に一致する小説がありません")
                }
            }
        } else {
            // 小説リストの表示
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                state = listState
            ) {
                item {
                    // 結果件数を表示
                    Text(
                        text = "${displayedNovels.size}件の小説が見つかりました",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(
                    items = displayedNovels,
                    key = { it.novel.ncode }
                ) { novelWithReadInfo ->
                    NovelListItem(
                        novel = novelWithReadInfo.novel,
                        unreadCount = novelWithReadInfo.unreadCount,
                        showTitle = showTitle,
                        showAuthor = showAuthor,
                        showSynopsis = showSynopsis,
                        showTags = showTags,
                        showRating = showRating,
                        showUpdateDate = showUpdateDate,
                        showEpisodeCount = showEpisodeCount,
                        onClick = { onNovelClick(novelWithReadInfo.novel.ncode) },
                        onFavoriteClick = { isFavorite ->
                            scope.launch {
                                repository.updateFavoriteStatus(novelWithReadInfo.novel.ncode, isFavorite)
                                novelList = novelList.map { novel ->
                                    if (novel.ncode == novelWithReadInfo.novel.ncode) {
                                        novel.copy(is_favorite = isFavorite)
                                    } else {
                                        novel
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun NovelListItem(
    novel: NovelDescEntity,
    unreadCount: Int,
    showTitle: Boolean,
    showAuthor: Boolean,
    showSynopsis: Boolean,
    showTags: Boolean,
    showRating: Boolean,
    showUpdateDate: Boolean,
    showEpisodeCount: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                if (showTitle) {
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (showAuthor) {
                    Text(
                        text = "作者: ${novel.author}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (showSynopsis) {
                    Text(
                        text = novel.Synopsis,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (showTags) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "タグ: ${novel.main_tag}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (novel.sub_tag.isNotEmpty()) {
                            Text(
                                text = ", ${novel.sub_tag}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (showRating) {
                        Text(
                            text = "評価: ${novel.rating}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (showUpdateDate) {
                        Text(
                            text = "更新: ${novel.last_update_date}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (showEpisodeCount) {
                        Row {
                            Text(
                                text = "全${novel.total_ep}話",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            if (unreadCount > 0) {
                                Text(
                                    text = "(未読${unreadCount}話)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = { onFavoriteClick(!novel.is_favorite) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (novel.is_favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (novel.is_favorite) "お気に入りから削除" else "お気に入りに追加",
                    tint = if (novel.is_favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
