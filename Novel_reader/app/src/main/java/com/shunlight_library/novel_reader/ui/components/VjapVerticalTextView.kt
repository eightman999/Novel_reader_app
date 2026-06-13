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
 * 適用済み設定と読書位置の追跡。
 * 再コンポーズによる不要な再レイアウト（＝ページ位置の喪失）を防ぎ、
 * 設定変更時はページ計算完了後に直前の位置を復元する。
 */
private class VjapAppliedState(initialRate: Float) {
    var fontSizePx = -1
    var fontColor = ""
    var backgroundColor = ""
    var fontPath: String? = null
    var content: String? = null
    var title: String? = null

    /** 次のページ計算完了時に復元する進捗率 */
    var restoreRate = initialRate

    /** 現在の進捗率（ページ送りのたびに更新） */
    var currentRate = initialRate
}

/**
 * vjapライブラリを使用した縦書きテキスト表示コンポーザブル。
 *
 * - fontSizeはspで受け取り、内部でpx換算してvjapに渡す
 * - スワイプ無効・タップでページ送りのページ分割モードで動作
 * - 読書進捗はページ番号/総ページ数（0.0〜1.0）で保存・復元
 * - ページをめくるたびに onReadingRateChanged で進捗率を通知
 * - フォントサイズ・色・フォントの変更時も現在の読書位置を維持する
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

    val applied = remember { VjapAppliedState(savedReadingRate) }

    AndroidView(
        factory = { context ->
            val density = context.resources.displayMetrics.scaledDensity
            val fontSizePx = (fontSize * density).toInt().coerceAtLeast(12)

            VTextLayout(context).apply {
                setVirtical(true)

                // スワイプ無効 → タップでページ送りのページ分割モード
                setScrollDisabled(true)

                // ページ計算完了後に保存済み（または設定変更直前の）ページを復元
                setOnReadyListener { totalPage ->
                    if (totalPage > 0 && applied.restoreRate > 0f) {
                        val targetPage = Math.round(applied.restoreRate * totalPage).coerceIn(1, totalPage)
                        setCurrentPage(targetPage)
                    }
                    val rate = if (totalPage > 0) getCurrentPage().toFloat() / totalPage else 0f
                    applied.currentRate = rate.coerceIn(0f, 1f)
                    onReadingRateChanged(applied.currentRate)
                }

                // ページ送りのたびに進捗率を通知（読書位置の保存用）
                setOnPageChangedListener { page, totalPage ->
                    if (totalPage > 0) {
                        applied.currentRate = (page.toFloat() / totalPage).coerceIn(0f, 1f)
                        onReadingRateChanged(applied.currentRate)
                    }
                }

                setOnPageEndListener {
                    if (getTotalPage() > 0) {
                        applied.currentRate = 1f
                        onReadingRateChanged(1f)
                    }
                }

                setFontSize(fontSizePx)
                try {
                    setColor(fontColor, backgroundColor)
                } catch (e: Exception) {
                    setColor("#000000", "#FFFFFF")
                }
                if (!customFontPath.isNullOrEmpty()) {
                    vTextView.setFont(customFontPath)
                }
                initContent(episodeTitle, aozoraText)

                applied.fontSizePx = fontSizePx
                applied.fontColor = fontColor
                applied.backgroundColor = backgroundColor
                applied.fontPath = customFontPath
                applied.content = aozoraText
                applied.title = episodeTitle
            }
        },
        update = { layout ->
            val density = layout.resources.displayMetrics.scaledDensity
            val fontSizePx = (fontSize * density).toInt().coerceAtLeast(12)

            val contentChanged = applied.content != aozoraText || applied.title != episodeTitle
            val settingsChanged = applied.fontSizePx != fontSizePx ||
                    applied.fontColor != fontColor ||
                    applied.backgroundColor != backgroundColor ||
                    applied.fontPath != customFontPath

            // 変更がある場合のみ適用（無条件に呼ぶと再レイアウトで読書位置が失われる）
            if (contentChanged || settingsChanged) {
                // 再レイアウト後の復元位置: 別エピソードなら保存済み位置、設定変更なら現在位置
                applied.restoreRate = if (contentChanged) savedReadingRate else applied.currentRate

                if (applied.fontSizePx != fontSizePx) {
                    layout.setFontSize(fontSizePx)
                    applied.fontSizePx = fontSizePx
                }
                if (applied.fontColor != fontColor || applied.backgroundColor != backgroundColor) {
                    try {
                        layout.setColor(fontColor, backgroundColor)
                    } catch (e: Exception) {
                        layout.setColor("#000000", "#FFFFFF")
                    }
                    applied.fontColor = fontColor
                    applied.backgroundColor = backgroundColor
                }
                if (applied.fontPath != customFontPath) {
                    layout.vTextView.setFont(if (customFontPath.isNullOrEmpty()) null else customFontPath)
                    applied.fontPath = customFontPath
                }
                if (contentChanged) {
                    layout.initContent(episodeTitle, aozoraText)
                    applied.content = aozoraText
                    applied.title = episodeTitle
                }
            }
        },
        modifier = modifier
    )
}
