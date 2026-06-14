/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Screen for adjusting user settings.
 */
package com.shunlight_library.novel_reader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.documentfile.provider.DocumentFile
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncManager
import com.shunlight_library.novel_reader.ui.DatabaseSyncActivity
import com.shunlight_library.novel_reader.ui.components.DatabaseFileSelector
import com.shunlight_library.novel_reader.ui.components.ServerDirectorySelector
import com.shunlight_library.novel_reader.utils.FontUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shunlight_library.novel_reader.worker.AutoUpdateScheduler
import com.shunlight_library.novel_reader.api.NovelApiUtils
import com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory
import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenUpdated(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val autoUpdateScheduler = remember { AutoUpdateScheduler(context) }
    val scope = rememberCoroutineScope()

    // State variables for settings with initial values from DataStore
    var themeMode by remember { mutableStateOf("System") }
    var fontFamily by remember { mutableStateOf("Gothic") }
    var fontSize by remember { mutableStateOf(16) }
    var backgroundColor by remember { mutableStateOf("White") }
    var selfServerAccess by remember { mutableStateOf(false) }
    var textOrientation by remember { mutableStateOf("Horizontal") }
    var swipeEnabled by remember { mutableStateOf(true) }
    var tapEnabled by remember { mutableStateOf(false) }
    var autoRubyEnabled by remember { mutableStateOf(true) }
    var selfServerPath by remember { mutableStateOf("") }
    var imageSaveLocation by remember { mutableStateOf("") }

    // 新しい状態変数を追加
    var fontColor by remember { mutableStateOf("#000000") }
    var episodeBackgroundColor by remember { mutableStateOf("#FFFFFF") }
    var useDefaultBackground by remember { mutableStateOf(true) }

    // データベース同期関連の状態変数
    var showDBSyncDialog by remember { mutableStateOf(false) }
    var selectedDbUri by remember { mutableStateOf<Uri?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    // シンプルリストモード
    var useSimpleListMode by remember { mutableStateOf(false) }

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
    var autoDownloadEnabled by remember { mutableStateOf(true) }
    var excludeShortFromUpdate by remember { mutableStateOf(true) }

    // インジケーターランプ設定の状態変数
    var indicatorLampEnabled by remember { mutableStateOf(true) }
    var indicatorLampStyle by remember { mutableStateOf("SOLID") }

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

    val imageSaveLocationLabel = remember(imageSaveLocation) {
        if (imageSaveLocation.isBlank()) {
            "未設定"
        } else {
            runCatching {
                val uri = Uri.parse(imageSaveLocation)
                DocumentFile.fromTreeUri(context, uri)?.name
                    ?: uri.lastPathSegment
                    ?: imageSaveLocation
            }.getOrElse {
                imageSaveLocation
            }
        }
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

    val imageDirectoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val uriString = it.toString()
            val contentResolver = context.contentResolver
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                val alreadyPersisted = contentResolver.persistedUriPermissions.any { permission ->
                    permission.uri == it
                }
                if (!alreadyPersisted) {
                    contentResolver.takePersistableUriPermission(it, flags)
                }

                val previousUri = imageSaveLocation
                imageSaveLocation = uriString
                scope.launch {
                    settingsStore.saveImageSaveLocation(uriString)
                }

                if (previousUri.isNotBlank() && previousUri != uriString) {
                    runCatching {
                        contentResolver.releasePersistableUriPermission(Uri.parse(previousUri), flags)
                    }.onFailure { e ->
                        Log.w("SettingsScreen", "前の保存先の権限解放に失敗: ${e.message}", e)
                    }
                }

                Toast.makeText(context, "画像の保存先を設定しました", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Log.e("SettingsScreen", "保存先ディレクトリの権限取得に失敗: ${e.message}", e)
                Toast.makeText(context, "フォルダへのアクセス権限を取得できませんでした", Toast.LENGTH_LONG).show()
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
            swipeEnabled = settingsStore.swipeEnabled.first()
            tapEnabled = settingsStore.tapEnabled.first()
            autoRubyEnabled = settingsStore.autoRubyEnabled.first()
            selfServerPath = settingsStore.selfServerPath.first()
            imageSaveLocation = settingsStore.imageSaveLocation.first()

            // 表示関連の設定読み込み
            fontColor = settingsStore.fontColor.first()
            episodeBackgroundColor = settingsStore.episodeBackgroundColor.first()
            useDefaultBackground = settingsStore.useDefaultBackground.first()

            // シンプルリストモードの読み込み
            useSimpleListMode = settingsStore.getUseSimpleListMode()

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
            autoDownloadEnabled = settingsStore.autoDownloadEnabled.first()
            excludeShortFromUpdate = settingsStore.excludeShortFromUpdate.first()

            // インジケーターランプ設定の読み込み
            indicatorLampEnabled = settingsStore.indicatorLampEnabled.first()
            indicatorLampStyle = settingsStore.indicatorLampStyle.first()

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

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
        },
        bottomBar = {
            // 画面下部に固定されるボタン
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
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

                                // 左右スワイプ設定を保存
                                settingsStore.saveSwipeEnabled(swipeEnabled)
                                settingsStore.saveTapEnabled(tapEnabled)
                                settingsStore.saveAutoRubyEnabled(autoRubyEnabled)

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
                                settingsStore.saveAutoUpdateSettings(autoUpdateEnabled, autoUpdateTime, autoDownloadEnabled)

                                // インジケーターランプ設定を保存
                                settingsStore.saveIndicatorLampSettings(indicatorLampEnabled, indicatorLampStyle)

                                if (imageSaveLocation.isNotBlank()) {
                                    settingsStore.saveImageSaveLocation(imageSaveLocation)
                                } else {
                                    settingsStore.clearImageSaveLocation()
                                }

                                // 自動更新スケジュールをリセット（バックログをクリアして再スケジュール）
                                autoUpdateScheduler.resetSchedule(autoUpdateEnabled, autoUpdateTime)

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
                    // シンプルリストモード
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("シンプルリストモード")
                            Text(
                                "縁取り（カード）を廃止してフラットなリスト表示にする",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useSimpleListMode,
                            onCheckedChange = {
                                useSimpleListMode = it
                                scope.launch { settingsStore.saveUseSimpleListMode(it) }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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

                // 左右スワイプ切替
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("左右スワイプで話を移動")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = swipeEnabled,
                        onCheckedChange = { swipeEnabled = it }
                    )
                }

                // タップで話を移動
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("画面タップで話を移動")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = tapEnabled,
                        onCheckedChange = { tapEnabled = it }
                    )
                }

                // ルビ自動変換
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("漢字（よみがな）をルビとして表示")
                        Text(
                            text = "本文中の「漢字（よみがな）」形式を自動でルビに変換します",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoRubyEnabled,
                        onCheckedChange = { autoRubyEnabled = it }
                    )
                }
            }
            HorizontalDivider()

            SettingSection(title = "画像保存先") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "現在の保存先",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = imageSaveLocationLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (imageSaveLocation.isNotBlank() && imageSaveLocationLabel != imageSaveLocation) {
                        Text(
                            text = imageSaveLocation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val initialUri = imageSaveLocation
                                .takeIf { it.isNotBlank() }
                                ?.let { uriString -> runCatching { Uri.parse(uriString) }.getOrNull() }
                            imageDirectoryPickerLauncher.launch(initialUri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("フォルダを選択")
                    }

                    if (imageSaveLocation.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val previousUri = imageSaveLocation
                                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                if (previousUri.isNotBlank()) {
                                    runCatching {
                                        val uri = Uri.parse(previousUri)
                                        val hasPermission = context.contentResolver.persistedUriPermissions.any { it.uri == uri }
                                        if (hasPermission) {
                                            context.contentResolver.releasePersistableUriPermission(uri, flags)
                                        }
                                    }.onFailure { e ->
                                        Log.w("SettingsScreen", "保存先の権限解放に失敗: ${e.message}", e)
                                    }
                                }

                                imageSaveLocation = ""
                                scope.launch {
                                    settingsStore.clearImageSaveLocation()
                                }
                                Toast.makeText(context, "画像の保存先を未設定にしました", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("保存先をクリア")
                        }
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

                    // 自動ダウンロードの有効/無効
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("更新を自動でDL")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = autoDownloadEnabled,
                            onCheckedChange = { autoDownloadEnabled = it }
                        )
                    }

                    // 説明文
                    Text(
                        text = "更新があった場合、自動的にエピソードをダウンロードします。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // 短編を更新確認から除外（自動更新・手動更新の両方に適用）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("短編を更新確認から除外")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = excludeShortFromUpdate,
                        onCheckedChange = {
                            excludeShortFromUpdate = it
                            scope.launch { settingsStore.saveExcludeShortFromUpdate(it) }
                        }
                    )
                }

                // 説明文
                Text(
                    text = "短編は新しい話が増えないため、更新確認の対象から外して高速化します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            HorizontalDivider()

            // インジケーターランプ設定セクション
            SettingSection(title = "インジケーターランプ設定") {
                // インジケーターランプの有効/無効
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("インジケーターランプを表示")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = indicatorLampEnabled,
                        onCheckedChange = { indicatorLampEnabled = it }
                    )
                }

                // インジケーターランプが有効な場合のみスタイル設定を表示
                if (indicatorLampEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "表示スタイル",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        RadioButtonOption(
                            text = "点灯（常時表示）",
                            selected = indicatorLampStyle == "SOLID",
                            onClick = { indicatorLampStyle = "SOLID" }
                        )
                        RadioButtonOption(
                            text = "点滅",
                            selected = indicatorLampStyle == "BLINKING",
                            onClick = { indicatorLampStyle = "BLINKING" }
                        )
                    }

                    // 説明文
                    Text(
                        text = "画面左下に更新・取得処理の状態を表示します。\nインジケーターをタップすると詳細を確認できます。",
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

            HorizontalDivider()

            // データベース整合性チェックセクション
            OrphanedEpisodeCheckSection()

            HorizontalDivider()

            // 開発者向けオプション
            SettingSection(title = "開発者向けオプション") {
                // すべての更新スケジュールを削除
                Button(
                    onClick = {
                        scope.launch {
                            autoUpdateScheduler.pruneAllWork()
                            Toast.makeText(context, "すべての更新スケジュールを削除しました", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("すべての更新スケジュールを削除")
                }
                Text(
                    text = "注意: 実行中のタスクもすべてキャンセル・削除されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun OrphanedEpisodeCheckSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = NovelReaderApplication.getRepository()

    var isChecking by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var orphanedNcodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var progressCurrent by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }

    SettingSection(title = "データベース整合性チェック") {
        Text(
            text = "エピソードは存在するが小説情報が欠落しているデータを検知し、APIから小説情報を取得して復元します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Button(
            onClick = {
                scope.launch {
                    isChecking = true
                    try {
                        val found = withContext(Dispatchers.IO) {
                            repository.findOrphanedEpisodeNcodes()
                        }
                        orphanedNcodes = found
                        if (found.isEmpty()) {
                            Toast.makeText(context, "孤立エピソードは見つかりませんでした", Toast.LENGTH_SHORT).show()
                        } else {
                            showConfirmDialog = true
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "検知エラー: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isChecking = false
                    }
                }
            },
            enabled = !isChecking && !isRestoring,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("検知中...")
            } else {
                Text("孤立エピソード検知・復元")
            }
        }

        if (isRestoring) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (progressTotal > 0) progressCurrent.toFloat() / progressTotal else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Text(
                text = "復元中: $progressCurrent / $progressTotal",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }

    // 確認ダイアログ
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("孤立エピソード検知結果") },
            text = {
                Column {
                    Text("${orphanedNcodes.size}件の孤立エピソードが見つかりました。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "対象ncode:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    orphanedNcodes.forEach { ncode ->
                        Text(
                            text = "  - $ncode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("APIから小説情報を取得して復元しますか？")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    isRestoring = true
                    progressTotal = orphanedNcodes.size
                    progressCurrent = 0

                        scope.launch {
                        val successes = mutableListOf<String>()
                        val failures = mutableListOf<String>()

                        for (ncode in orphanedNcodes) {
                            try {
                                withContext(Dispatchers.IO) {
                                    repository.restoreNovelMetadata(ncode) { success, message ->
                                        if (success) {
                                            successes.add(ncode)
                                        } else {
                                            failures.add("$ncode: $message")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                failures.add("$ncode: ${e.message}")
                            }
                            progressCurrent++
                        }

                        isRestoring = false
                        val sb = StringBuilder()
                        if (successes.isNotEmpty()) {
                            sb.appendLine("成功: ${successes.size}件")
                            successes.forEach { sb.appendLine("  - $it") }
                        }
                        if (failures.isNotEmpty()) {
                            sb.appendLine("失敗: ${failures.size}件")
                            failures.forEach { sb.appendLine("  - $it") }
                        }
                        resultMessage = sb.toString()
                        showResultDialog = true
                    }
                }) {
                    Text("復元する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // 結果ダイアログ
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text("復元結果") },
            text = { Text(resultMessage) },
            confirmButton = {
                TextButton(onClick = { showResultDialog = false }) {
                    Text("OK")
                }
            }
        )
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

// ─────────────────────────────────────────────
// Sub-screens for the new Settings hub flow
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDisplayScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    var themeMode by remember { mutableStateOf("System") }
    var fontFamily by remember { mutableStateOf("Gothic") }
    var fontSize by remember { mutableStateOf(16) }
    var textOrientation by remember { mutableStateOf("Horizontal") }
    var useSimpleListMode by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(true) }
    var showAuthor by remember { mutableStateOf(true) }
    var showSynopsis by remember { mutableStateOf(true) }
    var showTags by remember { mutableStateOf(true) }
    var showRating by remember { mutableStateOf(true) }
    var showUpdateDate by remember { mutableStateOf(true) }
    var showEpisodeCount by remember { mutableStateOf(true) }
    var indicatorLampEnabled by remember { mutableStateOf(true) }
    var indicatorLampStyle by remember { mutableStateOf("SOLID") }
    var customFonts by remember { mutableStateOf<List<CustomFontInfo>>(emptyList()) }
    var showCustomFontDialog by remember { mutableStateOf(false) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                scope.launch {
                    val customFont = com.shunlight_library.novel_reader.utils.FontUtils.importFontFromUri(context, uri)
                    if (customFont != null) {
                        settingsStore.saveCustomFont(customFont.id, customFont.name, customFont.filePath, customFont.fontType)
                        fontFamily = customFont.id
                        customFonts = settingsStore.getAllCustomFontInfo()
                        android.widget.Toast.makeText(context, "フォント「${customFont.name}」を追加しました", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "フォントの追加に失敗しました", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        themeMode = settingsStore.themeMode.first()
        fontFamily = settingsStore.fontFamily.first()
        fontSize = settingsStore.fontSize.first()
        textOrientation = settingsStore.textOrientation.first()
        useSimpleListMode = settingsStore.getUseSimpleListMode()
        val ds = settingsStore.getDisplaySettings()
        showTitle = ds.showTitle; showAuthor = ds.showAuthor; showSynopsis = ds.showSynopsis
        showTags = ds.showTags; showRating = ds.showRating; showUpdateDate = ds.showUpdateDate
        showEpisodeCount = ds.showEpisodeCount
        indicatorLampEnabled = settingsStore.indicatorLampEnabled.first()
        indicatorLampStyle = settingsStore.indicatorLampStyle.first()
        customFonts = settingsStore.getAllCustomFontInfo()
    }

    if (showCustomFontDialog) {
        AlertDialog(
            onDismissRequest = { showCustomFontDialog = false },
            title = { Text("カスタムフォント") },
            text = {
                Column {
                    if (customFonts.isNotEmpty()) {
                        Text("保存済みのフォント", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                        customFonts.forEach { fontInfo ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .selectable(selected = fontFamily == fontInfo.id, onClick = { fontFamily = fontInfo.id; showCustomFontDialog = false })
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = fontFamily == fontInfo.id, onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column { Text(fontInfo.name); Text("形式: ${fontInfo.type.uppercase()}", style = MaterialTheme.typography.bodySmall) }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    scope.launch {
                                        if (com.shunlight_library.novel_reader.utils.FontUtils.deleteCustomFont(context, fontInfo.path)) {
                                            settingsStore.deleteCustomFont(fontInfo.id)
                                            if (fontFamily == fontInfo.id) fontFamily = "Gothic"
                                            customFonts = settingsStore.getAllCustomFontInfo()
                                            android.widget.Toast.makeText(context, "フォント「${fontInfo.name}」を削除しました", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) { Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Button(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(android.content.Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf","font/otf","application/x-font-ttf","application/x-font-otf","application/x-font-ttc","font/collection","application/octet-stream"))
                        }
                        fontPickerLauncher.launch(intent)
                        showCustomFontDialog = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("新しいフォントを追加") }
                }
            },
            confirmButton = { TextButton(onClick = { showCustomFontDialog = false }) { Text("閉じる") } }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("表示・フォント") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } }
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 8.dp) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                settingsStore.saveThemeMode(themeMode)
                                settingsStore.saveFontFamily(fontFamily)
                                settingsStore.saveFontSize(fontSize)
                                settingsStore.saveTextOrientation(textOrientation)
                                settingsStore.saveUseSimpleListMode(useSimpleListMode)
                                settingsStore.saveDisplaySettings(DisplaySettings(showTitle, showAuthor, showSynopsis, showTags, showRating, showUpdateDate, showEpisodeCount))
                                settingsStore.saveIndicatorLampSettings(indicatorLampEnabled, indicatorLampStyle)
                                android.widget.Toast.makeText(context, "設定を保存しました", android.widget.Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "保存に失敗しました: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("設定を保存") }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingSection(title = "表示モード") {
                Column(modifier = Modifier.selectableGroup()) {
                    RadioButtonOption("システム設定に従う", themeMode == "System") { themeMode = "System" }
                    RadioButtonOption("ライトモード", themeMode == "Light") { themeMode = "Light" }
                    RadioButtonOption("ダークモード", themeMode == "Dark") { themeMode = "Dark" }
                }
            }
            HorizontalDivider()
            SettingSection(title = "フォント") {
                Column(modifier = Modifier.selectableGroup()) {
                    RadioButtonOption("ゴシック体", fontFamily == "Gothic") { fontFamily = "Gothic" }
                    RadioButtonOption("明朝体", fontFamily == "Mincho") { fontFamily = "Mincho" }
                    RadioButtonOption("丸ゴシック", fontFamily == "Rounded") { fontFamily = "Rounded" }
                    RadioButtonOption("筆記体", fontFamily == "Handwriting") { fontFamily = "Handwriting" }
                    customFonts.forEach { fi -> RadioButtonOption("カスタム: ${fi.name}", fontFamily == fi.id) { fontFamily = fi.id } }
                    Button(onClick = { showCustomFontDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("カスタムフォントを管理...") }
                    if (fontFamily !in listOf("Gothic","Mincho","Rounded","Handwriting")) {
                        val sel = customFonts.firstOrNull { it.id == fontFamily }
                        if (sel != null) {
                            Button(
                                onClick = { scope.launch {
                                    com.shunlight_library.novel_reader.utils.FontUtils.deleteCustomFont(context, sel.path)
                                    settingsStore.deleteCustomFont(sel.id)
                                    fontFamily = "Gothic"
                                    customFonts = settingsStore.getAllCustomFontInfo()
                                    android.widget.Toast.makeText(context, "フォント「${sel.name}」を削除しました", android.widget.Toast.LENGTH_SHORT).show()
                                }},
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) { Text("選択中のカスタムフォントを削除") }
                        }
                    }
                }
            }
            HorizontalDivider()
            SettingSection(title = "フォントサイズ (${fontSize}sp)") {
                Slider(value = fontSize.toFloat(), onValueChange = { fontSize = it.toInt() }, valueRange = 12f..24f, steps = 6, modifier = Modifier.padding(horizontal = 16.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("小", fontSize = 12.sp); Text("中", fontSize = 16.sp); Text("大", fontSize = 24.sp)
                }
            }
            HorizontalDivider()
            SettingSection(title = "テキスト表示の向き") {
                Column(modifier = Modifier.selectableGroup()) {
                    RadioButtonOption("横書き", textOrientation == "Horizontal") { textOrientation = "Horizontal" }
                    RadioButtonOption("縦書き", textOrientation == "Vertical") { textOrientation = "Vertical" }
                }
            }
            HorizontalDivider()
            SettingSection(title = "小説一覧の表示設定") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("シンプルリストモード")
                            Text("縁取り（カード）を廃止してフラットなリスト表示にする", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = useSimpleListMode, onCheckedChange = { useSimpleListMode = it; scope.launch { settingsStore.saveUseSimpleListMode(it) } })
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    listOf(
                        "タイトルを表示" to showTitle,
                        "作者名を表示" to showAuthor,
                        "あらすじを表示" to showSynopsis,
                        "タグを表示" to showTags,
                        "評価を表示" to showRating,
                        "更新日を表示" to showUpdateDate,
                        "エピソード数を表示" to showEpisodeCount
                    ).forEachIndexed { i, (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label); Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = value, onCheckedChange = { v ->
                                when (i) {
                                    0 -> showTitle = v; 1 -> showAuthor = v; 2 -> showSynopsis = v
                                    3 -> showTags = v; 4 -> showRating = v; 5 -> showUpdateDate = v
                                    6 -> showEpisodeCount = v
                                }
                            })
                        }
                    }
                }
            }
            HorizontalDivider()
            SettingSection(title = "インジケーターランプ設定") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("インジケーターランプを表示"); Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = indicatorLampEnabled, onCheckedChange = { indicatorLampEnabled = it })
                }
                if (indicatorLampEnabled) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("表示スタイル", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                        RadioButtonOption("点灯（常時表示）", indicatorLampStyle == "SOLID") { indicatorLampStyle = "SOLID" }
                        RadioButtonOption("点滅", indicatorLampStyle == "BLINKING") { indicatorLampStyle = "BLINKING" }
                    }
                    Text(
                        "画面左下に更新・取得処理の状態を表示します。\nインジケーターをタップすると詳細を確認できます。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsReadingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    var episodeBackgroundColor by remember { mutableStateOf("#F5F5DC") }
    var fontColor by remember { mutableStateOf("#000000") }
    var swipeEnabled by remember { mutableStateOf(true) }
    var tapEnabled by remember { mutableStateOf(false) }

    val backgroundOptions = listOf("White" to "#FFFFFF", "Cream" to "#F5F5DC", "Light Gray" to "#EEEEEE", "Light Blue" to "#E6F2FF", "Dark Gray" to "#303030", "Black" to "#000000")
    val fontColorOptions = listOf("Black" to "#000000", "Dark Gray" to "#333333", "Navy" to "#000080", "Dark Green" to "#006400", "White" to "#FFFFFF")

    LaunchedEffect(Unit) {
        episodeBackgroundColor = settingsStore.episodeBackgroundColor.first()
        fontColor = settingsStore.fontColor.first()
        swipeEnabled = settingsStore.swipeEnabled.first()
        tapEnabled = settingsStore.tapEnabled.first()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("読書設定") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } }
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 8.dp) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                settingsStore.saveEpisodeBackgroundColor(episodeBackgroundColor)
                                settingsStore.saveFontColor(fontColor)
                                settingsStore.saveSwipeEnabled(swipeEnabled)
                                settingsStore.saveTapEnabled(tapEnabled)
                                android.widget.Toast.makeText(context, "設定を保存しました", android.widget.Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "保存に失敗しました: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("設定を保存") }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingSection(title = "背景色 (エピソード表示時)") {
                backgroundOptions.forEach { (label, hex) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = episodeBackgroundColor == hex, onClick = { episodeBackgroundColor = hex })
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = episodeBackgroundColor == hex, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(label)
                        Spacer(modifier = Modifier.weight(1f))
                        Box(modifier = Modifier.size(24.dp).background(
                            try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                        ))
                    }
                }
            }
            HorizontalDivider()
            SettingSection(title = "フォント色 (エピソード表示時)") {
                fontColorOptions.forEach { (label, hex) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = fontColor == hex, onClick = { fontColor = hex })
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = fontColor == hex, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(label)
                        Spacer(modifier = Modifier.weight(1f))
                        Box(modifier = Modifier.size(24.dp).background(
                            try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Black }
                        ))
                    }
                }
            }
            HorizontalDivider()
            SettingSection(title = "操作設定") {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("左右スワイプで話を移動"); Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = swipeEnabled, onCheckedChange = { swipeEnabled = it })
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("画面タップで話を移動"); Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = tapEnabled, onCheckedChange = { tapEnabled = it })
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNetworkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    var selfServerAccess by remember { mutableStateOf(false) }
    var selfServerPath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        selfServerAccess = settingsStore.selfServerAccess.first()
        selfServerPath = settingsStore.selfServerPath.first()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("ネットワーク") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } }
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 8.dp) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                settingsStore.saveSelfServerAccess(selfServerAccess)
                                settingsStore.saveSelfServerPath(selfServerPath)
                                android.widget.Toast.makeText(context, "設定を保存しました", android.widget.Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "保存に失敗しました: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("設定を保存") }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingSection(title = "自己サーバーアクセス") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("自己サーバーへの接続"); Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = selfServerAccess, onCheckedChange = { selfServerAccess = it })
                }
            }
            if (selfServerAccess) {
                HorizontalDivider()
                SettingSection(title = "自己サーバーのディレクトリ設定") {
                    com.shunlight_library.novel_reader.ui.components.ServerDirectorySelector(
                        currentPath = selfServerPath,
                        onPathSelected = { uri -> selfServerPath = uri.toString() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAutoUpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val autoUpdateScheduler = remember { com.shunlight_library.novel_reader.worker.AutoUpdateScheduler(context) }
    val scope = rememberCoroutineScope()

    var autoUpdateEnabled by remember { mutableStateOf(false) }
    var autoUpdateTime by remember { mutableStateOf("03:00") }
    var autoDownloadEnabled by remember { mutableStateOf(true) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        autoUpdateEnabled = settingsStore.autoUpdateEnabled.first()
        autoUpdateTime = settingsStore.autoUpdateTime.first()
        autoDownloadEnabled = settingsStore.autoDownloadEnabled.first()
    }

    if (showTimePickerDialog) {
        TimePickerDialog(
            initialTime = autoUpdateTime,
            onDismiss = { showTimePickerDialog = false },
            onTimeSelected = { selectedTime -> autoUpdateTime = selectedTime; showTimePickerDialog = false }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("自動更新") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } }
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 8.dp) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                settingsStore.saveAutoUpdateSettings(autoUpdateEnabled, autoUpdateTime, autoDownloadEnabled)
                                autoUpdateScheduler.resetSchedule(autoUpdateEnabled, autoUpdateTime)
                                android.widget.Toast.makeText(context, "設定を保存しました", android.widget.Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "保存に失敗しました: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("設定を保存") }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingSection(title = "自動更新設定") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("自動更新を有効にする"); Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = autoUpdateEnabled, onCheckedChange = { autoUpdateEnabled = it })
                }
                if (autoUpdateEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showTimePickerDialog = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自動更新時間"); Spacer(modifier = Modifier.weight(1f))
                        Text(autoUpdateTime, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Schedule, contentDescription = "時間を選択", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "指定した時間に小説の更新をチェックします。\n更新があれば通知が表示されます。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("更新を自動でDL"); Spacer(modifier = Modifier.weight(1f))
                        Switch(checked = autoDownloadEnabled, onCheckedChange = { autoDownloadEnabled = it })
                    }
                    Text(
                        "更新があった場合、自動的にエピソードをダウンロードします。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsStorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    var imageSaveLocation by remember { mutableStateOf("") }
    var selectedDbUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showDBSyncDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val imageSaveLocationLabel = remember(imageSaveLocation) {
        if (imageSaveLocation.isBlank()) "未設定"
        else runCatching {
            val uri = android.net.Uri.parse(imageSaveLocation)
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: imageSaveLocation
        }.getOrElse { imageSaveLocation }
    }

    val imageDirectoryPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val uriString = it.toString()
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                val alreadyPersisted = context.contentResolver.persistedUriPermissions.any { p -> p.uri == it }
                if (!alreadyPersisted) context.contentResolver.takePersistableUriPermission(it, flags)
                val prev = imageSaveLocation
                imageSaveLocation = uriString
                scope.launch { settingsStore.saveImageSaveLocation(uriString) }
                if (prev.isNotBlank() && prev != uriString) {
                    runCatching { context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(prev), flags) }
                }
                android.widget.Toast.makeText(context, "画像の保存先を設定しました", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                android.widget.Toast.makeText(context, "フォルダへのアクセス権限を取得できませんでした", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        imageSaveLocation = settingsStore.imageSaveLocation.first()
    }

    if (showDBSyncDialog && selectedDbUri != null) {
        AlertDialog(
            onDismissRequest = { if (!isSyncing) showDBSyncDialog = false },
            title = { Text("データベース同期") },
            text = {
                if (isSyncing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(); Spacer(modifier = Modifier.height(16.dp)); Text("同期中です。しばらくお待ちください...")
                    }
                } else { Text("選択したデータベースファイルから内部データベースに同期しますか？") }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!isSyncing) {
                        isSyncing = true
                        scope.launch {
                            try {
                                val syncManager = com.shunlight_library.novel_reader.data.sync.DatabaseSyncManager(context)
                                val success = syncManager.syncFromExternalDb(selectedDbUri!!)
                                android.widget.Toast.makeText(context, if (success) "データベースの同期に成功しました" else "データベースの同期に失敗しました", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "エラー: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            } finally { isSyncing = false; showDBSyncDialog = false }
                        }
                    }
                }, enabled = !isSyncing) { Text(if (isSyncing) "同期中..." else "同期する") }
            },
            dismissButton = { TextButton(onClick = { showDBSyncDialog = false }, enabled = !isSyncing) { Text("キャンセル") } }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("ストレージ・DB") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingSection(title = "画像保存先") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("現在の保存先", style = MaterialTheme.typography.bodyLarge)
                    Text(imageSaveLocationLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    if (imageSaveLocation.isNotBlank() && imageSaveLocationLabel != imageSaveLocation) {
                        Text(imageSaveLocation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        val initialUri = imageSaveLocation.takeIf { it.isNotBlank() }?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                        imageDirectoryPickerLauncher.launch(initialUri)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Folder, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("フォルダを選択")
                    }
                    if (imageSaveLocation.isNotBlank()) {
                        OutlinedButton(onClick = {
                            val prev = imageSaveLocation
                            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            if (prev.isNotBlank()) {
                                runCatching {
                                    val uri = android.net.Uri.parse(prev)
                                    if (context.contentResolver.persistedUriPermissions.any { it.uri == uri }) {
                                        context.contentResolver.releasePersistableUriPermission(uri, flags)
                                    }
                                }
                            }
                            imageSaveLocation = ""
                            scope.launch { settingsStore.clearImageSaveLocation() }
                            android.widget.Toast.makeText(context, "画像の保存先を未設定にしました", android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("保存先をクリア")
                        }
                    }
                }
            }
            HorizontalDivider()
            SettingSection(title = "データベース同期") {
                Text("外部のSQLiteデータベースと内部データベースを同期します。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                com.shunlight_library.novel_reader.ui.components.DatabaseFileSelector(onFileSelected = { uri -> selectedDbUri = uri; showDBSyncDialog = true })
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val intent = android.content.Intent(context, com.shunlight_library.novel_reader.ui.DatabaseSyncActivity::class.java)
                    context.startActivity(intent)
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("詳細な同期画面を開く") }
            }
            HorizontalDivider()
            OrphanedEpisodeCheckSection()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDeveloperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val autoUpdateScheduler = remember { com.shunlight_library.novel_reader.worker.AutoUpdateScheduler(context) }
    val errorLogStore = remember { com.shunlight_library.novel_reader.data.ErrorLogStore(context) }
    val notificationStore = remember { com.shunlight_library.novel_reader.data.NotificationStore(context) }
    val settingsStore = remember { SettingsStore(context) }
    val repository = remember { com.shunlight_library.novel_reader.NovelReaderApplication.getRepository() }
    val scope = rememberCoroutineScope()

    var errorLogs by remember { mutableStateOf<List<com.shunlight_library.novel_reader.data.ErrorLog>>(emptyList()) }
    var notifications by remember { mutableStateOf<List<com.shunlight_library.novel_reader.data.AppNotification>>(emptyList()) }
    var selectedErrorFilter by remember { mutableStateOf("全て") }
    var lastAutoUpdateRun by remember { mutableStateOf("") }
    var hasPendingWork by remember { mutableStateOf(false) }
    var dbDebugInfo by remember { mutableStateOf("") }
    var selectedErrorLog by remember { mutableStateOf<com.shunlight_library.novel_reader.data.ErrorLog?>(null) }
    var selectedNotification by remember { mutableStateOf<com.shunlight_library.novel_reader.data.AppNotification?>(null) }

    val errorFilterTypes = listOf("全て", "AutoUpdateFatal", "UpdateCheckError", "DownloadError", "DownloadBatchError", "EpisodeListError")
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
    val shortDateFormat = remember { java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()) }

    LaunchedEffect(Unit) {
        scope.launch {
            errorLogs = errorLogStore.getAllErrorLogs()
            notifications = notificationStore.getAllNotifications()
            lastAutoUpdateRun = try {
                val ts = settingsStore.lastAutoUpdateRunAt.first()
                if (ts > 0) dateFormat.format(java.util.Date(ts)) else "未実行"
            } catch (_: Exception) { "取得失敗" }
            hasPendingWork = try { autoUpdateScheduler.hasPendingWork() } catch (_: Exception) { false }
            dbDebugInfo = try { repository.getDatabaseDebugInfo() } catch (_: Exception) { "取得失敗" }
        }
    }

    selectedErrorLog?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedErrorLog = null },
            title = { Text("エラー詳細", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState())
                ) {
                    Text("日時: ${dateFormat.format(java.util.Date(log.timestamp))}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("小説名: ${log.novelTitle}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Nコード: ${log.ncode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("エピソード: ${log.episodeTitle ?: "第${log.episodeNo}話"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("エラー種類: ${log.errorType}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("エラーメッセージ: ${log.errorMessage}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    log.stackTrace?.let { trace ->
                        Text("スタックトレース:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Text(trace, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            val text = buildString {
                                append("日時: ${dateFormat.format(java.util.Date(log.timestamp))}\n")
                                append("小説名: ${log.novelTitle}\n")
                                append("Nコード: ${log.ncode}\n")
                                append("エピソード: ${log.episodeTitle ?: "第${log.episodeNo}話"}\n")
                                append("種類: ${log.errorType}\n")
                                append("メッセージ: ${log.errorMessage}\n")
                                log.stackTrace?.let { append("スタックトレース:\n$it") }
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("エラーログ", text))
                            Toast.makeText(context, "クリップボードにコピーしました", Toast.LENGTH_SHORT).show()
                        }) { Text("コピー") }
                        TextButton(onClick = {
                            val tag = "NovelReader_Dev"
                            Log.e(tag, "[${log.errorType}] ${log.novelTitle} (${log.ncode}) - ${log.errorMessage}")
                            log.stackTrace?.let { Log.e(tag, it) }
                            Toast.makeText(context, "Logcatに出力しました", Toast.LENGTH_SHORT).show()
                        }) { Text("Logcat") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            scope.launch {
                                errorLogStore.deleteErrorLog(log.id)
                                errorLogs = errorLogStore.getAllErrorLogs()
                            }
                            selectedErrorLog = null
                        }) { Text("削除") }
                        TextButton(onClick = { selectedErrorLog = null }) { Text("閉じる") }
                    }
                }
            }
        )
    }

    selectedNotification?.let { notif ->
        AlertDialog(
            onDismissRequest = { selectedNotification = null },
            title = { Text(notif.title, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())
                ) {
                    Text("日時: ${dateFormat.format(java.util.Date(notif.timestamp))}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("通知本文:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(notif.content, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("種別: ${notif.type.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                    notif.downloadDetails?.let { details ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("ダウンロード詳細:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        details.forEach { detail ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("  ${detail.title} (成功:${detail.successEpisodes.size} 失敗:${detail.failedEpisodes.size})", style = MaterialTheme.typography.bodySmall)
                            detail.failedEpisodes.forEach { ep ->
                                Text("    ✗ 第${ep.episodeNo}話 ${ep.title} - ${ep.error ?: ""}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNotification = null }) { Text("閉じる") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("開発者オプション") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === 自動更新ステータス ===
            SettingSection(title = "自動更新ステータス") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("最終自動更新実行:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(lastAutoUpdateRun, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("実行待ちワーク:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (hasPendingWork) "あり" else "なし", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (hasPendingWork) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // === DB診断情報 ===
            SettingSection(title = "DB診断情報") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(dbDebugInfo, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("エラーログ数: ${errorLogs.size}", style = MaterialTheme.typography.bodySmall, color = if (errorLogs.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // === エラーログ一覧 ===
            SettingSection(title = "エラーログ (${errorLogs.size}件)") {
                // フィルタチップ
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    errorFilterTypes.forEach { filter ->
                        FilterChip(
                            selected = selectedErrorFilter == filter,
                            onClick = { selectedErrorFilter = filter },
                            label = { Text(filter, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                val filteredLogs = if (selectedErrorFilter == "全て") errorLogs else errorLogs.filter { it.errorType == selectedErrorFilter }

                if (filteredLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("エラーログはありません", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    filteredLogs.take(20).forEach { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedErrorLog = log },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(shortDateFormat.format(java.util.Date(log.timestamp)), style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(log.errorType, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                                Text(log.novelTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(log.errorMessage, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (filteredLogs.size > 20) {
                        Text("...他${filteredLogs.size - 20}件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val emailText = errorLogStore.formatErrorLogsForEmail(filteredLogs)
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:contact@furinlab.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "小説リーダー エラーログ (${filteredLogs.size}件)")
                                    putExtra(Intent.EXTRA_TEXT, emailText)
                                }
                                context.startActivity(Intent.createChooser(intent, "エラーログを送信"))
                            }
                        },
                        enabled = filteredLogs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("メール送信", fontSize = 12.sp) }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                errorLogStore.clearAllErrorLogs()
                                errorLogs = emptyList()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        enabled = errorLogs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("全削除", fontSize = 12.sp) }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val text = errorLogStore.formatErrorLogsForEmail(filteredLogs)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("エラーログ", text))
                                Toast.makeText(context, "${filteredLogs.size}件をクリップボードにコピーしました", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = filteredLogs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("コピー", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            val tag = "NovelReader_Dev"
                            filteredLogs.forEach { log ->
                                Log.e(tag, "[${log.errorType}] ${log.novelTitle} (${log.ncode}) ${log.episodeTitle ?: "第${log.episodeNo}話"} - ${log.errorMessage}")
                                log.stackTrace?.let { Log.e(tag, it) }
                            }
                            Toast.makeText(context, "Logcatに${filteredLogs.size}件出力しました", Toast.LENGTH_SHORT).show()
                        },
                        enabled = filteredLogs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Logcat送信", fontSize = 12.sp) }
                }
            }

            // === 通知履歴 ===
            SettingSection(title = "通知履歴 (${notifications.size}件)") {
                if (notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text("通知はありません", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    notifications.take(10).forEach { notif ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedNotification = notif },
                            colors = CardDefaults.cardColors(
                                containerColor = when (notif.type) {
                                     com.shunlight_library.novel_reader.data.NotificationType.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                     com.shunlight_library.novel_reader.data.NotificationType.UPDATE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                     else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(notif.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(shortDateFormat.format(java.util.Date(notif.timestamp)), style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Text(notif.content, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (notifications.size > 10) {
                        Text("...他${notifications.size - 10}件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // === ワークマネージャー操作 ===
            SettingSection(title = "WorkManager操作") {
                Button(
                    onClick = {
                        scope.launch {
                            autoUpdateScheduler.pruneAllWork()
                            Toast.makeText(context, "すべての更新スケジュールを削除しました", Toast.LENGTH_SHORT).show()
                            hasPendingWork = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) { Text("すべての更新スケジュールを削除") }
                Text(
                    "注意: 実行中のタスクもすべてキャンセル・削除されます。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            autoUpdateScheduler.runManualUpdate()
                            Toast.makeText(context, "手動更新を開始しました", Toast.LENGTH_SHORT).show()
                            hasPendingWork = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) { Text("手動更新を即座に実行") }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    // 時間と分の初期値を取得（不正な値の場合は安全なデフォルトにフォールバック）
    val initialValues = remember(initialTime) {
        val parts = initialTime.split(":")
            .map { it.trim() }
        val hour = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: 3
        val minute = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
        hour to minute
    }
    var hour by remember(initialTime) { mutableStateOf(initialValues.first) }
    var minute by remember(initialTime) { mutableStateOf(initialValues.second) }

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
