# Firebase Cloud Messaging (FCM) 通知の使い方

このドキュメントでは、Novel Reader AppでFirebase Cloud Messaging (FCM) を使ってプッシュ通知を送信する方法を説明します。

## 目次

1. [概要](#概要)
2. [事前準備](#事前準備)
3. [FCMトークンの取得方法](#fcmトークンの取得方法)
4. [通知の送信方法](#通知の送信方法)
5. [通知のデータ形式](#通知のデータ形式)
6. [トラブルシューティング](#トラブルシューティング)

---

## 概要

Firebase Cloud Messaging (FCM) は、Googleが提供するクロスプラットフォームのメッセージングソリューションです。このアプリでは、以下の機能に対応しています：

- **プッシュ通知の受信**: サーバーから送信されたメッセージを受信し、通知として表示
- **データペイロードの処理**: 通知に含まれるカスタムデータを処理
- **通知タイプの分類**: 小説更新、お知らせなど、通知の種類に応じた処理

---

## 事前準備

### 1. Firebase プロジェクトの設定

1. [Firebase Console](https://console.firebase.google.com/) にアクセス
2. プロジェクトを選択（または新規作成）
3. Android アプリを追加
   - **パッケージ名**: `com.shunlight_library.novel_reader`
   - **アプリのニックネーム**: 任意（例: Novel Reader）
   - **デバッグ用署名証明書 SHA-1**: 必要に応じて追加

### 2. google-services.json の配置

1. Firebase Console からダウンロードした `google-services.json` ファイルを取得
2. `Novel_reader/app/` ディレクトリに配置
3. ファイルの配置場所:
   ```
   Novel_reader_app/
   └── Novel_reader/
       └── app/
           └── google-services.json  ← ここに配置
   ```

### 3. アプリのビルドとインストール

```bash
cd Novel_reader
./gradlew clean
./gradlew assembleDebug
./gradlew installDebug
```

---

## FCMトークンの取得方法

アプリにプッシュ通知を送信するには、デバイス固有の **FCMトークン** が必要です。

### 方法1: Logcatでトークンを確認

1. Android Studio の Logcat を開く
2. フィルターに `FCMService` と入力
3. アプリを起動すると、以下のようなログが表示されます：
   ```
   D/FCMService: 新しいFCMトークンが生成されました: dXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX...
   ```
4. このトークンをコピーして保存

### 方法2: コードで取得

`MainActivity.kt` などに以下のコードを追加して、トークンを取得できます：

```kotlin
import com.google.firebase.messaging.FirebaseMessaging

// FCMトークンを取得
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (!task.isSuccessful) {
        Log.w(TAG, "トークン取得失敗", task.exception)
        return@addOnCompleteListener
    }

    // 新しいトークンを取得
    val token = task.result
    Log.d(TAG, "FCMトークン: $token")
    // TODO: トークンをサーバーに送信する処理
}
```

---

## 通知の送信方法

### 方法1: Firebase Console から送信（最も簡単）

1. [Firebase Console](https://console.firebase.google.com/) にアクセス
2. **Cloud Messaging** セクションに移動
3. **最初のキャンペーンを作成** → **Firebase Notification メッセージ**
4. 通知の内容を入力：
   - **通知タイトル**: 例: `新しいエピソード公開`
   - **通知テキスト**: 例: `「はぐるまどらいぶ。」の第1217話が公開されました`
5. **ターゲット** で以下のいずれかを選択：
   - **すべてのユーザー**: アプリをインストールした全デバイスに送信
   - **トピック**: 特定のトピックに登録したユーザーに送信
   - **FCMトークン**: 特定のデバイスに送信（テスト用）
6. **追加オプション** でデータペイロードを追加（任意）
7. **確認** → **公開**

### 方法2: Firebase Admin SDK から送信（サーバーサイド）

#### Node.js の例

```javascript
const admin = require('firebase-admin');

// Firebase Admin SDK の初期化
const serviceAccount = require('./path/to/serviceAccountKey.json');
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

// 通知メッセージを作成
const message = {
  notification: {
    title: '新しいエピソード公開',
    body: '「はぐるまどらいぶ。」の第1217話が公開されました'
  },
  data: {
    type: 'novel_update',
    ncode: 'n9939AB',
    title: '新しいエピソード公開',
    body: '第1217話が公開されました'
  },
  token: 'dXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX...' // 受信デバイスのFCMトークン
};

// 送信
admin.messaging().send(message)
  .then((response) => {
    console.log('通知送信成功:', response);
  })
  .catch((error) => {
    console.log('通知送信失敗:', error);
  });
```

#### Python の例

```python
import firebase_admin
from firebase_admin import credentials, messaging

# Firebase Admin SDK の初期化
cred = credentials.Certificate('path/to/serviceAccountKey.json')
firebase_admin.initialize_app(cred)

# 通知メッセージを作成
message = messaging.Message(
    notification=messaging.Notification(
        title='新しいエピソード公開',
        body='「はぐるまどらいぶ。」の第1217話が公開されました'
    ),
    data={
        'type': 'novel_update',
        'ncode': 'n9939AB',
        'title': '新しいエピソード公開',
        'body': '第1217話が公開されました'
    },
    token='dXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX...'  # 受信デバイスのFCMトークン
)

# 送信
response = messaging.send(message)
print('通知送信成功:', response)
```

### 方法3: cURL で送信（テスト用）

Firebase Console からサーバーキーを取得し、以下のコマンドで送信できます：

```bash
curl -X POST https://fcm.googleapis.com/fcm/send \
  -H "Authorization: key=YOUR_SERVER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "to": "dXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX...",
    "notification": {
      "title": "新しいエピソード公開",
      "body": "「はぐるまどらいぶ。」の第1217話が公開されました"
    },
    "data": {
      "type": "novel_update",
      "ncode": "n9939AB",
      "title": "新しいエピソード公開",
      "body": "第1217話が公開されました"
    }
  }'
```

**注意**: この方法は非推奨です。Firebase Admin SDK の使用を推奨します。

---

## 通知のデータ形式

### 基本的な通知形式

```json
{
  "notification": {
    "title": "通知のタイトル",
    "body": "通知の本文"
  },
  "data": {
    "type": "通知のタイプ（novel_update, announcement など）",
    "key1": "value1",
    "key2": "value2"
  }
}
```

### 通知タイプ別のデータペイロード

#### 1. 小説更新通知 (`novel_update`)

```json
{
  "notification": {
    "title": "新しいエピソード公開",
    "body": "「はぐるまどらいぶ。」の第1217話が公開されました"
  },
  "data": {
    "type": "novel_update",
    "ncode": "n9939AB",
    "title": "新しいエピソード公開",
    "body": "第1217話が公開されました"
  }
}
```

**データペイロードの説明:**
- `type`: `novel_update` (固定)
- `ncode`: 小説のncode（例: `n9939AB`）
- `title`: 通知タイトル
- `body`: 通知本文

#### 2. お知らせ通知 (`announcement`)

```json
{
  "notification": {
    "title": "メンテナンスのお知らせ",
    "body": "明日午前2時からメンテナンスを実施します"
  },
  "data": {
    "type": "announcement",
    "title": "メンテナンスのお知らせ",
    "body": "明日午前2時からメンテナンスを実施します"
  }
}
```

**データペイロードの説明:**
- `type`: `announcement` (固定)
- `title`: お知らせタイトル
- `body`: お知らせ本文

#### 3. カスタム通知（デフォルト）

```json
{
  "notification": {
    "title": "カスタム通知",
    "body": "カスタムメッセージ"
  },
  "data": {
    "title": "カスタム通知",
    "body": "カスタムメッセージ",
    "custom_key": "custom_value"
  }
}
```

**データペイロードの説明:**
- `type`: 指定しない（または任意の値）
- その他のキーは自由に追加可能

---

## 通知の受信処理

アプリ側では、`MyFirebaseMessagingService.kt` で以下の処理を行います：

### 1. FCMトークンの更新

```kotlin
override fun onNewToken(token: String) {
    super.onNewToken(token)
    AppLogger.d(TAG, "新しいFCMトークンが生成されました: $token")
    // TODO: トークンをサーバーに送信
}
```

### 2. メッセージの受信

```kotlin
override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)

    // データペイロードがある場合
    if (remoteMessage.data.isNotEmpty()) {
        handleDataPayload(remoteMessage.data)
    }

    // 通知ペイロードがある場合
    remoteMessage.notification?.let {
        val title = it.title ?: "小説リーダー"
        val body = it.body ?: ""
        sendNotification(title, body, remoteMessage.data)
    }
}
```

### 3. 通知の表示

```kotlin
private fun sendNotification(title: String, messageBody: String, data: Map<String, String> = emptyMap()) {
    // 通知チャネルを作成
    createNotificationChannel()

    // アプリ起動用のIntent
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        // データペイロードをIntentに追加
        data.forEach { (key, value) ->
            putExtra(key, value)
        }
    }

    val pendingIntent = PendingIntent.getActivity(...)

    // 通知を作成して表示
    val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(messageBody)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))

    notificationManager.notify(notificationId++, notificationBuilder.build())
}
```

---

## トラブルシューティング

### 通知が届かない場合

1. **google-services.json が正しく配置されているか確認**
   - `Novel_reader/app/google-services.json` にファイルが存在するか
   - パッケージ名が `com.shunlight_library.novel_reader` と一致しているか

2. **FCMトークンが正しく取得できているか確認**
   - Logcat で `FCMService` タグのログを確認
   - トークンが表示されているか確認

3. **通知権限が許可されているか確認**
   - Android 13以降では、通知権限を手動で許可する必要があります
   - 設定 → アプリ → Novel Reader → 通知 → 許可

4. **アプリがバックグラウンドで実行されているか確認**
   - バックグラウンドでもFCMメッセージは受信されます
   - アプリを完全に終了した場合でも通知は届きます

5. **Firebase Console のステータスを確認**
   - Cloud Messaging セクションで送信履歴を確認
   - エラーメッセージがないか確認

### Logcat でエラーを確認

Android Studio の Logcat で以下のフィルターを設定：

```
tag:FCMService
```

または

```
package:com.shunlight_library.novel_reader
```

エラーメッセージを確認して、問題を特定します。

### よくあるエラー

#### エラー1: `MissingDefaultServiceAccountException`

**原因**: `google-services.json` が配置されていないか、正しくない

**解決策**:
1. Firebase Console から最新の `google-services.json` をダウンロード
2. `Novel_reader/app/` に配置
3. アプリを再ビルド

#### エラー2: `NotificationPermissionDenied`

**原因**: Android 13以降で通知権限が許可されていない

**解決策**:
1. 設定 → アプリ → Novel Reader → 通知 → 許可
2. または、アプリ起動時に権限をリクエストするコードを追加

#### エラー3: `InvalidRegistrationToken`

**原因**: FCMトークンが無効または期限切れ

**解決策**:
1. アプリを再インストール
2. 新しいFCMトークンを取得
3. サーバー側のトークンを更新

---

## 高度な使い方

### トピック購読

特定のトピック（例: `novel_updates`）に登録して、そのトピックに送信された通知を受信できます。

```kotlin
import com.google.firebase.messaging.FirebaseMessaging

// トピックに登録
FirebaseMessaging.getInstance().subscribeToTopic("novel_updates")
    .addOnCompleteListener { task ->
        if (task.isSuccessful) {
            Log.d(TAG, "トピック登録成功: novel_updates")
        } else {
            Log.w(TAG, "トピック登録失敗", task.exception)
        }
    }
```

サーバー側からトピックに送信：

```javascript
const message = {
  notification: {
    title: '新着情報',
    body: '新しい小説が追加されました'
  },
  topic: 'novel_updates'  // トピック名
};

admin.messaging().send(message);
```

### 条件付き送信

特定の条件を満たすデバイスにのみ送信できます。

```javascript
const message = {
  notification: {
    title: 'Android 13以降のユーザーへ',
    body: '新機能が追加されました'
  },
  condition: "'android_13' in topics && 'beta_tester' in topics"
};

admin.messaging().send(message);
```

---

## まとめ

このドキュメントでは、Firebase Cloud Messaging (FCM) を使った通知の送信方法を説明しました。

**重要なポイント:**
1. `google-services.json` を正しく配置する
2. FCMトークンを取得して、サーバーに保存する
3. Firebase Console または Firebase Admin SDK で通知を送信する
4. 通知のデータ形式を正しく設定する
5. アプリ側で通知を受信・表示する処理を実装する

詳細は [Firebase公式ドキュメント](https://firebase.google.com/docs/cloud-messaging) を参照してください。
