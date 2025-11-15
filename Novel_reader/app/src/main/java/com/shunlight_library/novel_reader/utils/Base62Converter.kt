/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Base62 encoding/decoding utility for compact ID representation.
 */
package com.shunlight_library.novel_reader.utils

import java.math.BigInteger

/**
 * Base62エンコーディングを提供するユーティリティクラス
 *
 * 数値IDをコンパクトな英数字文字列に変換する。
 * 主にKakuyomuの数値作品IDをPseudo-Ncodeに変換する際に使用。
 *
 * 文字セット: 0-9, A-Z, a-z (62文字)
 */
object Base62Converter {
    private const val BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62

    /**
     * 数値をBase62文字列にエンコード
     *
     * @param number エンコードする数値（0以上）
     * @return Base62文字列
     * @throws IllegalArgumentException 負の数が渡された場合
     */
    fun encode(number: Long): String {
        if (number < 0) {
            throw IllegalArgumentException("Number must be non-negative: $number")
        }
        if (number == 0L) {
            return "0"
        }

        var num = number
        val result = StringBuilder()

        while (num > 0) {
            result.append(BASE62_CHARS[(num % BASE).toInt()])
            num /= BASE
        }

        return result.reverse().toString()
    }

    /**
     * Base62文字列を数値にデコード
     *
     * @param base62 デコードするBase62文字列
     * @return 元の数値
     * @throws IllegalArgumentException 無効な文字が含まれる場合
     */
    fun decode(base62: String): Long {
        if (base62.isEmpty()) {
            throw IllegalArgumentException("Base62 string cannot be empty")
        }

        var result = 0L

        for (char in base62) {
            val index = BASE62_CHARS.indexOf(char)
            if (index == -1) {
                throw IllegalArgumentException("Invalid Base62 character: $char")
            }
            result = result * BASE + index
        }

        return result
    }

    /**
     * BigInteger数値をBase62文字列にエンコード
     *
     * Long型の範囲を超える大きな数値に対応
     *
     * @param number エンコードする数値（0以上）
     * @return Base62文字列
     * @throws IllegalArgumentException 負の数が渡された場合
     */
    fun encodeBigInteger(number: BigInteger): String {
        if (number < BigInteger.ZERO) {
            throw IllegalArgumentException("Number must be non-negative: $number")
        }
        if (number == BigInteger.ZERO) {
            return "0"
        }

        var num = number
        val result = StringBuilder()
        val base = BigInteger.valueOf(BASE.toLong())

        while (num > BigInteger.ZERO) {
            result.append(BASE62_CHARS[(num % base).toInt()])
            num /= base
        }

        return result.reverse().toString()
    }

    /**
     * Base62文字列をBigInteger数値にデコード
     *
     * Long型の範囲を超える大きな数値に対応
     *
     * @param base62 デコードするBase62文字列
     * @return 元の数値
     * @throws IllegalArgumentException 無効な文字が含まれる場合
     */
    fun decodeBigInteger(base62: String): BigInteger {
        if (base62.isEmpty()) {
            throw IllegalArgumentException("Base62 string cannot be empty")
        }

        var result = BigInteger.ZERO
        val base = BigInteger.valueOf(BASE.toLong())

        for (char in base62) {
            val index = BASE62_CHARS.indexOf(char)
            if (index == -1) {
                throw IllegalArgumentException("Invalid Base62 character: $char")
            }
            result = result * base + BigInteger.valueOf(index.toLong())
        }

        return result
    }
}
