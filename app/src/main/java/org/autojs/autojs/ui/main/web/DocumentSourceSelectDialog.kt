package org.autojs.autojs.ui.main.web

import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import android.webkit.WebView

class DocumentSourceSelectDialog(private val webView: WebView) {

    private sealed class ListItem {
        data class BuiltinDoc(val source: DocumentSource) : ListItem()
        object Divider : ListItem()
        data class CustomDoc(val name: String, val url: String, val isLocal: Boolean) : ListItem()
    }

    private val context = webView.context
    private val items = mutableListOf<ListItem>()
    private var selectedPosition = -1

    init {
        // 内置文档源
        DocumentSource.values().forEach {
            items.add(ListItem.BuiltinDoc(it))
        }

        // 自定义文档：每次打开都重新读取，确保 CustomOnlineUrlDialog 中新增/删除的条目实时反映
        val customDocs = CustomDocsStore.load(context)
        if (customDocs.isNotEmpty()) {
            items.add(ListItem.Divider)
            customDocs.forEach { item ->
                // 优先显示用户取的名称；没取名时退回到地址
                val displayName = item.name.ifBlank { item.url }
                items.add(
                    ListItem.CustomDoc(
                        displayName,
                        item.url,
                        item.isLocal
                    )
                )
            }
        }

        // 回显当前选中项
        val defaultSource = DocumentSource.DOC_V1_LOCAL
        val sp = EditorAppManager.getSaveStatus(context)
        val savedName = sp.getString(EditorAppManager.DocumentSourceKEY, defaultSource.name)

        selectedPosition = if (savedName == EditorAppManager.CUSTOM_SOURCE) {
            val savedUri = sp.getString(EditorAppManager.CUSTOM_SOURCE_URI_KEY, null)
            val savedLocal = sp.getBoolean(EditorAppManager.CUSTOM_SOURCE_IS_LOCAL_KEY, false)
            items.indexOfFirst {
                it is ListItem.CustomDoc && it.url == savedUri && it.isLocal == savedLocal
            }
        } else {
            items.indexOfFirst {
                it is ListItem.BuiltinDoc && it.source.name == savedName
            }
        }

        // 找不到就退回默认的“本地文档”，而不是 0 号的在线文档
        if (selectedPosition < 0) {
            selectedPosition = items.indexOfFirst {
                it is ListItem.BuiltinDoc && it.source == defaultSource
            }.coerceAtLeast(0)
        }
    }

    fun show(): AlertDialog {
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                // 这里条目很少，直接新建视图，避免 Divider 和普通行复用后样式串味
                val view = View.inflate(context, android.R.layout.simple_list_item_single_choice, null)
                val checkedView = view as CheckedTextView

                when (val item = items[position]) {
                    is ListItem.Divider -> {
                        checkedView.text = "自定义文档源"
                        checkedView.setTextColor(Color.parseColor("#4CAF50"))
                        checkedView.textSize = 15f
                        checkedView.setPadding(dp(24), dp(16), dp(24), dp(4))
                        checkedView.checkMarkDrawable = null
                        checkedView.isChecked = false
                    }

                    is ListItem.BuiltinDoc -> {
                        checkedView.text = item.source.sourceName
                        bindNormalRow(checkedView, position)
                    }

                    is ListItem.CustomDoc -> {
                        checkedView.text = item.name
                        bindNormalRow(checkedView, position)
                    }
                }
                return view
            }

            private fun bindNormalRow(checkedView: CheckedTextView, position: Int) {
                checkedView.setTextColor(Color.BLACK)
                checkedView.textSize = 14f
                // 左右各留 24dp，避免贴边
                checkedView.setPadding(dp(24), dp(14), dp(24), dp(14))
                checkedView.isChecked = position == selectedPosition
            }

            override fun isEnabled(position: Int): Boolean {
                return items[position] !is ListItem.Divider
            }
        }

        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(context)
            .setTitle("选择文档源")
            .setAdapter(adapter) { _, which ->
                val item = items.getOrNull(which) ?: return@setAdapter
                if (item is ListItem.Divider) return@setAdapter
                selectedPosition = which
                adapter.notifyDataSetChanged()
                switchDocument(item)
                // 选完就关掉，否则页面在弹窗后面加载，用户会以为没反应
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        return dialog
    }

    private fun switchDocument(item: ListItem) {
        when (item) {
            is ListItem.BuiltinDoc -> {
                EditorAppManager.switchDocument(webView, item.source)
            }

            is ListItem.CustomDoc -> {
                // 统一走 switchDocument：本地会启动 NanoHTTPD，在线直接加载，并持久化选中状态
                EditorAppManager.switchDocument(webView, item.url, item.isLocal)
            }

            else -> {}
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}