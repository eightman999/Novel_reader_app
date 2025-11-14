/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Pseudo-Ncode generation and validation for multi-site support.
 */
package com.shunlight_library.novel_reader.utils

/**
 * Pseudo-Ncode生成・検証ユーティリティ
 *
 * 異なる小説サイトの作品IDを統一的なNcode形式で管理するためのクラス。
 *
 * フォーマット:
 * - 小説家になろう: そのままのNcode (例: "n1234ab", "n9999zz")
 * - カクヨム: "K" + Base62エンコードされた数値ID (例: "K1A2B3C")
 *
 * Base62を使用することで、長い数値IDをコンパクトに表現できる。
 */
object PseudoNcodeGenerator {
    private const val KAKUYOMU_PREFIX = "K"

    /**
     * KakuyomuのworkIdからPseudo-Ncodeを生成
     *
     * 例: workId="1177354054887277844" → "K9zXYt1A2B3"
     *
     * @param workId Kakuyomuの数値作品ID（文字列形式）
     * @return "K"で始まるPseudo-Ncode
     * @throws IllegalArgumentException workIdが数値でない場合
     */
    fun generateKakuyomuNcode(workId: String): String {
        val numericId = workId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid Kakuyomu work ID (not a number): $workId")

        if (numericId < 0) {
            throw IllegalArgumentException("Invalid Kakuyomu work ID (negative): $workId")
        }

        return KAKUYOMU_PREFIX + Base62Converter.encode(numericId)
    }

    /**
     * Kakuyomu Pseudo-NcodeからworkIdを抽出
     *
     * 例: pseudoNcode="K9zXYt1A2B3" → "1177354054887277844"
     *
     * @param pseudoNcode "K"で始まるPseudo-Ncode
     * @return 元の数値作品ID（文字列形式）
     * @throws IllegalArgumentException Kakuyomu Pseudo-Ncodeでない場合
     */
    fun extractKakuyomuWorkId(pseudoNcode: String): String {
        if (!isKakuyomuNcode(pseudoNcode)) {
            throw IllegalArgumentException("Not a Kakuyomu pseudo-ncode: $pseudoNcode")
        }

        val base62Part = pseudoNcode.substring(KAKUYOMU_PREFIX.length)
        if (base62Part.isEmpty()) {
            throw IllegalArgumentException("Invalid Kakuyomu pseudo-ncode (empty Base62 part): $pseudoNcode")
        }

        return Base62Converter.decode(base62Part).toString()
    }

    /**
     * Ncodeがカクヨム由来かどうかを判定
     *
     * @param ncode 判定対象のNcode
     * @return カクヨムのPseudo-Ncodeの場合true
     */
    fun isKakuyomuNcode(ncode: String): Boolean {
        return ncode.startsWith(KAKUYOMU_PREFIX) && ncode.length > KAKUYOMU_PREFIX.length
    }

    /**
     * Ncodeが小説家になろう由来かどうかを判定
     *
     * @param ncode 判定対象のNcode
     * @return 小説家になろうのNcodeの場合true（Kakuyomu形式でない場合）
     */
    fun isSyosetuNcode(ncode: String): Boolean {
        return !isKakuyomuNcode(ncode) && ncode.isNotEmpty()
    }
}
