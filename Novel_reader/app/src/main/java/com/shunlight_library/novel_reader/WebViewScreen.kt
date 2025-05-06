package com.shunlight_library.novel_reader

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream
import org.yaml.snakeyaml.Yaml
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
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

    // 小説情報を取得する関数
    fun fetchNovelInfo(ncode: String, isR18: Boolean, onComplete: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                loadingMessage = "APIからデータを取得中..."

                // APIエンドポイントを選択
                val apiUrl = if (isR18) {
                    "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5&json"
                } else {
                    "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5&json"
                }

                withContext(Dispatchers.IO) {
                    val connection = URL(apiUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        // gzipファイルを解凍
                        val inputStream = GZIPInputStream(connection.inputStream)
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val content = StringBuilder()
                        var line: String?

                        while (reader.readLine().also { line = it } != null) {
                            content.append(line).append("\n")
                        }

                        // YAMLデータを解析
                        val yaml = Yaml()
                        val yamlData = yaml.load<List<Map<String, Any>>>(content.toString())

                        if (yamlData.size >= 2) {
                            // 2番目の要素が小説データ（1番目はリザルトコード）
                            val novelData = yamlData[1]

                            // NovelDescEntityの作成
                            val title = novelData["title"] as String
                            val author = novelData["writer"] as String
                            val synopsis = novelData["story"] as? String ?: ""
                            val generalAllNo = novelData["general_all_no"] as Int

                            // 現在の日時を取得
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val currentDate = dateFormat.format(Date())

                            // NovelDescEntityの作成
                            val novelEntity = NovelDescEntity(
                                ncode = ncode,
                                title = title,
                                author = author,
                                Synopsis = synopsis,
                                main_tag = "", // APIからは取得できないのでデフォルト値
                                sub_tag = "", // APIからは取得できないのでデフォルト値
                                rating = if (isR18) 1 else 2,
                                last_update_date = currentDate,
                                total_ep = 0, // 初期値は0、後で更新処理で正確な値が設定される
                                general_all_no = generalAllNo,
                                updated_at = currentDate
                            )

                            // データベースに保存
                            repository.insertNovel(novelEntity)

                            // 更新キューに追加
                            try {
                                // UpdateQueueEntityを作成して保存
                                val updateQueue = UpdateQueueEntity(
                                    ncode = ncode,
                                    total_ep = 0, // 初期値は0
                                    general_all_no = generalAllNo,
                                    update_time = currentDate
                                )
                                repository.insertUpdateQueue(updateQueue)
                            } catch (e: Exception) {
                                Log.e("WebViewScreen", "更新キュー追加エラー", e)
                            }

                            withContext(Dispatchers.Main) {
                                onComplete(true, "小説「$title」を登録しました")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onComplete(false, "小説情報が取得できませんでした")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onComplete(false, "API接続エラー: ${connection.responseCode}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WebViewScreen", "小説情報取得エラー", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, "エラー: ${e.message}")
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
                        isLoading = true
                        loadingMessage = "小説情報を取得中..."

                        fetchNovelInfo(detectedNcode, isR18) { success, message ->
                            isLoading = false
                            if (success) {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                showAddDialog = false
                            } else {
                                loadingMessage = message
                            }
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
                            // +ボタンの機能（小説登録）
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
            // WebViewの表示
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, loadedUrl: String) {
                                super.onPageFinished(view, loadedUrl)
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                                currentLoadingUrl = loadedUrl // 現在のURLを更新
                                currentUrl = loadedUrl // 現在のURLを保存
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        // Cookieを有効にする設定を追加
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)


                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        loadUrl(url)
                        webView = this
                    }
                },
                update = { view ->
                    // 初期URLが変更された場合のみloadUrlを呼び出す
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