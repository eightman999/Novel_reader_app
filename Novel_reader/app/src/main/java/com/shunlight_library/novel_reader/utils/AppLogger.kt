package com.shunlight_library.novel_reader.utils

import android.util.Log
import com.shunlight_library.novel_reader.BuildConfig

/**
 * アプリケーション全体のロギングを制御するユーティリティクラス
 *
 * 開発環境（debug）では全てのログを出力
 * リリース環境（release）ではログ出力を無効化
 */
object AppLogger {
    private const val DEFAULT_TAG = "NovelReader"

    /**
     * ログ出力が有効かどうか
     * BuildConfig.ENABLE_LOGGING がtrueの場合のみログを出力
     */
    private val isLoggingEnabled: Boolean
        get() = BuildConfig.ENABLE_LOGGING

    /**
     * デバッグログを出力
     * @param tag ログタグ（省略時はDEFAULT_TAG）
     * @param message ログメッセージ
     */
    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.d(tag, message)
        }
    }

    /**
     * 情報ログを出力
     * @param tag ログタグ（省略時はDEFAULT_TAG）
     * @param message ログメッセージ
     */
    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.i(tag, message)
        }
    }

    /**
     * 警告ログを出力
     * @param tag ログタグ（省略時はDEFAULT_TAG）
     * @param message ログメッセージ
     */
    fun w(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.w(tag, message)
        }
    }

    /**
     * 警告ログを出力（例外付き）
     * @param tag ログタグ（省略時はDEFAULT_TAG）
     * @param message ログメッセージ
     * @param throwable 例外オブジェクト
     */
    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        if (isLoggingEnabled) {
            Log.w(tag, message, throwable)
        }
    }

    /**
     * エラーログを出力
     * @param tag ログタグ（省略時はDEFAULT_TAG）
     * @param message ログメッセージ
     */
    fun e(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.e(tag, message)
        }
    }

    /**
     * エラーログを出力（例外付き）
     * @param tag ログタグ（省略時はDEFAULT_TAG）
     * @param message ログメッセージ
     * @param throwable 例外オブジェクト
     */
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        if (isLoggingEnabled) {
            Log.e(tag, message, throwable)
        }
    }

    /**
     * Verboseログを出力
     * @param tag ログタグ（省略時はDEFAULT_TAG）
     * @param message ログメッセージ
     */
    fun v(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.v(tag, message)
        }
    }
}
