package com.shunlight_library.novel_reader.utils

import android.util.Log
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.data.entity.ImageCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 画像のダウンロードとローカルキャッシュを行うユーティリティ。
 */
object ImageCacheUtils {
    private const val TAG = "ImageCacheUtils"

    /**
     * 指定されたURLの画像をダウンロードしてキャッシュし、ローカルURIを返す。
     * 既にハッシュが存在する場合は再利用する。
     */
    suspend fun downloadAndCacheImage(url: String): String? {
        val repository = NovelReaderApplication.getRepository()
        // URL一致で既存データを確認
        repository.getImageByUrl(url)?.let {
            return "file://${it.local_path}"
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
                    return@withContext "file://${it.local_path}"
                }

                val extension = when {
                    mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
                    mimeType.contains("png") -> ".png"
                    mimeType.contains("webp") -> ".webp"
                    mimeType.contains("avif") -> ".avif"
                    else -> return@withContext null
                }

                val context = NovelReaderApplication.getAppContext()
                val file = File(context.filesDir, hash + extension)
                FileOutputStream(file).use { it.write(bytes) }

                repository.insertImageCache(
                    ImageCacheEntity(
                        hash = hash,
                        original_url = url,
                        local_path = file.absolutePath,
                        mime_type = mimeType
                    )
                )

                "file://${file.absolutePath}"
            } catch (e: Exception) {
                Log.e(TAG, "画像ダウンロード失敗: ${e.message}", e)
                null
            }
        }
    }
}
