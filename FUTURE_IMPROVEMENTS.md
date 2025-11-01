# 🔮 将来的な改善課題

**作成日**: 2025-11-01
**ステータス**: 未対応（優先度順）

本ドキュメントは、コードレビューで特定された問題のうち、即座の修正を行わなかった項目をまとめています。
これらの項目は、将来的なリファクタリングや機能改善の際に対応することを推奨します。

---

## 📋 目次

1. [中優先度の改善課題](#中優先度の改善課題)
2. [低優先度の改善課題](#低優先度の改善課題)
3. [その他の軽微な問題](#その他の軽微な問題)
4. [実施済みの修正](#実施済みの修正)

---

## 🟡 中優先度の改善課題

### 1. LaunchedEffect の重複削除

#### 📍 対象ファイル
`EpisodeListScreen.kt` (行95, 109)

#### 問題の詳細
同じキー `ncode` で2つの `LaunchedEffect` が定義されており、重複実行による非効率が発生しています。

```kotlin
// ❌ 現状：重複した LaunchedEffect
LaunchedEffect(ncode) {
    novel = repository.getNovelByNcode(ncode)
    lastRead = repository.getLastReadByNcode(ncode)
}

LaunchedEffect(ncode) {  // 重複！
    repository.getEpisodesByNcode(ncode).collect { episodeList ->
        episodes = episodeList.sortedWith(...)
    }
}
```

#### 推奨される修正方法
```kotlin
// ✅ 推奨：1つの LaunchedEffect に統合
LaunchedEffect(ncode) {
    // 並列実行
    coroutineScope {
        launch {
            novel = repository.getNovelByNcode(ncode)
        }
        launch {
            lastRead = repository.getLastReadByNcode(ncode)
        }
    }

    // Flow の収集
    repository.getEpisodesByNcode(ncode).collect { episodeList ->
        episodes = episodeList.sortedWith(...)
    }
}
```

#### 影響とリスク
- **リスク**: UI動作の変更により予期しない副作用の可能性
- **影響範囲**: エピソード一覧画面の初期化処理
- **推定作業時間**: 15-20分
- **テスト要件**: エピソード一覧画面の表示確認、画面遷移の動作確認

---

### 2. データベーストランザクション管理

#### 📍 対象ファイル
`NovelRepository.kt` (行127-141)

#### 問題の詳細
複数の削除操作が個別に実行されており、途中でエラーが発生すると孤立したレコードが残る可能性があります。

```kotlin
// ❌ 現状：個別実行で途中失敗時に不整合
suspend fun deleteNovelWithRelations(novel: NovelDescEntity) {
    withContext(Dispatchers.IO) {
        episodeDao.deleteEpisodesByNcode(novel.ncode)
        lastReadNovelDao.deleteLastRead(lastRead)
        updateQueueDao.deleteUpdateQueueByNcode(novel.ncode)
        novelDescDao.deleteNovel(novel)
        // ← エラーが発生すると途中まで削除された状態に
    }
}
```

#### 推奨される修正方法
```kotlin
// ✅ 推奨：Room の @Transaction アノテーションを使用
@Transaction
suspend fun deleteNovelWithRelations(novel: NovelDescEntity) {
    withContext(Dispatchers.IO) {
        // すべて成功するか、すべて失敗するか（アトミック操作）
        episodeDao.deleteEpisodesByNcode(novel.ncode)

        val lastRead = lastReadNovelDao.getLastReadByNcode(novel.ncode)
        if (lastRead != null) {
            lastReadNovelDao.deleteLastRead(lastRead)
        }

        updateQueueDao.deleteUpdateQueueByNcode(novel.ncode)
        novelDescDao.deleteNovel(novel)
    }
}
```

#### 影響とリスク
- **リスク**: データベース整合性の問題
- **影響範囲**: 小説削除機能
- **推定作業時間**: 20-30分
- **テスト要件**:
  - 正常な削除処理の確認
  - エラー発生時のロールバック確認
  - 関連データの整合性確認

---

### 3. WebView 初期化タイミングの問題

#### 📍 対象ファイル
`WebViewScreen.kt` (行75-80)

#### 問題の詳細
`CookieManager` が `webView` の初期化前に設定される可能性があります。

```kotlin
// ❌ 現状：webView が null の可能性
LaunchedEffect(Unit) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)  // webView が未初期化の可能性
}
```

#### 推奨される修正方法
```kotlin
// ✅ 推奨：AndroidView 内で初期化直後に設定
AndroidView(
    factory = { context ->
        WebView(context).apply {
            webView = this  // ← 最初に参照を保存

            // ✅ 初期化直後に設定
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // その他の設定...
        }
    }
)
```

#### 影響とリスク
- **リスク**: Cookie設定が反映されない可能性
- **影響範囲**: WebView画面でのR18サイトアクセス
- **推定作業時間**: 10-15分
- **テスト要件**:
  - WebView表示の確認
  - R18サイトへのアクセス確認
  - Cookie動作の確認

---

## 🟢 低優先度の改善課題

### 4. N+1 クエリ問題の最適化

#### 📍 対象ファイル
`RecentlyReadNovelsScreen.kt` (行50-52)

#### 問題の詳細
ループ内で個別にクエリを実行しているため、多数のレコードがある場合にパフォーマンスが低下します。

```kotlin
// ❌ 現状：ループ内で個別クエリ（N+1問題）
val novelWithInfoList = lastReadNovels.map { lastRead ->
    val novel = repository.getNovelByNcode(lastRead.ncode)  // N回実行される
    LastReadNovelWithInfo(lastRead, novel)
}
```

#### 推奨される修正方法

**ステップ1**: DAO に JOIN クエリを追加
```kotlin
// ✅ DAO に新しいメソッドを追加
@Query("""
    SELECT
        ln.*,
        nd.title,
        nd.author,
        nd.Synopsis,
        nd.main_tag,
        nd.sub_tag,
        nd.rating,
        nd.last_update_date,
        nd.total_ep
    FROM last_read_novels ln
    LEFT JOIN novels_descs nd ON ln.ncode = nd.ncode
    ORDER BY ln.last_read_date DESC
""")
fun getLastReadNovelsWithDetails(): Flow<List<LastReadNovelWithInfo>>
```

**ステップ2**: データクラスを定義
```kotlin
data class LastReadNovelWithInfo(
    @Embedded val lastRead: LastReadNovelEntity,
    @Embedded(prefix = "novel_") val novel: NovelDescEntity?
)
```

**ステップ3**: Repository で使用
```kotlin
fun getLastReadNovelsWithDetails(): Flow<List<LastReadNovelWithInfo>> {
    return lastReadNovelDao.getLastReadNovelsWithDetails()
}
```

**ステップ4**: Screen で使用
```kotlin
// ✅ 1回のクエリで取得
LaunchedEffect(Unit) {
    repository.getLastReadNovelsWithDetails().collect { list ->
        novelWithInfoList = list
    }
}
```

#### 影響とリスク
- **リスク**: データベーススキーマ変更による影響
- **影響範囲**: 最近読んだ小説画面
- **推定作業時間**: 45-60分
- **テスト要件**:
  - クエリのパフォーマンステスト
  - データ取得の正確性確認
  - 多数のレコードでのテスト

---

### 5. ハードコードされた設定値の外部化

#### 📍 対象ファイル
複数ファイル

| ファイル | 行 | 内容 |
|----------|-----|------|
| `NovelApiUtils.kt` | 78-80 | API エンドポイント URL |
| `ReleaseUtils.kt` | 35 | GitHub API URL |
| `WebViewScreen.kt` | 105 | User-Agent 文字列 |

#### 問題の詳細
API URLやUser-Agent文字列がコード内にハードコードされており、変更時にコード修正が必要です。

```kotlin
// ❌ 現状：ハードコードされた値
val apiUrl = "https://api.syosetu.com/novelapi/api/..."
val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ..."
```

#### 推奨される修正方法

**ステップ1**: 設定ファイルを作成
```kotlin
// ✅ 新しいファイル: AppConfig.kt
package com.shunlight_library.novel_reader.config

object AppConfig {
    // API エンドポイント
    const val SYOSETU_API_BASE = "https://api.syosetu.com/novelapi/api/"
    const val SYOSETU_R18_API_BASE = "https://api.syosetu.com/novel18api/api/"
    const val SYOSETU_WEB_BASE = "https://ncode.syosetu.com"
    const val SYOSETU_R18_WEB_BASE = "https://novel18.syosetu.com"

    // GitHub
    const val GITHUB_API_BASE = "https://api.github.com"
    const val GITHUB_REPO = "eightman999/Novel_reader_app"

    // User-Agent
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // タイムアウト設定（BuildConfig から読み込むことも可能）
    const val CONNECT_TIMEOUT_MS = 15000
    const val READ_TIMEOUT_MS = 30000

    // API クエリパラメータ
    const val API_QUERY_FIELDS = "t-n-u-w-s-k-g-ga-e-l-ua-nt"

    // 画像関連
    val SUPPORTED_IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "avif", "gif", "bmp")
}
```

**ステップ2**: 既存コードを更新
```kotlin
// ✅ 使用例
import com.shunlight_library.novel_reader.config.AppConfig

val apiUrl = if (isR18) {
    "${AppConfig.SYOSETU_R18_API_BASE}?of=${AppConfig.API_QUERY_FIELDS}&ncode=$ncode&gzip=5&json"
} else {
    "${AppConfig.SYOSETU_API_BASE}?of=${AppConfig.API_QUERY_FIELDS}&ncode=$ncode&gzip=5&json"
}

connection.connectTimeout = AppConfig.CONNECT_TIMEOUT_MS
connection.readTimeout = AppConfig.READ_TIMEOUT_MS
```

#### 影響とリスク
- **リスク**: 低（設定値を一箇所で管理するだけ）
- **影響範囲**: 全ファイル（広範囲だが機械的な置換）
- **推定作業時間**: 60-90分
- **テスト要件**:
  - すべてのAPI呼び出しの動作確認
  - WebView表示の確認
  - ネットワーク接続の確認

---

### 6. 例外処理のログ改善

#### 📍 対象ファイル
`NotificationData.kt` (行113)

#### 問題の詳細
例外が無視されるため、予期しない値の問題を追跡できません。

```kotlin
// ⚠️ 現状：例外が無視される
type = try {
    NotificationType.valueOf(typeString)
} catch (e: Exception) {
    NotificationType.INFO  // デフォルト値
}
```

#### 推奨される修正方法
```kotlin
// ✅ 推奨：警告ログを出力
type = try {
    NotificationType.valueOf(typeString)
} catch (e: Exception) {
    Log.w(TAG, "Unknown notification type: '$typeString', using default INFO", e)
    NotificationType.INFO
}
```

#### 影響とリスク
- **リスク**: なし（ログ追加のみ）
- **影響範囲**: 通知機能
- **推定作業時間**: 5分
- **テスト要件**: 通知機能の動作確認

---

### 7. Flow キャンセル処理の明確化

#### 📍 対象ファイル
`RecentlyReadNovelsScreen.kt` (行45-57)

#### 問題の詳細
`LaunchedEffect` 内で `Flow.first()` を直接呼び出しており、キャンセル時の処理が不明確です。

```kotlin
// ⚠️ 現状：キャンセル処理が暗黙的
LaunchedEffect(Unit) {
    val lastReadNovels = repository.allLastReadNovels.first()
    // ...
}
```

#### 推奨される修正方法
```kotlin
// ✅ 推奨：明示的なキャンセル処理（オプション）
LaunchedEffect(Unit) {
    try {
        val lastReadNovels = repository.allLastReadNovels.first()
        // ... 処理 ...
    } catch (e: CancellationException) {
        // キャンセル時のクリーンアップ（必要に応じて）
        Log.d(TAG, "Flow collection cancelled")
        throw e  // 必ず再スロー
    }
}
```

#### 影響とリスク
- **リスク**: 低（Composeが自動的に管理）
- **影響範囲**: 最近読んだ小説画面
- **推定作業時間**: 10分
- **備考**: Compose の `LaunchedEffect` は自動的にキャンセル処理を行うため、必須ではない

---

## 📝 その他の軽微な問題

### 8. データベーステーブル名のスペルミス（コメント）

#### 📍 対象ファイル
`ImprovedDatabaseSyncManager.kt` (行142)

#### 問題の詳細
コメント内でテーブル名が間違っています（実際のテーブル名は正しい）。

```kotlin
// ⚠️ コメントのスペルミス
val requiredTables = listOf("novels_descs", "episodes", "rast_read_novel")
// "rast_read_novel" は "last_read_novel" の誤り（コメントまたはコード）
```

#### 推奨される修正方法
実際のテーブル名を確認して修正:
```kotlin
// ✅ 正しいテーブル名
val requiredTables = listOf("novels_descs", "episodes", "last_read_novels")
```

#### 影響とリスク
- **リスク**: データベース互換性チェックの誤動作
- **影響範囲**: データベース同期機能
- **推定作業時間**: 5分
- **テスト要件**: データベース同期機能の確認

---

### 9. 入力検証の欠如

#### 📍 対象ファイル
`NovelApiUtils.kt` (行359-370)

#### 問題の詳細
`noveltype` パラメータが無効な値でも処理が続行されます。

```kotlin
// ⚠️ 現状：無効な値のチェックなし
suspend fun fetchEpisode(
    ncode: String,
    episodeNo: Int,
    isR18: Boolean = false,
    noveltype: Int? = null  // ← 無効な値チェックなし
): EpisodeEntity? {
    val url = if (noveltype == 2) {
        "$baseUrl/$ncode/"
    } else {
        "$baseUrl/$ncode/$episodeNo/"
    }
}
```

#### 推奨される修正方法
```kotlin
// ✅ 推奨：入力検証を追加
suspend fun fetchEpisode(
    ncode: String,
    episodeNo: Int,
    isR18: Boolean = false,
    noveltype: Int? = null
): EpisodeEntity? {
    // 入力検証
    if (ncode.isBlank()) {
        Log.w(TAG, "Invalid ncode: blank")
        return null
    }
    if (episodeNo < 1) {
        Log.w(TAG, "Invalid episodeNo: $episodeNo (must be >= 1)")
        return null
    }
    if (noveltype != null && noveltype !in listOf(1, 2)) {
        Log.w(TAG, "Invalid noveltype: $noveltype (must be 1 or 2)")
        return null
    }

    // 既存の処理...
}
```

#### 影響とリスク
- **リスク**: 低（検証追加による安全性向上）
- **影響範囲**: エピソード取得機能
- **推定作業時間**: 15-20分
- **テスト要件**:
  - 正常な値でのエピソード取得
  - 異常な値でのエラーハンドリング確認

---

## ✅ 実施済みの修正

以下の項目は、2025-11-01 に修正完了しました：

### 高優先度
- ✅ HttpURLConnection のリソースリーク（4ファイル）
- ✅ CoroutineScope の不適切な使用

### 中優先度
- ✅ メソッド名の重複定義（SettingsStore.kt）
- ✅ タイムアウト設定の改善

詳細は [コミット 99cf3e4](https://github.com/eightman999/Novel_reader_app/commit/99cf3e4) を参照してください。

---

## 📊 優先度別実施推奨時期

| 優先度 | 項目数 | 推奨時期 | 推定合計作業時間 |
|--------|--------|----------|------------------|
| 🟡 中 | 3項目 | 1-2ヶ月以内 | 60-80分 |
| 🟢 低 | 6項目 | 3-6ヶ月以内 | 120-180分 |

---

## 🎯 改善実施時の注意事項

### 実施前の準備
1. ✅ 現在の動作を十分にテスト
2. ✅ テストカバレッジを確認
3. ✅ バックアップまたはブランチを作成

### 実施時の原則
1. ✅ 1つずつ段階的に対応
2. ✅ 各修正後に必ずテスト
3. ✅ 既存機能を破壊しない

### 実施後の確認
1. ✅ ユニットテストの実行
2. ✅ 統合テストの実行
3. ✅ 手動での動作確認

---

## 📚 参考資料

### データベース最適化
- [Android Room - @Transaction](https://developer.android.com/training/data-storage/room/accessing-data#transaction)
- [N+1 Query Problem](https://stackoverflow.com/questions/97197/what-is-the-n1-selects-problem-in-orm-object-relational-mapping)

### Jetpack Compose
- [LaunchedEffect Best Practices](https://developer.android.com/jetpack/compose/side-effects#launchedeffect)
- [Coroutines in Compose](https://developer.android.com/jetpack/compose/kotlin#coroutines)

### コード品質
- [Android Lint](https://developer.android.com/studio/write/lint)
- [Detekt - Kotlin Static Analysis](https://detekt.dev/)

---

**最終更新**: 2025-11-01
**次回レビュー推奨日**: 2025-12-01
