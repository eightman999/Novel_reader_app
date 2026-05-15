package com.shunlight_library.novel_reader.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTMLコンテンツをvjapライブラリが使用する青空文庫風テキスト形式に変換する。
 *
 * 変換ルール:
 * - <ruby>本文<rt>ルビ</rt></ruby> → |本文《ルビ》
 * - <p>...</p> → テキスト + 改行
 * - <br> → 改行
 * - その他HTMLタグ → テキストのみ抽出
 */
object VjapTextConverter {

    fun convertHtmlToAozora(html: String): String {
        if (html.isBlank()) return ""

        val doc = Jsoup.parseBodyFragment(html)
        val sb = StringBuilder()
        processNode(doc.body(), sb)
        return sb.toString().trimEnd()
    }

    private fun processNode(node: Node, sb: StringBuilder) {
        when {
            node is TextNode -> {
                sb.append(node.text())
            }
            node is Element -> {
                when (node.tagName().lowercase()) {
                    "ruby" -> processRubyElement(node, sb)
                    "rt" -> { /* handled inside ruby */ }
                    "p" -> {
                        val para = StringBuilder()
                        processChildren(node, para)
                        // 段落先頭の字下げ（　）を除去
                        sb.append(para.toString().removePrefix("　"))
                        sb.append("\n")
                    }
                    "br" -> sb.append("\n")
                    "img" -> { /* skip images */ }
                    else -> processChildren(node, sb)
                }
            }
        }
    }

    private fun processRubyElement(ruby: Element, sb: StringBuilder) {
        val rtElement = ruby.selectFirst("rt")
        val rubyText = rtElement?.text()?.trim() ?: ""

        // collect base text (all non-rt text nodes and elements)
        val baseText = StringBuilder()
        for (child in ruby.childNodes()) {
            if (child is Element && child.tagName().equals("rt", ignoreCase = true)) continue
            if (child is Element && child.tagName().equals("rb", ignoreCase = true)) {
                baseText.append(child.text())
            } else if (child is TextNode) {
                baseText.append(child.text())
            }
        }

        val base = baseText.toString().trim()
        if (base.isNotEmpty() && rubyText.isNotEmpty()) {
            sb.append("|").append(base).append("《").append(rubyText).append("》")
        } else if (base.isNotEmpty()) {
            sb.append(base)
        }
    }

    private fun processChildren(element: Element, sb: StringBuilder) {
        for (child in element.childNodes()) {
            processNode(child, sb)
        }
    }
}
