/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Unit tests for Base62Converter.
 */
package com.shunlight_library.novel_reader.utils

import org.junit.Test
import org.junit.Assert.*
import java.math.BigInteger

/**
 * Base62Converterのユニットテスト
 *
 * Base62エンコード/デコードの正しさを検証する。
 * カクヨムのwork ID変換に使用されるため、重要なテストクラス。
 */
class Base62ConverterTest {

    // ========================================
    // エンコード - 基本テスト
    // ========================================

    @Test
    fun encode_zero_returnsZero() {
        val result = Base62Converter.encode(0)
        assertEquals("0", result)
    }

    @Test
    fun encode_one_returnsOne() {
        val result = Base62Converter.encode(1)
        assertEquals("1", result)
    }

    @Test
    fun encode_smallNumber_returnsCorrectString() {
        val result = Base62Converter.encode(61)
        assertEquals("z", result)
    }

    @Test
    fun encode_largeNumber_returnsCorrectString() {
        // 62 = 10 in base62
        val result = Base62Converter.encode(62)
        assertEquals("10", result)
    }

    @Test
    fun encode_veryLargeNumber_handlesLongMaxValue() {
        val result = Base62Converter.encode(Long.MAX_VALUE)
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // Long.MAX_VALUE = 9223372036854775807
        // Base62でエンコードすると "AzL8n0Y58m7" となる
        assertEquals("AzL8n0Y58m7", result)
    }

    // ========================================
    // デコード - 基本テスト
    // ========================================

    @Test
    fun decode_zero_returnsZero() {
        val result = Base62Converter.decode("0")
        assertEquals(0L, result)
    }

    @Test
    fun decode_one_returnsOne() {
        val result = Base62Converter.decode("1")
        assertEquals(1L, result)
    }

    @Test
    fun decode_singleChar_returnsCorrectNumber() {
        val result = Base62Converter.decode("z")
        assertEquals(61L, result)
    }

    @Test
    fun decode_twoChars_returnsCorrectNumber() {
        val result = Base62Converter.decode("10")
        assertEquals(62L, result)
    }

    // ========================================
    // ラウンドトリップテスト
    // ========================================

    @Test
    fun encode_decode_roundTrip_smallNumbers() {
        val testNumbers = listOf(0L, 1L, 10L, 61L, 62L, 100L, 999L, 1234L)

        testNumbers.forEach { number ->
            val encoded = Base62Converter.encode(number)
            val decoded = Base62Converter.decode(encoded)
            assertEquals("Failed for number: $number", number, decoded)
        }
    }

    @Test
    fun encode_decode_roundTrip_largeNumbers() {
        val testNumbers = listOf(
            10000L,
            999999L,
            1000000L,
            123456789L,
            9876543210L,
            Long.MAX_VALUE
        )

        testNumbers.forEach { number ->
            val encoded = Base62Converter.encode(number)
            val decoded = Base62Converter.decode(encoded)
            assertEquals("Failed for number: $number", number, decoded)
        }
    }

    // ========================================
    // BigInteger - 基本テスト
    // ========================================

    @Test
    fun encodeBigInteger_zero_returnsZero() {
        val result = Base62Converter.encodeBigInteger(BigInteger.ZERO)
        assertEquals("0", result)
    }

    @Test
    fun encodeBigInteger_one_returnsOne() {
        val result = Base62Converter.encodeBigInteger(BigInteger.ONE)
        assertEquals("1", result)
    }

    @Test
    fun encodeBigInteger_kakuyomuWorkId_returnsCorrectString() {
        // 実際のカクヨムwork ID（19桁の数値）
        val workId = BigInteger("1177354054887277844")
        val result = Base62Converter.encodeBigInteger(workId)
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // 実際のエンコード結果を確認（デコードで逆変換できればOK）
    }

    @Test
    fun decodeBigInteger_zero_returnsZero() {
        val result = Base62Converter.decodeBigInteger("0")
        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun decodeBigInteger_one_returnsOne() {
        val result = Base62Converter.decodeBigInteger("1")
        assertEquals(BigInteger.ONE, result)
    }

    // ========================================
    // BigInteger - ラウンドトリップテスト
    // ========================================

    @Test
    fun encodeBigInteger_decodeBigInteger_roundTrip_smallNumbers() {
        val testNumbers = listOf(
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.TEN,
            BigInteger.valueOf(61),
            BigInteger.valueOf(62),
            BigInteger.valueOf(1234567890)
        )

        testNumbers.forEach { number ->
            val encoded = Base62Converter.encodeBigInteger(number)
            val decoded = Base62Converter.decodeBigInteger(encoded)
            assertEquals("Failed for number: $number", number, decoded)
        }
    }

    @Test
    fun encodeBigInteger_decodeBigInteger_roundTrip_kakuyomuWorkIds() {
        // 実際のカクヨムwork IDのパターン（19桁）
        val testWorkIds = listOf(
            "1177354054887277844",
            "1177354054887854131",
            "1234567890123456789",
            "9999999999999999999"
        )

        testWorkIds.forEach { workIdStr ->
            val workId = BigInteger(workIdStr)
            val encoded = Base62Converter.encodeBigInteger(workId)
            val decoded = Base62Converter.decodeBigInteger(encoded)
            assertEquals("Failed for work ID: $workIdStr", workId, decoded)
        }
    }

    // ========================================
    // エラーケース
    // ========================================

    @Test(expected = IllegalArgumentException::class)
    fun encode_negativeNumber_throwsException() {
        Base62Converter.encode(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun encodeBigInteger_negativeNumber_throwsException() {
        Base62Converter.encodeBigInteger(BigInteger.valueOf(-1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_emptyString_throwsException() {
        Base62Converter.decode("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeBigInteger_emptyString_throwsException() {
        Base62Converter.decodeBigInteger("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_invalidCharacter_throwsException() {
        Base62Converter.decode("abc@123")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeBigInteger_invalidCharacter_throwsException() {
        Base62Converter.decodeBigInteger("abc#123")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_specialCharacters_throwsException() {
        Base62Converter.decode("hello!")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_space_throwsException() {
        Base62Converter.decode("a b c")
    }

    // ========================================
    // エッジケース
    // ========================================

    @Test
    fun encode_allValidCharacters_canBeDecoded() {
        // Base62の全文字（0-9, A-Z, a-z）が正しく処理されることを確認
        val base62Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

        for (i in 0 until 62) {
            val char = base62Chars[i].toString()
            // 単一文字のデコードが正しい値を返すか
            val decoded = Base62Converter.decode(char)
            assertEquals(i.toLong(), decoded)

            // その値を再エンコードすると元の文字に戻るか
            val reencoded = Base62Converter.encode(decoded)
            assertEquals(char, reencoded)
        }
    }

    @Test
    fun encodeBigInteger_veryLargeNumber_handlesCorrectly() {
        // Long.MAX_VALUEを超える非常に大きな数値
        val veryLargeNumber = BigInteger("999999999999999999999999999999")
        val encoded = Base62Converter.encodeBigInteger(veryLargeNumber)
        val decoded = Base62Converter.decodeBigInteger(encoded)
        assertEquals(veryLargeNumber, decoded)
    }

    @Test
    fun encode_consistentResults_multipleCalls() {
        // 同じ入力に対して常に同じ結果を返すか
        val testNumber = 123456L
        val result1 = Base62Converter.encode(testNumber)
        val result2 = Base62Converter.encode(testNumber)
        val result3 = Base62Converter.encode(testNumber)

        assertEquals(result1, result2)
        assertEquals(result2, result3)
    }
}
