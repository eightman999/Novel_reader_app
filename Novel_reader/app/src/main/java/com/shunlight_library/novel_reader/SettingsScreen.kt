package com.shunlight_library.novel_reader

import android.app.Activity
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.shunlight_library.novel_reader.utils.FontUtils
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

    // カスタムフォント関連の状態変数
    var customFonts by remember { mutableStateOf<List<CustomFontInfo>>(emptyList()) }
    var showCustomFontDialog by remember { mutableStateOf(false) }

    // 時間選択ダイアログ状態
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val actualBackgroundColor = try {
        Color(AndroidColor.parseColor(backgroundColor))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.background
    }

    // フォントピッカーランチャーを定義
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // フォントファイルをインポート
                scope.launch {
                    val customFont = FontUtils.importFontFromUri(context, uri)
                    if (customFont != null) {
                        // 設定に保存
                        settingsStore.saveCustomFont(
                            customFont.id,
                            customFont.name,
                            customFont.filePath,
                            customFont.fontType
                        )

                        // フォントを選択
                        fontFamily = customFont.id

                        // リストを更新
                        customFonts = settingsStore.getAllCustomFontInfo()

                        Toast.makeText(context, "フォント「${customFont.name}」を追加しました", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "フォントの追加に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Load saved preferences when the screen is created
    LaunchedEffect(Unit) {
        try {
            // 基本設定の読み込み
            themeMode = settingsStore.themeMode.first()
            fontFamily = settingsStore.fontFamily.first()
            fontSize = settingsStore.fontSize.first()
            selfServerAccess = settingsStore.selfServerAccess.first()
            textOrientation = settingsStore.textOrientation.first()
            selfServerPath = settingsStore.selfServerPath.first()

            // 表示関連の設定読み込み
            fontColor = settingsStore.fontColor.first()
            episodeBackgroundColor = settingsStore.episodeBackgroundColor.first()
            useDefaultBackground = settingsStore.useDefaultBackground.first()

            // 表示設定の読み込み
            val displaySettings = settingsStore.getDisplaySettings()
            showTitle = displaySettings.showTitle
            showAuthor = displaySettings.showAuthor
            showSynopsis = displaySettings.showSynopsis
            showTags = displaySettings.showTags
            showRating = displaySettings.showRating
            showUpdateDate = displaySettings.showUpdateDate
            showEpisodeCount = displaySettings.showEpisodeCount

            // 自動更新設定の読み込み
            autoUpdateEnabled = settingsStore.autoUpdateEnabled.first()
            autoUpdateTime = settingsStore.autoUpdateTime.first()

            // カスタムフォント情報の読み込み
            customFonts = settingsStore.getAllCustomFontInfo()
        } catch (e: Exception) {
            Log.e("SettingsScreen", "設定の読み込みエラー: ${e.message}")
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

    // カスタムフォントダイアログ
    if (showCustomFontDialog) {
        AlertDialog(
            onDismissRequest = { showCustomFontDialog = false },
            title = { Text("カスタムフォント") },
            text = {
                Column {
                    // カスタムフォント一覧
                    if (customFonts.isNotEmpty()) {
                        Text(
                            text = "保存済みのフォント",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        customFonts.forEach { fontInfo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = fontFamily == fontInfo.id,
                                        onClick = {
                                            fontFamily = fontInfo.id
                                            showCustomFontDialog = false
                                        }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = fontFamily == fontInfo.id,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(fontInfo.name)
                                    Text(
                                        text = "形式: ${fontInfo.type.uppercase()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))

                                // 削除ボタン
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            // ファイルを削除
                                            if (FontUtils.deleteCustomFont(context, fontInfo.path)) {
                                                // 設定からも削除
                                                settingsStore.deleteCustomFont(fontInfo.id)

                                                // 現在選択中のフォントが削除された場合はデフォルトに戻す
                                                if (fontFamily == fontInfo.id) {
                                                    fontFamily = "Gothic"
                                                }

                                                // リストを更新
                                                customFonts = settingsStore.getAllCustomFontInfo()

                                                Toast.makeText(context, "フォント「${fontInfo.name}」を削除しました", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "フォントの削除に失敗しました", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "削除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    // 新しいフォントを追加するボタン
                    Button(
                        onClick = {
                            // フォント選択インテントを起動
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                    "font/ttf",
                                    "font/otf",
                                    "application/x-font-ttf",
                                    "application/x-font-otf",
                                    "application/x-font-ttc", // ttcファイル用に追加
                                    "font/collection",        // ttcファイル用に追加
                                    "application/octet-stream"
                                ))
                            }
                            fontPickerLauncher.launch(intent)
                            showCustomFontDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("新しいフォントを追加")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomFontDialog = false }) {
                    Text("閉じる")
                }
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
        } else if (showCustomFontDialog) {
            showCustomFontDialog = false
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
                    // 標準フォント選択肢
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
                    RadioButtonOption(
                        text = "丸ゴシック",
                        selected = fontFamily == "Rounded",
                        onClick = { fontFamily = "Rounded" }
                    )
                    RadioButtonOption(
                        text = "筆記体",
                        selected = fontFamily == "Handwriting",
                        onClick = { fontFamily = "Handwriting" }
                    )

                    // カスタムフォント一覧
                    customFonts.forEach { fontInfo ->
                        RadioButtonOption(
                            text = "カスタム: ${fontInfo.name}",
                            selected = fontFamily == fontInfo.id,
                            onClick = { fontFamily = fontInfo.id }
                        )
                    }

                    // カスタムフォント追加ボタン
                    Button(
                        onClick = { showCustomFontDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text("カスタムフォントを管理...")
                    }

                    // 削除ボタン（カスタムフォントが選択されている場合のみ表示）
                    if (fontFamily !in listOf("Gothic", "Mincho", "Rounded", "Handwriting")) {
                        val selectedFontInfo = customFonts.firstOrNull { it.id == fontFamily }
                        if (selectedFontInfo != null) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        // ファイルを削除
                                        FontUtils.deleteCustomFont(context, selectedFontInfo.path)

                                        // 設定からも削除
                                        settingsStore.deleteCustomFont(selectedFontInfo.id)

                                        // デフォルトフォントに戻す
                                        fontFamily = "Gothic"

                                        // リストを更新
                                        customFonts = settingsStore.getAllCustomFontInfo()

                                        Toast.makeText(context, "フォント「${selectedFontInfo.name}」を削除しました", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text("選択中のカスタムフォントを削除")
                            }
                        }
                    }
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