/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Routes hardware volume keys to the reader screen for page turning.
 */
package com.shunlight_library.novel_reader.utils

import android.view.KeyEvent

/**
 * 音量キーによるページ送りの中継。
 *
 * Activity の dispatchKeyEvent から呼ばれ、読書画面が登録したハンドラへ渡す。
 * ハンドラ未登録（読書画面以外・設定OFF）の場合は消費せず、通常の音量操作になる。
 */
object VolumeKeyPaging {
    /** 引数 true=次ページ（音量下）、false=前ページ（音量上） */
    @Volatile
    var handler: ((forward: Boolean) -> Unit)? = null

    /** 音量キーを処理した場合 true（音量変更・音量UI表示を抑止する） */
    fun dispatch(event: KeyEvent): Boolean {
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP -> false
            else -> return false
        }
        val current = handler ?: return false
        // 押下時のみページ送り（長押しのリピートも1ページずつ送る）。離した時も消費して音量変更を防ぐ
        if (event.action == KeyEvent.ACTION_DOWN) {
            current(forward)
        }
        return true
    }
}
