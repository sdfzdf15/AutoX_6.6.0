package org.autojs.autojs.ui.main.web

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.webkit.WebView

class CustomOnlineUrlDialog(private val webView: WebView) {

    private val context: Context
        get() = webView.context

    fun show() {
        val sp = EditorAppManager.getSaveStatus(context)

        val onlineUrlEdit = EditText(context).apply {
            hint = "请输入在线文档网址"
            setText(sp.getString("custom_online_url", "https://sdfzdf15.github.io/AutoxDocs/"))
        }

        val localUrlEdit = EditText(context).apply {
            hint = "请输入本地文档地址"
            setText(sp.getString("custom_local_url", "file:///sdcard/脚本/docs/v1/"))
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(onlineUrlEdit)
            addView(localUrlEdit)
        }

        AlertDialog.Builder(context)
            .setTitle("自定义文档设置")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val onlineUrl = onlineUrlEdit.text.toString().trim()
                val localUrl = localUrlEdit.text.toString().trim()
                sp.edit().apply {
                    if (onlineUrl.isNotEmpty()) {
                        putString("custom_online_url", onlineUrl)
                    }
                    if (localUrl.isNotEmpty()) {
                        putString("custom_local_url", localUrl)
                    }
                    apply()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
