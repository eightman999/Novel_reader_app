package com.shunlight_library.novel_reader.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 自己サーバーのディレクトリを選択するためのコンポーネント
 * @param currentPath 現在設定されているパス
 * @param onPathSelected パスが選択された時のコールバック
 */
@Composable
fun ServerDirectorySelector(
    currentPath: String,
    onPathSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ディレクトリ選択のランチャー
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // URIからパスを取得
                val path = uri.toString()

                // 永続的な権限を取得
                val contentResolver = context.contentResolver
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                try {
                    // 永続的な権限を付与
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d("ServerDirectorySelector", "取得した永続的なアクセス権限: $path")

                    // 成功メッセージをトーストで表示
                    Toast.makeText(context, "ディレクトリへのアクセス権限を取得しました", Toast.LENGTH_SHORT).show()

                    // 選択されたURIをコールバックで通知
                    onPathSelected(uri)
                } catch (e: Exception) {
                    Log.e("ServerDirectorySelector", "権限取得エラー: ${e.message}", e)
                    Toast.makeText(context, "アクセス権限の取得に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 選択されたパスがある場合は表示
        if (currentPath.isNotEmpty()) {
            Text(
                text = "選択されたディレクトリ: $currentPath",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Text(
            text = "小説サーバーのrootディレクトリを選択してください。\nindex.htmlファイルおよびnovelsディレクトリが含まれるフォルダを選んでください。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Button(
            onClick = {
                // ディレクトリ選択インテントを起動
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    // ドキュメントツリー全体への永続的な権限を要求
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                directoryPickerLauncher.launch(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("ディレクトリを選択")
        }
    }
}