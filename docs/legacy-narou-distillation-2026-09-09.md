# 旧なろう系プロジェクト蒸留ノート（2026-09-09）

## 目的

`NR-DB-Maker`、`narou_db_view`、`NovelReader_apple`、`narou_db_formatter`、および参照用fork `narou` に散らばった旧設計から、現在の Android / Kotlin `Novel_reader_app` に残すべき不変条件だけを抽出する。

旧コードをそのまま移植するための文書ではない。旧repoを退役させても、データ欠損・更新判定・復旧設計を再び間違えないための lessons-learned である。

## 1. 旧構成で繰り返し現れた処理段階

旧 `NR-DB-Maker` は処理を概ね以下へ分けていた。

1. remote metadata / gzip YAML の取得
2. raw YAML の展開
3. YAMLの妥当性・取得可否判定
4. canonical DB用データへの変換
5. remote話数とlocal detail数の差分計算
6. 欠損・取得不能作品の別管理

現在のAndroid版では同じ責務が API adapter / repository / Room / update service / repair scan に統合されているため、別ツールとして復活させる必要はない。ただし責務そのものは保つ。

## 2. 最重要の不変条件: remote総話数はlocal完全性の証明ではない

旧 `NR-DB-Maker` では `general_all_no - details_count` を更新差分として扱う発想があった。

これは更新の概算には使えても、完全性判定としては不十分である。

例:

- remote `general_all_no = 20`
- localに1〜4話、6〜20話が存在
- local件数は19

件数差は1だが、重要なのは「第5話が欠けている」ことである。

さらにlocalの `MAX(episode_no)` だけをresume点にすると、途中欠番を永久に飛ばす。

したがって現行の正準ルールは以下とする。

- `general_all_no`: remoteが主張する最新総話数
- `total_ep`: localで実際に取得済みとみなせる話数
- 完全性: 1..general_all_no のepisode番号集合を検査して判定
- resume: MAXだけでなくmissing episode scanを考慮
- fetch失敗: 「存在しない話」と「一時取得失敗」を区別可能にする

既存の欠落修正scan / placeholder / retry実装は、この不変条件を守るための機構として扱う。

## 3. 「取得不能」をmagic contentで表現しない

旧YAML検査では、APIの `allcount: 0` 応答を特定文字列と比較し、取得不能作品を別statusへ送っていた。

問題点:

- wire-formatの些細な変更に弱い
- empty / malformed / unavailable / adult-site mismatchなどの理由が潰れる
- raw response表現がdomain stateへ漏れる

現行では可能な限りtyped resultへ変換する。

例:

- available
- not_found
- access_restricted
- source_mismatch
- malformed_response
- transient_failure

raw API形式はadapter境界で止める。

## 4. raw source と canonical DB を分離する

旧ツールで `dl/*.gz` → `yml/*.yml` → DB変換と段階が分かれていた点は有用だった。

現行アプリでは中間ファイルを常設する必要はないが、概念上は次を分ける。

- source response
- parsed source model
- canonical Novel/Episode entity
- derived update queue / notification state

source parserの変更がRoom schemaや読書状態を直接壊さないようにする。

## 5. user stateを再取得で消さない

旧DB builder系はsource dataの作り直しを中心にしていたが、現行アプリにはsource由来でない状態が増えている。

例:

- favorite
- last read
- reading progress
- bookmark
- local display preferences
- update notification state

remote metadataのrefresh / reimport / repairで、これらをsource値として上書きしてはならない。

source-owned fields と user-owned fields を明示的に分離する。

## 6. repairは通常updateとは別の操作として考える

通常updateの目的:

- 新話の発見
- metadata更新
- 新規episodeの取得

repairの目的:

- episode番号の穴
- 空本文 / placeholder
- 不整合なtotal_ep
- migration / import由来の欠損
- source mappingの破損

通常updateを何度回してもrepair対象が直らない設計を避ける。ただし両者の検査ロジックを完全に同一化する必要もない。

## 7. iOS / Swift版から残す教訓

`NovelReader_apple` は 2026-06-13 に既にFROZENと明示され、Android版へ集中する判断が記録されている。

残すべき判断:

- platform portは「作れるから」開始しない
- canonical behavior / DB semantics / source adaptersが安定してからportする
- 二つのplatformで同じ取得ロジックを別実装し、仕様差分を増やさない

将来iOS版を再開する場合、可能ならsource protocol / database interchange / test fixtureを共有し、Android版の仕様を手作業で再現しない。

## 8. `narou` forkの扱い

`eightman999/narou` は `whiteleaf7/narou` のforkであり、自作本体ではない。

価値は以下に限定する。

- 既存実装の参照
- EPUB / vertical-writing / text normalization等のprior art確認
- source site対応史の参照

自作Novel Readerの正典や独自資産として扱わない。forkを保有し続ける必要がなくなれば削除候補でよい。

## 9. 現行で守る回帰試験

1. episode 5だけ欠けた1..20のDBでrepairが5を検出する
2. MAX(episode_no)=20でも途中欠番を完全と判定しない
3. remote総話数20 / local実体19を `total_ep=20` に見せない
4. transient fetch failure後に欠番が再取得可能
5. not-found / restricted / malformed / timeoutを同じ状態に潰さない
6. refreshでfavorite / last-read / progressを保持する
7. reimport後にepisode重複を作らない
8. source site / R18区分変更時もcanonical ncode identityを誤統合しない
9. update queueの `total_ep` と `general_all_no` の意味を逆転させない
10. migration後にmissing-episode scanが機能する

## 10. 旧repo退役方針

- `NR-DB-Maker`: data pipeline prototype。上記不変条件へ蒸留済み。新規開発終了。
- `narou_db_view`: 旧DB閲覧・管理UI。Android版へ役割統合済み。新規開発終了。
- `NovelReader_apple`: 既にFROZEN。再開条件を満たすまで触らない。
- `narou_db_formatter`: 実質空repo。歴史的価値がなければ削除候補。
- `narou`: upstream fork。必要な参照が終われば削除候補。

現行の仕様・実装判断は `Novel_reader_app` のコード、docs、testsを正典とする。
