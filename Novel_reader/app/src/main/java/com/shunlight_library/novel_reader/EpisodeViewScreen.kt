/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Composable screen displaying episode content via WebView.
 */
package com.shunlight_library.novel_reader

import android.graphics.Color as AndroidColor
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
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
import com.shunlight_library.novel_reader.ui.components.VjapVerticalTextView
import com.shunlight_library.novel_reader.utils.FontUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

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
            Log.e("EpisodeViewScreen", "設定の読み込みエラー: ${e.message}")
        }
    }

    LaunchedEffect(ncode, episodeNo) {

        scrollState.scrollTo(0)
        scope.launch {
            try {
                // エピソード情報の取得
                episode = repository.getEpisode(ncode, episodeNo)
                novel = repository.getNovelByNcode(ncode)

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
                Log.e("EpisodeViewScreen", "データ取得エラー: ${e.message}")
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
    fun saveReadingRate() {
        if (textOrientation == "Vertical") {
            // vjap縦書きモード: ページベースの進捗率を直接保存
            scope.launch(Dispatchers.IO) {
                repository.updateReadingRate(ncode, episodeNo, vjapReadingRate)
            }
        } else {
            // WebView横書きモード: JavaScriptでスクロール位置を取得
            webView?.evaluateJavascript("""
            (function() {
                var maxScroll = document.body.scrollHeight - window.innerHeight;
                var currentScroll = window.scrollY;
                if (maxScroll <= 0) return 0;
                var scrollRatio = currentScroll / maxScroll;
                return Math.max(0, Math.min(1, scrollRatio));
            })();
        """.trimIndent()) { result ->
                val readingRate = result.toFloatOrNull() ?: 0f
                scope.launch(Dispatchers.IO) {
                    repository.updateReadingRate(ncode, episodeNo, readingRate)
                }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = episode?.e_title ?: "エピソード $episodeNo",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onTitleTap() }
                    )
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
                                        Log.e("EpisodeViewScreen", "しおり更新エラー: ${e.message}")
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

                    // 既存のフォントサイズボタン
                    IconButton(onClick = {
                        if (fontSize > 12) {
                            fontSize--
                            scope.launch {
                                settingsStore.saveFontSize(fontSize)
                            }
                        }
                    }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "フォントサイズを小さく")
                    }
                    IconButton(onClick = {
                        if (fontSize < 24) {
                            fontSize++
                            scope.launch {
                                settingsStore.saveFontSize(fontSize)
                            }
                        }
                    }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "フォントサイズを大きく")
                    }
                }
            )
        },
        bottomBar = {
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
    ) { innerPadding ->
        // システムバックボタンで直前の画面に戻る
        BackHandler {
            saveReadingRate()
            onBack()
        }
        
        // エピソード本文の表示
        if (episode != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(actualBackgroundColor)
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
            ) {
                // 縦書き時はWebViewが横スクロールするため、verticalScrollは不要
                // 縦書き時はCSSがpaddingを管理するためComposeのhorizontal paddingは不要
                val columnModifier = if (textOrientation == "Vertical") {
                    Modifier
                        .fillMaxSize()
                } else {
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState)
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
                        val bgColor = if (useDefaultBackground) "#FFFFFF" else backgroundColor
                        key(ncode, episodeNo) {
                            VjapVerticalTextView(
                                htmlContent = applyRubyFixes(episode!!.body, autoRubyEnabled),
                                episodeTitle = episode!!.e_title ?: "第${episodeNo}話",
                                fontSize = fontSize,
                                fontColor = fontColor,
                                backgroundColor = bgColor,
                                customFontPath = if (isCustomFont && customFontPath.isNotEmpty()) customFontPath else null,
                                savedReadingRate = episode!!.reading_rate,
                                onReadingRateChanged = { rate -> vjapReadingRate = rate },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // 横書きモード: WebViewでHTML表示
                        EnhancedHtmlRubyWebView(
                            htmlContent = episode!!.body,
                            fontSize = fontSize,
                            rubyFontSize = (fontSize * 0.6).toInt(),
                            backgroundColor = if (useDefaultBackground) null else backgroundColor,
                            fontColor = fontColor,
                            fontFamily = fontFamily,
                            isCustomFont = isCustomFont,
                            customFontPath = customFontPath,
                            textOrientation = textOrientation,
                            autoRubyEnabled = autoRubyEnabled,
                            ncode = ncode,
                            episodeNo = episodeNo,
                            savedReadingRate = episode!!.reading_rate,
                            modifier = Modifier.padding(bottom = 32.dp),
                            onWebViewCreated = { webView = it }
                        )
                    }
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

// EpisodeViewScreen.kt内に追加するWebViewScrollInterfaceクラス
class WebViewScrollInterface(
    private val ncode: String,
    private val episodeNo: String,
    private val repo: NovelRepository,
    private val scope: CoroutineScope,
    private val updateThreshold: Float = 0.01f // 更新する最小変化量
) {
    private var lastSavedPosition = 0f

    @JavascriptInterface
    fun saveScrollPosition(position: Float) {
        // 前回保存した位置との差が閾値を超えた場合のみ保存処理を実行
        if (abs(position - lastSavedPosition) > updateThreshold) {
            scope.launch(Dispatchers.IO) {
                repo.updateReadingRate(ncode, episodeNo, position)
                lastSavedPosition = position
            }
        }
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
    ncode: String,
    episodeNo: String,
    savedReadingRate: Float = 0f,
    repository: NovelRepository = NovelReaderApplication.getRepository(),
   onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
            line-height: 1.8;
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

    // スクロール位置を保存・復元するためのJavaScriptを追加
    val isVertical = textOrientation == "Vertical"
    val scrollMonitorScript = """
    <script>
        // ページ読み込み完了後の処理
        window.onload = function() {
            var isVertical = ${isVertical};

            // スクロール位置を復元
            var maxScroll;
            var targetPosition;

            if (isVertical) {
                // 縦書き(vertical-rl): 右端が先頭、左端が末尾
                // scrollX=maxScroll が先頭(rate=0)、scrollX=0 が末尾(rate=1)
                maxScroll = document.body.scrollWidth - window.innerWidth;
                if (maxScroll > 0) {
                    if (${savedReadingRate} > 0) {
                        // 保存済みレートから位置を復元（反転: rate=0→右端, rate=1→左端）
                        targetPosition = maxScroll * (1.0 - ${savedReadingRate});
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
                if (${savedReadingRate} > 0) {
                    maxScroll = document.body.scrollHeight - window.innerHeight;
                    targetPosition = maxScroll * ${savedReadingRate};
                    setTimeout(function() {
                        window.scrollTo(0, targetPosition);
                    }, 100);
                }
            }

            // スクロール位置を監視して保存
            var scrollTimeout;
            window.addEventListener('scroll', function() {
                clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(function() {
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
            $fixedHtml
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
                    // レイアウト設定を追加
                    useWideViewPort = true
                    loadWithOverviewMode = false
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                }
                onWebViewCreated(this)
                // JavaScriptインターフェースを追加
                addJavascriptInterface(
                    WebViewScrollInterface(ncode, episodeNo, repository, scope),
                    "Android"
                )
                // HTMLをロード
                loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
            }
        },
        update = { view ->
            // 縦書き時はTEXT_AUTOSIZINGを無効化（vertical-rlレイアウトに干渉するため）
            view.settings.layoutAlgorithm = if (textOrientation == "Vertical") {
                WebSettings.LayoutAlgorithm.NORMAL
            } else {
                WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            }
            view.loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
        modifier = modifier.fillMaxSize()
    )

}