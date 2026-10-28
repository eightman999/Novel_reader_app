#!/usr/bin/env bash
# ============================================================
# verify.sh — Novel_reader_app 検証ハーネス (AIエージェント向け)
#
# gradle プロジェクトのルートはリポ直下ではなく Novel_reader/ サブディレクトリ。
#
# 使い方:
#   ./verify.sh          高速検証: unitテスト (testDebugUnitTest)
#   ./verify.sh --check  ツール存在確認と実行ステップの表示のみ (ビルドは走らない)
#   ./verify.sh --full   重い検証: lint + assembleDebug
#                        + connectedAndroidTest (デバイス/エミュレータ接続時のみ)
#
# 契約:
#   - 成功時は最終行に "VERIFY PASS"、失敗時は "VERIFY FAIL" を出して exit 1
#   - ツール欠如・デバイス未接続のステップは SKIP 表示して続行
#   - bash 3.2 / BSD 互換。gradle は常に --console=plain
# ============================================================
set -euo pipefail

# リポ直下へ移動 (相対パス起動でも安定するよう二重解決)
cd "$(cd "$(dirname "$0")" && pwd)"

# gradle プロジェクトのルート (settings.gradle.kts の場所)
GRADLE_DIR="Novel_reader"

MODE="fast"
case "${1:-}" in
  --check) MODE="check" ;;
  --full)  MODE="full" ;;
  "")      MODE="fast" ;;
  *)
    echo "不明な引数: $1 (使用可能: 無印 / --check / --full)"
    echo "VERIFY FAIL"
    exit 1
    ;;
esac

STEP=0
TOTAL=0
SKIP_COUNT=0

# ---- 共通ヘルパー ----

# コマンド存在確認
have() { command -v "$1" >/dev/null 2>&1; }

# Gradle互換JDKの選択: JAVA_HOME 未設定なら JDK17 を優先使用する
# (システム既定が新しすぎるJava(実測: OpenJDK 25)だと Gradle 8.x が起動しないため)
if [ -z "${JAVA_HOME:-}" ] && /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  export JAVA_HOME
fi

# 接続中の実機/エミュレータが1台以上あるか (adb devices の実測で判定)
device_connected() {
  have adb || return 1
  adb devices 2>/dev/null | awk 'NR>1 && $2 == "device" {n++} END {exit (n>0 ? 0 : 1)}'
}

# ステップ見出し表示
step_banner() {
  STEP=$((STEP + 1))
  echo "[${STEP}/${TOTAL}] $1"
}

# SKIP 表示 (理由付き)
skip_step() {
  echo "  SKIP: $1"
  SKIP_COUNT=$((SKIP_COUNT + 1))
}

# gradle 実行 (サブディレクトリの gradlew を使用、常に --console=plain)
run_gradle() {
  (cd "$GRADLE_DIR" && ./gradlew --console=plain "$@")
}

fail() {
  echo "VERIFY FAIL"
  exit 1
}

# gradle ルートの実在確認
check_gradle_root() {
  if [ ! -x "$GRADLE_DIR/gradlew" ]; then
    echo "  NG: $GRADLE_DIR/gradlew が見つからない (gradle ルート想定が崩れている)"
    fail
  fi
}

# ---- --check: ツール存在確認 + ステップ表示のみ ----
if [ "$MODE" = "check" ]; then
  TOTAL=4
  step_banner "ツール確認: java"
  if have java; then
    echo "  OK: $(java -version 2>&1 | head -n 1)"
  else
    echo "  NG: java が見つからない (gradle 系ステップは SKIP される)"
  fi

  step_banner "ツール確認: adb"
  if have adb; then
    if device_connected; then
      echo "  OK: adb あり / デバイス接続あり"
    else
      echo "  OK: adb あり / デバイス未接続 (connectedAndroidTest は SKIP される)"
    fi
  else
    echo "  NG: adb が見つからない (connectedAndroidTest は SKIP される)"
  fi

  step_banner "ツール確認: flutter"
  if have flutter; then
    echo "  OK: flutter あり (このリポでは未使用)"
  else
    echo "  --: flutter なし (このリポでは未使用のため影響なし)"
  fi

  step_banner "実行ステップ一覧 (gradle ルート: ${GRADLE_DIR}/)"
  echo "  無印  : [1/1] unitテスト (testDebugUnitTest)"
  echo "  --full: [1/3] lint / [2/3] assembleDebug / [3/3] connectedAndroidTest (デバイス接続時のみ)"
  echo "VERIFY PASS"
  exit 0
fi

# ---- 無印: 高速検証 (unitテスト) ----
if [ "$MODE" = "fast" ]; then
  TOTAL=1
  step_banner "unitテスト (testDebugUnitTest)"
  if have java; then
    check_gradle_root
    run_gradle testDebugUnitTest || fail
  else
    skip_step "java が見つからないため実行不可"
  fi
fi

# ---- --full: 重い検証 ----
if [ "$MODE" = "full" ]; then
  TOTAL=3

  step_banner "lint"
  if have java; then
    check_gradle_root
    run_gradle lint || fail
  else
    skip_step "java が見つからないため実行不可"
  fi

  step_banner "assembleDebug"
  if have java; then
    run_gradle assembleDebug || fail
  else
    skip_step "java が見つからないため実行不可"
  fi

  step_banner "connectedAndroidTest (Room MigrationTest 等の実機テスト)"
  if ! have java; then
    skip_step "java が見つからないため実行不可"
  elif device_connected; then
    run_gradle connectedAndroidTest || fail
  else
    skip_step "デバイス/エミュレータ未接続 (adb devices で実測判定)"
  fi
fi

if [ "$SKIP_COUNT" -gt 0 ]; then
  echo "注意: SKIP が ${SKIP_COUNT} 件ある (上記の理由を参照)。SKIP されたステップは未検証。"
fi
echo "VERIFY PASS"
