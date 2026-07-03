# カクヨムダウンロードプロトコル

**必須**: カクヨムには公式APIが存在しないため、HTMLスクレイピングで情報を取得する

## レート制限とアクセス制御

```kotlin
// KakuyomuAdapter.kt での実装パターン
companion object {
    private const val RATE_LIMIT_DELAY_MS = 500L  // 0.5秒（スクレイピング時の推奨間隔）
    private var lastAccessTime = 0L
}

private suspend fun applyRateLimit() {
    val elapsed = System.currentTimeMillis() - lastAccessTime
    if (elapsed < RATE_LIMIT_DELAY_MS) {
        delay(RATE_LIMIT_DELAY_MS - elapsed)
    }
    lastAccessTime = System.currentTimeMillis()
}
```

**重要なルール**:
- レート制限は**0.5秒間隔**（500ms）
- 全てのHTTPリクエスト前に`applyRateLimit()`を呼び出す（companion `Mutex.withLock` で直列化済み。v2.0.20監査 M1）
- サーバー負荷軽減のため、この間隔を必ず守る

## エピソード本文取得の優先順位

```kotlin
// エピソード本文取得パターン（KakuyomuAdapter.kt）
// パターン1: 両方のクラスを持つdiv（最優先）
val bodyElement1 = doc.select("div.widget-episodeBody.js-episode-body")
if (bodyElement1.isNotEmpty()) {
    episodeBody = bodyElement1.html()
}

// パターン2: widget-episodeBody のみ（フォールバック）
if (episodeBody.isEmpty()) {
    val bodyElement2 = doc.select("div.widget-episodeBody")
    if (bodyElement2.isNotEmpty()) {
        episodeBody = bodyElement2.html()
    }
}

// パターン3: js-episode-body のみ（さらにフォールバック）
if (episodeBody.isEmpty()) {
    val bodyElement3 = doc.select("div.js-episode-body")
    // ...
}
```

**重要なルール**:
- `div.widget-episodeBody.js-episode-body`（両方のクラス）を**最優先**で使用
- 複数のフォールバックパターンを用意し、確実な取得を保証
- パターンの優先順位を守る

## エピソードタイトル取得の優先順位

```kotlin
// 個別エピソードページからのタイトル取得（KakuyomuAdapter.kt）
// パターン1: header#contentMain-header（最優先、Pascalコード参考）
val titleElement1 = doc.select("header#contentMain-header")
if (titleElement1.isNotEmpty()) {
    episodeTitle = titleElement1.text()
}

// パターン2: widget-episodeTitle
if (episodeTitle.isEmpty()) {
    val titleElement2 = doc.select("p.widget-episodeTitle")
    if (titleElement2.isNotEmpty()) {
        episodeTitle = titleElement2.text()
    }
}

// パターン3: h1タグ（第2フォールバック、Pascalコード参考）
if (episodeTitle.isEmpty()) {
    val titleElement3 = doc.select("h1").firstOrNull()
    if (titleElement3 != null) {
        episodeTitle = titleElement3.text()
    }
}

// パターン4: 最後のフォールバック（Pascalコード参考）
if (episodeTitle.isEmpty()) {
    episodeTitle = "第${episodeNo}話"
}

// タイトルのクリーンアップ処理
episodeTitle = cleanupText(episodeTitle)
```

**重要なルール**:
- `header#contentMain-header`を**最優先**で使用
- `p.widget-episodeTitle`は第2優先
- `h1`タグは第3フォールバック
- 最後のフォールバックとして「第X話」形式を生成
- 取得したタイトルは必ず`cleanupText()`でクリーンアップ

## 章タイトル取得の優先順位

```kotlin
// 個別エピソードページからの章タイトル取得（Pascalコード参考）
// パターン1: chapterTitle level1
val chapterElement1 = doc.select("p.chapterTitle.level1 span")
if (chapterElement1.isNotEmpty()) {
    chapterTitle = chapterElement1.text()
}

// パターン2: chapterTitle level2
if (chapterTitle.isEmpty()) {
    val chapterElement2 = doc.select("p.chapterTitle.level2 span")
    if (chapterElement2.isNotEmpty()) {
        chapterTitle = chapterElement2.text()
    }
}

// チャプタータイトルのクリーンアップ処理
chapterTitle = cleanupText(chapterTitle)
```

**重要なルール**:
- `p.chapterTitle.level1 span`を**最優先**で使用
- `p.chapterTitle.level2 span`はフォールバック
- 章タイトルが存在しない場合もある（空文字列）
- 取得した章タイトルは必ず`cleanupText()`でクリーンアップ

## エピソード本文のエラーチェック

```kotlin
// HTMLページ読み込みエラーのチェック（Pascalコード参考）
private fun checkForLoadingError(html: String): Boolean {
    val errorIndicator = "<div class=\"dots-indicator\" id=\"LoadingEpisode\">"
    val checkLength = minOf(html.length, 200)  // 最初の200文字をチェック
    val prefix = html.take(checkLength)
    return prefix.contains(errorIndicator)
}

// エラーチェックの使用例
if (checkForLoadingError(episodeBody)) {
    android.util.Log.w("KakuyomuAdapter", "HTMLページ読み込みエラーを検出: $episodeId")
    return@withContext "★HTMLページ読み込みエラー\n本文を正しく取得できませんでした。\n後ほど再度お試しください。"
}
```

**重要なルール**:
- 本文の先頭200文字以内に`<div class="dots-indicator" id="LoadingEpisode">`が含まれていればエラー
- エラーを検出した場合は、分かりやすいエラーメッセージを返す
- エラーログを出力して問題の追跡を容易にする
- v2.0.15以降、この文字列を本文として保存しないよう normalizeEpisodeBody で全保存経路にガードを追加済み（空文字に正規化）

## HTTP取得の再試行ロジック

```kotlin
// HTTP取得の再試行実装パターン（KakuyomuAdapter.kt）
private suspend fun performHttpRequest(urlString: String, maxRetries: Int = 3): String {
    var lastException: Exception? = null

    for (attempt in 1..maxRetries) {
        try {
            // HTTP接続処理
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    // 完全なHTMLをバッファリングして取得（Pascalコード参考）
                    val contentLength = connection.contentLength
                    val html = StringBuilder()

                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(8192)  // 8KBバッファ
                        var charsRead: Int

                        // 全データを読み込むまでループ（Pascalコードと同様）
                        while (reader.read(buffer).also { charsRead = it } != -1) {
                            html.append(buffer, 0, charsRead)
                        }
                    }

                    val htmlString = html.toString()
                    android.util.Log.d("KakuyomuAdapter", "HTTP取得成功: $urlString (Content-Length: $contentLength, Actual: ${htmlString.length})")

                    return htmlString
                }
                HttpURLConnection.HTTP_NOT_FOUND -> throw Exception("HTTP 404")
                HttpURLConnection.HTTP_FORBIDDEN -> throw Exception("HTTP 403")
                else -> throw Exception("HTTP error: $responseCode")
            }
        } catch (e: SocketTimeoutException) {
            if (attempt < maxRetries) {
                delay(1000L * attempt)  // 指数バックオフ: 1秒、2秒、3秒
            }
        } catch (e: UnknownHostException) {
            if (attempt < maxRetries) {
                delay(1000L * attempt)
            }
        } catch (e: ConnectException) {
            if (attempt < maxRetries) {
                delay(1000L * attempt)
            }
        }
    }

    throw lastException ?: Exception("HTTP取得失敗")
}
```

**重要なルール**:
- **完全なHTMLをバッファリングして取得**（Pascalコードと同様、全データを読み込むまでループ）
- 8KBバッファを使用して効率的に読み込み
- Content-Lengthと実際のデータサイズをログで確認
- 最大**3回**の再試行を実装
- 再試行時は**指数バックオフ**（1秒、2秒、3秒）を使用
- `SocketTimeoutException`、`UnknownHostException`、`ConnectException`は再試行対象
- HTTPエラー（404、403等）は即座にスロー（再試行しない）
- 詳細なログ出力で問題の特定を容易にする

## テキストクリーンアップ処理

**必須**: HTMLから取得したテキストは必ずクリーンアップ処理を行う

### HTMLエスケープ文字のデコード

```kotlin
// HTMLエスケープ文字のデコード（KakuyomuAdapter.kt）
private fun decodeHtmlEntities(text: String): String {
    var decoded = text
    decoded = decoded.replace("&lt;", "<")
    decoded = decoded.replace("&gt;", ">")
    decoded = decoded.replace("&quot;", "\"")
    decoded = decoded.replace("&nbsp;", " ")
    decoded = decoded.replace("&yen;", "\\")
    decoded = decoded.replace("&brvbar;", "|")
    decoded = decoded.replace("&copy;", "©")
    // &amp; は最後に処理（他のエスケープ文字に影響しないように）
    decoded = decoded.replace("&amp;", "&")
    return decoded
}
```

### Unicodeエスケープ文字のデコード

```kotlin
// 数値エスケープ文字のデコード（&#xxxx; 形式）
private fun decodeNumericEntities(text: String): String {
    var decoded = text

    // 16進数形式: &#x????;
    val hexPattern = Regex("&#x([0-9A-Fa-f]+);")
    decoded = hexPattern.replace(decoded) { matchResult ->
        try {
            val codePoint = matchResult.groupValues[1].toInt(16)
            String(Character.toChars(codePoint))
        } catch (e: Exception) {
            "？"  // デコード失敗時は？に置換
        }
    }

    // 10進数形式: &#????;
    val decPattern = Regex("&#([0-9]+);")
    decoded = decPattern.replace(decoded) { matchResult ->
        try {
            val codePoint = matchResult.groupValues[1].toInt(10)
            String(Character.toChars(codePoint))
        } catch (e: Exception) {
            "？"  // デコード失敗時は？に置換
        }
    }

    return decoded
}

// Unicodeエスケープ文字のデコード（\uxxxx 形式）
private fun decodeUnicodeEscapes(text: String): String {
    val pattern = Regex("\\\\u([0-9A-Fa-f]{4})")
    return pattern.replace(text) { matchResult ->
        try {
            val codePoint = matchResult.groupValues[1].toInt(16)
            String(Character.toChars(codePoint))
        } catch (e: Exception) {
            "？"  // デコード失敗時は？に置換
        }
    }
}
```

### 本文のクリーンアップ処理

```kotlin
// エピソード本文のクリーンアップ（KakuyomuAdapter.kt）
private fun cleanupEpisodeBody(html: String): String {
    var cleaned = html

    // 1. 改行タグを実際の改行に変換（<br />、<br>、<br/>）
    cleaned = cleaned.replace(Regex("<br\\s*/?>"), "\n")

    // 2. HTMLエスケープ文字のデコード
    cleaned = decodeHtmlEntities(cleaned)

    // 3. Unicodeエスケープ文字のデコード（&#xxxx; 形式）
    cleaned = decodeNumericEntities(cleaned)

    // 4. Unicodeエスケープ文字のデコード（\uxxxx 形式）
    cleaned = decodeUnicodeEscapes(cleaned)

    // 5. 余計なタグの除去（空の <p> タグなど）
    cleaned = cleaned.replace(Regex("<p[^>]*>\\s*</p>"), "")

    // 6. 各行の先頭にある半角空白を除去
    cleaned = cleaned.lines().joinToString("\n") { line ->
        line.trimStart(' ')
    }

    return cleaned
}
```

### あらすじのクリーンアップ処理

```kotlin
// あらすじのクリーンアップ（KakuyomuAdapter.kt）
private fun cleanupSynopsis(text: String): String {
    var cleaned = text

    // 1. あらすじ部分の"が\"とエスケープされているため"に戻す
    cleaned = cleaned.replace("\\\"", "\"")

    // 2. 改行が\n表記となっている場合は実際の改行に変換
    cleaned = cleaned.replace("\\n", "\n")

    // 3. HTMLエスケープ文字のデコード
    cleaned = decodeHtmlEntities(cleaned)

    // 4. Unicodeエスケープ文字のデコード（&#xxxx; 形式）
    cleaned = decodeNumericEntities(cleaned)

    // 5. Unicodeエスケープ文字のデコード（\uxxxx 形式）
    cleaned = decodeUnicodeEscapes(cleaned)

    // 6. 前書きの最後にある"…続きを読む"を削除する
    cleaned = cleaned.replace("…続きを読む", "")

    // 7. 前後の空白を除去
    cleaned = cleaned.trim()

    return cleaned
}
```

**重要なルール**:
- **タイトル、作者名、エピソードタイトル**: `cleanupText()` を使用
- **あらすじ**: `cleanupSynopsis()` を使用（エスケープされた引用符や改行の処理を含む）
- **エピソード本文**: `cleanupEpisodeBody()` を使用（改行タグの変換や余計なタグの除去を含む）
- HTMLから取得したすべてのテキストに適用すること
- デコード処理の順序を守る：HTMLエスケープ → 数値エスケープ → Unicodeエスケープ

## エピソード一覧取得の複数フォールバック

**必須**: エピソード一覧の取得には複数の方法を試行し、確実に全エピソードを取得する

```kotlin
// エピソード一覧取得の優先順位（KakuyomuAdapter.kt）
private suspend fun parseEpisodeList(workId: String, pseudoNcode: String): List<EpisodeEntity> {
    // 方法1: エピソードページの目次から取得（最優先、最も確実）
    val episodesFromToc = extractEpisodesFromToc(workId, pseudoNcode)
    if (episodesFromToc.isNotEmpty()) {
        return episodesFromToc
    }

    // 方法2: 作品ページのHTMLから取得（フォールバック）
    // ...
}

// エピソードページの目次から全エピソードを抽出
private suspend fun extractEpisodesFromToc(workId: String, pseudoNcode: String): List<EpisodeEntity> {
    // 1. 作品ページから最初のエピソードリンクを取得
    val workUrl = "https://kakuyomu.jp/works/$workId"
    val firstEpisodeLink = doc.select("a.widget-toc-episode-episodeTitle, a.WorkTocSection_link__ocg9K").firstOrNull()?.attr("href")

    // 2. エピソードページを取得（完全な目次が含まれる）
    val episodeUrl = "https://kakuyomu.jp$firstEpisodeLink"
    val episodeHtml = performHttpRequest(episodeUrl)
    val episodeDoc = Jsoup.parse(episodeHtml)

    // 3. 目次から全エピソードを抽出
    val tocItems = episodeDoc.select("ol.widget-toc-items li.widget-toc-episode")
    tocItems.forEach { item ->
        val link = item.select("a.widget-toc-episode-episodeTitle").attr("href")
        val episodeId = link.substringAfterLast("/")
        val title = item.select("span.widget-toc-episode-titleLabel").text()
        val publishedAt = item.select("time.widget-toc-episode-datePublished").attr("datetime")
        // エピソードを追加
    }

    return episodes
}
```

**重要なルール**:
- **方法1（エピソードページの目次）**: 任意のエピソードページには完全な目次が表示されている（最優先、最も確実）
  - 作品ページから最初のエピソードリンクを取得
  - そのエピソードページを取得し、目次セクション（`ol.widget-toc-items`）から全エピソードを抽出
  - セレクタ：`li.widget-toc-episode` → `a.widget-toc-episode-episodeTitle`（リンク）、`span.widget-toc-episode-titleLabel`（タイトル）、`time.widget-toc-episode-datePublished`（公開日時）
- **方法2（HTMLフォールバック）**: 目次が取得できない場合のフォールバック
- 992話のような大量のエピソードも確実に取得できる
- レート制限を守るため、各HTTPリクエスト前に`applyRateLimit()`を呼び出す

## URL構造

```kotlin
// カクヨムのURL構造
val workUrl = "https://kakuyomu.jp/works/{workId}"
val episodeUrl = "https://kakuyomu.jp/works/{workId}/episodes/{episodeId}"
```

**重要なルール**:
- 作品IDとエピソードIDは独立した19桁の数値
- 連番ではないため、目次から全エピソードIDを取得する必要がある
- Pseudo-Ncode形式（`KK-{workId}`）でデータベースに格納

## エラーハンドリング

**必須**: 全てのカクヨム関連処理でエラーハンドリングを実装

- ネットワークエラー時は再試行ロジックを適用
- HTMLパースエラー時は複数のフォールバックパターンを試行
- 取得失敗時は詳細なログを出力し、空データまたはnullを返す
- ユーザーには適切なエラーメッセージを表示

このルールは全てのカクヨム関連処理（小説情報取得、エピソード一覧取得、エピソード本文取得、更新確認）で統一して適用すること。
