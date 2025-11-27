/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Unit tests for KakuyomuAdapter text cleanup functions.
 */
package com.shunlight_library.novel_reader.data.adapter

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * KakuyomuAdapterのテキストクリーンアップ関数のユニットテスト
 *
 * HTMLスクレイピングで取得したテキストの正しい処理を検証する。
 * これらの関数はWebサイトの変更に影響を受けやすいため、リグレッションテストが重要。
 */
class KakuyomuTextCleanupTest {

    private lateinit var adapter: KakuyomuAdapter

    @Before
    fun setup() {
        adapter = KakuyomuAdapter()
    }

    // ========================================
    // decodeHtmlEntities - HTMLエスケープ文字のデコード
    // ========================================

    @Test
    fun decodeHtmlEntities_ampersand_decodesCorrectly() {
        val input = "これは&amp;です"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("これは&です", result)
    }

    @Test
    fun decodeHtmlEntities_lessThan_decodesCorrectly() {
        val input = "値は&lt;10です"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("値は<10です", result)
    }

    @Test
    fun decodeHtmlEntities_greaterThan_decodesCorrectly() {
        val input = "値は&gt;5です"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("値は>5です", result)
    }

    @Test
    fun decodeHtmlEntities_quotes_decodesCorrectly() {
        val input = "彼は&quot;こんにちは&quot;と言った"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("彼は\"こんにちは\"と言った", result)
    }

    @Test
    fun decodeHtmlEntities_nbsp_decodesCorrectly() {
        val input = "単語&nbsp;の間"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("単語 の間", result)
    }

    @Test
    fun decodeHtmlEntities_yen_decodesCorrectly() {
        val input = "価格は&yen;1000です"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("価格は\\1000です", result)
    }

    @Test
    fun decodeHtmlEntities_brvbar_decodesCorrectly() {
        val input = "A&brvbar;B"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("A|B", result)
    }

    @Test
    fun decodeHtmlEntities_copyright_decodesCorrectly() {
        val input = "Copyright&copy;2025"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("Copyright©2025", result)
    }

    @Test
    fun decodeHtmlEntities_multipleEntities_decodesCorrectly() {
        val input = "&lt;div&gt;テキスト&amp;記号&lt;/div&gt;"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("<div>テキスト&記号</div>", result)
    }

    @Test
    fun decodeHtmlEntities_emptyString_returnsEmpty() {
        val result = adapter.decodeHtmlEntities("")
        assertEquals("", result)
    }

    @Test
    fun decodeHtmlEntities_noEntities_returnsSame() {
        val input = "これは普通のテキストです"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals(input, result)
    }

    // ========================================
    // decodeNumericEntities - 数値エスケープ文字のデコード
    // ========================================

    @Test
    fun decodeNumericEntities_decimalFormat_decodesHiragana() {
        // &#12354; = あ
        val input = "&#12354;いうえお"
        val result = adapter.decodeNumericEntities(input)
        assertEquals("あいうえお", result)
    }

    @Test
    fun decodeNumericEntities_hexFormat_decodesHiragana() {
        // &#x3042; = あ
        val input = "&#x3042;いうえお"
        val result = adapter.decodeNumericEntities(input)
        assertEquals("あいうえお", result)
    }

    @Test
    fun decodeNumericEntities_decimalFormat_decodesKanji() {
        // &#26085; = 日
        val input = "&#26085;本"
        val result = adapter.decodeNumericEntities(input)
        assertEquals("日本", result)
    }

    @Test
    fun decodeNumericEntities_hexFormat_decodesKanji() {
        // &#x65E5; = 日
        val input = "&#x65E5;本"
        val result = adapter.decodeNumericEntities(input)
        assertEquals("日本", result)
    }

    @Test
    fun decodeNumericEntities_multipleEntities_decodesAll() {
        val input = "&#12371;&#12435;&#12395;&#12385;&#12399;"  // こんにちは
        val result = adapter.decodeNumericEntities(input)
        assertEquals("こんにちは", result)
    }

    @Test
    fun decodeNumericEntities_mixedDecimalAndHex_decodesAll() {
        val input = "&#12354;&#x3044;&#12358;&#x3048;"  // あいうえ
        val result = adapter.decodeNumericEntities(input)
        assertEquals("あいうえ", result)
    }

    @Test
    fun decodeNumericEntities_invalidCodePoint_replacesWithQuestionMark() {
        // 無効なコードポイント
        val input = "&#999999999;"
        val result = adapter.decodeNumericEntities(input)
        assertEquals("？", result)
    }

    @Test
    fun decodeNumericEntities_emptyString_returnsEmpty() {
        val result = adapter.decodeNumericEntities("")
        assertEquals("", result)
    }

    @Test
    fun decodeNumericEntities_noEntities_returnsSame() {
        val input = "これは普通のテキストです"
        val result = adapter.decodeNumericEntities(input)
        assertEquals(input, result)
    }

    // ========================================
    // decodeUnicodeEscapes - Unicodeエスケープ文字のデコード
    // ========================================

    @Test
    fun decodeUnicodeEscapes_hiragana_decodesCorrectly() {
        // \u3042 = あ
        val input = "\\u3042いうえお"
        val result = adapter.decodeUnicodeEscapes(input)
        assertEquals("あいうえお", result)
    }

    @Test
    fun decodeUnicodeEscapes_kanji_decodesCorrectly() {
        // \u65E5 = 日
        val input = "\\u65E5本"
        val result = adapter.decodeUnicodeEscapes(input)
        assertEquals("日本", result)
    }

    @Test
    fun decodeUnicodeEscapes_multipleEscapes_decodesAll() {
        val input = "\\u3053\\u3093\\u306B\\u3061\\u306F"  // こんにちは
        val result = adapter.decodeUnicodeEscapes(input)
        assertEquals("こんにちは", result)
    }

    @Test
    fun decodeUnicodeEscapes_mixedWithNormalText_decodesCorrectly() {
        val input = "Hello \\u4E16\\u754C"  // Hello 世界
        val result = adapter.decodeUnicodeEscapes(input)
        assertEquals("Hello 世界", result)
    }

    @Test
    fun decodeUnicodeEscapes_invalidCodePoint_replacesWithQuestionMark() {
        // 無効な形式（4桁でない）は変換されない
        val input = "\\u304"  // 3桁
        val result = adapter.decodeUnicodeEscapes(input)
        assertEquals(input, result)  // 変換されずそのまま
    }

    @Test
    fun decodeUnicodeEscapes_emptyString_returnsEmpty() {
        val result = adapter.decodeUnicodeEscapes("")
        assertEquals("", result)
    }

    @Test
    fun decodeUnicodeEscapes_noEscapes_returnsSame() {
        val input = "これは普通のテキストです"
        val result = adapter.decodeUnicodeEscapes(input)
        assertEquals(input, result)
    }

    // ========================================
    // 統合テスト - 複数のデコード処理の組み合わせ
    // ========================================

    @Test
    fun combinedDecoding_htmlAndNumeric_decodesCorrectly() {
        val input = "&lt;div&gt;&#12354;&#12356;&#12358;&lt;/div&gt;"
        var result = adapter.decodeHtmlEntities(input)
        result = adapter.decodeNumericEntities(result)
        assertEquals("<div>あいう</div>", result)
    }

    @Test
    fun combinedDecoding_allThreeTypes_decodesCorrectly() {
        val input = "&quot;\\u3053\\u3093\\u306B\\u3061\\u306F&quot; &#12392; &#x8A00;&#x3063;&#x305F;"
        var result = adapter.decodeHtmlEntities(input)
        result = adapter.decodeUnicodeEscapes(result)
        result = adapter.decodeNumericEntities(result)
        // "こんにちは" と 言った
        assertTrue(result.contains("こんにちは"))
        assertTrue(result.contains("と"))
        assertTrue(result.contains("言"))
        assertTrue(result.contains("った"))
    }

    @Test
    fun combinedDecoding_realWorldExample_decodesCorrectly() {
        // 実際のカクヨムからのテキストを模したテスト
        val input = "これは&#12486;&#12473;&#12488;です&amp;確認中"
        var result = adapter.decodeHtmlEntities(input)
        result = adapter.decodeNumericEntities(result)
        assertEquals("これはテストです&確認中", result)
    }

    // ========================================
    // エッジケース
    // ========================================

    @Test
    fun decodeHtmlEntities_ampAtEnd_decodesCorrectly() {
        // &amp; は最後に処理されるため、他のエンティティに影響しない
        val input = "&amp;&lt;&amp;&gt;&amp;"
        val result = adapter.decodeHtmlEntities(input)
        assertEquals("&<&>&", result)
    }

    @Test
    fun decodeNumericEntities_partialEntity_notDecoded() {
        // 不完全なエンティティは変換されない
        val input = "&#1234"  // セミコロンなし
        val result = adapter.decodeNumericEntities(input)
        assertEquals(input, result)
    }

    @Test
    fun decodeUnicodeEscapes_partialEscape_notDecoded() {
        // 不完全なエスケープは変換されない
        val input = "\\u304"  // 4桁でない
        val result = adapter.decodeUnicodeEscapes(input)
        assertEquals(input, result)
    }

    @Test
    fun decodeNumericEntities_zeroCodePoint_decodesCorrectly() {
        // コードポイント0は特殊だが処理できるべき
        val input = "&#0;"
        val result = adapter.decodeNumericEntities(input)
        // Null文字が含まれる（表示されないが存在する）
        assertTrue(result.contains('\u0000'))
    }
}
