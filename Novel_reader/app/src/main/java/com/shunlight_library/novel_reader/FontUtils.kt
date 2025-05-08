// FontUtils.kt

package com.shunlight_library.novel_reader.utils

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * フォント関連のユーティリティクラス
 */
object FontUtils {
    private const val TAG = "FontUtils"

    /**
     * 内蔵フォントの一覧
     */
    val BUILT_IN_FONTS = mapOf(
        "Gothic" to "fonts/NotoSansJP-Regular.ttf",
        "Mincho" to "fonts/NotoSerifJP-Regular.ttf",
        "Rounded" to "fonts/MPLUSRounded1c-Regular.ttf",
        "Handwriting" to "fonts/HannariMincho-Regular.ttf"
    )

    /**
     * 内蔵フォントをアセットからロードする
     * @param context コンテキスト
     * @param fontName フォント名（BUILT_IN_FONTSのキー）
     * @return Typefaceオブジェクト、失敗した場合はnull
     */
    fun loadBuiltInFont(context: Context, fontName: String): Typeface? {
        val fontPath = BUILT_IN_FONTS[fontName] ?: return null

        return try {
            Typeface.createFromAsset(context.assets, fontPath)
        } catch (e: Exception) {
            Log.e(TAG, "内蔵フォントのロードに失敗しました: $fontName, $fontPath", e)
            null
        }
    }

    /**
     * 外部フォントファイルをロードする
     * @param context コンテキスト
     * @param fontPath フォントファイルのパス（ローカルファイルシステムまたはコンテンツURI）
     * @return Typefaceオブジェクト、失敗した場合はnull
     */
    fun loadExternalFont(context: Context, fontPath: String): Typeface? {
        return try {
            if (fontPath.startsWith("content://")) {
                // コンテンツURIの場合は一時ファイルにコピーしてからロード
                val uri = android.net.Uri.parse(fontPath)
                val tempFile = File(context.cacheDir, "temp_font_${System.currentTimeMillis()}.ttf")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val typeface = Typeface.createFromFile(tempFile)
                // 処理後は一時ファイルを削除するが、Typefaceは有効なまま
                tempFile.delete()
                typeface
            } else {
                // 通常のファイルパスの場合はそのままロード
                Typeface.createFromFile(fontPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "外部フォントのロードに失敗しました: $fontPath", e)
            null
        }
    }

    /**
     * フォント名をCSSのfont-familyに変換する
     * @param fontName フォント名またはカスタムフォントのパス
     * @return CSSのfont-family値
     */
    fun fontNameToCssFontFamily(fontName: String, context: Context): String {
        return when (fontName) {
            "Gothic" -> "sans-serif"
            "Mincho" -> "serif"
            "Rounded" -> "'M PLUS Rounded 1c', sans-serif"
            "Handwriting" -> "'Hannari Mincho', serif"
            else -> {
                // カスタムフォントの場合は、WebViewにフォントを登録するか既定のフォントを使用
                "sans-serif"
            }
        }
    }
}