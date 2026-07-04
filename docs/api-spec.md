# なろう小説API仕様

**必須**: なろう小説APIはYAML形式でデータを返す

## APIレスポンス形式
- **フォーマット**: YAML（デフォルト）
- **圧縮**: gzip圧縮（`gzip=5`パラメータ使用時）
- **注意**: `&json` パラメータは使用しない（YAMLがデフォルト）

## レスポンス構造
```yaml
---
-
  allcount: 1
-
  title: はぐるまどらいぶ。
  userid: 939213
  writer: かばやきだれ
  story: |
    あらすじ...
    複数行のテキスト
    （改行が保持される）
  # または
  story: >
    複数行のテキストが
    1行にまとめられる
    （改行が空白に置き換えられる）
  noveltype: 1
  general_all_no: 1216
  length: 4739978
  updated_at: 2025-11-04 18:35:37
```

**YAMLの複数行記法**:
- `|` (リテラルスカラー): 改行をそのまま保持
- `>` (折り畳みスカラー): 改行を空白に置き換え（段落をまとめる）
- SnakeYAMLライブラリが両方を自動処理

## 実装パターン
```kotlin
// API呼び出し（YAML形式、gzip圧縮）
val apiUrl = if (isR18) {
    "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
} else {
    "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
}

// YAMLパース（インデント修正を適用）
// なろう小説APIから返されるYAMLには、折り畳みスカラー（>）や
// リテラルスカラー（|）のインデントが不統一な場合があり、
// SnakeYAMLがパースエラーを起こすことがある。
// そのため、パース前にfixYamlFoldedScalarIndentation()でインデントを修正する。
val fixedYaml = fixYamlFoldedScalarIndentation(responseContent)
val yaml = Yaml()
val yamlData = yaml.load<List<Map<String, Any>>>(fixedYaml)
val novelData = yamlData[1]  // 0番目はメタデータ、1番目が小説情報

// GZIP解凍判定
val useGzip = url.contains("gzip=", ignoreCase = true) ||
              connection.contentEncoding?.contains("gzip", ignoreCase = true) == true
```

**重要なルール**:
- URLに `gzip=` パラメータがある場合は必ずGZIP解凍を行う
- Content-Encodingヘッダーだけでなく、URLパラメータもチェックする
- レスポンスの0番目の要素はメタデータ（allcount）、1番目が実際の小説情報

## R18作品判定ルール

**必須**: 小説のR18判定は`rating`フィールドで行う

```kotlin
// R18判定の実装パターン
val isR18 = novel.rating == 1

// APIエンドポイント選択（YAML形式）
val apiUrl = if (isR18) {
    "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
} else {
    "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
}

// WebサイトURL選択
val webUrl = if (isR18) {
    "https://novel18.syosetu.com/$ncode/"
} else {
    "https://ncode.syosetu.com/$ncode/"
}
```

**重要なルール**:
- **rating = 1** → R18サイト（novel18.syosetu.com）
- **rating = 2** → 一般サイト（ncode.syosetu.com）
- R18作品の更新確認・閲覧は専用APIとサイトを使用
- 一般作品の更新確認・閲覧は通常APIとサイトを使用

このルールは全てのAPI呼び出し、URL生成、WebView表示で統一して適用すること。

## 短編小説のタイトル取得ルール

**必須**: 短編小説（noveltype=2）と連載小説（noveltype=1）の両方でタイトルを正しく取得する

### HTML構造の違い
- **連載小説**: `<h1 class="p-novel__title p-novel__title--rensai">タイトル</h1>`
- **短編小説**: `<h1 class="p-novel__title">タイトル</h1>` （`--rensai`クラスなし）

### 実装パターン
```kotlin
// タイトル取得時のフォールバック処理（NovelApiUtils.kt等）
val title = doc.select("h1.p-novel__title.p-novel__title--rensai").text()
    .ifEmpty { doc.select("h1.p-novel__title").text() }
```

**重要なルール**:
- 連載小説専用のCSSセレクタ（`p-novel__title--rensai`）だけでは短編小説のタイトルが取得できない
- 必ずフォールバック処理を実装し、汎用セレクタ（`p-novel__title`）も試す
- タイトルが空の場合の処理を適切に行う
- 短編小説の場合、取得したタイトルを`e_title`フィールドに格納する

このルールはエピソード取得、WebViewでの表示、データベース保存などで統一して適用すること。
