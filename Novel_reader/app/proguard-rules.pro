# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# クラッシュレポートのスタックトレースに行番号を残す（難読化後の解析に必須）
-keepattributes SourceFile,LineNumberTable

# ---------------------------------------------------------------------------
# snakeyaml (なろうAPI YAMLレスポンスのパースに使用: NovelApiUtils.kt)
# ---------------------------------------------------------------------------
# 実装は yaml.load<List<Map<String, Any>>>(...) という Map ベースの読み取りのみで、
# 独自データクラスへの Bean バインディング（yaml.loadAs）は使用していない。
# ただし Yaml() はデフォルトコンストラクタ（SafeConstructor 不使用）を使っており、
# snakeyaml 内部の Constructor/Representer/Introspector 等はリフレクションで
# 自身のクラスを参照するため、R8 に剥がされると「なろうAPI取得」が全滅する
# (docs/api-spec.md 記載の最重要フロー)。snakeyaml は消費者proguardルールを
# 同梱していないため、ライブラリ全体を明示的に keep する。
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# ---------------------------------------------------------------------------
# WebView JavaScript Interface (EpisodeViewScreen.kt: 本文閲覧のスクロール位置保存)
# ---------------------------------------------------------------------------
# addJavascriptInterface(WebViewScrollInterface(...), ...) で登録され、
# @JavascriptInterface アノテーション付きメソッドは WebView 側 JS から
# リフレクション経由で呼ばれる。難読化・削除されると本文閲覧時の
# 読書位置保存（reading_rate）が壊れるため、クラス名・メンバー名を維持する。
-keepclassmembers class com.shunlight_library.novel_reader.WebViewScrollInterface {
    public *;
}
-keep,allowobfuscation @interface android.webkit.JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------------------------------------------------------------------------
# compose-markdown (com.github.jeziellago) / Markwon
# ---------------------------------------------------------------------------
# 内部実装は Markwon(atlassian commonmark ベース)。バンドルされている
# consumer proguard.txt は空で、ServiceLoader(META-INF/services)も
# 使用していないことを確認済み。R8のmissing class警告が出た場合のみ
# 個別に -dontwarn を追加する。
