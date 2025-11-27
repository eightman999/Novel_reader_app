/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Unit tests for PseudoNcodeGenerator.
 */
package com.shunlight_library.novel_reader.utils

import org.junit.Test
import org.junit.Assert.*

/**
 * PseudoNcodeGeneratorのユニットテスト
 *
 * カクヨムとなろうのNcode形式変換の正しさを検証する。
 * マルチサイト対応の中核機能のため、重要なテストクラス。
 */
class PseudoNcodeGeneratorTest {

    // ========================================
    // カクヨムNcode生成 - 基本テスト
    // ========================================

    @Test
    fun generateKakuyomuNcode_validWorkId_returnsCorrectFormat() {
        val workId = "1177354054887277844"
        val result = PseudoNcodeGenerator.generateKakuyomuNcode(workId)

        // "K"で始まることを確認
        assertTrue("Result should start with 'K'", result.startsWith("K"))

        // "K"の後にBase62文字列が続くことを確認
        assertTrue("Result should have content after 'K'", result.length > 1)

        // Base62の有効な文字のみで構成されていることを確認
        val base62Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val encodedPart = result.substring(1)
        assertTrue("Encoded part should only contain Base62 characters",
            encodedPart.all { it in base62Chars })
    }

    @Test
    fun generateKakuyomuNcode_realExample_returnsValidNcode() {
        // CLAUDE.mdに記載されている実際の例
        val workId = "1177354054887277844"
        val result = PseudoNcodeGenerator.generateKakuyomuNcode(workId)

        // Pseudo-Ncodeとして有効な形式か確認
        assertTrue(result.startsWith("K"))
        assertTrue(result.length > 1)
    }

    @Test
    fun generateKakuyomuNcode_multipleWorkIds_generatesDifferentNcodes() {
        val workId1 = "1177354054887277844"
        val workId2 = "1177354054887854131"

        val ncode1 = PseudoNcodeGenerator.generateKakuyomuNcode(workId1)
        val ncode2 = PseudoNcodeGenerator.generateKakuyomuNcode(workId2)

        // 異なるwork IDは異なるPseudo-Ncodeを生成
        assertNotEquals(ncode1, ncode2)
    }

    @Test
    fun generateKakuyomuNcode_zero_returnsKZero() {
        val workId = "0"
        val result = PseudoNcodeGenerator.generateKakuyomuNcode(workId)
        assertEquals("K0", result)
    }

    @Test
    fun generateKakuyomuNcode_one_returnsKOne() {
        val workId = "1"
        val result = PseudoNcodeGenerator.generateKakuyomuNcode(workId)
        assertEquals("K1", result)
    }

    // ========================================
    // カクヨムwork ID抽出 - 基本テスト
    // ========================================

    @Test
    fun extractKakuyomuWorkId_validNcode_returnsWorkId() {
        val originalWorkId = "1177354054887277844"
        val ncode = PseudoNcodeGenerator.generateKakuyomuNcode(originalWorkId)
        val extractedWorkId = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)

        assertEquals(originalWorkId, extractedWorkId)
    }

    @Test
    fun extractKakuyomuWorkId_kZero_returnsZero() {
        val workId = PseudoNcodeGenerator.extractKakuyomuWorkId("K0")
        assertEquals("0", workId)
    }

    @Test
    fun extractKakuyomuWorkId_kOne_returnsOne() {
        val workId = PseudoNcodeGenerator.extractKakuyomuWorkId("K1")
        assertEquals("1", workId)
    }

    // ========================================
    // ラウンドトリップテスト
    // ========================================

    @Test
    fun generateAndExtract_roundTrip_smallNumbers() {
        val testWorkIds = listOf("0", "1", "10", "100", "999", "12345")

        testWorkIds.forEach { workId ->
            val ncode = PseudoNcodeGenerator.generateKakuyomuNcode(workId)
            val extracted = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
            assertEquals("Failed for work ID: $workId", workId, extracted)
        }
    }

    @Test
    fun generateAndExtract_roundTrip_kakuyomuWorkIds() {
        // 実際のカクヨムwork IDのパターン（19桁）
        val testWorkIds = listOf(
            "1177354054887277844",
            "1177354054887854131",
            "1234567890123456789",
            "9999999999999999999"
        )

        testWorkIds.forEach { workId ->
            val ncode = PseudoNcodeGenerator.generateKakuyomuNcode(workId)
            val extracted = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
            assertEquals("Failed for work ID: $workId", workId, extracted)
        }
    }

    @Test
    fun generateAndExtract_roundTrip_consistentResults() {
        // 同じwork IDから生成したNcodeは常に同じwork IDを抽出できるか
        val workId = "1177354054887277844"

        val ncode1 = PseudoNcodeGenerator.generateKakuyomuNcode(workId)
        val ncode2 = PseudoNcodeGenerator.generateKakuyomuNcode(workId)

        assertEquals("Generated ncodes should be identical", ncode1, ncode2)

        val extracted1 = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode1)
        val extracted2 = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode2)

        assertEquals(workId, extracted1)
        assertEquals(workId, extracted2)
    }

    // ========================================
    // isKakuyomuNcode - 判定テスト
    // ========================================

    @Test
    fun isKakuyomuNcode_validKakuyomuNcode_returnsTrue() {
        val validNcodes = listOf(
            "K0",
            "K1",
            "K123abc",
            "KABCxyz789",
            PseudoNcodeGenerator.generateKakuyomuNcode("1177354054887277844")
        )

        validNcodes.forEach { ncode ->
            assertTrue("Should recognize $ncode as Kakuyomu ncode",
                PseudoNcodeGenerator.isKakuyomuNcode(ncode))
        }
    }

    @Test
    fun isKakuyomuNcode_syosetuNcode_returnsFalse() {
        val syosetuNcodes = listOf(
            "n1234ab",
            "n9999zz",
            "ncode123",
            "N1234AB"
        )

        syosetuNcodes.forEach { ncode ->
            assertFalse("Should not recognize $ncode as Kakuyomu ncode",
                PseudoNcodeGenerator.isKakuyomuNcode(ncode))
        }
    }

    @Test
    fun isKakuyomuNcode_onlyK_returnsFalse() {
        // "K"だけの場合はfalse
        assertFalse(PseudoNcodeGenerator.isKakuyomuNcode("K"))
    }

    @Test
    fun isKakuyomuNcode_emptyString_returnsFalse() {
        assertFalse(PseudoNcodeGenerator.isKakuyomuNcode(""))
    }

    @Test
    fun isKakuyomuNcode_lowercaseK_returnsFalse() {
        // 小文字の"k"で始まる場合はfalse
        assertFalse(PseudoNcodeGenerator.isKakuyomuNcode("k123abc"))
    }

    // ========================================
    // isSyosetuNcode - 判定テスト
    // ========================================

    @Test
    fun isSyosetuNcode_validSyosetuNcode_returnsTrue() {
        val syosetuNcodes = listOf(
            "n1234ab",
            "n9999zz",
            "ncode123",
            "N1234AB",
            "abc123"  // "K"で始まらなければなろうNcodeとみなす
        )

        syosetuNcodes.forEach { ncode ->
            assertTrue("Should recognize $ncode as Syosetu ncode",
                PseudoNcodeGenerator.isSyosetuNcode(ncode))
        }
    }

    @Test
    fun isSyosetuNcode_kakuyomuNcode_returnsFalse() {
        val kakuyomuNcodes = listOf(
            "K0",
            "K123abc",
            PseudoNcodeGenerator.generateKakuyomuNcode("1177354054887277844")
        )

        kakuyomuNcodes.forEach { ncode ->
            assertFalse("Should not recognize $ncode as Syosetu ncode",
                PseudoNcodeGenerator.isSyosetuNcode(ncode))
        }
    }

    @Test
    fun isSyosetuNcode_emptyString_returnsFalse() {
        assertFalse(PseudoNcodeGenerator.isSyosetuNcode(""))
    }

    // ========================================
    // エラーケース
    // ========================================

    @Test(expected = IllegalArgumentException::class)
    fun generateKakuyomuNcode_invalidWorkId_throwsException() {
        PseudoNcodeGenerator.generateKakuyomuNcode("not_a_number")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateKakuyomuNcode_negativeWorkId_throwsException() {
        PseudoNcodeGenerator.generateKakuyomuNcode("-123")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateKakuyomuNcode_emptyString_throwsException() {
        PseudoNcodeGenerator.generateKakuyomuNcode("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractKakuyomuWorkId_syosetuNcode_throwsException() {
        // なろうNcodeを渡した場合はエラー
        PseudoNcodeGenerator.extractKakuyomuWorkId("n1234ab")
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractKakuyomuWorkId_onlyK_throwsException() {
        // "K"だけの場合はエラー
        PseudoNcodeGenerator.extractKakuyomuWorkId("K")
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractKakuyomuWorkId_emptyString_throwsException() {
        PseudoNcodeGenerator.extractKakuyomuWorkId("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractKakuyomuWorkId_invalidFormat_throwsException() {
        // "K"で始まらないものを渡した場合はエラー
        PseudoNcodeGenerator.extractKakuyomuWorkId("abc123")
    }

    // ========================================
    // エッジケース
    // ========================================

    @Test
    fun generateKakuyomuNcode_veryLargeWorkId_handlesCorrectly() {
        // 非常に大きなwork ID（Long.MAX_VALUEを超える）
        val veryLargeWorkId = "999999999999999999999999999"
        val ncode = PseudoNcodeGenerator.generateKakuyomuNcode(veryLargeWorkId)
        val extracted = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)

        assertEquals(veryLargeWorkId, extracted)
    }

    @Test
    fun isKakuyomuNcode_and_isSyosetuNcode_mutuallyExclusive() {
        // カクヨムNcodeとなろうNcodeは排他的
        val kakuyomuNcode = PseudoNcodeGenerator.generateKakuyomuNcode("123456")

        assertTrue(PseudoNcodeGenerator.isKakuyomuNcode(kakuyomuNcode))
        assertFalse(PseudoNcodeGenerator.isSyosetuNcode(kakuyomuNcode))

        val syosetuNcode = "n1234ab"

        assertFalse(PseudoNcodeGenerator.isKakuyomuNcode(syosetuNcode))
        assertTrue(PseudoNcodeGenerator.isSyosetuNcode(syosetuNcode))
    }

    @Test
    fun generateKakuyomuNcode_leadingZeros_handledCorrectly() {
        // 先頭にゼロがある数値文字列
        val workId = "0000000123"
        val ncode = PseudoNcodeGenerator.generateKakuyomuNcode(workId)
        val extracted = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)

        // BigIntegerは先頭のゼロを除去するため、"123"になる
        assertEquals("123", extracted)
    }
}
