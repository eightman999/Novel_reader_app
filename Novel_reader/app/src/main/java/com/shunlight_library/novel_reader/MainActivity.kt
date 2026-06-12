/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Main activity hosting Compose UI and navigation.
 */
package com.shunlight_library.novel_reader

import RecentlyUpdatedNovelsScreen
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.ui.theme.Novel_readerTheme
import com.shunlight_library.novel_reader.ui.theme.LightOrange
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import com.shunlight_library.novel_reader.navigation.NavigationManager
import com.shunlight_library.novel_reader.navigation.Screen
import com.shunlight_library.novel_reader.ui.DatabaseSyncActivity
import com.shunlight_library.novel_reader.data.NotificationStore
import com.shunlight_library.novel_reader.ui.components.NotificationDialog
import com.shunlight_library.novel_reader.utils.ReleaseUtils
import com.shunlight_library.novel_reader.AppInfo
import com.shunlight_library.novel_reader.data.AppNotification
import com.shunlight_library.novel_reader.data.NotificationType
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.shunlight_library.novel_reader.metadata.MetadataUpdateManager
import com.shunlight_library.novel_reader.metadata.MetadataUpdateResult
import com.shunlight_library.novel_reader.ui.components.IndicatorLampGroup
import com.shunlight_library.novel_reader.ui.components.IndicatorLampStyle
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private lateinit var navigationManager: NavigationManager
    private lateinit var backPressedCallback: OnBackPressedCallback

    // Android 13+ の通知権限要求
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 結果は無視 */ }
    
    // R18ダイアログの表示状態
    private val _showR18Dialog = mutableStateOf(false)
    val showR18Dialog: Boolean
        get() = _showR18Dialog.value

    // R18ダイアログを表示するメソッド
    fun showR18Dialog() {
        _showR18Dialog.value = true
    }

    // R18ダイアログを非表示にするメソッド
    fun hideR18Dialog() {
        _showR18Dialog.value = false
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ナビゲーションマネージャーの作成
        navigationManager = NavigationManager()

        // Android 13+ で通知権限をリクエスト（POST_NOTIFICATIONS）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // OnBackPressedCallback を設定
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // まずナビゲーションマネージャーにバック処理を任せる
                if (!navigationManager.navigateBack()) {
                    // ナビゲーションマネージャーが処理できなければアプリを終了
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        // ステータスバーを表示する（設定修正）
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // エッジツーエッジ表示を有効化
        enableEdgeToEdge()

        // システムバーを非表示にしてコンテンツをその下に表示
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val settingsStore = remember { SettingsStore(this) }
            val notificationStore = remember { NotificationStore(this) }
            val themeMode by settingsStore.themeMode.collectAsState(initial = "System")
            val scope = rememberCoroutineScope()

            // アプリ起動時に未読通知をチェック
            LaunchedEffect(key1 = Unit) {
                scope.launch {
                    val unreadNotifications = notificationStore.getUnreadNotifications()
                    if (unreadNotifications.isNotEmpty()) {
                        // 通知があることを示すフラグを設定
                        // 実際の通知表示はメイン画面で行う
                    }
                }
            }

            // GitHubの最新リリースをバックグラウンドで確認
            LaunchedEffect(Unit) {
                scope.launch {
                    ReleaseUtils.checkForNewRelease(this@MainActivity)
                }
            }

            // テーマモードに基づいてダークテーマかどうかを決定
            val isDarkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            Novel_readerTheme(darkTheme = isDarkTheme) {
                // ナビゲーションマネージャーをコンポーザブルに提供
                NovelReaderApp(
                    navigationManager = navigationManager,
                    notificationStore = notificationStore
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderApp(
    navigationManager: NavigationManager,
    notificationStore: NotificationStore? = null
) {
    var developerUnlocked by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // WebView用の状態変数を追加
    var showWebView by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // エピソード一覧と閲覧のための変数を追加
    var showEpisodeList by remember { mutableStateOf(false) }
    var showEpisodeView by remember { mutableStateOf(false) }
    var currentNcode by remember { mutableStateOf("") }
    var currentEpisodeNo by remember { mutableStateOf("") }
    // R18コンテンツ用のダイアログ表示状態
    var showR18Dialog by remember { mutableStateOf(false) }
    var updateInfoText by remember { mutableStateOf("0作品0話") }
    var showRecentlyReadNovels by remember { mutableStateOf(false) }
    var showRecentlyUpdatedNovelsScreen by remember { mutableStateOf(false) }
    // URLを開くヘルパー関数を修正
    fun openUrl(url: String) {
        currentUrl = url
        showWebView = true
    }

    // リポジトリを取得
    val repository = NovelReaderApplication.getRepository()

    // インジケーターランプの設定を取得
    val settingsStore = remember { SettingsStore(context) }
    val indicatorEnabled by settingsStore.indicatorLampEnabled.collectAsState(initial = true)
    val indicatorStyleStr by settingsStore.indicatorLampStyle.collectAsState(initial = "SOLID")
    val indicatorStyle = when (indicatorStyleStr) {
        "BLINKING" -> IndicatorLampStyle.BLINKING
        else -> IndicatorLampStyle.SOLID
    }

    // 処理状態を監視
    val processingStates by repository.processingStates.collectAsState()

    // 最後に読んだ小説の情報を取得
    var lastReadNovel by remember { mutableStateOf<LastReadNovelEntity?>(null) }
    var novelInfo by remember { mutableStateOf<NovelDescEntity?>(null) }
    var showNovelList by remember { mutableStateOf(false) }
    var showUpdateInfo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        lastReadNovel = repository.getMostRecentlyReadNovel()
        if (lastReadNovel != null) {
            novelInfo = repository.getNovelByNcode(lastReadNovel!!.ncode)
        }
    }
    // 画面状態変数をキーとしたLaunchedEffectで、メイン画面に戻るたびに最新情報を更新
    LaunchedEffect(showSettings, showWebView, showNovelList, showEpisodeList, showEpisodeView) {
        if (!showSettings && !showWebView && !showNovelList && !showEpisodeList && !showEpisodeView) {
            // メイン画面に戻ってきたときに最新の情報を取得
            lastReadNovel = repository.getMostRecentlyReadNovel()
            if (lastReadNovel != null) {
                novelInfo = repository.getNovelByNcode(lastReadNovel!!.ncode)
            }
        }
    }
    LaunchedEffect(Unit) {
        lastReadNovel = repository.getMostRecentlyReadNovel()
        if (lastReadNovel != null) {
            novelInfo = repository.getNovelByNcode(lastReadNovel!!.ncode)
        }

        // 更新情報も取得
        val (workCount, episodeCount) = repository.getUpdateCountsWithEpisodes()
        updateInfoText = "${workCount}作品${episodeCount}話"
    }

    // R18コンテンツ選択ダイアログ
    if (showR18Dialog) {
        AlertDialog(
            onDismissRequest = { showR18Dialog = false },
            title = { Text("R18コンテンツを選択") },
            text = { Text("閲覧したいR18サイトを選択してください") },
            confirmButton = {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            openUrl("https://noc.syosetu.com/top/top/")
                            showR18Dialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ノクターン")
                    }

                    Button(
                        onClick = {
                            openUrl("https://mid.syosetu.com/top/top/")
                            showR18Dialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ミッドナイト")
                    }

                    Button(
                        onClick = {
                            openUrl("https://mnlt.syosetu.com/top/top/")
                            showR18Dialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ムーンライト")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showR18Dialog = false }) {
                    Text("キャンセル")
                }
            }
        )
        BackHandler {
            showR18Dialog = false
        }
    }

    // インジケーターランプとコンテンツを重ねて表示
    Box(modifier = Modifier.fillMaxSize()) {
        // メインコンテンツ
        when (val currentScreen = navigationManager.currentScreen) {
        is Screen.Main -> {
            MainScreen(
                onNavigate = { screen -> navigationManager.navigateTo(screen) },
                notificationStore = notificationStore,
                onDeveloperUnlocked = { developerUnlocked = true }
            )
        }

        is Screen.Settings -> {
            SettingsScreenUpdated(
                onBack = { navigationManager.navigateBack() }
            )
        }

        is Screen.SettingsMenu -> {
            SettingsMenuScreen(
                onBack = { navigationManager.navigateBack() },
                onNavigate = { screen -> navigationManager.navigateTo(screen) },
                developerUnlocked = developerUnlocked
            )
        }

        is Screen.SettingsDisplay -> {
            SettingsDisplayScreen(onBack = { navigationManager.navigateBack() })
        }

        is Screen.SettingsReading -> {
            SettingsReadingScreen(onBack = { navigationManager.navigateBack() })
        }

        is Screen.SettingsNetwork -> {
            SettingsNetworkScreen(onBack = { navigationManager.navigateBack() })
        }

        is Screen.SettingsAutoUpdate -> {
            SettingsAutoUpdateScreen(onBack = { navigationManager.navigateBack() })
        }

        is Screen.SettingsStorage -> {
            SettingsStorageScreen(onBack = { navigationManager.navigateBack() })
        }

        is Screen.SettingsDeveloper -> {
            SettingsDeveloperScreen(onBack = { navigationManager.navigateBack() })
        }

        is Screen.NovelList -> {
            NovelListScreen(
                onBack = { navigationManager.navigateTo(Screen.Main) },
                onNovelClick = { ncode ->
                    navigationManager.navigateTo(Screen.EpisodeList(ncode, currentScreen))
                },
                navigationManager = navigationManager
            )
        }

    is Screen.EpisodeList -> {
        EpisodeListScreen(
            ncode = currentScreen.ncode,
            onBack = {
                navigationManager.navigateTo(Screen.NovelList())
            },
            onEpisodeClick = { ncode, episodeNo ->
                navigationManager.navigateTo(Screen.EpisodeView(ncode, episodeNo, currentScreen))
            },
            onAuthorClick = { url ->
                navigationManager.navigateTo(Screen.WebView(url, currentScreen))
            }
        )
    }

    is Screen.EpisodeView -> {
        EpisodeViewScreen(
            ncode = currentScreen.ncode,
            episodeNo = currentScreen.episodeNo,
            onBack = {
                currentScreen.source?.let { source ->
                    navigationManager.navigateBackTo(source)
                } ?: navigationManager.navigateBack()
            },
            onBackToToc = {
                currentScreen.source?.let { source ->
                    navigationManager.navigateBackTo(source)
                    if (source !is Screen.EpisodeList) {
                        navigationManager.navigateTo(Screen.EpisodeList(currentScreen.ncode, source))
                    }
                } ?: navigationManager.navigateTo(Screen.EpisodeList(currentScreen.ncode))
            },
            onPrevious = {
                val prevEpisodeNo = currentScreen.episodeNo.toIntOrNull()?.let { it - 1 }?.toString() ?: "1"
                if (prevEpisodeNo.toInt() >= 1) {
                    navigationManager.navigateTo(Screen.EpisodeView(
                        ncode = currentScreen.ncode,
                        episodeNo = prevEpisodeNo,
                        source = currentScreen.source))  // Use the source from currentScreen
                }
            },
            onNext = {
                val nextEpisodeNo = currentScreen.episodeNo.toIntOrNull()?.let { it + 1 }?.toString() ?: "1"
                navigationManager.navigateTo(Screen.EpisodeView(
                    ncode = currentScreen.ncode,
                    episodeNo = nextEpisodeNo,
                    source = currentScreen.source))  // Use the source from currentScreen
            }
        )
    }


    is Screen.WebView -> {
            WebViewScreen(
                url = currentScreen.url,
                onBack = { navigationManager.navigateBack() }
            )
        }

    is Screen.RecentlyReadNovels -> {
        RecentlyReadNovelsScreen(
            onBack = { navigationManager.navigateBack() },
            onNovelClick = { ncode, episodeNo ->
                navigationManager.navigateTo(Screen.EpisodeView(ncode, episodeNo, currentScreen))
            }
        )
    }

        is Screen.RecentlyUpdatedNovels -> {
            RecentlyUpdatedNovelsScreen(
                onBack = { navigationManager.navigateBack() },
                onNovelClick = { ncode ->
                    navigationManager.navigateTo(Screen.EpisodeList(ncode, currentScreen))
                }
            )
        }

        is Screen.UpdateInfo -> {
            UpdateInfoScreen(
                onBack = { navigationManager.navigateBack() },
                onNovelClick = { ncode ->
                    navigationManager.navigateTo(Screen.EpisodeList(ncode, currentScreen))
                }
            )
        }

        is Screen.DownloadQueue -> {
            val downloadQueueViewModel = remember { com.shunlight_library.novel_reader.ui.viewmodel.DownloadQueueViewModel() }
            com.shunlight_library.novel_reader.ui.screens.DownloadQueueScreen(
                onNavigateBack = { navigationManager.navigateBack() },
                viewModel = downloadQueueViewModel
            )
        }

        is Screen.DatabaseSync -> {
            // アクティビティを起動するが、ナビゲーションバックは機能する
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                val intent = Intent(context, DatabaseSyncActivity::class.java)
                context.startActivity(intent)
                // アクティビティが上に表示される間すぐに戻る
                navigationManager.navigateBack()
            }
            // アクティビティが起動している間、ローディングまたは空の画面を表示
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        }

        // インジケーターランプを左下に表示
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            IndicatorLampGroup(
                states = processingStates,
                style = indicatorStyle,
                enabled = indicatorEnabled
            )
        }
    }
}



@Composable
fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray.copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        Text(
            text = title,
            color = Color.Gray,
            fontSize = 16.sp
        )
    }
}

@Composable
fun MenuButton(
    icon: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(160.dp)
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            fontSize = 16.sp
        )
    }
}

// MainActivity.kt - MainScreen関数を更新
@Composable
fun MainScreen(
    onNavigate: (Screen) -> Unit,
    notificationStore: NotificationStore? = null,
    onDeveloperUnlocked: () -> Unit = {}
) {
    val repository = NovelReaderApplication.getRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状態変数
    var lastReadNovel by remember { mutableStateOf<LastReadNovelEntity?>(null) }
    var novelInfo by remember { mutableStateOf<NovelDescEntity?>(null) }
    var updateInfoText by remember { mutableStateOf("0作品0話") }
    var showR18Dialog by remember { mutableStateOf(false) }

    // 通知関連の状態
    var unreadNotificationCount by remember { mutableStateOf(0) }
    var showNotificationDialog by remember { mutableStateOf(false) }

    // 自動更新バックログ確認ダイアログの状態
    var showAutoUpdateBacklogDialog by remember { mutableStateOf(false) }
    val autoUpdateScheduler = remember { com.shunlight_library.novel_reader.worker.AutoUpdateScheduler(context) }

    // メタデータ補完ダイアログ用の状態
    var showMetadataConfirm by remember { mutableStateOf(false) }
    var showMetadataProgressDialog by remember { mutableStateOf(false) }
    var isMetadataRunning by remember { mutableStateOf(false) }
    var metadataProgress by remember { mutableStateOf(0f) }
    var metadataStatusText by remember { mutableStateOf("処理を準備しています...") }
    var metadataResult by remember { mutableStateOf<MetadataUpdateResult?>(null) }
    var metadataProcessed by remember { mutableStateOf(0) }
    var metadataTotal by remember { mutableStateOf(0) }

    var versionTapCount by remember { mutableStateOf(0) }
    var lastVersionTapTime by remember { mutableStateOf(0L) }
    val versionTapThreshold = 1000

    fun onVersionTap() {
        val current = System.currentTimeMillis()
        if (current - lastVersionTapTime < versionTapThreshold) {
            versionTapCount++
            val remaining = 7 - versionTapCount
            if (versionTapCount >= 7) {
                versionTapCount = 0
                onDeveloperUnlocked()
                scope.launch {
                    val info = repository.getDatabaseDebugInfo()
                    val message = "おめでとう！あなたも開発者だ！"
                    notificationStore?.addNotification(
                        AppNotification(
                            id = "dev_${System.currentTimeMillis()}",
                            title = message,
                            content = info,
                            timestamp = System.currentTimeMillis(),
                            type = NotificationType.INFO
                        )
                    )
                    sendDevNotification(context, message, info)
                }
            } else if (remaining <= 3) {
                Toast.makeText(context, "あと ${remaining} 回で開発者オプションが有効になります", Toast.LENGTH_SHORT).show()
            }
        } else {
            versionTapCount = 1
        }
        lastVersionTapTime = current
    }

    // 通知データの監視
    if (notificationStore != null) {
        val unreadCount by notificationStore.unreadCountFlow.collectAsState(initial = 0)
        unreadNotificationCount = unreadCount
    }

    // 最後に読んだ小説と更新情報の取得
    LaunchedEffect(Unit) {
        lastReadNovel = repository.getMostRecentlyReadNovel()
        if (lastReadNovel != null) {
            novelInfo = repository.getNovelByNcode(lastReadNovel!!.ncode)
        }

        // 更新情報も取得
        val (workCount, episodeCount) = repository.getUpdateCountsWithEpisodes()
        updateInfoText = "${workCount}作品${episodeCount}話"

        // 自動更新のバックログをチェック（予定時刻を過ぎて未実行の場合のみ）
        run {
            val settingsStore = SettingsStore(context)
            val enabled = settingsStore.autoUpdateEnabled.first()
            val timeStr = settingsStore.autoUpdateTime.first()
            val lastRun = settingsStore.lastAutoUpdateRunAt.first()
            val lastPrompt = settingsStore.lastBacklogPromptAt.first()

            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            val parts = timeStr.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 3
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
            cal.set(java.util.Calendar.MINUTE, minute)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val scheduledToday = cal.timeInMillis

            val debounceMs = 6 * 60 * 60 * 1000L // 6時間
            val shouldPrompt = enabled && now >= scheduledToday && lastRun < scheduledToday &&
                (now - lastPrompt) > debounceMs && autoUpdateScheduler.hasPendingWork()

            if (shouldPrompt) {
                showAutoUpdateBacklogDialog = true
                settingsStore.setLastBacklogPromptAt(now)
            }
        }
    }

    // R18コンテンツ選択ダイアログ
    if (showR18Dialog) {
        AlertDialog(
            onDismissRequest = { showR18Dialog = false },
            title = { Text("R18コンテンツを選択") },
            text = { Text("閲覧したいR18サイトを選択してください") },
            confirmButton = {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onNavigate(Screen.WebView("https://noc.syosetu.com/top/top/"))
                            showR18Dialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ノクターン")
                    }

                    Button(
                        onClick = {
                            onNavigate(Screen.WebView("https://mid.syosetu.com/top/top/"))
                            showR18Dialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ミッドナイト")
                    }

                    Button(
                        onClick = {
                            onNavigate(Screen.WebView("https://mnlt.syosetu.com/top/top/"))
                            showR18Dialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ムーンライト")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showR18Dialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // 自動更新バックログ確認ダイアログ
    if (showAutoUpdateBacklogDialog) {
        AlertDialog(
            onDismissRequest = { showAutoUpdateBacklogDialog = false },
            title = { Text("自動更新の確認") },
            text = {
                Column {
                    Text("実行待ちの自動更新があります。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "これは以下のいずれかの理由で発生します：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("• 長時間アプリを使用していなかった", style = MaterialTheme.typography.bodySmall)
                    Text("• 実行予定時刻を過ぎている", style = MaterialTheme.typography.bodySmall)
                    Text("• 設定を変更した直後", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("今すぐ実行しますか？それともスキップしますか？")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAutoUpdateBacklogDialog = false
                        scope.launch {
                            // 手動で更新を実行
                            autoUpdateScheduler.runManualUpdate()
                            android.widget.Toast.makeText(
                                context,
                                "自動更新を開始しました",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("今すぐ実行")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAutoUpdateBacklogDialog = false
                        scope.launch {
                            // バックログをリセット
                            val settingsStore = SettingsStore(context)
                            val enabled = settingsStore.autoUpdateEnabled.first()
                            val time = settingsStore.autoUpdateTime.first()
                            autoUpdateScheduler.resetSchedule(enabled, time)

                            // 今日分は処理済み扱いにして再表示を防止
                            settingsStore.setLastAutoUpdateRunAt(System.currentTimeMillis())

                            // 更新キューをクリア
                            repository.clearAllUpdateQueue()

                            // 表示更新
                            val (workCount, episodeCount) = repository.getUpdateCountsWithEpisodes()
                            updateInfoText = "${workCount}作品${episodeCount}話"
                            android.widget.Toast.makeText(
                                context,
                                "自動更新をスキップしました",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("スキップ")
                }
            }
        )
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 新着・更新情報セクション
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightOrange)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "新着・更新情報",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 新着・更新情報ボタン
                    Button(
                        onClick = {
                            // 新着・更新情報画面に遷移
                            onNavigate(Screen.UpdateInfo)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = LightOrange
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = updateInfoText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "最後に開いていた小説",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 最後に読んだ小説の情報ボタン
                    Button(
                        onClick = {
                            if (lastReadNovel != null) {
                                // 適切なソースを指定して EpisodeView に遷移
                                onNavigate(Screen.EpisodeView(
                                    ncode = lastReadNovel!!.ncode,
                                    episodeNo = lastReadNovel!!.episode_no.toString(),
                                    source = Screen.Main
                                ))
                            }
                        },
                        enabled = novelInfo != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = if (novelInfo != null) LightOrange else Color.Gray,
                            disabledContainerColor = Color.LightGray,
                            disabledContentColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (novelInfo != null)
                                "${novelInfo!!.title} ${lastReadNovel!!.episode_no}話"
                            else
                                "まだ小説を読んでいません",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // 小説をさがすセクション
            item {
                SectionHeader(title = "小説をさがす")
            }

            // ランキングとPickup
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MenuButton(
                        icon = "⚪",
                        text = "ランキング",
                        onClick = {
                            onNavigate(Screen.WebView("https://yomou.syosetu.com/rank/top/"))
                        }
                    )
                    MenuButton(
                        icon = "📢",
                        text = "PickUp!",
                        onClick = {
                            onNavigate(Screen.WebView("https://syosetu.com/pickup/list/"))
                        }
                    )
                }
            }

            // キーワード検索と詳細検索
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MenuButton(
                        icon = "🔍",
                        text = "キーワード",
                        onClick = {
                            onNavigate(Screen.WebView("https://yomou.syosetu.com/search/keyword/"))
                        }
                    )
                    MenuButton(
                        icon = ">",
                        text = "詳細検索",
                        onClick = {
                            onNavigate(Screen.WebView("https://yomou.syosetu.com/search.php"))
                        }
                    )
                }
            }

            //カクヨム＆R18セクション
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MenuButton(
                        icon = "📖",
                        text = "カクヨム",
                        onClick = {
                            onNavigate(Screen.WebView("https://kakuyomu.jp/"))
                        }
                    )
                    MenuButton(
                        icon = "<",
                        text = "R18",
                        onClick = {
                            showR18Dialog = true
                        }
                    )
                }
            }

            // 小説を読むセクション
            item {
                SectionHeader(title = "小説を読む")
            }

            // 小説一覧と最近更新された小説
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MenuButton(
                        icon = "📚",
                        text = "小説一覧",
                        onClick = {
                            onNavigate(Screen.NovelList(source = Screen.Main))
                        }
                    )
                    MenuButton(
                        icon = ">",
                        text = "最近更新された小説",
                        onClick = {
                            onNavigate(Screen.RecentlyUpdatedNovels)
                        }
                    )
                }
            }

            // 最近読んだ小説
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MenuButton(
                        icon = ">",
                        text = "最近読んだ小説",
                        onClick = {
                            onNavigate(Screen.RecentlyReadNovels)
                        }
                    )
                    Spacer(modifier = Modifier.width(160.dp)) // 右側は空欄
                }
            }

            // オプションセクション
            item {
                SectionHeader(title = "オプション")
            }

            // 設定、通知、DB同期、キュー、メタデータ補完
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MenuButton(
                            icon = "⚙",
                            text = "設定",
                            onClick = {
                                onNavigate(Screen.SettingsMenu)
                            }
                        )

                        // 通知ボタン（バッジ付き）
                        Box {
                            MenuButton(
                                icon = "🔔",
                                text = "通知",
                                onClick = {
                                    showNotificationDialog = true
                                }
                            )
                            // 未読通知バッジ
                            if (unreadNotificationCount > 0) {
                                Badge(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-8).dp, y = 8.dp)
                                ) {
                                    Text(
                                        text = if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString(),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MenuButton(
                            icon = "",
                            text = "DB同期",
                            onClick = {
                                onNavigate(Screen.DatabaseSync)
                            }
                        )

                        MenuButton(
                            icon = "",
                            text = "ダウンロード状況",
                            onClick = {
                                onNavigate(Screen.DownloadQueue)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MenuButton(
                            icon = "🗂",
                            text = "メタデータ補完",
                            onClick = {
                                showMetadataConfirm = true
                            }
                        )
                        Spacer(modifier = Modifier.width(160.dp))
                    }
                }
            }

            // アプリバージョン表示
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    MenuButton(
                        icon = "ℹ",
                        text = "バージョン ${AppInfo.VERSION_NAME}",
                        onClick = { onVersionTap() }
                    )
                }
            }
        }
    }
    
    if (showMetadataConfirm) {
        AlertDialog(
            onDismissRequest = { showMetadataConfirm = false },
            title = { Text("メタデータ補完の確認") },
            text = { Text("不足しているメタデータをバッチ処理で取得しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    showMetadataConfirm = false
                    showMetadataProgressDialog = true
                    isMetadataRunning = true
                    metadataResult = null
                    metadataProgress = 0f
                    metadataProcessed = 0
                    metadataTotal = 0
                    metadataStatusText = "処理を準備しています..."
                    scope.launch {
                        val result = MetadataUpdateManager.updateMissingMetadata(repository) { processed, total ->
                            withContext(Dispatchers.Main) {
                                metadataTotal = total
                                metadataProcessed = processed
                                metadataProgress = if (total == 0) 1f else processed.toFloat() / total.toFloat()
                                metadataStatusText = if (total == 0) {
                                    "更新対象はありません。"
                                } else {
                                    "処理中... (${processed}/${total})"
                                }
                            }
                        }

                        withContext(Dispatchers.Main) {
                            isMetadataRunning = false
                            metadataResult = result
                            metadataStatusText = when {
                                result.hadError -> {
                                    "処理中にエラーが発生しました。ログを確認してください。"
                                }
                                result.targets == 0 -> {
                                    "更新対象の小説は見つかりませんでした。"
                                }
                                else -> {
                                    "処理が完了しました。${result.targets}件中${result.updated}件のメタデータを更新しました。"
                                }
                            }
                            metadataProgress = 1f
                        }
                    }
                }) {
                    Text("実行")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMetadataConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (showMetadataProgressDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isMetadataRunning) {
                    showMetadataProgressDialog = false
                }
            },
            title = {
                Text(if (isMetadataRunning) "メタデータ補完を実行中" else "メタデータ補完の結果")
            },
            text = {
                Column {
                    if (isMetadataRunning) {
                        LinearProgressIndicator(
                            progress = metadataProgress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(metadataStatusText)
                        if (metadataTotal > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${metadataProcessed}/${metadataTotal}")
                        }
                    } else {
                        Text(metadataStatusText)
                        metadataResult?.let { result ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("対象作品: ${result.targets}件")
                            Text("更新された作品: ${result.updated}件")
                            if (result.hadError) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "処理中に問題が発生しました。詳細はログを確認してください。",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isMetadataRunning) {
                    TextButton(onClick = { }, enabled = false) {
                        Text("処理中")
                    }
                } else {
                    TextButton(onClick = { showMetadataProgressDialog = false }) {
                        Text("閉じる")
                    }
                }
            }
        )
    }

    // 通知ダイアログ
    if (showNotificationDialog && notificationStore != null) {
        NotificationDialog(
            notificationStore = notificationStore,
            onDismiss = { showNotificationDialog = false }
        )
    }
}

private const val DEV_CHANNEL_ID = "dev_info"
private const val DEV_NOTIFICATION_ID = 2001

private fun sendDevNotification(context: Context, title: String, content: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(DEV_CHANNEL_ID, "Developer", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notification = NotificationCompat.Builder(context, DEV_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    manager.notify(DEV_NOTIFICATION_ID, notification)
}
