package com.shunlight_library.novel_reader.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.data.database.NovelDatabase
import com.shunlight_library.novel_reader.data.entity.ImageCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Arrays
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** DBと画像キャッシュを1つの暗号化アーカイブとして保存・復元する。 */
class EncryptedBackupManager(private val context: Context) {
    data class Result(
        val success: Boolean,
        val imageCount: Int = 0,
        val syncResult: ImprovedDatabaseSyncManager.SyncResult? = null,
        val errorMessage: String? = null
    )

    companion object {
        private const val DB_ENTRY = "novel_database"
        private const val IMAGE_PREFIX = "images/"
        private val HASH_PATTERN = Regex("[0-9a-f]{64}")
        private const val MAX_IMAGES = 50_000
        private const val MAX_IMAGE_BYTES = 128L * 1024 * 1024
        private const val MAX_DB_BYTES = 16L * 1024 * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 24L * 1024 * 1024 * 1024
    }

    suspend fun export(destination: Uri, password: CharArray): Result = withContext(Dispatchers.IO) {
        val dbSnapshot = File(context.cacheDir, "novel-db-${UUID.randomUUID()}.db")
        val encrypted = File(context.cacheDir, "novel-backup-${UUID.randomUUID()}.nrbackup")
        try {
            createDbSnapshot(dbSnapshot)
            check(dbSnapshot.length() in 1..MAX_DB_BYTES) { "DBのサイズが上限を超えています" }

            val images = readImageRecords(dbSnapshot)
            check(images.size <= MAX_IMAGES) { "画像の件数が上限を超えています" }
            FileOutputStream(encrypted).use { output ->
                EncryptedBackupCrypto.writeEncryptedZip(output, password) { zip ->
                    addFile(zip, DB_ENTRY, FileInputStream(dbSnapshot), MAX_DB_BYTES)
                    var totalBytes = dbSnapshot.length()
                    for (image in images) {
                        check(HASH_PATTERN.matches(image.hash)) { "画像キャッシュのハッシュが不正です" }
                        val digest = MessageDigest.getInstance("SHA-256")
                        zip.putNextEntry(ZipEntry("$IMAGE_PREFIX${image.hash}"))
                        val bytes = openImage(image).use { input ->
                            copyBounded(input, zip, MAX_IMAGE_BYTES, digest)
                        }
                        zip.closeEntry()
                        check(toHex(digest.digest()) == image.hash) { "画像キャッシュが破損しています: ${image.hash}" }
                        totalBytes += bytes
                        check(totalBytes <= MAX_ARCHIVE_BYTES) { "バックアップのサイズが上限を超えています" }
                    }
                }
            }
            FileInputStream(encrypted).use { input ->
                context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    copyBounded(input, output, MAX_ARCHIVE_BYTES)
                } ?: error("保存先を開けませんでした")
            }
            Result(success = true, imageCount = images.size)
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(destination, null, null) }
            Result(success = false, errorMessage = e.message ?: "暗号化バックアップの作成に失敗しました")
        } finally {
            Arrays.fill(password, '\u0000')
            dbSnapshot.delete()
            encrypted.delete()
        }
    }

    suspend fun restore(source: Uri, password: CharArray): Result = withContext(Dispatchers.IO) {
        val encrypted = File(context.cacheDir, "novel-restore-${UUID.randomUUID()}.nrbackup")
        val archive = File(context.cacheDir, "novel-restore-${UUID.randomUUID()}.zip")
        val dbSnapshot = File(context.cacheDir, "novel-restore-${UUID.randomUUID()}.db")
        var dbMerged = false
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(encrypted).use { output -> copyBounded(input, output, MAX_ARCHIVE_BYTES) }
            } ?: error("バックアップを開けませんでした")
            FileInputStream(encrypted).use { input ->
                FileOutputStream(archive).use { output ->
                    EncryptedBackupCrypto.decrypt(input, output, password, MAX_ARCHIVE_BYTES)
                }
            }

            ZipFile(archive).use { zip ->
                validateEntries(zip)
                val dbEntry = zip.getEntry(DB_ENTRY) ?: error("バックアップにDBがありません")
                zip.getInputStream(dbEntry).use { input ->
                    FileOutputStream(dbSnapshot).use { output -> copyBounded(input, output, MAX_DB_BYTES) }
                }
                val images = readImageRecords(dbSnapshot)
                check(images.size <= MAX_IMAGES) { "画像の件数が上限を超えています" }
                // DBを変更する前にすべての画像を読み、欠落と改ざんを確認する。
                var totalBytes = dbSnapshot.length()
                for (image in images) {
                    extensionForMime(image.mime_type)
                    val entry = zip.getEntry("$IMAGE_PREFIX${image.hash}")
                        ?: error("画像が欠けています: ${image.hash}")
                    val digest = MessageDigest.getInstance("SHA-256")
                    val imageBytes = zip.getInputStream(entry).use { input ->
                        copyBounded(input, NullOutputStream, MAX_IMAGE_BYTES, digest)
                    }
                    check(toHex(digest.digest()) == image.hash) { "画像が破損しています: ${image.hash}" }
                    totalBytes += imageBytes
                    check(totalBytes <= MAX_ARCHIVE_BYTES) { "展開後の画像がサイズ上限を超えました" }
                }

                val sync = ImprovedDatabaseSyncManager(context)
                    .syncFromExternalDb(Uri.fromFile(dbSnapshot))
                check(sync.success) { sync.errorMessage ?: "DBの復元に失敗しました" }
                dbMerged = true

                val imageDir = File(context.filesDir, "backup_images").apply { mkdirs() }
                for (image in images) {
                    val extension = extensionForMime(image.mime_type)
                    val restored = File(imageDir, image.hash + extension)
                    val partial = File(imageDir, image.hash + extension + ".part")
                    try {
                        zip.getInputStream(zip.getEntry("$IMAGE_PREFIX${image.hash}")).use { input ->
                            FileOutputStream(partial).use { output -> copyBounded(input, output, MAX_IMAGE_BYTES) }
                        }
                        check(partial.renameTo(restored)) { "画像の配置に失敗しました" }
                    } finally {
                        partial.delete()
                    }
                    NovelReaderApplication.getRepository().insertImageCache(
                        image.copy(local_path = restored.absolutePath)
                    )
                }
                Result(success = true, imageCount = images.size, syncResult = sync)
            }
        } catch (e: Exception) {
            val detail = e.message ?: "バックアップの復元に失敗しました"
            Result(success = false, errorMessage = if (dbMerged) "DBの復元後に画像の配置が失敗しました。再実行してください: $detail" else detail)
        } finally {
            Arrays.fill(password, '\u0000')
            dbSnapshot.delete()
            archive.delete()
            encrypted.delete()
        }
    }

    private fun readImageRecords(dbFile: File): List<ImageCacheEntity> =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT hash, original_url, local_path, mime_type FROM image_cache", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val hash = cursor.getString(0)
                        check(HASH_PATTERN.matches(hash)) { "画像ハッシュが不正です" }
                        add(ImageCacheEntity(hash, cursor.getString(1), cursor.getString(2), cursor.getString(3)))
                        check(size <= MAX_IMAGES) { "画像の件数が上限を超えています" }
                    }
                }
            }
        }

    private suspend fun createDbSnapshot(destination: File) {
        val db = NovelDatabase.getDatabase(context).openHelper.writableDatabase
        val version = db.query("SELECT sqlite_version()").use { cursor ->
            check(cursor.moveToFirst()) { "SQLiteのバージョンを取得できません" }
            cursor.getString(0).split('.').mapNotNull(String::toIntOrNull)
        }
        val supportsVacuumInto = version.size >= 2 &&
            (version[0] > 3 || version[0] == 3 && version[1] >= 27)
        if (supportsVacuumInto) {
            check(!destination.exists() || destination.delete()) { "一時DBを準備できません" }
            val escapedPath = destination.absolutePath.replace("'", "''")
            db.execSQL("VACUUM INTO '$escapedPath'")
        } else {
            val export = DatabaseExportManager(context).exportDatabase(Uri.fromFile(destination))
            check(export.success) { export.errorMessage ?: "DBの書き出しに失敗しました" }
        }
    }

    private fun validateEntries(zip: ZipFile) {
        val seen = mutableSetOf<String>()
        for (entry in zip.entries().asSequence()) {
            check(!entry.isDirectory && seen.add(entry.name)) { "重複または不正なZIPエントリです" }
            check(entry.name == DB_ENTRY ||
                (entry.name.startsWith(IMAGE_PREFIX) && HASH_PATTERN.matches(entry.name.removePrefix(IMAGE_PREFIX)))) {
                "不正なZIPエントリ名です"
            }
            check(seen.size <= MAX_IMAGES + 1) { "ZIPエントリ数が上限を超えています" }
        }
    }

    private fun addFile(zip: ZipOutputStream, name: String, input: InputStream, maxBytes: Long) {
        zip.putNextEntry(ZipEntry(name))
        input.use { copyBounded(it, zip, maxBytes) }
        zip.closeEntry()
    }

    private fun openImage(image: ImageCacheEntity): InputStream = when {
        image.local_path.startsWith("content://") ->
            context.contentResolver.openInputStream(Uri.parse(image.local_path)) ?: error("画像を開けません: ${image.hash}")
        image.local_path.startsWith("file://") ->
            FileInputStream(Uri.parse(image.local_path).path ?: error("画像のパスが不正です"))
        else -> FileInputStream(image.local_path)
    }

    private fun extensionForMime(mime: String): String = when {
        mime.contains("jpeg", true) || mime.contains("jpg", true) -> ".jpg"
        mime.contains("png", true) -> ".png"
        mime.contains("webp", true) -> ".webp"
        mime.contains("avif", true) -> ".avif"
        else -> error("未対応の画像形式です")
    }

    private fun copyBounded(
        input: InputStream,
        output: OutputStream,
        limit: Long,
        digest: MessageDigest? = null
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= limit) { "ファイルのサイズが上限を超えています" }
            digest?.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private object NullOutputStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }
}
