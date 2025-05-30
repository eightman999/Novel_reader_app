# AGENTS.md - Novel Reader Project

このドキュメントは、AIエージェントがNovel Readerプロジェクトに貢献する際の規約とガイドラインを定義します。

## 1. Coding Style

### Kotlin Coding Standards

**基本規則**
- インデント: 4 spaces (no tabs)
- 行末に不要な空白を含めない
- ファイル終端に改行を1行追加
- 最大行長: 120 characters

**命名規則**
```kotlin
// Classes: PascalCase
class NovelDescEntity

// Functions & Variables: camelCase
fun fetchEpisode()
val episodeNo = "1"

// Constants: UPPER_SNAKE_CASE
const val MAX_RETRY_COUNT = 3

// Packages: snake_case
package com.shunlight_library.novel_reader.data.entity
```

**Compose特有の規則**
- Composable functions: PascalCase
- Preview functions: `{ComponentName}Preview` 形式
- State variables: `by remember { mutableStateOf() }` を使用

**フォーマッタ設定**
```bash
# Android Studio standard formatter
# Format > Reformat Code (Ctrl+Alt+L)

# Or use ktlint if available
./gradlew ktlintFormat
```

### コメント規約
- 日本語コメント許可
- 複雑なロジックには必ず説明コメント
- TODO/FIXME は Issue 番号と関連付け

```kotlin
/**
 * 小説のエピソードを取得します
 * @param ncode Novel code identifier
 * @param episodeNo Episode number
 * @param isR18 True if R18 content
 * @return Retrieved episode or null if failed
 */
suspend fun fetchEpisode(ncode: String, episodeNo: Int, isR18: Boolean): EpisodeEntity?
```

## 2. Testing and Quality Assurance

### テスト実行コマンド

**Unit Tests**
```bash
# Run all unit tests
./gradlew test

# Debug build tests
./gradlew testDebugUnitTest

# Release build tests  
./gradlew testReleaseUnitTest
```

**Instrumentation Tests**
```bash
# Integration tests on emulator/device
./gradlew connectedDebugAndroidTest

# Run specific test class
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shunlight_library.novel_reader.ExampleInstrumentedTest
```

### カバレッジ要件
- **Minimum coverage**: 70%
- **Recommended coverage**: 85%
- **必須テスト対象**:
  - Repository layer
  - API layer (NovelApiUtils)
  - Database operations (DAO)

### 品質チェック
```bash
# Lint check
./gradlew lint

# Dependency vulnerability check
./gradlew dependencyUpdates
```

### 失敗時の対応
- **Auto retry**: Maximum 3 attempts
- **Cache usage**: Avoid `--no-daemon` (for build performance)
- **Parallel execution**: Use `./gradlew --parallel` when possible

## 3. Build and Deployment

### ローカルビルド

**Development Build**
```bash
# Generate debug APK
./gradlew assembleDebug

# Signed release build
./gradlew assembleRelease

# Clean build
./gradlew clean build
```

**Docker Container Build (for CI)**
```bash
# Android development environment container
docker run --rm -v $(pwd):/project \
  android-build-env:latest ./gradlew assembleRelease
```

### 本番リリース手順

**GitHub Actions Workflow**
- ワークフロー名: `.github/workflows/release.yml`
- トリガー: Tag push with `v*` pattern
- 成果物: Signed APK files

**Manual Release**
```bash
# Create release tag
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0

# Generate Bundle for Play Console
./gradlew bundleRelease
```

### 機密情報の取り扱い

**Environment Variables (local.properties)**
```properties
# Development settings
API_BASE_URL=https://api.syosetu.com/
DEBUG_MODE=true
```

**GitHub Secrets (Production)**
- `KEYSTORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Signing key alias
- `KEY_PASSWORD`: Signing key password

**重要**: 小説サイトのAPIキーや認証情報は環境変数で管理し、コードに直接記述しない

## 4. Documentation Update Policy

### 同時更新が必要なファイル

**Database Schema Changes**
- Update `README.md` tech stack section
- Record changes in `CHANGELOG.md`
- Add migration procedures

**API Changes**
- Update KDoc in `api/` package
- Verify terms of service impact
- Update rate limiting information

**UI Changes**
- Update screenshots in `docs/screenshots/`
- Update usage sections

### 自動生成ドキュメント

**KDoc Generation**
```bash
# Generate API documentation with Dokka
./gradlew dokkaHtml

# Output: build/dokka/html/
```

**Database Schema Export**
```bash
# Export Room schema
./gradlew kspDebugKotlin
# Output: app/schemas/
```

### ADR (Architectural Decision Record) 追加基準

以下の場合はADRを作成する：
- **Architecture pattern changes** (MVVM → MVI etc.)
- **Major dependency additions/removals** (Room → Realm etc.)
- **Data synchronization method changes**
- **Security requirement changes**
- **Major external API specification changes**

## 5. Commit and PR Message Conventions

### コミットメッセージ形式

**Conventional Commits + Japanese Support**
```
<type>(<scope>): <description>

# Examples:
feat(ui): エピソード表示に縦書きモード追加
fix(api): 小説情報取得時のタイムアウト処理を修正
docs: README.mdにセットアップ手順を追加
```

**Type List**
- `feat`: New features
- `fix`: Bug fixes
- `docs`: Documentation updates
- `style`: Code style fixes
- `refactor`: Code refactoring
- `test`: Test additions/modifications
- `chore`: Build/configuration changes

**Scope Examples**
- `ui`: User interface
- `api`: API related
- `db`: Database related
- `sync`: Data synchronization
- `settings`: Settings functionality

### PRテンプレート必須項目

```markdown
## 📋 Changes Overview
<!-- Briefly describe what was changed -->

## 🧪 Test Results
<!-- Tests executed and results -->
- [ ] Unit tests: `./gradlew test`
- [ ] UI tests: `./gradlew connectedDebugAndroidTest`
- [ ] Manual testing: Operation confirmed

## 🎯 Impact Scope
<!-- Features/components affected by this change -->
- [ ] Novel loading functionality
- [ ] Database schema
- [ ] Settings screen
- [ ] Other: ___________

## 📱 Operation Verification
<!-- Attach screenshots/recordings if available -->

## ⚠️ Important Notes
<!-- Points reviewers should pay special attention to -->

## 🔗 Related Issues
<!-- Related issue numbers -->
Closes #___
```

### 自動レビュアーアサイン条件

**Required Review Targets**
- Changes in `data/dao/` → Database experts
- Changes in `api/` → API design experts
- Changes in `ui/` → UI/UX experts
- Security or authentication related → Security experts

**Auto-merge Conditions**
- Documentation updates only
- Test additions only
- Dependency updates (patch versions only)

## 🚨 Important Considerations

### API利用に関する制限
- **Rate limiting**: Maximum 5 requests per second
- **User-Agent**: Must be configured
- **Error handling**: Always implement retry logic

### データ取り扱い
- 個人の読書データは暗号化して保存
- 小説コンテンツのキャッシュは適切な期限設定
- ログには個人識別可能な情報を含めない

---

**このAGENTS.mdは `main` ブランチの最新版を参照してください。**
**変更提案は Issue または PR でお願いします。**
