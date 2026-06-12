/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * WebView screen for browsing novel sites.
 */
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
import android.os.Message

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
    var detectedNovelId by remember { mutableStateOf("") }
    var detectedSiteName by remember { mutableStateOf("") }
    var detectedNovelUrl by remember { mutableStateOf("") }

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

            // ユーザーエージェント設定（モバイル版強制）
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            // WebViewデフォルトエンコーディング
            defaultTextEncodingName = "UTF-8"

            // 追加の設定
            allowContentAccess = true
            allowFileAccess = false  // セキュリティ向上: ファイルシステムアクセスを無効化
            loadWithOverviewMode = true
            useWideViewPort = true
            setGeolocationEnabled(true)
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW  // セキュリティ向上: 混在コンテンツを禁止
        }

        // WebViewClientの設定
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, loadedUrl: String) {
                super.onPageFinished(view, loadedUrl)
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
                currentLoadingUrl = loadedUrl
                currentUrl = loadedUrl

                // target="_blank"リンクを修正するJavaScriptを実行
                view.evaluateJavascript("""
                    (function() {
                        // すべてのtarget="_blank"リンクからtarget属性を除去
                        var links = document.querySelectorAll('a[target="_blank"]');
                        console.log('Found ' + links.length + ' target="_blank" links');
                        for (var i = 0; i < links.length; i++) {
                            links[i].removeAttribute('target');
                            console.log('Removed target from link: ' + links[i].href);
                        }

                        // リンクにクリックイベントリスナーを追加（念のため）
                        var readButtons = document.querySelectorAll('.read_button');
                        for (var i = 0; i < readButtons.length; i++) {
                            readButtons[i].addEventListener('click', function(e) {
                                var href = this.getAttribute('href');
                                if (href) {
                                    console.log('Read button clicked, navigating to: ' + href);
                                    window.location.href = href;
                                }
                            });
                        }

                        return 'target="_blank" links processed: ' + links.length;
                    })();
                """.trimIndent()) { result ->
                    Log.d("WebViewScreen", "Target blank links processing result: $result")
                }

                // 年齢確認ページかチェック
                if (loadedUrl.contains("ageauth")) {
                    Log.d("WebViewScreen", "年齢確認ページを検出しました")
                    view.evaluateJavascript("""
                    (function() {
                        var links = document.getElementsByTagName('a');
                        for (var i = 0; i < links.length; i++) {
                            if (links[i].textContent.trim() === 'Enter') {
                                links[i].click();
                                return true;
                            }
                        }
                        // 'Enter'が見つからない場合、'はい'や'同意する'ボタンも探す
                        for (var i = 0; i < links.length; i++) {
                            var text = links[i].textContent.trim();
                            if (text === 'はい' || text === '同意する' ||
                                text === 'Yes' || text === 'I agree') {
                                links[i].click();
                                return true;
                            }
                        }
                        // リンクが見つからなかった場合、フォームのボタンを探してクリック
                        var buttons = document.getElementsByTagName('button');
                        for (var i = 0; i < buttons.length; i++) {
                            buttons[i].click();
                            return true;
                        }
                        // フォームの送信ボタンも探す
                        var inputs = document.querySelectorAll('input[type="submit"]');
                        for (var i = 0; i < inputs.length; i++) {
                            inputs[i].click();
                            return true;
                        }
                        return false;
                    })();
                """.trimIndent()) { result ->
                        Log.d("WebViewScreen", "年齢確認自動クリック結果: $result")
                    }
                }

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
                Log.d("WebViewScreen", "shouldOverrideUrlLoading called with URL: $url")
                view.loadUrl(url)
                return true
            }

            // ページロード前のリクエスト処理を追加
            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)

                // R18サイトへのアクセス時にCookieを設定
                if (url.contains("novel18.syosetu.com") ||
                    url.contains("noc.syosetu.com") ||
                    url.contains("mid.syosetu.com") ||
                    url.contains("mnlt.syosetu.com") ||
                    url.contains("ageauth")) {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setCookie(url, "over18=yes")
                    cookieManager.flush()
                }
            }
        }

        // WebChromeClientの設定（新しいウィンドウ作成を処理）
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                Log.d("WebViewScreen", "onCreateWindow called - redirecting to same WebView")

                // 新しいウィンドウを作成する代わりに、一時的なWebViewを作成して
                // そのURLを元のWebViewで読み込む
                val tempWebView = WebView(view!!.context)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        Log.d("WebViewScreen", "New window would load URL: $url, redirecting to main WebView")
                        // 元のWebViewでURLを読み込む
                        webView.loadUrl(url)
                        return true
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null) {
                            Log.d("WebViewScreen", "Temp WebView started loading: $url, redirecting to main WebView")
                            webView.loadUrl(url)
                        }
                    }
                }

                // メッセージを一時的なWebViewに送信
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()

                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    Log.d("WebViewScreen", "Page load completed")
                }
            }
        }
    }

    // URLから小説IDを抽出する関数（マルチサイト対応）
    fun extractNovelInfo(url: String): Triple<String?, Boolean, String?> {
        // 新しいアダプターファクトリーでサイト判定
        val adapterResult = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapterByUrl(url)
        if (adapterResult != null) {
            val (adapter, novelId) = adapterResult
            val siteName = adapter.getSiteName()

            // 小説家になろうの場合、R18判定も行う
            if (adapter.getSiteType() == com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU) {
                val (_, isR18) = NovelApiUtils.extractNcodeFromUrl(url)
                return Triple(novelId, isR18, siteName)
            } else {
                // カクヨムの場合
                return Triple(novelId, false, siteName)
            }
        }
        return Triple(null, false, null)
    }

    // 小説登録関数（マルチサイト対応）
    fun registerNovel(novelUrl: String, callback: (Boolean, String) -> Unit) {
        isLoading = true
        loadingMessage = "キューに追加中..."

        scope.launch {
            try {
                // キューに追加
                val queueId = com.shunlight_library.novel_reader.manager.RegistrationQueueManager.addToQueue(novelUrl)

                if (queueId != null) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        showAddDialog = false
                        callback(true, "ダウンロードキューに追加しました")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        callback(false, "キューに追加できませんでした。")
                    }
                }
            } catch (e: Exception) {
                Log.e("WebViewScreen", "キュー追加エラー", e)
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
            title = { Text("小説を登録しますか？") },
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
                    Text("URL: $detectedNovelUrl")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        registerNovel(detectedNovelUrl) { success, message ->
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
                            val (novelId, _, siteName) = extractNovelInfo(currentUrl)
                            if (novelId != null && siteName != null) {
                                detectedNovelId = novelId
                                detectedSiteName = siteName
                                detectedNovelUrl = currentUrl
                                showAddDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    "このページから小説を登録できません（対応サイト: 小説家になろう、カクヨム）",
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

                        // R18サイトのCookieを事前に設定
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setCookie("https://novel18.syosetu.com", "over18=yes")
                        cookieManager.setCookie("https://noc.syosetu.com", "over18=yes")
                        cookieManager.setCookie("https://mid.syosetu.com", "over18=yes")
                        cookieManager.setCookie("https://mnlt.syosetu.com", "over18=yes")
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        cookieManager.flush()

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
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                }
            )
        }
    }
}