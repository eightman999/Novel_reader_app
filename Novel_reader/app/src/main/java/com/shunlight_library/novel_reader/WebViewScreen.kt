// WebViewScreen.kt - Ensure proper WebView configuration with cookies, cache, and JavaScript

package com.shunlight_library.novel_reader

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

import androidx.activity.compose.BackHandler
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import com.shunlight_library.novel_reader.api.NovelApiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onBack: () -> Unit
) {
    val repository = NovelReaderApplication.getRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentLoadingUrl by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(url) }

    // ダイアログの表示状態
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var detectedNcode by remember { mutableStateOf("") }
    var isR18 by remember { mutableStateOf(false) }

    // WebView内の履歴を戻るか、メイン画面に戻るかを判断
    BackHandler {
        if (canGoBack) {
            webView?.goBack()
        } else {
            onBack()
        }
    }

    // CookieManagerの初期化と設定
    LaunchedEffect(Unit) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        cookieManager.flush()
    }

    // WebViewの設定を強化
    fun configureWebView(webView: WebView) {
        webView.settings.apply {
            // JavaScriptを有効化
            javaScriptEnabled = true

            // DOMストレージを有効化
            domStorageEnabled = true

            // キャッシュモードの設定
            cacheMode = WebSettings.LOAD_DEFAULT

            // データベースストレージAPIを有効化
            databaseEnabled = true

            // ズーム機能
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // ユーザーエージェント設定
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36"

            // WebViewデフォルトエンコーディング
            defaultTextEncodingName = "UTF-8"

            // 追加の設定
            allowContentAccess = true
            allowFileAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setGeolocationEnabled(true)
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // WebViewClientの設定
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, loadedUrl: String) {
                super.onPageFinished(view, loadedUrl)
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
                currentLoadingUrl = loadedUrl
                currentUrl = loadedUrl

                // ページ読み込み完了時にCookieを確認
                view.evaluateJavascript("""
                    (function() {
                        return document.cookie;
                    })();
                """.trimIndent()) { result ->
                    Log.d("WebViewScreen", "Current cookies: $result")
                }

                // セッション維持のためのJavaScript実行
                view.evaluateJavascript("""
                    (function() {
                        if (window.localStorage) {
                            return window.localStorage.getItem('session');
                        }
                        return null;
                    })();
                """.trimIndent()) { result ->
                    Log.d("WebViewScreen", "Session data: $result")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }
        }

        // WebChromeClientの設定
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    // ページ読み込み完了時の処理
                    Log.d("WebViewScreen", "Page load completed")
                }
            }
        }
    }

    // ncode抽出用の正規表現パターン
    val ncodePattern = Pattern.compile("https://(ncode|novel18)\\.syosetu\\.com/([^/]+)/?.*")

    // URLからncodeを抽出する関数
    fun extractNcode(url: String): Pair<String?, Boolean> {
        val matcher = ncodePattern.matcher(url)
        if (matcher.matches()) {
            val domain = matcher.group(1)
            val ncode = matcher.group(2)
            val isR18 = domain == "novel18"
            return Pair(ncode, isR18)
        }
        return Pair(null, false)
    }

    // 小説登録関数
    fun registerNovel(ncode: String, isR18: Boolean, callback: (Boolean, String) -> Unit) {
        isLoading = true
        loadingMessage = "小説情報を取得中..."

        scope.launch {
            try {
                // まず小説が既に登録されているか確認
                if (NovelApiUtils.isNovelAlreadyRegistered(repository, ncode)) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        callback(false, "この小説は既に登録されています")
                        showAddDialog = false
                    }
                    return@launch
                }

                // 小説詳細を取得
                val novelEntity = NovelApiUtils.fetchNovelDetails(ncode, isR18)

                if (novelEntity != null) {
                    // URLEntityも作成
                    val urlEntity = com.shunlight_library.novel_reader.data.entity.URLEntity(
                        ncode = ncode,
                        api_url = if (isR18) {
                            "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5&json"
                        } else {
                            "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5&json"
                        },
                        url = if (isR18) {
                            "https://novel18.syosetu.com/$ncode/"
                        } else {
                            "https://ncode.syosetu.com/$ncode/"
                        },
                        is_r18 = isR18
                    )

                    // データベースに保存
                    repository.insertNovel(novelEntity)
                    repository.insertURL(urlEntity)

                    // 更新キューエントリを作成
                    val updateQueue = com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity(
                        ncode = ncode,
                        total_ep = 0, // 初期値は0
                        general_all_no = novelEntity.general_all_no,
                        update_time = novelEntity.updated_at
                    )
                    repository.insertUpdateQueue(updateQueue)

                    // 登録と同時にダウンロード処理を開始
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        showAddDialog = false

                        // 成功メッセージを表示
                        callback(true, "小説「${novelEntity.title}」を登録しました")

                        // ダウンロードサービスを開始
                        val intent = Intent(context, com.shunlight_library.novel_reader.service.UpdateService::class.java).apply {
                            action = com.shunlight_library.novel_reader.service.UpdateService.ACTION_START_UPDATE
                            putExtra(com.shunlight_library.novel_reader.service.UpdateService.EXTRA_NCODE, ncode)
                            putExtra(com.shunlight_library.novel_reader.service.UpdateService.EXTRA_UPDATE_TYPE, com.shunlight_library.novel_reader.service.UpdateService.UPDATE_TYPE_DOWNLOAD)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            // Android 12以降ではフォアグラウンドサービスタイプを指定
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                intent.putExtra(
                                    "android.content.extra.FOREGROUND_SERVICE_TYPE",
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                                )
                            }
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        callback(false, "小説情報が取得できませんでした")
                    }
                }
            } catch (e: Exception) {
                Log.e("WebViewScreen", "小説登録エラー", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                    callback(false, "エラー: ${e.message}")
                }
            }
        }
    }

    // 確認ダイアログ
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoading) {
                    showAddDialog = false
                }
            },
            title = { Text("小説の登録") },
            text = {
                if (isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(loadingMessage)
                    }
                } else {
                    Text("「$detectedNcode」を小説一覧に登録しますか？")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        registerNovel(detectedNcode, isR18) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text("登録する")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    enabled = !isLoading
                ) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小説サイト閲覧") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val (ncode, r18) = extractNcode(currentUrl)
                            if (ncode != null) {
                                detectedNcode = ncode
                                isR18 = r18
                                showAddDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    "このページから小説を登録できません",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "小説登録")
                    }
                }
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        configureWebView(this)
                        loadUrl(url)
                        webView = this
                    }
                },
                update = { view ->
                    if (currentLoadingUrl.isEmpty() || currentLoadingUrl == url) {
                        view.loadUrl(url)
                        currentLoadingUrl = url
                        currentUrl = url
                    }
                }
            )
        }
    }
}