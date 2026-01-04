package com.sna.sdk.testapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.TextView

internal class UiLog(private val textView: TextView) {
    private val maxChars = 200_000

    fun clear() {
        textView.text = ""
    }

    fun append(line: String) {
        val current = textView.text?.toString().orEmpty()
        val next = current + line
        textView.text = if (next.length <= maxChars) next else next.takeLast(maxChars)
    }

    fun copyToClipboard(context: Context, label: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, textView.text?.toString().orEmpty()))
    }
}

