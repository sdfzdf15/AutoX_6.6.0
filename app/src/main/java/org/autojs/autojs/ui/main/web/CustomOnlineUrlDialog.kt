package org.autojs.autojs.ui.main.web

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.webkit.WebView

class CustomOnlineUrlDialog(private val webView: WebView) {

    private val context: Context
        get() = webView.context

    fun show() {
        val sp = EditorAppManager.getSaveStatus(context)
        val editText = EditText(context).apply {
            hint = "请输入在线文档网址"
            setText(sp.getString("custom_online_url", ""))
        }

        AlertDialog.Builder(context)
            .setTitle("自定义在线文档")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    sp.edit().putString("custom_online_url", url).apply()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}