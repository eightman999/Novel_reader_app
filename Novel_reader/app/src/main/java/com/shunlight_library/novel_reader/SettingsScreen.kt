package com.shunlight_library.novel_reader

import android.app.TimePickerDialog
import android.graphics.Color as AndroidColor
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncManager
import com.shunlight_library.novel_reader.ui.DatabaseSyncActivity
import com.shunlight_library.novel_reader.ui.components.DatabaseFileSelector
import com.shunlight_library.novel_reader.ui.components.ServerDirectorySelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenUpdated(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    // State variables for settings with initial values from DataStore
    var themeMode by remember { mutableStateOf("System") }
    var fontFamily by remember { mutableStateOf("Gothic") }
    var fontSize by remember { mutableStateOf(16) }
    var backgroundColor by remember { mutableStateOf("White") }
    var selfServerAccess by remember { mutableStateOf(false) }
    var textOrientation by remember { mutableStateOf("Horizontal") }
    var selfServerPath by remember { mutableStateOf("") }

    // 新しい状態変数を追加
    var fontColor by remember { mutableStateOf("#000000") }
    var episodeBackgroundColor by remember { mutableStateOf("#FFFFFF") }
    var useDefaultBackground by remember { mutableStateOf(true) }

    // データベース同期関連の状態変数
    var showDBSyncDialog by remember { mutableStateOf(false) }
    var selectedDbUri by remember { mutableStateOf<Uri?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    // 表示設定の状態変数
    var showTitle by remember { mutableStateOf(true) }
    var showAuthor by remember { mutableStateOf(true) }
    var showSynopsis by remember { mutableStateOf(true) }
    var showTags by remember { mutableStateOf(true) }
    var showRating by remember { mutableStateOf(true) }
    var showUpdateDate by remember { mutableStateOf(true) }
    var showEpisodeCount by remember { mutableStateOf(true) }
    useDefaultBackground = false // 強制的に背景色設定を使用
    // 状態変数に自動更新設定を追加
    var autoUpdateEnabled by remember { mutableStateOf(false) }
    var autoUpdateTime by remember { mutableStateOf("03:00") }
    var customFontPath by remember { mutableStateOf("") }

    // 時間選択ダイアログ状態
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val actualBackgroundColor = try {
        Color(AndroidColor.parseColor(backgroundColor))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.background
    }
    // Load saved preferences when the screen is created
    LaunchedEffect(Unit) {
        try {
            fontSize = settingsStore.fontSize.first()
            fontFamily = settingsStore.fontFamily.first()
            fontColor = settingsStore.fontColor.first()
            backgroundColor = settingsStore.episodeBackgroundColor.first()
            useDefaultBackground = settingsStore.useDefaultBackground.first()
            textOrientation = settingsStore.textOrientation.first()
            autoUpdateEnabled = settingsStore.autoUpdateEnabled.first()
            autoUpdateTime = settingsStore.autoUpdateTime.first()
            customFontPath = settingsStore.customFontPath.first()
        } catch (e: Exception) {
            Log.e("EpisodeViewScreen", "設定の読み込みエラー: ${e.message}")
        }
    }
    // 時間選択ダイアログ
    if (showTimePickerDialog) {
        TimePickerDialog(
            initialTime = autoUpdateTime,
            onDismiss = { showTimePickerDialog = false },
            onTimeSelected = { selectedTime ->
                autoUpdateTime = selectedTime
                showTimePickerDialog = false
            }
        )
    }
    // データベース同期ダイアログ
    if (showDBSyncDialog && selectedDbUri != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isSyncing) {
                    showDBSyncDialog = false
                }
            },
            title = {
                Text("データベース同期")
            },
            text = {
                if (isSyncing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("同期中です。しばらくお待ちください...")
                    }
                } else {
                    Text("選択したデータベースファイルから内部データベースに同期しますか？")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isSyncing) {
                            isSyncing = true

                            // 同期処理を実行
                            scope.launch {
                                try {
                                    val syncManager = DatabaseSyncManager(context)
                                    val success = syncManager.syncFromExternalDb(selectedDbUri!!)

                                    if (success) {
                                        Toast.makeText(context, "データベースの同期に成功しました", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "データベースの同期に失敗しました", Toast.LENGTH_SHORT).show()
                                    }

                                    isSyncing = false
                                    showDBSyncDialog = false
                                } catch (e: Exception) {
                                    Log.e("SettingsScreen", "同期エラー: ${e.message}", e)
                                    Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_LONG).show()
                                    isSyncing = false
                                    showDBSyncDialog = false
                                }
                            }
                        }
                    },
                    enabled = !isSyncing
                ) {
                    Text(if (isSyncing) "同期中..." else "同期する")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDBSyncDialog = false
                    },
                    enabled = !isSyncing
                ) {
                    Text("キャンセル")
                }
            }
        )
    }

    // BackHandlerの処理
    BackHandler {
        if (showDBSyncDialog && !isSyncing) {
            showDBSyncDialog = false
        } else {
            onBack()
        }
    }

    // Background color options
    val backgroundOptions = listOf("Default", "White", "Cream", "Light Gray", "Light Blue", "Dark Gray", "Black")
    val backgroundColors = mapOf(
        "Default" to MaterialTheme.colorScheme.background,
        "White" to Color(0xFFFFFFFF),
        "Cream" to Color(0xFFF5F5DC),
        "Light Gray" to Color(0xFFEEEEEE),
        "Light Blue" to Color(0xFFE6F2FF),
        "Dark Gray" to Color(0xFF303030),
        "Black" to Color(0xFF000000)
    )

    // フォントカラーの選択肢
    val fontColorOptions = listOf("Black", "Dark Gray", "Navy", "Dark Green", "White")
    val fontColors = mapOf(
        "Black" to "#000000",
        "Dark Gray" to "#333333",
        "Navy" to "#000080",
        "Dark Green" to "#006400",
        "White" to "#FFFFFF"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        bottomBar = {
            // 画面下部に固定されるボタン
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                // 基本設定を保存
                                settingsStore.saveAllSettings(
                                    themeMode = themeMode,
                                    fontFamily = fontFamily,
                                    fontSize = fontSize,
                                    selfServerAccess = selfServerAccess,
                                    textOrientation = textOrientation,
                                    selfServerPath = selfServerPath,
                                    fontColor = fontColor,
                                    episodeBackgroundColor = episodeBackgroundColor,
                                    useDefaultBackground = useDefaultBackground
                                )

                                // 表示設定を保存
                                settingsStore.saveDisplaySettings(
                                    DisplaySettings(
                                        showTitle = showTitle,
                                        showAuthor = showAuthor,
                                        showSynopsis = showSynopsis,
                                        showTags = showTags,
                                        showRating = showRating,
                                        showUpdateDate = showUpdateDate,
                                        showEpisodeCount = showEpisodeCount
                                    )
                                )

                                // 自動更新設定を保存
                                settingsStore.saveAutoUpdateSettings(autoUpdateEnabled, autoUpdateTime)

                                // カスタムフォント設定を保存
                                settingsStore.saveCustomFont(customFontPath)

                                // 自動更新スケジュールを設定
                                val app = context.applicationContext as NovelReaderApplication
                                app.scheduleUpdateWork(autoUpdateEnabled, autoUpdateTime)

                                // 保存したことをユーザーに通知
                                Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()

                                // 設定画面を閉じる
                                onBack()
                            } catch (e: Exception) {
                                Log.e("SettingsScreen", "設定保存エラー: ${e.message}", e)
                                Toast.makeText(context, "設定の保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("設定を保存")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Mode Setting
            SettingSection(title = "表示モード") {
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    RadioButtonOption(
                        text = "システム設定に従う",
                        selected = themeMode == "System",
                        onClick = { themeMode = "System" }
                    )
                    RadioButtonOption(
                        text = "ライトモード",
                        selected = themeMode == "Light",
                        onClick = { themeMode = "Light" }
                    )
                    RadioButtonOption(
                        text = "ダークモード",
                        selected = themeMode == "Dark",
                        onClick = { themeMode = "Dark" }
                    )
                }
            }

            HorizontalDivider()

            // Font Family Setting
            SettingSection(title = "フォント") {
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    RadioButtonOption(
                        text = "ゴシック体",
                        selected = fontFamily == "Gothic",
                        onClick = { fontFamily = "Gothic" }
                    )
                    RadioButtonOption(
                        text = "明朝体",
                        selected = fontFamily == "Mincho",
                        onClick = { fontFamily = "Mincho" }
                    )
                }
            }

            HorizontalDivider()

            // Font Size Setting
            SettingSection(title = "フォントサイズ (${fontSize}sp)") {
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.toInt() },
                    valueRange = 12f..24f,
                    steps = 6,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("小", fontSize = 12.sp)
                    Text("中", fontSize = 16.sp)
                    Text("大", fontSize = 24.sp)
                }
            }

            HorizontalDivider()


            // Self-Server Access Setting
            SettingSection(title = "自己サーバーアクセス") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自己サーバーへの接続")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = selfServerAccess,
                        onCheckedChange = { selfServerAccess = it }
                    )
                }
            }

            HorizontalDivider()

            // Text Orientation Setting
            SettingSection(title = "テキスト表示の向き") {
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    RadioButtonOption(
                        text = "横書き",
                        selected = textOrientation == "Horizontal",
                        onClick = { textOrientation = "Horizontal" }
                    )
                    RadioButtonOption(
                        text = "縦書き",
                        selected = textOrientation == "Vertical",
                        onClick = { textOrientation = "Vertical" }
                    )
                }
            }

            HorizontalDivider()

            // 小説一覧の表示設定
            SettingSection(title = "小説一覧の表示設定") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    // タイトル表示設定
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("タイトルを表示")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = showTitle,
                            onCheckedChange = { showTitle = it }
                        )
                    }

                    // 作者表示設定
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("作者名を表示")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = showAuthor,
                            onCheckedChange = { showAuthor = it }
                        )
                    }

                    // あらすじ表示設定
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("あらすじを表示")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = showSynopsis,
                            onCheckedChange = { showSynopsis = it }
                        )
                    }

                    // タグ表示設定
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("タグを表示")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = showTags,
                            onCheckedChange = { showTags = it }
                        )
                    }

                    // 評価表示設定
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("評価を表示")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = showRating,
                            onCheckedChange = { showRating = it }
                        )
                    }

                    // 更新日表示設定
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("更新日を表示")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = showUpdateDate,
                            onCheckedChange = { showUpdateDate = it }
                        )
                    }

                    // エピソード数表示設定
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("エピソード数を表示")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = showEpisodeCount,
                            onCheckedChange = { showEpisodeCount = it }
                        )
                    }
                }
            }

            // 自己サーバーアクセスがONの場合のみディレクトリ選択を表示
            if (selfServerAccess) {
                HorizontalDivider()

                // 自己サーバーのディレクトリ設定セクション
                SettingSection(title = "自己サーバーのディレクトリ設定") {
                    // ServerDirectorySelectorコンポーネントを使用
                    ServerDirectorySelector(
                        currentPath = selfServerPath,
                        onPathSelected = { uri ->
                            selfServerPath = uri.toString()
                        }
                    )
                }
            }

            HorizontalDivider()

            // 小説表示設定セクションを追加
            // SettingsScreenUpdated.kt - 小説表示設定セクション
            SettingSection(title = "小説表示設定") {
                // 背景色設定
                Text(
                    text = "背景色 (エピソード表示時)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // デフォルト背景色スイッチを削除し、直接カラーオプションを表示
                backgroundOptions.forEach { option ->
                    if (option != "Default") { // "Default"は表示しない
                        val colorHex = when (option) {
                            "White" -> "#FFFFFF"
                            "Cream" -> "#F5F5DC" // デフォルト
                            "Light Gray" -> "#EEEEEE"
                            "Light Blue" -> "#E6F2FF"
                            "Dark Gray" -> "#303030"
                            "Black" -> "#000000"
                            else -> "#FFFFFF"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = episodeBackgroundColor == colorHex,
                                    onClick = { episodeBackgroundColor = colorHex }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = episodeBackgroundColor == colorHex,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(option)
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = try {
                                            Color(android.graphics.Color.parseColor(colorHex))
                                        } catch (e: Exception) {
                                            Color.White
                                        }
                                    )
                            )
                        }
                    }
                }

                // フォント色の設定
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "フォント色 (エピソード表示時)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                fontColorOptions.forEach { option ->
                    val colorHex = fontColors[option] ?: "#000000"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = fontColor == colorHex,
                                onClick = { fontColor = colorHex }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = fontColor == colorHex,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(option)
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = try {
                                        Color(android.graphics.Color.parseColor(colorHex))
                                    } catch (e: Exception) {
                                        Color.Black
                                    }
                                )
                        )
                    }
                }
            }
            HorizontalDivider()

            SettingSection(title = "自動更新設定") {
                // 自動更新の有効/無効
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自動更新を有効にする")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = { autoUpdateEnabled = it }
                    )
                }

                // 自動更新が有効な場合のみ時間設定を表示
                if (autoUpdateEnabled) {
                    // 自動更新時間の設定
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePickerDialog = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自動更新時間")
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = autoUpdateTime,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "時間を選択",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 説明文
                    Text(
                        text = "指定した時間に小説の更新をチェックします。\n更新があれば通知が表示されます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            HorizontalDivider()

            // データベース同期セクション
            SettingSection(title = "データベース同期") {
                Text(
                    text = "外部のSQLiteデータベースと内部データベースを同期します。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // データベースファイル選択コンポーネントを使用
                DatabaseFileSelector(
                    onFileSelected = { uri ->
                        selectedDbUri = uri
                        showDBSyncDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 詳細な同期画面を開くボタン
                Button(
                    onClick = {
                        val intent = Intent(context, DatabaseSyncActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("詳細な同期画面を開く")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))


        }
    }
}

// SettingSection, RadioButtonOption, など既存の補助Composable関数は
// 元のSettingsScreen.ktと同様に使用します。
@Composable
fun SettingSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun RadioButtonOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // null because we're handling click on the row
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text)
    }
}

@Composable
fun DatabaseSyncDialog(
    selectedUri: Uri,
    onDismiss: () -> Unit,
    onComplete: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isSyncing) onDismiss()
        },
        title = {
            Text("内部DBへの書き込み")
        },
        text = {
            when {
                isSyncing -> {
                    CircularProgressIndicator()
                }
                syncResult == null -> {
                    Text("選択したディレクトリの情報を内部DBに同期しますか？")
                }
                syncResult == true -> {
                    Text("同期が完了しました！")
                }
                else -> {
                    Text("同期中にエラーが発生しました。もう一度お試しください。")
                }
            }
        },
        confirmButton = {
            if (syncResult == null && !isSyncing) {
                TextButton(
                    onClick = {
                        isSyncing = true
                        scope.launch {
                            try {
                                val syncManager = DatabaseSyncManager(context)
                                val result = syncManager.syncFromExternalDb(selectedUri)
                                syncResult = result

                                if (result) {
                                    Toast.makeText(context, "データベースの同期に成功しました", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "データベースの同期に失敗しました", Toast.LENGTH_SHORT).show()
                                }

                                isSyncing = false
                                onComplete(result)
                            } catch (e: Exception) {
                                Log.e("DatabaseSync", "同期エラー: ${e.message}", e)
                                syncResult = false
                                isSyncing = false
                                Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_LONG).show()
                                onComplete(false)
                            }
                        }
                    }
                ) {
                    Text("同期する")
                }
            } else {
                TextButton(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text("閉じる")
                }
            }
        },
        dismissButton = {
            if (syncResult == null && !isSyncing) {
                TextButton(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text("キャンセル")
                }
            }
        }
    )
}
@Composable
fun TimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    // 時間と分の初期値を取得
    val timeParts = initialTime.split(":")
    var hour by remember { mutableStateOf(timeParts[0].toIntOrNull() ?: 3) }
    var minute by remember { mutableStateOf(timeParts[1].toIntOrNull() ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自動更新時間を設定") },
        text = {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 時間選択
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 時間セレクター
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("時間")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { hour = (hour - 1 + 24) % 24 }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上へ")
                            }
                            Text(
                                text = hour.toString().padStart(2, '0'),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            IconButton(onClick = { hour = (hour + 1) % 24 }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下へ")
                            }
                        }
                    }

                    Text(":", style = MaterialTheme.typography.headlineMedium)

                    // 分セレクター
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("分")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { minute = (minute - 5 + 60) % 60 }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上へ")
                            }
                            Text(
                                text = minute.toString().padStart(2, '0'),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            IconButton(onClick = { minute = (minute + 5) % 60 }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下へ")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // フォーマットされた時間文字列を作成して返す
                    val timeString = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                    onTimeSelected(timeString)
                }
            ) {
                Text("設定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}