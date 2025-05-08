package com.shunlight_library.novel_reader.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * フォント関連のユーティリティクラス
 */
object FontUtils {
    private const val TAG = "FontUtils"
    private const val FONTS_DIR = "custom_fonts"

    /**
     * 内蔵フォントの一覧
     */
    val BUILT_IN_FONTS = mapOf(
        "Gothic" to "sans-serif",
        "Mincho" to "serif",
        "Rounded" to "'M PLUS Rounded 1c', sans-serif",
        "Handwriting" to "'Hannari Mincho', serif"
    )

    /**
     * サポートするフォント形式
     */
    val SUPPORTED_FONT_TYPES = listOf("ttf", "ttc", "otf")

    /**
     * カスタムフォントの情報クラス
     */
    data class CustomFont(
        val id: String,        // 一意のID
        val name: String,      // 表示名
        val filePath: String,  // 内部ストレージ上のパス
        val fontType: String   // フォント形式（ttf/ttc/otf）
    )

    /**
     * カスタムフォントをロードする
     * @param context コンテキスト
     * @param fontPath 内部ストレージ上のフォントファイルパス
     * @return Typefaceオブジェクト、失敗した場合はnull
     */
    fun loadCustomFont(context: Context, fontPath: String): Typeface? {
        return try {
            val file = File(fontPath)
            if (file.exists()) {
                Typeface.createFromFile(file)
            } else {
                Log.e(TAG, "カスタムフォントファイルが見つかりません: $fontPath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "カスタムフォントのロードに失敗しました: $fontPath", e)
            null
        }
    }

    /**
     * URIからフォントファイルを内部ストレージにコピーする
     * @param context コンテキスト
     * @param uri フォントファイルのURI
     * @return コピーしたフォントの情報、失敗した場合はnull
     */
    fun importFontFromUri(context: Context, uri: Uri): CustomFont? {
        try {
            // フォント形式を確認
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            val fileName = documentFile?.name ?: return null
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())

            if (extension !in SUPPORTED_FONT_TYPES) {
                Log.e(TAG, "未対応のフォント形式です: $fileName")
                return null
            }

            // フォント名を取得（拡張子を除く）
            val fontNameWithoutExt = fileName.substringBeforeLast(".")
            val fontId = UUID.randomUUID().toString()

            // フォントディレクトリを用意
            val fontDir = File(context.filesDir, FONTS_DIR)
            if (!fontDir.exists()) {
                fontDir.mkdirs()
            }

            // フォントファイルを内部ストレージにコピー
            val fontFile = File(fontDir, "${fontId}.${extension}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(fontFile).use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                Log.e(TAG, "ファイルの読み込みに失敗しました: $uri")
                return null
            }

            // カスタムフォント情報を作成して返す
            return CustomFont(
                id = fontId,
                name = fontNameWithoutExt,
                filePath = fontFile.absolutePath,
                fontType = extension
            )
        } catch (e: Exception) {
            Log.e(TAG, "フォントのインポートに失敗しました: ${e.message}", e)
            return null
        }
    }

    /**
     * 保存されているすべてのカスタムフォントを取得する
     * @param context コンテキスト
     * @param fontInfoList SettingsStoreから取得したカスタムフォント情報
     * @return カスタムフォントのリスト
     */
    fun getAllCustomFonts(context: Context, fontInfoList: List<com.shunlight_library.novel_reader.CustomFontInfo>): List<CustomFont> {
        val fontDir = File(context.filesDir, FONTS_DIR)
        if (!fontDir.exists() || fontInfoList.isEmpty()) {
            return emptyList()
        }

        // SettingsStoreの情報からCustomFontオブジェクトに変換
        return fontInfoList.mapNotNull { info ->
            val file = File(info.path)
            if (file.exists()) {
                CustomFont(
                    id = info.id,
                    name = info.name,
                    filePath = info.path,
                    fontType = info.type
                )
            } else {
                null
            }
        }
    }

    /**
     * カスタムフォントを削除する
     * @param context コンテキスト
     * @param fontPath 削除するフォントのパス
     * @return 削除に成功したかどうか
     */
    fun deleteCustomFont(context: Context, fontPath: String): Boolean {
        try {
            val file = File(fontPath)
            return file.exists() && file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "フォントの削除に失敗しました: $fontPath", e)
            return false
        }
    }

    /**
     * フォント名がカスタムフォントかどうかをチェック
     * @param fontName フォント名
     * @return カスタムフォントかどうか
     */
    fun isCustomFont(fontName: String): Boolean {
        return !BUILT_IN_FONTS.containsKey(fontName)
    }

    /**
     * フォント名をCSSのfont-familyに変換する
     * @param fontName フォント名
     * @param isCustomFont カスタムフォントかどうか
     * @return CSSのfont-family値
     */
    fun fontNameToCssFontFamily(fontName: String, isCustomFont: Boolean = false): String {
        return if (isCustomFont) {
            "CustomFont, sans-serif" // カスタムフォントの場合
        } else {
            BUILT_IN_FONTS[fontName] ?: "sans-serif" // 内蔵フォントの場合
        }
    }

    /**
     * カスタムフォントのCSSを生成する
     * @param fontPath カスタムフォントのファイルパス
     * @return @font-faceを含むCSS
     */
    fun generateCustomFontCss(fontPath: String): String {
        val file = File(fontPath)
        if (!file.exists()) {
            return ""
        }

        val extension = file.name.substringAfterLast('.', "").lowercase(Locale.getDefault())
        val format = when (extension) {
            "ttf" -> "truetype"
            "otf" -> "opentype"
            "ttc" -> "truetype"
            else -> "truetype"
        }

        return """
        @font-face {
            font-family: 'CustomFont';
            src: url('file://${file.absolutePath}') format('$format');
            font-weight: normal;
            font-style: normal;
        }
        """.trimIndent()
    }
}