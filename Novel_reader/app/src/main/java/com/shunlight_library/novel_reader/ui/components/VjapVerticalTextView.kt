/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Compose wrapper for the vjap vertical Japanese text library.
 */
package com.shunlight_library.novel_reader.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.shunlight_library.novel_reader.utils.VjapTextConverter
import jp.taizan.android.vjap.VTextLayout

/**
 * vjapライブラリを使用した縦書きテキスト表示コンポーザブル。
 *
 * - fontSizeはspで受け取り、内部でpx換算してvjapに渡す
 * - スワイプ無効・タップでページ送りのページ分割モードで動作
 * - 読書進捗はページ番号/総ページ数（0.0〜1.0）で保存・復元
 */
@Composable
fun VjapVerticalTextView(
    htmlContent: String,
    episodeTitle: String,
    fontSize: Int = 18,
    fontColor: String = "#000000",
    backgroundColor: String = "#FFFFFF",
    customFontPath: String? = null,
    savedReadingRate: Float = 0f,
    onReadingRateChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val aozoraText = remember(htmlContent) {
        VjapTextConverter.convertHtmlToAozora(htmlContent)
    }

    val savedRateRef = remember(savedReadingRate) { savedReadingRate }

    AndroidView(
        factory = { context ->
            val density = context.resources.displayMetrics.scaledDensity
            val fontSizePx = (fontSize * density).toInt().coerceAtLeast(12)

            VTextLayout(context).apply {
                setVirtical(true)

                // スワイプ無効 → タップでページ送りのページ分割モード
                setScrollDisabled(true)

                setFontSize(fontSizePx)
                try {
                    setColor(fontColor, backgroundColor)
                } catch (e: Exception) {
                    setColor("#000000", "#FFFFFF")
                }
                if (!customFontPath.isNullOrEmpty()) {
                    vTextView.setFont(customFontPath)
                }

                // ページ計算完了後に保存済みページを復元
                setOnReadyListener { totalPage ->
                    if (totalPage > 0 && savedRateRef > 0f) {
                        val targetPage = (savedRateRef * totalPage).toInt().coerceIn(1, totalPage)
                        setCurrentPage(targetPage)
                    }
                    val rate = if (totalPage > 0) getCurrentPage().toFloat() / totalPage.toFloat() else 0f
                    onReadingRateChanged(rate.coerceIn(0f, 1f))
                }

                setOnPageEndListener {
                    val total = getTotalPage()
                    if (total > 0) onReadingRateChanged(1f)
                }

                initContent(episodeTitle, aozoraText)
            }
        },
        update = { layout ->
            // フォントサイズ・色など設定変更のみ反映（コンテンツは変わらない）
            val density = layout.resources.displayMetrics.scaledDensity
            val fontSizePx = (fontSize * density).toInt().coerceAtLeast(12)
            layout.setFontSize(fontSizePx)
            try {
                layout.setColor(fontColor, backgroundColor)
            } catch (e: Exception) {
                layout.setColor("#000000", "#FFFFFF")
            }
            if (!customFontPath.isNullOrEmpty()) {
                layout.vTextView.setFont(customFontPath)
            } else {
                layout.vTextView.setFont(null)
            }
        },
        modifier = modifier
    )
}
