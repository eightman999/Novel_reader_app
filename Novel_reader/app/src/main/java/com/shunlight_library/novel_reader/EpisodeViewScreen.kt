/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Composable screen displaying episode content via WebView.
 */
package com.shunlight_library.novel_reader

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import com.shunlight_library.novel_reader.ui.components.VjapPageController
import com.shunlight_library.novel_reader.ui.components.VjapVerticalTextView
import com.shunlight_library.novel_reader.utils.AppLogger
import com.shunlight_library.novel_reader.utils.FontUtils
import com.shunlight_library.novel_reader.utils.VolumeKeyPaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeViewScreen(
    ncode: String,
    episodeNo: String,
    onBack: () -> Unit,
    onBackToToc: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val repository = NovelReaderApplication.getRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }

    var episode by remember { mutableStateOf<EpisodeEntity?>(null) }
    var novel by remember { mutableStateOf<NovelDescEntity?>(null) }
    val scrollState = rememberScrollState()

    // 設定値
    var fontSize by remember { mutableStateOf(18) }
    var fontFamily by remember { mutableStateOf("Gothic") }
    var fontColor by remember { mutableStateOf("#000000") }
    var backgroundColor by remember { mutableStateOf("#FFFFFF") }
    var useDefaultBackground by remember { mutableStateOf(true) }
    var textOrientation by remember { mutableStateOf("Horizontal") }
    var swipeEnabled by remember { mutableStateOf(true) }
    var tapEnabled by remember { mutableStateOf(false) }
    var autoRubyEnabled by remember { mutableStateOf(true) }
    var readerMargins by remember { mutableStateOf(ReaderMargins()) }
    var volumeKeyPagingEnabled by remember { mutableStateOf(false) }
    var autoNextEpisodeAtEnd by remember { mutableStateOf(false) }
    var lineHeight by remember { mutableStateOf(SettingsStore.DEFAULT_LINE_HEIGHT) }
    var showReadingProgress by remember { mutableStateOf(true) }

    // 設定の読み込み完了まで本文を描画しない（既定値の横書きで一瞬描画→縦書きへ差し替わるのを防ぐ）
    var settingsLoaded by remember { mutableStateOf(false) }

    // 横書き: JSブリッジから通知される現在の読書率（保存は終了・中断時にこの値を使う）
    var horizontalReadingRate by remember { mutableStateOf(0f) }

    // 現在表示中の話の読書位置が読み込み済みか（未読み込みの 0 で保存済み位置を上書きしないため）
    var readingRateReady by remember { mutableStateOf(false) }

    // 縦書きのページ情報（1始まり、未計算は -1）
    var vjapPage by remember { mutableStateOf(1) }
    var vjapLastPage by remember { mutableStateOf(-1) }

    // ページ移動スライダー・本文内検索の表示状態
    var showNavigator by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatchCount by remember { mutableStateOf(0) }
    var searchMatchIndex by remember { mutableStateOf(0) }
    var verticalSearchMatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // 検索バーを開いたら入力欄にフォーカスしてキーボードを出す
    LaunchedEffect(showSearchBar) {
        if (showSearchBar) {
            try {
                searchFocusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // 入力欄がまだ配置されていない場合は無視（タップで入力できる）
            }
        }
    }

    // 縦書きビューを音量キーからページ送りするためのハンドル
    val vjapPageController = remember { VjapPageController() }

    // 最終ページからの次話遷移を二重に発火させないためのフラグ（話が切り替わるとリセット）
    var nextEpisodeRequested by remember { mutableStateOf(false) }

    // カスタムフォント情報
    var customFonts by remember { mutableStateOf<List<CustomFontInfo>>(emptyList()) }
    var isCustomFont by remember { mutableStateOf(false) }
    var customFontPath by remember { mutableStateOf("") }

    // vjap縦書き用: ページ変更で更新される読書進捗率
    var vjapReadingRate by remember { mutableStateOf(0f) }

    // 開発者モード関連の状態
    var titleTapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var devModeEnabled by remember { mutableStateOf(false) }
    val tapTimeThreshold = 1000 // 連続タップと判定する時間間隔（ミリ秒）

    // 設定の読み込み
    LaunchedEffect(Unit) {
        try {
            // 基本設定の読み込み
            fontSize = settingsStore.fontSize.first()
            fontFamily = settingsStore.fontFamily.first()
            fontColor = settingsStore.fontColor.first()
            backgroundColor = settingsStore.episodeBackgroundColor.first()
            useDefaultBackground = settingsStore.useDefaultBackground.first()
            textOrientation = settingsStore.textOrientation.first()
            swipeEnabled = settingsStore.swipeEnabled.first()
            tapEnabled = settingsStore.tapEnabled.first()
            autoRubyEnabled = settingsStore.autoRubyEnabled.first()
            readerMargins = settingsStore.readerMargins.first()
            volumeKeyPagingEnabled = settingsStore.volumeKeyPagingEnabled.first()
            autoNextEpisodeAtEnd = settingsStore.autoNextEpisodeAtEnd.first()
            lineHeight = settingsStore.readerLineHeight.first()
            showReadingProgress = settingsStore.showReadingProgress.first()

            // カスタムフォント情報を読み込む
            customFonts = settingsStore.getAllCustomFontInfo()

            // 選択されているフォントがカスタムフォントかどうかを判定
            val selectedFont = customFonts.find { it.id == fontFamily }
            if (selectedFont != null) {
                isCustomFont = true
                customFontPath = selectedFont.path
            } else {
                isCustomFont = false
                customFontPath = ""
            }
        } catch (e: Exception) {
            AppLogger.e("EpisodeViewScreen", "設定の読み込みエラー: ${e.message}")
        } finally {
            settingsLoaded = true
        }
    }

    LaunchedEffect(ncode, episodeNo) {
        // 前後話ナビゲーション時に旧話の本文で描画されるのを防ぐ
        // （nullの間はローディング表示になり、新データ到着時にWebView/縦書きViewが再生成される）
        episode = null
        nextEpisodeRequested = false
        readingRateReady = false
        vjapPage = 1
        vjapLastPage = -1
        searchMatchCount = 0
        searchMatchIndex = 0
        verticalSearchMatches = emptyList()

        scrollState.scrollTo(0)
        scope.launch {
            try {
                // エピソード情報の取得
                episode = repository.getEpisode(ncode, episodeNo)
                novel = repository.getNovelByNcode(ncode)

                // 縦書き進捗の初期値を保存済みの値に合わせる
                // （開いた直後に戻った場合に0で上書きされるのを防ぐ）
                vjapReadingRate = episode?.reading_rate ?: 0f
                horizontalReadingRate = episode?.reading_rate ?: 0f
                readingRateReady = episode != null

                // 削除済み作品の検出
                if (novel == null) {
                    Toast.makeText(context, "この作品は削除されました。ホームに戻ります。", Toast.LENGTH_LONG).show()
                    kotlinx.coroutines.delay(1500)
                    onBack()
                    return@launch
                }

                // エピソードを既読に設定
                val episodeNumber = episodeNo.toIntOrNull() ?: 1

                // 既存のエピソードが取得できた場合は既読マークを設定
                if (episode != null) {
                    // EpisodeEntityに追加した is_read フラグを 1 (既読) に設定
                    repository.updateEpisodeReadStatus(ncode, episodeNo, 1)

                    // それ以前のエピソードも全て既読に設定
                    repository.markEpisodesAsReadUpTo(ncode, episodeNumber)
                }

                // 最後に読んだ情報を更新（従来の処理）
                repository.updateLastRead(ncode, episodeNumber)
            } catch (e: Exception) {
                AppLogger.e("EpisodeViewScreen", "データ取得エラー: ${e.message}")
            }
        }
    }

    // タイトルをタップした時の処理関数
    fun onTitleTap() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime < tapTimeThreshold) {
            titleTapCount++
            if (titleTapCount == 5) {
                devModeEnabled = !devModeEnabled
                titleTapCount = 0
            }
        } else {
            titleTapCount = 1
        }
        lastTapTime = currentTime
    }

    // 既定背景はテーマに追従する（ダークテーマで白背景に固定しない）
    val isDarkDefault = useDefaultBackground && MaterialTheme.colorScheme.background.luminance() < 0.5f
    val themeBackgroundHex = colorToHex(MaterialTheme.colorScheme.background)
    val themeTextHex = colorToHex(MaterialTheme.colorScheme.onBackground)
    // 本文描画に使う背景色・文字色（既定背景時: ライトは従来どおり白、ダークはテーマ色）
    val contentBackgroundHex = when {
        !useDefaultBackground -> backgroundColor
        isDarkDefault -> themeBackgroundHex
        else -> "#FFFFFF"
    }
    val contentFontColor = if (isDarkDefault) themeTextHex else fontColor

    // 背景色の設定を計算
    val actualBackgroundColor = if (useDefaultBackground) {
        MaterialTheme.colorScheme.background
    } else {
        try {
            Color(AndroidColor.parseColor(backgroundColor))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.background
        }
    }
    // 読書位置の保存。画面離脱直後に画面のスコープがキャンセルされても保存が落ちないよう
    // アプリ全体のスコープで書き込み、横書きは JS ブリッジで追跡済みの値を使う
    // （離脱時に evaluateJavascript すると WebView 破棄と競合してコールバックが来ない）
    fun saveReadingRate() {
        if (!readingRateReady) return
        val rate = if (textOrientation == "Vertical") vjapReadingRate else horizontalReadingRate
        val targetNcode = ncode
        val targetEpisodeNo = episodeNo
        NovelReaderApplication.getApplicationScope().launch(Dispatchers.IO) {
            try {
                repository.updateReadingRate(targetNcode, targetEpisodeNo, rate.coerceIn(0f, 1f))
            } catch (e: Exception) {
                AppLogger.e("EpisodeViewScreen", "読書位置の保存エラー: ${e.message}")
            }
        }
    }

    // バックグラウンド移行（ホーム・画面オフ・アプリ切替・構成変更）時にも読書位置を保存する
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentSaveReadingRate by rememberUpdatedState(::saveReadingRate)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) currentSaveReadingRate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun canGoNext(): Boolean {
        val currentEp = episodeNo.toIntOrNull() ?: return false
        return novel?.let { currentEp < it.total_ep } ?: false
    }

    // 最終ページでさらにページ送りされた場合: 設定ONなら次話へ（通常のページめくりと同じ扱い）
    fun goToNextEpisodeFromEnd() {
        if (!autoNextEpisodeAtEnd || nextEpisodeRequested) return
        if (!canGoNext()) {
            Toast.makeText(context, "最新話です", Toast.LENGTH_SHORT).show()
            return
        }
        nextEpisodeRequested = true
        saveReadingRate()
        onNext()
    }

    // 音量キーによるページ送り（true=次ページ、false=前ページ）
    fun handleVolumeKey(forward: Boolean) {
        if (episode == null || nextEpisodeRequested) return
        if (textOrientation == "Vertical") {
            if (forward) {
                if (!vjapPageController.nextPage()) goToNextEpisodeFromEnd()
            } else {
                vjapPageController.prevPage()
            }
            return
        }
        // 横書き: WebView内を画面の9割ずつスクロール。末尾でさらに送ると次話へ
        val direction = if (forward) 1 else -1
        webView?.evaluateJavascript(
            "(function(){if(window.__stopRestoring)window.__stopRestoring();var b=window.scrollY;" +
                "window.scrollBy(0,$direction*window.innerHeight*0.9);" +
                "return window.scrollY!==b;})()"
        ) { moved ->
            if (forward && moved == "false") goToNextEpisodeFromEnd()
        }
    }

    val currentHandleVolumeKey by rememberUpdatedState(::handleVolumeKey)
    DisposableEffect(volumeKeyPagingEnabled) {
        val handler: (Boolean) -> Unit = { forward -> currentHandleVolumeKey(forward) }
        if (volumeKeyPagingEnabled) VolumeKeyPaging.handler = handler
        onDispose {
            if (VolumeKeyPaging.handler === handler) VolumeKeyPaging.handler = null
        }
    }

    // ---- 本文内検索 ----
    fun jumpToVerticalMatch(index: Int) {
        val matches = verticalSearchMatches
        if (matches.isEmpty()) return
        val page = vjapPageController.findPageOfChar(matches[index])
        if (page < 0) {
            Toast.makeText(context, "ページ計算中です。少し待ってから再度お試しください", Toast.LENGTH_SHORT).show()
            return
        }
        vjapPageController.goToPage(page)
    }

    fun runSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        if (textOrientation == "Vertical") {
            val text = vjapPageController.text ?: return
            val matches = mutableListOf<Int>()
            var from = text.indexOf(query, 0, ignoreCase = true)
            while (from >= 0) {
                matches.add(from)
                from = text.indexOf(query, from + query.length, ignoreCase = true)
            }
            verticalSearchMatches = matches
            searchMatchCount = matches.size
            searchMatchIndex = 0
            if (matches.isEmpty()) {
                Toast.makeText(context, "見つかりませんでした", Toast.LENGTH_SHORT).show()
            } else {
                jumpToVerticalMatch(0)
            }
        } else {
            // 結果件数・現在位置は WebView の FindListener から反映される
            webView?.findAllAsync(query)
        }
    }

    fun searchStep(forward: Boolean) {
        if (searchMatchCount <= 0) return
        if (textOrientation == "Vertical") {
            val count = verticalSearchMatches.size
            searchMatchIndex = (searchMatchIndex + (if (forward) 1 else -1) + count) % count
            jumpToVerticalMatch(searchMatchIndex)
        } else {
            webView?.findNext(forward)
        }
    }

    fun closeSearch() {
        showSearchBar = false
        searchQuery = ""
        searchMatchCount = 0
        searchMatchIndex = 0
        verticalSearchMatches = emptyList()
        webView?.clearMatches()
    }

    // ---- 表示設定のクイック変更 ----
    fun changeFontSize(delta: Int) {
        val newSize = (fontSize + delta).coerceIn(12, 24)
        if (newSize == fontSize) return
        fontSize = newSize
        scope.launch { settingsStore.saveFontSize(newSize) }
    }

    fun changeLineHeight(delta: Float) {
        val newHeight = ((lineHeight + delta) * 10).let { Math.round(it) / 10f }
            .coerceIn(SettingsStore.MIN_LINE_HEIGHT, SettingsStore.MAX_LINE_HEIGHT)
        if (newHeight == lineHeight) return
        lineHeight = newHeight
        scope.launch { settingsStore.saveReaderLineHeight(newHeight) }
    }

    // 横書き: 指定の読書率までスクロールする（復元中なら復元を打ち切る）
    fun scrollHorizontalTo(rate: Float) {
        val clamped = rate.coerceIn(0f, 1f)
        webView?.evaluateJavascript(
            "(function(){if(window.__stopRestoring)window.__stopRestoring();" +
                "var m=document.body.scrollHeight-window.innerHeight;" +
                "if(m>0)window.scrollTo(0,m*$clamped);})()",
            null
        )
        horizontalReadingRate = clamped
    }

    // 進捗表示用の文字列
    val progressLabel: String = if (textOrientation == "Vertical") {
        if (vjapLastPage > 0) {
            val percent = (vjapPage.toFloat() / vjapLastPage * 100).toInt().coerceIn(0, 100)
            "$vjapPage / $vjapLastPage ページ（$percent%）"
        } else {
            "ページ計算中…"
        }
    } else {
        "${(horizontalReadingRate * 100).toInt().coerceIn(0, 100)}%"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchBar) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("本文内を検索") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    } else {
                        Text(
                            text = episode?.e_title ?: "エピソード $episodeNo",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onTitleTap() }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        saveReadingRate()
                        onBackToToc()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "目次に戻る")
                    }
                },
                actions = {
                    if (showSearchBar) {
                        if (searchMatchCount > 0) {
                            Text(
                                "${searchMatchIndex + 1}/$searchMatchCount",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        IconButton(onClick = { searchStep(false) }, enabled = searchMatchCount > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "前の検索結果")
                        }
                        IconButton(onClick = { searchStep(true) }, enabled = searchMatchCount > 0) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "次の検索結果")
                        }
                        IconButton(onClick = { closeSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "検索を閉じる")
                        }
                        return@TopAppBar
                    }

                    IconButton(onClick = { showSearchBar = true }, enabled = episode != null) {
                        Icon(Icons.Default.Search, contentDescription = "本文内を検索")
                    }
                    IconButton(onClick = { showNavigator = !showNavigator }, enabled = episode != null) {
                        Icon(Icons.Default.Tune, contentDescription = "ページ移動スライダー")
                    }

                    // しおりボタン
                    episode?.let {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        // しおりフラグを反転
                                        val newBookmarkStatus = if (it.is_bookmark == 1) 0 else 1
                                        repository.updateEpisodeBookmarkStatus(ncode, episodeNo, newBookmarkStatus)

                                        // 表示を更新するために再取得
                                        episode = repository.getEpisode(ncode, episodeNo)

                                        // ユーザーに通知
                                        val message = if (newBookmarkStatus == 1) "しおりを追加しました" else "しおりを削除しました"
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        AppLogger.e("EpisodeViewScreen", "しおり更新エラー: ${e.message}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (it.is_bookmark == 1) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (it.is_bookmark == 1) "しおりを削除" else "しおりを追加",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "表示設定")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("文字を大きく（$fontSize）") },
                                leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
                                enabled = fontSize < 24,
                                onClick = { changeFontSize(1) }
                            )
                            DropdownMenuItem(
                                text = { Text("文字を小さく") },
                                leadingIcon = { Icon(Icons.Default.ZoomOut, contentDescription = null) },
                                enabled = fontSize > 12,
                                onClick = { changeFontSize(-1) }
                            )
                            DropdownMenuItem(
                                text = { Text("行間を広く（${"%.1f".format(lineHeight)}）") },
                                leadingIcon = { Icon(Icons.Default.UnfoldMore, contentDescription = null) },
                                enabled = lineHeight < SettingsStore.MAX_LINE_HEIGHT,
                                onClick = { changeLineHeight(0.1f) }
                            )
                            DropdownMenuItem(
                                text = { Text("行間を狭く") },
                                leadingIcon = { Icon(Icons.Default.UnfoldLess, contentDescription = null) },
                                enabled = lineHeight > SettingsStore.MIN_LINE_HEIGHT,
                                onClick = { changeLineHeight(-0.1f) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showReadingProgress) "進捗表示を隠す" else "進捗表示を出す") },
                                leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) },
                                onClick = {
                                    val newValue = !showReadingProgress
                                    showReadingProgress = newValue
                                    showOverflowMenu = false
                                    scope.launch { settingsStore.saveShowReadingProgress(newValue) }
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
          Column {
            if (showNavigator && episode != null) {
                ReadingNavigatorPanel(
                    isVertical = textOrientation == "Vertical",
                    page = vjapPage,
                    lastPage = vjapLastPage,
                    readingRate = horizontalReadingRate,
                    progressLabel = progressLabel,
                    onPageSelected = { vjapPageController.goToPage(it) },
                    onRateSelected = { scrollHorizontalTo(it) }
                )
            }
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 縦書き時と横書き時でボタンの配置を変更
                    if (textOrientation == "Vertical") {
                        // 縦書き時: 次 - 目次 - 前（右から左へ読み進む）

                        // 次のエピソード（左側）
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    enabled = novel?.let {
                                        episodeNo.toIntOrNull()?.let { epNo ->
                                            epNo < it.total_ep
                                        } ?: false
                                    } ?: false,
                                    onClick = {
                                        saveReadingRate()
                                        onNext()
                                    }
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,  // 左矢印
                                contentDescription = "次のエピソード",
                                tint = if (novel?.let {
                                        episodeNo.toIntOrNull()?.let { epNo ->
                                            epNo < it.total_ep
                                        } ?: false
                                    } ?: false) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Text(
                                "次のエピソード",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (novel?.let {
                                        episodeNo.toIntOrNull()?.let { epNo ->
                                            epNo < it.total_ep
                                        } ?: false
                                    } ?: false) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        // 目次に戻る（中央）
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = {
                                    saveReadingRate()
                                    onBackToToc()
                                })
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.List, contentDescription = "目次に戻る")
                            Text("目次に戻る", style = MaterialTheme.typography.labelSmall)
                        }

                        // 前のエピソード（右側）
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    enabled = episodeNo.toIntOrNull()?.let { it > 1 } ?: false,
                                    onClick = {
                                        saveReadingRate()
                                        onPrevious()
                                    }
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,  // 右矢印
                                contentDescription = "前のエピソード",
                                tint = if (episodeNo.toIntOrNull()?.let { it > 1 } ?: false)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Text(
                                "前のエピソード",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (episodeNo.toIntOrNull()?.let { it > 1 } ?: false)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    } else {
                        // 横書き時: 前 - 目次 - 次（従来通り）

                        // 前のエピソード（左側）
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    enabled = episodeNo.toIntOrNull()?.let { it > 1 } ?: false,
                                    onClick = {
                                        saveReadingRate()
                                        onPrevious()
                                    }
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,  // 左矢印
                                contentDescription = "前のエピソード",
                                tint = if (episodeNo.toIntOrNull()?.let { it > 1 } ?: false)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Text(
                                "前のエピソード",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (episodeNo.toIntOrNull()?.let { it > 1 } ?: false)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        // 目次に戻る（中央）
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = {
                                    saveReadingRate()
                                    onBackToToc()
                                })
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.List, contentDescription = "目次に戻る")
                            Text("目次に戻る", style = MaterialTheme.typography.labelSmall)
                        }

                        // 次のエピソード（右側）
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    enabled = novel?.let {
                                        episodeNo.toIntOrNull()?.let { epNo ->
                                            epNo < it.total_ep
                                        } ?: false
                                    } ?: false,
                                    onClick = {
                                        saveReadingRate()
                                        onNext()
                                    }
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,  // 右矢印
                                contentDescription = "次のエピソード",
                                tint = if (novel?.let {
                                        episodeNo.toIntOrNull()?.let { epNo ->
                                            epNo < it.total_ep
                                        } ?: false
                                    } ?: false) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Text(
                                "次のエピソード",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (novel?.let {
                                        episodeNo.toIntOrNull()?.let { epNo ->
                                            epNo < it.total_ep
                                        } ?: false
                                    } ?: false) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
          }
        }
    ) { innerPadding ->
        // システムバックボタンで直前の画面に戻る
        BackHandler {
            saveReadingRate()
            onBack()
        }
        
        // エピソード本文の表示
        if (episode != null && settingsLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // 本文（縦書きvjap・横書きWebViewとも）は既定背景を白で描画するため、余白部分も同じ色に揃える
                    .background(parseColorOr(contentBackgroundHex, actualBackgroundColor))
                    .pointerInput(episodeNo, textOrientation, swipeEnabled, tapEnabled) {
                        // スワイプとスクロールを区別するための改善されたジェスチャー検出
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPosition = down.position
                                val downTime = System.currentTimeMillis()

                                var totalDragX = 0f
                                var totalDragY = 0f
                                var isDragging = false
                                var isScrolling = false

                                // ドラッグを追跡
                                val dragResult = drag(down.id) { change ->
                                    val dragAmount = change.position - change.previousPosition
                                    totalDragX += dragAmount.x
                                    totalDragY += dragAmount.y

                                    // 初動の判定：スクロールかスワイプかを決定
                                    if (!isDragging && !isScrolling) {
                                        val absX = abs(totalDragX)
                                        val absY = abs(totalDragY)

                                        // 一定距離移動したら方向を判定
                                        if (absX > 10f || absY > 10f) {
                                            if (textOrientation == "Vertical") {
                                                // 縦書き時: 横スクロールが必要
                                                // 縦方向の動き→スワイプ、横方向の動き→スクロール
                                                if (absY > absX * 1.5f && swipeEnabled) {
                                                    // 縦方向の動きが明確→スワイプと判定
                                                    isDragging = true
                                                } else {
                                                    // 横方向の動き→WebViewのスクロールに任せる
                                                    isScrolling = true
                                                }
                                            } else {
                                                // 横書き時: 縦スクロールが必要
                                                // 横方向の動き→スワイプ、縦方向の動き→スクロール
                                                if (absX > absY * 1.5f && swipeEnabled) {
                                                    // 横方向の動きが明確→スワイプと判定
                                                    isDragging = true
                                                } else {
                                                    // 縦方向の動き→WebViewのスクロールに任せる
                                                    isScrolling = true
                                                }
                                            }
                                        }
                                    }

                                    // スワイプと判定された場合のみイベントを消費
                                    if (isDragging) {
                                        change.consume()
                                    }
                                }

                                // ドラッグ終了時の処理
                                if (dragResult && isDragging && !isScrolling) {
                                    val swipeTime = System.currentTimeMillis() - downTime
                                    val swipeThreshold = 200f // スワイプと判定する最小距離
                                    val maxSwipeTime = 500L // スワイプと判定する最大時間（素早い動作）

                                    val currentEp = episodeNo.toIntOrNull() ?: 1
                                    val canPrev = currentEp > 1
                                    val canNext = novel?.let { currentEp < it.total_ep } ?: true

                                    if (textOrientation == "Vertical") {
                                        // 縦書き時: 縦方向のスワイプでページ切り替え
                                        if (abs(totalDragY) > swipeThreshold && swipeTime < maxSwipeTime) {
                                            saveReadingRate()
                                            if (totalDragY < 0 && canNext) {
                                                // 上スワイプ=次
                                                onNext()
                                            } else if (totalDragY > 0 && canPrev) {
                                                // 下スワイプ=前
                                                onPrevious()
                                            }
                                        }
                                    } else {
                                        // 横書き時: 横方向のスワイプでページ切り替え
                                        if (abs(totalDragX) > swipeThreshold && swipeTime < maxSwipeTime) {
                                            saveReadingRate()
                                            if (totalDragX > 0 && canPrev) {
                                                // 右スワイプ=前
                                                onPrevious()
                                            } else if (totalDragX < 0 && canNext) {
                                                // 左スワイプ=次
                                                onNext()
                                            }
                                        }
                                    }
                                } else if (!dragResult) {
                                    // タップの処理
                                    if (tapEnabled && abs(totalDragX) < 10f && abs(totalDragY) < 10f) {
                                        val width = size.width
                                        val currentEp = episodeNo.toIntOrNull() ?: 1
                                        val canPrev = currentEp > 1
                                        val canNext = novel?.let { currentEp < it.total_ep } ?: true
                                        saveReadingRate()

                                        if (textOrientation == "Vertical") {
                                            // 縦書き時: 左タップ=次、右タップ=前
                                            if (downPosition.x < width / 2f && canNext) {
                                                onNext()
                                            } else if (downPosition.x >= width / 2f && canPrev) {
                                                onPrevious()
                                            }
                                        } else {
                                            // 横書き時: 左タップ=前、右タップ=次
                                            if (downPosition.x < width / 2f && canPrev) {
                                                onPrevious()
                                            } else if (downPosition.x >= width / 2f && canNext) {
                                                onNext()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ユーザー設定の余白（タップ判定は余白部分も含めた画面全体で行う）
                    .padding(
                        start = readerMargins.left.dp,
                        top = readerMargins.top.dp,
                        end = readerMargins.right.dp,
                        bottom = readerMargins.bottom.dp
                    )
            ) {
                // 縦書き時はWebViewが横スクロールするため、verticalScrollは不要
                // 縦書き時はCSSがpaddingを管理するためComposeのhorizontal paddingは不要
                // 横書きWebViewは自身でスクロールさせる（外側をverticalScrollにすると
                // WebViewが全高で配置され window.scrollY が常に0となり、読書位置の保存・復元が効かない）
                val columnModifier = when {
                    devModeEnabled -> Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState)
                    textOrientation == "Vertical" -> Modifier
                        .fillMaxSize()
                    else -> Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                }

                Column(
                    modifier = columnModifier
                ) {
                    // 本文表示
                    if (devModeEnabled) {
                        // 開発者モード: HTMLソースを表示
                        Column {
                            Text(
                                text = "HTML Source:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = try {
                                    Color(AndroidColor.parseColor(fontColor))
                                } catch (e: Exception) {
                                    MaterialTheme.colorScheme.onBackground
                                }
                            )
                            Text(
                                text = episode!!.body,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = fontSize.sp,
                                    color = try {
                                        Color(AndroidColor.parseColor(fontColor))
                                    } catch (e: Exception) {
                                        MaterialTheme.colorScheme.onBackground
                                    }
                                ),
                                modifier = Modifier.padding(bottom = 32.dp)
                            )
                            HorizontalDivider()
                        }
                    } else if (textOrientation == "Vertical") {
                        // 縦書きモード: vjapライブラリで描画
                        key(ncode, episodeNo) {
                            VjapVerticalTextView(
                                htmlContent = applyRubyFixes(episode!!.body, autoRubyEnabled),
                                episodeTitle = episode!!.e_title ?: "第${episodeNo}話",
                                fontSize = fontSize,
                                fontColor = contentFontColor,
                                backgroundColor = contentBackgroundHex,
                                customFontPath = if (isCustomFont && customFontPath.isNotEmpty()) customFontPath else null,
                                // 縦書きはルビを行間に描くため横書きの行間より 0.2 広く取る
                                lineSpaceRate = lineHeight + 0.2f,
                                savedReadingRate = episode!!.reading_rate,
                                onReadingRateChanged = { rate -> vjapReadingRate = rate },
                                onPageInfoChanged = { page, last ->
                                    vjapPage = page
                                    vjapLastPage = last
                                },
                                // 「画面タップで話を移動」ON時はタップ自体が話移動になるため二重遷移させない
                                onNextAtEnd = { if (!tapEnabled) goToNextEpisodeFromEnd() },
                                controller = vjapPageController,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // 横書きモード: WebViewでHTML表示
                        // key()で話ごとにWebViewを再生成し、JSブリッジ（WebViewScrollInterface）が
                        // 古いepisodeNoを保持して別の話の進捗を上書きするのを防ぐ
                        key(ncode, episodeNo) {
                            EnhancedHtmlRubyWebView(
                                htmlContent = episode!!.body,
                                fontSize = fontSize,
                                rubyFontSize = (fontSize * 0.6).toInt(),
                                backgroundColor = contentBackgroundHex,
                                fontColor = contentFontColor,
                                lineHeight = lineHeight,
                                fontFamily = fontFamily,
                                isCustomFont = isCustomFont,
                                customFontPath = customFontPath,
                                textOrientation = textOrientation,
                                autoRubyEnabled = autoRubyEnabled,
                                ncode = ncode,
                                episodeNo = episodeNo,
                                savedReadingRate = episode!!.reading_rate,
                                modifier = Modifier.padding(bottom = 32.dp),
                                onReadingRateChanged = { rate -> horizontalReadingRate = rate },
                                onWebViewCreated = { created ->
                                    webView = created
                                    created.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                                        if (isDoneCounting) {
                                            searchMatchCount = numberOfMatches
                                            searchMatchIndex = activeMatchOrdinal
                                            if (numberOfMatches == 0 && searchQuery.isNotBlank()) {
                                                Toast.makeText(context, "見つかりませんでした", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onWebViewReleased = { released ->
                                    if (webView === released) webView = null
                                }
                            )
                        }
                    }
                }

                // 読書進捗（ページ数・％）
                if (showReadingProgress && !devModeEnabled) {
                    Text(
                        text = progressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = parseColorOr(contentFontColor, MaterialTheme.colorScheme.onBackground)
                            .copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(if (textOrientation == "Vertical") Alignment.BottomStart else Alignment.BottomEnd)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            // ローディング表示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(actualBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

}

// Applies broken ruby-tag repair and optional auto-ruby conversion.
// Extracted from EnhancedHtmlRubyWebView so the vertical path can use the same logic.
private fun applyRubyFixes(html: String, autoRubyEnabled: Boolean): String {
    var fixed = html.replace("<ruby>([^<]*?)</rb>\\(([^)]*?)\\)".toRegex()) {
        "<ruby>${it.groupValues[1]}<rt>${it.groupValues[2]}</rt></ruby>"
    }
    fixed = fixed.replace("<ruby>([^<(]*?)\\(([^)]*?)\\)".toRegex()) {
        "<ruby>${it.groupValues[1]}<rt>${it.groupValues[2]}</rt></ruby>"
    }
    if (autoRubyEnabled) {
        fixed = fixed.replace("([^<>\\s]+?)\\(([^)]+?)\\)".toRegex()) {
            "<ruby>${it.groupValues[1]}<rt>${it.groupValues[2]}</rt></ruby>"
        }
    }
    return fixed
}

// Add these utility functions
private fun isHtmlContent(content: String): Boolean {
    // Simple check for HTML tags
    return content.contains("<[^>]*>".toRegex())
}

private fun convertPlainTextToHtml(plainText: String): String {
    return plainText.split("\n").joinToString("\n") { line ->
        if (line.trim().isNotEmpty()) {
            "<p>$line</p>"
        } else {
            "<p>&nbsp;</p>" // Empty paragraph for blank lines
        }
    }
}

/**
 * 横書き WebView と読書位置をやり取りする JS ブリッジ。
 * - スクロールのたびに現在位置を Compose 側へ通知し、一定以上変化したら DB へ保存する
 * - 再読み込み（文字サイズ・行間変更など）時は直前の位置を復元位置として JS へ渡す
 */
class WebViewScrollInterface(
    private val ncode: String,
    private val episodeNo: String,
    private val repo: NovelRepository,
    initialRate: Float,
    private val onPositionChanged: (Float) -> Unit,
    private val updateThreshold: Float = 0.01f // 更新する最小変化量
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 次回ページ読み込み時に復元する読書率 */
    @Volatile
    private var restoreRate = initialRate.coerceIn(0f, 1f)

    /** JS から通知された最新の読書率 */
    @Volatile
    private var lastPosition = initialRate.coerceIn(0f, 1f)

    private var lastSavedPosition = initialRate.coerceIn(0f, 1f)

    @JavascriptInterface
    fun getRestoreRate(): Float = restoreRate

    @JavascriptInterface
    fun saveScrollPosition(position: Float) {
        // 本文はサニタイズ済みだが、ブリッジ境界は信頼できない値が来うる前提で
        // ネイティブ側でも読書率を必ず 0f〜1f にクランプし reading_rate の破壊を防ぐ
        val clamped = if (position.isNaN()) return else position.coerceIn(0f, 1f)
        lastPosition = clamped
        mainHandler.post { onPositionChanged(clamped) }

        // 先頭・末尾への到達は変化量が閾値未満でも保存する（0.995→1.0 の取りこぼし防止）
        val reachedEdge = (clamped == 0f || clamped == 1f) && clamped != lastSavedPosition
        if (reachedEdge || abs(clamped - lastSavedPosition) > updateThreshold) {
            lastSavedPosition = clamped
            NovelReaderApplication.getApplicationScope().launch(Dispatchers.IO) {
                try {
                    repo.updateReadingRate(ncode, episodeNo, clamped)
                } catch (e: Exception) {
                    AppLogger.e("WebViewScrollInterface", "読書位置の保存エラー: ${e.message}")
                }
            }
        }
    }

    /** 再読み込み前に、現在位置を次回の復元位置として確定する */
    fun prepareReload() {
        restoreRate = lastPosition
    }
}

/**
 * 外部サイトからスクレイプした本文HTMLを WebView 表示前にサニタイズする。
 * `<script>` やイベント属性(on*)を除去し、ルビ・画像・基本的な整形タグのみ許可する。
 * これにより JS 有効・JSブリッジ露出の WebView 上で本文由来の任意JS実行(XSS)を防ぐ。
 */
private val episodeBodySafelist: Safelist by lazy {
    Safelist.relaxed()
        // ルビ(ふりがな)関連タグを許可
        .addTags("ruby", "rt", "rp", "rb")
        // ローカルにキャッシュした挿絵(file/content)やデータURIの画像を表示できるよう許可
        .addProtocols("img", "src", "http", "https", "file", "content", "data")
}

/** content:// の挿絵として許可するプロバイダ（画像保存先に選べる SAF のストレージ） */
private val allowedImageContentAuthorities = setOf(
    "com.android.externalstorage.documents",
    "com.android.providers.downloads.documents"
)

/**
 * 挿絵の参照先を検証する。file:// はアプリ専用領域（画像キャッシュ）のみ、
 * content:// は画像保存先として使うストレージプロバイダのみ許可し、
 * 本文経由で端末内の任意ファイルを読み込ませない。
 */
private fun isAllowedImageSource(src: String, appFilesDir: String?): Boolean {
    val lower = src.lowercase()
    return when {
        lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("data:image/") -> true
        lower.startsWith("file://") -> {
            val path = Uri.parse(src).path ?: return false
            appFilesDir != null && !path.contains("..") && path.startsWith(appFilesDir)
        }
        lower.startsWith("content://") -> Uri.parse(src).authority in allowedImageContentAuthorities
        else -> false
    }
}

private fun sanitizeEpisodeHtml(html: String, appFilesDir: String? = null): String {
    return try {
        val cleaned = Jsoup.clean(html, episodeBodySafelist)
        val document = Jsoup.parseBodyFragment(cleaned)
        document.select("img[src]").forEach { img ->
            if (!isAllowedImageSource(img.attr("src"), appFilesDir)) img.remove()
        }
        document.body().html()
    } catch (e: Exception) {
        // 想定外の失敗時はテキスト化してでも安全側に倒す（生HTMLは絶対に流さない）
        Jsoup.clean(html, Safelist.none())
    }
}

// EnhancedHtmlRubyWebView関数を修正（スクロール位置保存・復元機能追加）
@Composable
fun EnhancedHtmlRubyWebView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 18,
    rubyFontSize: Int = 10,
    backgroundColor: String? = null,
    fontColor: String = "#000000",
    fontFamily: String = "Gothic",
    isCustomFont: Boolean = false,
    customFontPath: String = "",
    textOrientation: String = "Horizontal",
    autoRubyEnabled: Boolean = true,
    lineHeight: Float = SettingsStore.DEFAULT_LINE_HEIGHT,
    ncode: String,
    episodeNo: String,
    savedReadingRate: Float = 0f,
    repository: NovelRepository = NovelReaderApplication.getRepository(),
    onReadingRateChanged: (Float) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onWebViewReleased: (WebView) -> Unit
) {
    val context = LocalContext.current
    val currentOnReadingRateChanged by rememberUpdatedState(onReadingRateChanged)
    // 話ごと（呼び出し側の key(ncode, episodeNo)）に1つのブリッジを保持する
    val scrollInterface: WebViewScrollInterface = remember {
        WebViewScrollInterface(ncode, episodeNo, repository, savedReadingRate, { rate ->
            currentOnReadingRateChanged(rate)
        })
    }

    // HTMLを修正する関数（共通実装は applyRubyFixes に委譲）
    fun fixRubyTags(html: String): String = applyRubyFixes(html, autoRubyEnabled)

    // 背景色の設定（デフォルトの場合はテーマの色）
    val bgColor = backgroundColor ?: "#FFFFFF"

    // 文章の向き
    val writingMode = if (textOrientation == "Vertical") {
        "vertical-rl"
    } else {
        "horizontal-tb"
    }

    // カスタムフォントのCSS生成
    val customFontCss = if (isCustomFont && customFontPath.isNotEmpty()) {
        FontUtils.generateCustomFontCss(customFontPath)
    } else {
        ""
    }

    // フォントファミリーの設定
    val actualFontFamily = if (isCustomFont) {
        FontUtils.fontNameToCssFontFamily(fontFamily, true)
    } else {
        FontUtils.fontNameToCssFontFamily(fontFamily)
    }

    // ルビ用のCSSスタイルを定義
    val cssStyle = """
    <style>
        $customFontCss
        html {
            height: 100%;
            width: 100%;
            ${if (textOrientation == "Vertical") "overflow-x: scroll; overflow-y: hidden;" else ""}
        }
        body {
            font-family: $actualFontFamily;
            font-size: ${fontSize}px;
            line-height: $lineHeight;
            padding: 16px;
            margin: 0;
            background-color: $bgColor;
            color: $fontColor;
            writing-mode: $writingMode;
            ${if (textOrientation == "Vertical") {
                // width: max-content で縦書きコンテンツ全体を横方向に展開
                // min-width: 100% で最低でも画面幅は確保
                "height: 100%; width: max-content; min-width: 100%; overflow-x: scroll; overflow-y: hidden;"
            } else {
                "min-height: 100vh; overflow-y: auto; word-wrap: break-word; overflow-wrap: break-word;"
            }}
            box-sizing: border-box;
        }
        p {
            margin: 0.5em 0;
            ${if (textOrientation == "Vertical") "" else "word-wrap: break-word; overflow-wrap: break-word;"}
        }
        ruby {
            display: ruby;
            ruby-align: center;
            ${if (textOrientation == "Horizontal") {
                "ruby-position: over;"
            } else {
                "ruby-position: right;"
            }}
        }
        rt {
            display: ruby-text;
            font-size: ${rubyFontSize}px;
            text-align: center;
            line-height: 1.2;
            color: $fontColor;
        }
        img {
            max-width: 100%;
            height: auto;
            display: block;
            margin: 16px auto;
        }
    </style>
    """.trimIndent()

    val processedContent = if (isHtmlContent(htmlContent)) {
        htmlContent
    } else {
        convertPlainTextToHtml(htmlContent)
    }

    // HTMLを修正
    val fixedHtml = fixRubyTags(processedContent)

    // 外部サイト由来の本文HTMLをサニタイズ（script/on*除去、XSS防止）。
    // アプリが注入する scrollMonitorScript は別途head内に置くため影響を受けない。
    val sanitizedHtml = sanitizeEpisodeHtml(fixedHtml, context.filesDir.absolutePath)

    // スクロール位置を保存・復元するためのJavaScriptを追加
    val isVertical = textOrientation == "Vertical"
    val scrollMonitorScript = """
    <script>
        // ページ読み込み完了後の処理
        window.onload = function() {
            var isVertical = ${isVertical};
            // 復元位置はブリッジから取得する（文字サイズ変更などの再読み込み時は直前の位置になる）
            var restoreRate = (typeof Android !== 'undefined') ? Number(Android.getRestoreRate()) : 0;
            if (!(restoreRate >= 0 && restoreRate <= 1)) restoreRate = 0;
            // 位置復元中は保存しない（復元途中の位置で保存済みの読書位置を上書きしないため）
            var restoring = false;
            // 音量キー・スライダー操作から復元を打ち切れるよう公開する（復元中でなければ何もしない）
            window.__stopRestoring = function() {};

            // スクロール位置を復元
            var maxScroll;
            var targetPosition;

            if (isVertical) {
                // 縦書き(vertical-rl): 右端が先頭、左端が末尾
                // scrollX=maxScroll が先頭(rate=0)、scrollX=0 が末尾(rate=1)
                maxScroll = document.body.scrollWidth - window.innerWidth;
                if (maxScroll > 0) {
                    if (restoreRate > 0) {
                        // 保存済みレートから位置を復元（反転: rate=0→右端, rate=1→左端）
                        targetPosition = maxScroll * (1.0 - restoreRate);
                    } else {
                        // 未読: 先頭（右端）にスクロール
                        targetPosition = maxScroll;
                    }
                    setTimeout(function() {
                        window.scrollTo(targetPosition, 0);
                    }, 150);
                }
            } else {
                // 横書き: 縦スクロール
                // 読み込み直後は本文の高さが確定せず（文字サイズ自動調整等）位置がずれるため、
                // 一定時間（または利用者の操作まで）高さの変化に合わせて復元をかけ直す
                if (restoreRate > 0) {
                    restoring = true;
                    var applyRestore = function() {
                        if (!restoring) return;
                        var m = document.body.scrollHeight - window.innerHeight;
                        if (m > 0) window.scrollTo(0, m * restoreRate);
                    };
                    var stopRestoring = function() {
                        restoring = false;
                        if (resizeObserver) resizeObserver.disconnect();
                    };
                    window.__stopRestoring = stopRestoring;
                    var resizeObserver = (typeof ResizeObserver !== 'undefined')
                        ? new ResizeObserver(applyRestore) : null;
                    if (resizeObserver) resizeObserver.observe(document.body);
                    window.addEventListener('resize', applyRestore);
                    window.addEventListener('touchstart', stopRestoring, { passive: true });
                    applyRestore();
                    setTimeout(applyRestore, 100);
                    setTimeout(stopRestoring, 1500);
                }
            }

            // スクロール位置を監視して保存
            var scrollTimeout;
            window.addEventListener('scroll', function() {
                clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(function() {
                    if (restoring) return;
                    var maxScroll;
                    var currentScroll;
                    var scrollRatio;

                    if (isVertical) {
                        // 縦書き(vertical-rl): scrollX=maxScroll が先頭(rate=0)、scrollX=0 が末尾(rate=1)
                        maxScroll = document.body.scrollWidth - window.innerWidth;
                        currentScroll = window.scrollX;
                        if (maxScroll <= 0) return;
                        scrollRatio = 1.0 - (currentScroll / maxScroll);
                    } else {
                        // 横書き: 縦スクロール
                        maxScroll = document.body.scrollHeight - window.innerHeight;
                        currentScroll = window.scrollY;
                        if (maxScroll <= 0) return;
                        scrollRatio = currentScroll / maxScroll;
                    }

                    // 値の範囲を制限（0～1の範囲内に収める）
                    scrollRatio = Math.max(0, Math.min(1, scrollRatio));

                    // JavaScriptインターフェースを通じて値を保存
                    if (typeof Android !== 'undefined') {
                        Android.saveScrollPosition(scrollRatio);
                    }
                }, 300); // 300ms後に実行（スクロール中の頻繁な更新を防ぐ）
            });
        };
    </script>
    """.trimIndent()

    // HTMLコンテンツを整形
    val formattedHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=5.0">
            $cssStyle
            $scrollMonitorScript
        </head>
        <body>
            $sanitizedHtml
        </body>
        </html>
    """.trimIndent()

    // WebViewでHTMLをレンダリング
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true // JavaScriptを有効化
                    defaultFontSize = fontSize
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    defaultTextEncodingName = "UTF-8"
                    // ローカルファイル（ダウンロードした画像）へのアクセスを許可
                    allowFileAccess = true
                    allowContentAccess = true
                    // file:// からの他ファイル/クロスオリジン読み取りは明示的に禁止（万一の情報漏洩防止）
                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false
                    // レイアウト設定を追加
                    useWideViewPort = true
                    loadWithOverviewMode = false
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                }
                onWebViewCreated(this)
                // JavaScriptインターフェースを追加
                attachScrollBridge(scrollInterface)
                // HTMLをロード
                loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
                tag = formattedHtml
            }
        },
        update = { view ->
            // 縦書き時はTEXT_AUTOSIZINGを無効化（vertical-rlレイアウトに干渉するため）
            view.settings.layoutAlgorithm = if (textOrientation == "Vertical") {
                WebSettings.LayoutAlgorithm.NORMAL
            } else {
                WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            }
            // HTMLが実際に変わった場合のみ再ロード
            // （無条件に再ロードすると再コンポーズのたびにスクロール位置がリセットされる）
            if (view.tag != formattedHtml) {
                scrollInterface.prepareReload()
                view.loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
                view.tag = formattedHtml
            }
        },
        onRelease = { view ->
            onWebViewReleased(view)
            view.stopLoading()
            view.destroy()
        },
        modifier = modifier.fillMaxSize()
    )

}

/** 読書位置ブリッジを JS の `Android` オブジェクトとして公開する */
private fun WebView.attachScrollBridge(bridge: WebViewScrollInterface) {
    addJavascriptInterface(bridge, "Android")
}

/** "#RRGGBB" 等の色文字列を解析し、失敗時は [fallback] を返す */
private fun parseColorOr(hex: String, fallback: Color): Color =
    try {
        Color(AndroidColor.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }

/** Compose の色を "#RRGGBB" 形式へ変換する（WebView の CSS・vjap に渡すため） */
private fun colorToHex(color: Color): String =
    String.format("#%06X", 0xFFFFFF and color.toArgb())

/**
 * 読書画面下部のページ移動パネル。
 * 縦書きはページ番号、横書きはスクロール位置（％）をスライダーで指定する。
 * 縦書きは右から左へ読み進むため、スライダーも右端を先頭にする。
 */
@Composable
private fun ReadingNavigatorPanel(
    isVertical: Boolean,
    page: Int,
    lastPage: Int,
    readingRate: Float,
    progressLabel: String,
    onPageSelected: (Int) -> Unit,
    onRateSelected: (Float) -> Unit
) {
    // ドラッグ中の値（null のときは現在位置に追従）
    var dragValue by remember { mutableStateOf<Float?>(null) }
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (isVertical && lastPage <= 0) {
                Text("ページ計算中…", style = MaterialTheme.typography.labelMedium)
                return@Column
            }
            val label = when {
                dragValue == null -> progressLabel
                isVertical -> "${dragValue!!.toInt()} / $lastPage ページ"
                else -> "${(dragValue!! * 100).toInt()}%"
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
            val slider: @Composable () -> Unit = {
                if (isVertical) {
                    Slider(
                        value = (dragValue ?: page.toFloat()).coerceIn(1f, lastPage.toFloat()),
                        // タップでも確実に移動するよう、値の変化ごとに即座に反映する
                        onValueChange = {
                            val target = Math.round(it)
                            dragValue = target.toFloat()
                            if (target != page) onPageSelected(target)
                        },
                        onValueChangeFinished = { dragValue = null },
                        valueRange = 1f..maxOf(lastPage, 2).toFloat()
                    )
                } else {
                    Slider(
                        value = (dragValue ?: readingRate).coerceIn(0f, 1f),
                        onValueChange = {
                            dragValue = it
                            onRateSelected(it)
                        },
                        onValueChangeFinished = { dragValue = null }
                    )
                }
            }
            if (isVertical) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { slider() }
            } else {
                slider()
            }
        }
    }
}
