package com.shunlight_library.novel_reader

import RecentlyUpdatedNovelsScreen
import android.content.Intent
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var navigationManager: NavigationManager
    private lateinit var backPressedCallback: OnBackPressedCallback
    
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
    var updateInfoText by remember { mutableStateOf("新着0件・更新あり0件") }
    var showRecentlyReadNovels by remember { mutableStateOf(false) }
    var showRecentlyUpdatedNovelsScreen by remember { mutableStateOf(false) }
    // URLを開くヘルパー関数を修正
    fun openUrl(url: String) {
        currentUrl = url
        showWebView = true
    }

    // リポジトリを取得
    val repository = NovelReaderApplication.getRepository()

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
        val (newCount, updateCount) = repository.getUpdateCounts()
        updateInfoText = "新着${newCount}件・更新あり${updateCount}件"
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

when (val currentScreen = navigationManager.currentScreen) {
        is Screen.Main -> {
            MainScreen(
                onNavigate = { screen -> navigationManager.navigateTo(screen) },
                notificationStore = notificationStore
            )
        }

        is Screen.Settings -> {
            SettingsScreenUpdated(
                onBack = { navigationManager.navigateBack() }
            )
        }

        is Screen.NovelList -> {
            NovelListScreen(
                onBack = { navigationManager.navigateTo(Screen.Main) },
                onNovelClick = { ncode ->
                    navigationManager.navigateTo(Screen.EpisodeList(ncode, currentScreen))
                }
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
    notificationStore: NotificationStore? = null
) {
    val repository = NovelReaderApplication.getRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状態変数
    var lastReadNovel by remember { mutableStateOf<LastReadNovelEntity?>(null) }
    var novelInfo by remember { mutableStateOf<NovelDescEntity?>(null) }
    var updateInfoText by remember { mutableStateOf("新着0件・更新あり0件") }
    var showR18Dialog by remember { mutableStateOf(false) }
    
    // 通知関連の状態
    var unreadNotificationCount by remember { mutableStateOf(0) }
    var showNotificationDialog by remember { mutableStateOf(false) }

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
        val (newCount, updateCount) = repository.getUpdateCounts()
        updateInfoText = "新着${newCount}件・更新あり${updateCount}件"
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
                            fontWeight = FontWeight.Bold
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
                            fontWeight = FontWeight.Bold
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
                        icon = "<",
                        text = "R18",
                        onClick = {
                            showR18Dialog = true
                        }
                    )
                    Spacer(modifier = Modifier.width(160.dp)) // 右側は空欄
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

            // 設定、通知、DB同期
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MenuButton(
                        icon = "⚙",
                        text = "設定",
                        onClick = {
                            onNavigate(Screen.Settings)
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
                    
                    MenuButton(
                        icon = "",
                        text = "DB同期",
                        onClick = {
                            onNavigate(Screen.DatabaseSync)
                        }
                    )
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
                        onClick = {}
                    )
                }
            }
        }
    }
    
    // 通知ダイアログ
    if (showNotificationDialog && notificationStore != null) {
        NotificationDialog(
            notificationStore = notificationStore,
            onDismiss = { showNotificationDialog = false }
        )
    }
}