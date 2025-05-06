package com.shunlight_library.novel_reader

import android.graphics.Color as AndroidColor
import android.util.Log
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
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeViewScreen(
    ncode: String,
    episodeNo: String,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
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

    // 開発者モード関連の状態
    var titleTapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var devModeEnabled by remember { mutableStateOf(false) }
    val tapTimeThreshold = 1000 // 連続タップと判定する時間間隔（ミリ秒）

    // 設定の読み込み
    LaunchedEffect(Unit) {
        try {
            fontSize = settingsStore.fontSize.first()
            fontFamily = settingsStore.fontFamily.first()
            fontColor = settingsStore.fontColor.first()
            backgroundColor = settingsStore.episodeBackgroundColor.first()
            useDefaultBackground = settingsStore.useDefaultBackground.first()
            textOrientation = settingsStore.textOrientation.first()
        } catch (e: Exception) {
            Log.e("EpisodeViewScreen", "設定の読み込みエラー: ${e.message}")
        }
    }

    // エピソードデータの読み込み
    LaunchedEffect(ncode, episodeNo) {
        scrollState.scrollTo(0)
        scope.launch {
            try {
                // エピソード情報の取得
                episode = repository.getEpisode(ncode, episodeNo)
                novel = repository.getNovelByNcode(ncode)

                // 最後に読んだ情報を更新
                val episodeNumber = episodeNo.toIntOrNull() ?: 1
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = novel?.title ?: "小説を読む",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = episode?.e_title ?: "エピソード $episodeNo",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { onTitleTap() }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    // フォントサイズ調整ボタン
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
                                onClick = onPrevious
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
                            .clickable(onClick = onBack)
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
                                onClick = onNext
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
                    // エピソードタイトル
                    Text(
                        text = episode!!.e_title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = try {
                                Color(AndroidColor.parseColor(fontColor))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.onBackground
                            }
                        ),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .clickable { onTitleTap() }
                    )

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
                            Divider()
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
                            textOrientation = textOrientation,
                            modifier = Modifier.padding(bottom = 32.dp)
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

@Composable
fun EnhancedHtmlRubyWebView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 18,
    rubyFontSize: Int = 10,
    backgroundColor: String? = null,
    fontColor: String = "#000000",
    fontFamily: String = "Gothic",
    textOrientation: String = "Horizontal"
) {
    val context = LocalContext.current

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

    // フォントファミリーの設定
    val actualFontFamily = when (fontFamily) {
        "Mincho" -> "serif"
        "Gothic" -> "sans-serif"
        else -> "sans-serif"
    }

    // 背景色の設定（デフォルトの場合はテーマの色）
    val bgColor = backgroundColor ?: "#FFFFFF"

    // 文章の向き
    val writingMode = if (textOrientation == "Vertical") {
        "vertical-rl"
    } else {
        "horizontal-tb"
    }

    // ルビ用のCSSスタイルを定義
    val cssStyle = """
    <style>
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
            display: inline-flex;
            flex-direction: column-reverse;
            vertical-align: bottom;
        }
        rt {
            font-size: ${rubyFontSize}px;
            text-align: center;
            line-height: 1;
            color: $fontColor;
            display: inline;
        }
    </style>
    """.trimIndent()

    // JavaScriptで追加の調整
    val jsScript = """
        <script>
            // ドキュメント読み込み後にルビタグを調整
            document.addEventListener('DOMContentLoaded', function() {
                // すべてのルビ要素を取得
                var rubyElements = document.getElementsByTagName('ruby');
                for (var i = 0; i < rubyElements.length; i++) {
                    var ruby = rubyElements[i];
                    
                    // rtタグが見つからない場合は修正
                    if (ruby.getElementsByTagName('rt').length === 0) {
                        var text = ruby.textContent;
                        var match = text.match(/(.+?)\((.+?)\)/);
                        if (match) {
                            ruby.innerHTML = match[1] + '<rt>' + match[2] + '</rt>';
                        }
                    }
                }
            });
        </script>
    """.trimIndent()

    // HTMLを修正
    val fixedHtml = fixRubyTags(htmlContent)

    // HTMLコンテンツを整形
    val formattedHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            $cssStyle
            $jsScript
        </head>
        <body>
            $fixedHtml
        </body>
        </html>
    """.trimIndent()

    // WebViewでHTMLをレンダリング
    var webView: WebView? = null

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webView = this
                settings.apply {
                    javaScriptEnabled = true
                    defaultFontSize = fontSize
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    defaultTextEncodingName = "UTF-8"
                }

                // WebViewClientのカスタマイズ
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // ページ読み込み完了後の処理
                    }
                }

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