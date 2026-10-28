# Novel Reader — エージェント指示

Android小説リーダー（なろう／カクヨム）。プロジェクト共通の指示はこのファイルを正本とする。仕様変更時は該当する `docs/` も更新する。

## 作業と検証

- Gradleは `Novel_reader/` で実行する。変更範囲に関係するunit test、lint、debug buildを実行し、失敗が今回の変更に起因する場合は修正・再実行する。UI・端末依存の変更は対象操作を実機またはエミュレータで確認する。
- 利用するコマンドはGradle taskと現在の設定を確認して選ぶ。入口は `./gradlew test`、`./gradlew lint`、`./gradlew assembleDebug`、端末接続時の `./gradlew connectedAndroidTest`。実行していない検証と残る問題を明示する。
- 依頼範囲のローカル編集・formatter・検証・fixture作成は続行する。公開／release配布、本番・実データ削除、破壊的migration、課金、secrets・権限変更は明示された対象・範囲の承認が必要。commit／pushは依頼された場合だけ行う。
- 既存project運用として、コード変更時は `Novel_reader/app/build.gradle.kts` の `versionCode` を +1、`versionName` のpatchを更新する。コミット本文は日本語にする。版数自体はここへ複製しない。

## 保護する設計・データ契約

- データアクセスは `NovelRepository`、サイト固有処理は `NovelSiteAdapter` と各adapterに置く。設定は `SettingsStore` / DataStoreを利用する。
- schema変更にはRoom migrationを用意し、既読・栞・読書率・サイト情報・episode mappingを復元／同期で欠落させない。既存データを捨てるmigrationで済ませない。
- エピソード取得は1話取得→保存を繰り返す。全話をメモリへ集めてから保存しない。再取得・更新・欠落修復にも適用する。
- `site_type` は 1=なろう、2=カクヨム。なろうの `rating == 1` はR18用API／サイト、`rating == 2` は一般用を使う。siteとratingの意味を混同しない。
- なろうAPIはYAMLを扱い、gzipはURLパラメータとContent-Encodingを確認する。短編titleには連載専用selector以外のfallbackを残す。
- カクヨムのHTTPアクセスは `applyRateLimit()` の0.5秒間隔とMutex直列化を保つ。本文／タイトル／章タイトルと目次のfallback順、失敗検出、retry範囲は該当protocolを参照する。
- カクヨムの話番号とepisode IDは `episode_mapping` で保持する。既存ncodeの形式を推測で変更しない。
- 孤立エピソード復元はメタデータだけを取得し本文を再ダウンロードしない。`total_ep` / `episode_count` はDB内の実件数を使い、クエリは500件単位に分割する。
- `NavigationManager` と標準back dispatcherを連携させる。前後話遷移はback stackを積み上げず、読書位置・栞・読書率を維持する。filter/sort設定は永続化し不正enum値でも安全に復元する。
- ログは `AppLogger`、画像cacheは `ImageCacheUtils` / `image_cache` を利用する。R8のreflection／`@JavascriptInterface`／ServiceLoader追加時はkeep ruleも確認する。

## 必要なときだけ読む

| 変更する領域 | 参照先 |
|---|---|
| DB schema、migration、同期・復元 | [docs/db-schema.md](docs/db-schema.md) と現在のRoom schema／migration |
| なろうAPI、R18、短編title | [docs/api-spec.md](docs/api-spec.md) |
| カクヨム取得、HTTP retry、HTML cleanup、目次 | [docs/kakuyomu-protocol.md](docs/kakuyomu-protocol.md) |
| architecture・navigation・設定 | [docs/architecture-detail.md](docs/architecture-detail.md) |
| WorkManager・通知・時刻 | [docs/auto-update-flow.md](docs/auto-update-flow.md) |
| 孤立復元の旧仕様・制約の由来 | [旧AGENTS原文](.agent-context/legacy/AGENTS-before-2026-09-05.md) の「孤立エピソード検知・復元機能」 |

graphifyはユーザー指定や横断的な関係調査に有用な場合に使う。通常の局所変更に毎回query／全体更新を強制しない。graphやdocsが実装と矛盾したら、該当ソースと検証結果で確認する。

旧 [AGENTS](.agent-context/legacy/AGENTS-before-2026-09-05.md) と [CLAUDE](.agent-context/legacy/CLAUDE-before-2026-09-05.md) は原文を履歴として保全している。旧版数・実装完了の自己申告・モデル別役割・常時手順は現行指示ではない。通常作業で全文を読む必要はない。
