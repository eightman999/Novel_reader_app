package com.shunlight_library.novel_reader

import android.graphics.Color as AndroidColor
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.repository.NovelRepository
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

    // カスタムフォント情報
    var customFonts by remember { mutableStateOf<List<CustomFontInfo>>(emptyList()) }
    var isCustomFont by remember { mutableStateOf(false) }
    var customFontPath by remember { mutableStateOf("") }

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

//        scrollState.scrollTo(0)
        scope.launch {
            try {
                // エピソード情報の取得
                episode = repository.getEpisode(ncode, episodeNo)
                novel = repository.getNovelByNcode(ncode)

                // エピソードを既読に設定
                val episodeNumber = episodeNo.toIntOrNull() ?: 1

                // 既存のエピソードが取得できた場合は既読マークを設定
                if (episode != null) {
                    // EpisodeEntityに追加した is_read フラグを true に設定
                    repository.updateEpisodeReadStatus(ncode, episodeNo, true)

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
        webView?.evaluateJavascript("""
        (function() {
            var maxScroll = document.body.scrollHeight - window.innerHeight;
            if (maxScroll <= 0) return 0;
            var currentScroll = window.scrollY;
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
                actions = {
                    // しおりボタン
                    episode?.let {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        // しおりフラグを反転
                                        val newBookmarkStatus = !it.is_bookmark
                                        repository.updateEpisodeBookmarkStatus(ncode, episodeNo, newBookmarkStatus)

                                        // 表示を更新するために再取得
                                        episode = repository.getEpisode(ncode, episodeNo)

                                        // ユーザーに通知
                                        val message = if (newBookmarkStatus) "しおりを追加しました" else "しおりを削除しました"
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e("EpisodeViewScreen", "しおり更新エラー: ${e.message}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (it.is_bookmark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (it.is_bookmark) "しおりを削除" else "しおりを追加",
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
                    // 前のエピソード
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
                            Icons.Default.ArrowBack,
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

                    // 目次に戻る
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

                    // 次のエピソード
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
                            Icons.Default.ArrowForward,
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
    ) { innerPadding ->
        // エピソード本文の表示
        if (episode != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(actualBackgroundColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState)
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
                    } else {
                        // 通常モード: WebViewでHTML表示
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
    ncode: String,
    episodeNo: String,
    savedReadingRate: Float = 0f,
    repository: NovelRepository = NovelReaderApplication.getRepository(),
   onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // HTMLを修正する関数
    fun fixRubyTags(html: String): String {
        // パターン1: <ruby>対象</rb>(ルビ) の修正
        var fixed = html.replace("<ruby>([^<]*?)</rb>\\(([^)]*?)\\)".toRegex()) {
            val base = it.groupValues[1]
            val ruby = it.groupValues[2]
            "<ruby>$base<rt>$ruby</rt></ruby>"
        }

        // パターン2: <ruby>対象(ルビ) の修正
        fixed = fixed.replace("<ruby>([^<(]*?)\\(([^)]*?)\\)".toRegex()) {
            val base = it.groupValues[1]
            val ruby = it.groupValues[2]
            "<ruby>$base<rt>$ruby</rt></ruby>"
        }

        // パターン3: 対象(ルビ) パターンをrubyタグに変換
        fixed = fixed.replace("([^<>\\s]+?)\\(([^)]+?)\\)".toRegex()) {
            val base = it.groupValues[1]
            val ruby = it.groupValues[2]
            "<ruby>$base<rt>$ruby</rt></ruby>"
        }

        return fixed
    }

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
        body {
            font-family: $actualFontFamily;
            font-size: ${fontSize}px;
            line-height: 1.8;
            padding: 16px;
            margin: 0;
            background-color: $bgColor;
            color: $fontColor;
            writing-mode: $writingMode;
        }
        ruby {
            ruby-align: center;
        }
        rt {
            font-size: ${rubyFontSize}px;
            text-align: center;
            line-height: 1;
            color: $fontColor;
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
    val scrollMonitorScript = """
    <script>
        // ページ読み込み完了後の処理
        window.onload = function() {
            // 保存された位置があれば復元
            if (${savedReadingRate} > 0) {
                // スクロール位置の計算
                var maxScroll = document.body.scrollHeight - window.innerHeight;
                var targetPosition = maxScroll * ${savedReadingRate};
                // スクロール位置の復元（少し遅延させて確実に実行）
                setTimeout(function() {
                    window.scrollTo(0, targetPosition);
                }, 100);
            }
            
            // スクロール位置を監視して保存
            var scrollTimeout;
            window.addEventListener('scroll', function() {
                clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(function() {
                    // 現在のスクロール位置を0～1の範囲に正規化
                    var maxScroll = document.body.scrollHeight - window.innerHeight;
                    if (maxScroll <= 0) return;
                    
                    var currentScroll = window.scrollY;
                    var scrollRatio = currentScroll / maxScroll;
                    
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
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
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
            // コンポーネントの更新が必要な場合
            view.loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
        },
        modifier = modifier.fillMaxSize()
    )

}