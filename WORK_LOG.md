# Novel Reader App - 作業ログ

このファイルは開発作業の履歴を記録します。

## 2025/6/13

### 1. SettingsScreenのナビゲーションバー対応 (コミット: 3e21990)

**問題**: スマホの設定によっては設定保存ボタンがナビゲーションバーの下に表示される

**解決策**:
- `Scaffold`に`contentWindowInsets = WindowInsets(0.dp)`を追加
- `bottomBar`の`Surface`に`navigationBarsPadding()`を追加
- 必要なimport追加: `androidx.compose.foundation.layout.navigationBarsPadding`

**変更ファイル**:
- `SettingsScreen.kt`

**効果**: デバイス設定に関係なく、設定保存ボタンが常にアクセス可能に

### 2. MainActivityのバックナビゲーション現代化 (コミット: 6b3a162)

**問題**: deprecated な`onBackPressed()`を使用していた

**解決策**:
- `OnBackPressedCallback`を使用した現代的な実装に変更
- `OnBackPressedDispatcher`との統合
- `NavigationManager`との適切な連携

**変更ファイル**:
- `MainActivity.kt`

**効果**: Android 13+の推奨パターンに準拠、将来的な互換性確保

### 3. 小説一覧フィルター・ソート設定の永続化 (作業中)

**問題**: 
- 小説一覧の並び順・フィルター設定が画面を開くたびにリセットされる
- 適用ボタンでの保存と画面読み込み時の復元が必要

**解決策**:
- `SettingsStore`に`NovelListFilterSettings`データクラス追加
- 保存・読み込みメソッド実装
- `NovelListScreen`で設定の永続化と復元
- 適用ボタンでのみ保存、画面読み込み時に復元

**変更ファイル**:
- `SettingsStore.kt` (未コミット)
- `NovelListScreen.kt` (未コミット)

**効果**: ユーザーが設定したフィルター・ソート条件が次回起動時も維持される

### 4. CLAUDE.mdルール追加

**追加ルール**:
- Back Navigation Implementation: OnBackPressedDispatcherの必須使用
- Filter and Sort Settings Persistence: リスト画面設定の永続化パターン

**変更ファイル**:
- `CLAUDE.md` (未コミット)

### 5. UpdateInfoScreenボタンレイアウト変更 (コミット: 89d4a33)

**問題**: UpdateInfoScreenのボタンが横並びで表示され、小さな画面で使いにくい

**解決策**:
- `Row`を`Column`に変更して縦並びレイアウトに
- `verticalArrangement = Arrangement.spacedBy(12.dp)`で一定間隔確保
- すべてのボタンを`fillMaxWidth()`で幅いっぱいに表示
- 横方向の`Spacer`を削除

**変更ファイル**:
- `UpdateInfoScreen.kt`

**効果**: 小さな画面でもボタンが使いやすく、視覚的に整理された

### 6. EpisodeViewScreenバックナビゲーション修正 (コミット: 4163af9)

**問題**: 次のエピソードなどを押して移動した後に戻るボタンを押すと目次ではなく一つ前の画面に戻ってしまう

**解決策**:
- `TopAppBar`に`navigationIcon`を追加して目次に戻る機能実装
- `BackHandler`を追加してシステムバックボタンでも目次に戻るように設定
- 読書進度保存（`saveReadingRate()`）を実行してからナビゲーション
- `onBackToToc()`を使用して常に目次（EpisodeListScreen）に戻る

**変更ファイル**:
- `EpisodeViewScreen.kt`

**効果**: エピソード間移動後のナビゲーションが直感的になり、ユーザビリティが向上

**完了コミット**: 4163af9 (EpisodeViewScreen.kt), b1d9242 (WORK_LOG.md更新)

### 7. UpdateInfoScreenコンパイルエラー修正 (コミット: a313725)

**問題**: Button コンポーネントで重複する modifier パラメーターによりコンパイルエラーが発生

**解決策**:
- Button の重複する `modifier` パラメーターを削除
- 縦並びレイアウト用の `fillMaxWidth()` を維持
- パラメーターの順序を適切に調整

**変更ファイル**:
- `UpdateInfoScreen.kt`

**効果**: コンパイルエラーが解消され、UpdateInfoScreen が正常にビルドできるように

### 8. 非推奨警告の修正 (コミット: e87f584)

**問題**: Compose Material3 API の非推奨警告が複数発生

**解決策**:
- `Icons.Default.*` を `Icons.AutoMirrored.Filled.*` に更新
- `Divider` を `HorizontalDivider` に更新
- プログレスインジケーターをラムダベースのパラメーターに更新
- 最新の Compose Material3 API に準拠

**変更ファイル**:
- `EpisodeViewScreen.kt`: Arrow アイコンと List アイコン
- `NovelListScreen.kt`: Sort アイコンと Divider
- `SettingsScreen.kt`: Divider
- `UpdateInfoScreen.kt`: CircularProgressIndicator と LinearProgressIndicator

**効果**: 非推奨警告がすべて解消され、最新のCompose APIに準拠

### 8.1. アイコン参照エラーの修正 (コミット: cfb253c)

**問題**: List、ArrowForward、Sort アイコンが AutoMirrored 版では利用できずコンパイルエラー

**解決策**:
- `Icons.Default.List`、`Icons.Default.ArrowForward`、`Icons.Default.Sort` に戻す
- これらのアイコンは現在の Material3 バージョンで AutoMirrored 版が提供されていない
- `Icons.AutoMirrored.Filled.ArrowBack` は RTL レイアウト対応のため維持

**変更ファイル**:
- `EpisodeViewScreen.kt`: List と ArrowForward アイコン
- `NovelListScreen.kt`: Sort アイコン

**効果**: コンパイルエラーが解消され、利用可能なアイコンのみを使用

### 9. NovelListScreen お気に入り機能追加 (コミット: cb4bf27)

**問題**: 小説一覧でお気に入りの管理・フィルタリング機能が不足

**解決策**:
- データベースに `is_favorite` カラムとインデックスを追加
- マイグレーション 5→6 でデータベーススキーマ更新
- お気に入り状態の更新・取得メソッドを DAO・Repository に追加
- 小説アイテムにスターボタン (Star/StarBorder) を配置
- フィルターダイアログに「お気に入りのみ表示」チェックボックス追加
- お気に入りフィルター設定の永続化

**変更ファイル**:
- `NovelDescEntity.kt`: is_favorite フィールド追加
- `NovelDescDao.kt`: updateFavoriteStatus, getFavoriteNovels メソッド追加
- `NovelRepository.kt`: お気に入り関連メソッド追加
- `NovelDatabase.kt`: MIGRATION_5_6 追加、バージョン 6 に更新
- `SettingsStore.kt`: お気に入りフィルター設定の保存・復元
- `NovelListScreen.kt`: スターボタン・フィルター UI・ロジック実装

**効果**: ユーザーが好きな小説をお気に入り登録し、簡単に絞り込み表示可能

### 10. WorkManagerによるバックグラウンド自動更新機能実装 (コミット予定)

**問題**: 設定した時刻に自動でバックグラウンド更新を実行し、システム通知とアプリ内通知で結果を通知する機能が不足

**解決策**:
- `AutoUpdateWorker.kt`: WorkManagerによるバックグラウンド処理実装
- `AutoUpdateScheduler.kt`: 自動更新の時刻スケジュール管理
- `NotificationData.kt` & `NotificationStore.kt`: アプリ内通知のデータ管理
- `NotificationDialog.kt`: 通知一覧UI実装
- `MainActivity.kt`: メイン画面への通知機能統合（バッジ付き通知ボタン）
- `AndroidManifest.xml`: バックグラウンド実行に必要な権限追加
- `SettingsScreen.kt`: AutoUpdateSchedulerとの連携実装

**変更ファイル**:
- `worker/AutoUpdateWorker.kt` (新規作成)
- `worker/AutoUpdateScheduler.kt` (新規作成)  
- `data/NotificationData.kt` (新規作成)
- `ui/components/NotificationDialog.kt` (新規作成)
- `MainActivity.kt`: 通知機能統合
- `SettingsScreen.kt`: スケジューラー連携
- `AndroidManifest.xml`: 権限追加
- `.gitignore`: APKファイル除外設定

**効果**: 
- アプリ未起動・画面OFF時でもバックグラウンドで更新確認
- システム通知で「新規X作品、更新Y作品」を即座表示
- アプリ内通知で詳細な更新履歴管理
- メイン画面に未読通知数バッジ表示
- 設定変更時の即座反映

### 11. R18作品判定ルール追加 (コミット予定)

**追加ルール**: `rating`フィールドによるR18判定
- **rating = 1** → R18サイト（novel18.syosetu.com）
- **rating = 2** → 一般サイト（ncode.syosetu.com）

**変更ファイル**:
- `CLAUDE.md`: R18判定ルール追加
- `worker/AutoUpdateWorker.kt`: 正しいR18判定ロジック適用

### 12. アプリ名変更 (コミット: 実行中)

**問題**: アプリ名が技術的な名称「Novel_reader」で、ユーザーにとって分かりにくい

**解決策**:
- アプリ名を「オフラインで読もう！」に変更
- よりユーザーフレンドリーで機能を表現する名称に

**変更ファイル**:
- `app/src/main/res/values/strings.xml`

**効果**: ユーザーがアプリの機能を直感的に理解でき、オフライン読書の特徴を明確に伝える

### 13. アプリバージョン設定 (コミット: 実行中)

**問題**: アプリのバージョン管理が不明確

**解決策**:
- アプリバージョンを v1.3.5 (versionCode: 135) に設定
- リリースコメント用の機能説明文書作成

**変更ファイル**:
- `app/build.gradle.kts`

**効果**: 明確なバージョン管理でリリース履歴の追跡が可能

## 本日 (2025/6/13) の進捗まとめ

### 🎯 **完了した主要機能**

1. **WorkManagerバックグラウンド自動更新システム**
   - 設定時刻での24時間間隔自動実行
   - アプリ未起動時でもバックグラウンド動作
   - システム通知での更新結果表示

2. **アプリ内通知システム**
   - メイン画面への通知ボタン・バッジ表示
   - 通知履歴の永続化と管理機能
   - 既読/未読状態・削除機能

3. **R18作品判定ルール確立**
   - rating=1（R18）、rating=2（一般）の明確化
   - 全システムでの統一適用

4. **アプリ名・バージョン管理**
   - 「オフラインで読もう！」への名称変更
   - v1.3.5 (versionCode: 135) 設定

5. **開発環境整備**
   - .gitignoreにAPKファイル除外設定
   - アプリアイコン作成方針書策定

### 📁 **作成ファイル**
- `worker/AutoUpdateWorker.kt` - WorkManagerバックグラウンド処理
- `worker/AutoUpdateScheduler.kt` - 自動更新スケジュール管理
- `data/NotificationData.kt` - アプリ内通知データ管理
- `ui/components/NotificationDialog.kt` - 通知一覧UI

### 🔧 **変更ファイル**
- `MainActivity.kt` - 通知機能統合・バッジ表示
- `SettingsScreen.kt` - AutoUpdateScheduler連携
- `AndroidManifest.xml` - バックグラウンド実行権限
- `strings.xml` - アプリ名変更
- `build.gradle.kts` - バージョン設定
- `.gitignore` - APKファイル除外

### 📝 **ドキュメント更新**
- `CLAUDE.md` - R18判定ルール・自動更新機能仕様
- `WORK_LOG.md` - 全進捗記録

### 🎯 **達成した価値**
- ユーザーが設定した時刻での自動更新
- アプリ未使用時でも確実な更新確認
- 直感的な通知管理システム
- 明確なバージョン管理とリリース準備

## 開発パターンのまとめ

### 実装済みの重要な改善点

1. **現代的なバックナビゲーション**: deprecated な `onBackPressed()` から `OnBackPressedCallback` への移行
2. **設定の永続化**: フィルター・ソート設定の DataStore による保存・復元
3. **ナビゲーションバー対応**: edge-to-edge 表示での UI 要素隠れ問題の解決
4. **直感的なナビゲーション**: エピソード画面から常に目次へ戻る流れの確立
5. **モバイル UI 最適化**: ボタンレイアウトの縦並び化

### 確立された開発ルール

- OnBackPressedDispatcher の必須使用
- 設定変更時の適用ボタン方式
- navigationBarsPadding() によるナビゲーションバー対応
- 一作業ごとのコミットと作業ログ記録

## 次のタスク


1. 他の画面でのナビゲーションバー対応確認
2. 他のレイアウト改善の検討

## 2025/6/15
### EpisodeView back navigation fix
- System back now pops to the screen prior to any EpisodeView
- Back to Table of Contents also clears stacked EpisodeViews
- Fixed navigateBackTo logic to properly remove EpisodeView screens


## 2025/7/15
### GitHub release check and version display
- Notify when a new GitHub release is available during auto update and on startup
- Added ReleaseUtils utility and DataStore key for last notified release
- Added version info button at bottom of main menu
- Background check runs in AutoUpdateWorker and MainActivity


## 2025/9/11
### 1. NovelListScreen フィルター設定自動保存

**問題**: フィルター変更後にダイアログ外をタップすると設定が保存されず、画面遷移するとリセットされる

**解決策**:
- 設定読み込み完了フラグを導入
- `LaunchedEffect` で `sortField`・`sortDirection`・`filterSettings` の変更時に自動保存

**変更ファイル**:
- `NovelListScreen.kt`
- `WORK_LOG.md`

**効果**: 小説一覧ページのフィルター・ソート設定が画面遷移後も維持される

## 2025/9/12
### Fix favorite toggle on novel list screen
- Separated favorite star from list item click area
- Enabled toggling favorites without navigating to novel detail

## 2025/9/12
### NovelListScreen フィルター設定再保存の改善

**問題**: 設定を保存しても画面遷移時にリセットされることがあった

**解決策**:
- 設定保存処理をサスペンド関数化して確実に完了させる
- 画面離脱時にも設定を保存する `DisposableEffect` を追加

**変更ファイル**:
- `NovelListScreen.kt`
- `WORK_LOG.md`

**効果**: フィルター設定が画面遷移後も確実に維持される

## 2025/9/13
### 通知ログのメモリリーク対策

**問題**: 通知ログが無制限に蓄積されるとDataStore内のデータ量が増加し、メモリ使用量の増加やパフォーマンス低下を招く可能性があった

**解決策**:
- 通知保存数の上限 (`MAX_NOTIFICATION_COUNT = 100`) を導入
- 新規通知追加時に古い通知を削除する`enforceNotificationLimit`処理を実装

**変更ファイル**:
- `NotificationData.kt`
- `WORK_LOG.md`

**効果**: 古い通知が自動的に整理され、メモリ使用量とDataStoreサイズの膨張を防止

## 2025/10/05
### 更新処理の排他制御とキャンセル待機を強化

**問題**: 同一ncodeの更新が並行して進行するとデータ不整合が発生する恐れがあり、削除や再取得を実行した際に進行中の更新が完全に停止するまで待機できていなかった。

**解決策**:
- `NovelUpdateCoordinator` に `cancelAndWait` と完了通知機構を追加し、キャンセル後に確実にスロット解放を待機
- `EpisodeListScreen` の再取得・ダウンロード・エラー修正処理で更新停止待機を挟み、停止できない場合はユーザーへ警告表示
- `NovelRepository.deleteNovelWithRelations` が進行中の更新停止を確認できた場合のみ削除を続行するように調整

**変更ファイル**:
- `NovelUpdateCoordinator.kt`
- `EpisodeListScreen.kt`
- `UpdateService.kt`
- `NovelRepository.kt`
- `WORK_LOG.md`

**効果**: バックグラウンド更新とユーザー操作の競合を安全に解決し、同一ncodeに対する重複更新や削除時の整合性崩れを防止


### ミテミン画像共通ロジックの再適用とAPI共有化

**問題**:
- EpisodeListScreen と UpdateInfoScreen がそれぞれ独自に API へアクセスしており、R18 作品のエンドポイント選択や更新日時の扱いがばらついていた
- 共有の `fetchEpisodeWithRetry` を利用していても、前処理側が旧式の `Pair` ベース実装に依存していたためミテミン画像キャッシュの再利用が不安定だった

**解決策**:
- `NovelApiUtils.fetchNovelInfo` を両画面から直接呼び出し、R18 判定を含む統一的なレスポンスデータ（generalAllNo・updatedAt・userid など）を利用
- EpisodeListScreen では手製の API 呼び出し関数を廃止し、更新・再取得・エラー修正すべてのフローで共有データクラスを参照
- UpdateInfoScreen の一括チェック処理を共通ユーティリティに置き換え、欠けているメタデータ（userid/noveltype/length）の補完と更新キュー登録を一元化

**変更ファイル**:
- `EpisodeListScreen.kt`
- `UpdateInfoScreen.kt`

**効果**: すべての更新手段で同じ API・画像処理経路が用いられるようになり、ミテミン画像のローカルキャッシュ置換と R18 作品の更新検出が安定した
