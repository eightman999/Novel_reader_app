/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Email utility for sending error logs.
 */
package com.shunlight_library.novel_reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*

object EmailUtils {

    private const val SUPPORT_EMAIL = "contact@furinlab.com"

    fun sendErrorLogByEmail(context: Context, logs: List<ErrorLog>, errorLogStore: ErrorLogStore) {
        if (logs.isEmpty()) {
            return
        }

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))

            val subject = "小説リーダーアプリ エラーログ (${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())})"
            putExtra(Intent.EXTRA_SUBJECT, subject)

            val body = errorLogStore.formatErrorLogsForEmail(logs)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        context.startActivity(Intent.createChooser(intent, "メールアプリを選択"))
    }

    fun sendErrorLogByEmailWithAttachment(context: Context, logs: List<ErrorLog>, errorLogStore: ErrorLogStore) {
        if (logs.isEmpty()) {
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))

            val subject = "小説リーダーアプリ エラーログ (${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())})"
            putExtra(Intent.EXTRA_SUBJECT, subject)

            val body = errorLogStore.formatErrorLogsForEmail(logs)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        context.startActivity(Intent.createChooser(intent, "メールアプリを選択"))
    }
}
