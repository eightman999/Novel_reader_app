package com.shunlight_library.novel_reader.utils

import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.SettingsStore
import com.shunlight_library.novel_reader.data.entity.ImageCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 画像のダウンロードとローカルキャッシュを行うユーティリティ。
 */
object ImageCacheUtils {
    private const val TAG = "ImageCacheUtils"

    private fun resolveLocalUri(localPath: String): String {
        return when {
            localPath.startsWith("content://") -> localPath
            localPath.startsWith("file://") -> localPath
            else -> "file://$localPath"
        }
    }

    /**
     * 指定されたURLの画像をダウンロードしてキャッシュし、ローカルURIを返す。
     * 既にハッシュが存在する場合は再利用する。
     */
    suspend fun downloadAndCacheImage(url: String): String? {
        val repository = NovelReaderApplication.getRepository()
        // URL一致で既存データを確認
        repository.getImageByUrl(url)?.let {
            return resolveLocalUri(it.local_path)
        }

        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                val mimeType = connection.contentType ?: return@withContext null
                val bytes = connection.inputStream.use { it.readBytes() }

                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { b -> "%02x".format(b) }

                // ハッシュで既存キャッシュを確認
                repository.getImageByHash(hash)?.let {
                    return@withContext resolveLocalUri(it.local_path)
                }

                val extension = when {
                    mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
                    mimeType.contains("png") -> ".png"
                    mimeType.contains("webp") -> ".webp"
                    mimeType.contains("avif") -> ".avif"
                    else -> return@withContext null
                }

                val context = NovelReaderApplication.getAppContext()
                val settingsStore = SettingsStore(context)

                val storedPath = try {
                    val directoryUriString = settingsStore.getImageSaveLocation()
                    if (directoryUriString.isNotBlank()) {
                        val directoryUri = Uri.parse(directoryUriString)
                        val hasPersistedPermission = context.contentResolver.persistedUriPermissions.any { permission ->
                            permission.uri == directoryUri && permission.isWritePermission
                        }

                        if (!hasPersistedPermission) {
                            throw SecurityException("保存先ディレクトリへの書き込み権限がありません")
                        }

                        val documentDirectory = DocumentFile.fromTreeUri(context, directoryUri)
                        if (documentDirectory != null && documentDirectory.isDirectory && documentDirectory.canWrite()) {
                            val fileName = hash + extension
                            val targetDocument = documentDirectory.findFile(fileName)
                                ?: documentDirectory.createFile(mimeType, fileName)
                            if (targetDocument != null) {
                                context.contentResolver.openOutputStream(targetDocument.uri, "w")?.use { outputStream ->
                                    outputStream.write(bytes)
                                } ?: throw IOException("出力ストリームを開けませんでした")
                                targetDocument.uri.toString()
                            } else {
                                throw IOException("保存先のファイルを作成できませんでした")
                            }
                        } else {
                            throw IOException("保存先のディレクトリにアクセスできません")
                        }
                    } else {
                        null
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "外部保存先への書き込み権限がありません: ${e.message}")
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "選択された保存先への書き込みに失敗: ${e.message}", e)
                    null
                } ?: run {
                    val file = File(context.filesDir, hash + extension)
                    FileOutputStream(file).use { it.write(bytes) }
                    file.absolutePath
                }

                repository.insertImageCache(
                    ImageCacheEntity(
                        hash = hash,
                        original_url = url,
                        local_path = storedPath,
                        mime_type = mimeType
                    )
                )

                resolveLocalUri(storedPath)
            } catch (e: Exception) {
                Log.e(TAG, "画像ダウンロード失敗: ${e.message}", e)
                null
            }
        }
    }
}
