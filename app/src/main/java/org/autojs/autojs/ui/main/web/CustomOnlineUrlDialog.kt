package org.autojs.autojs.ui.main.web

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自定义文档列表的存储格式：
 * 统一保存在 custom_docs_list 中，内容为 [{"type":"在线","url":"..."},{"type":"本地","url":"..."}]
 */
internal object CustomDocsStore {

    const val KEY = "custom_docs_list"
    const val TYPE_ONLINE = "在线"
    const val TYPE_LOCAL = "本地"

    private const val LEGACY_ONLINE_KEY = "custom_online_url"
    private const val LEGACY_LOCAL_KEY = "custom_local_url"

    data class DocItem(val type: String, val url: String, val name: String = "") {
        val isLocal: Boolean
            get() = type == TYPE_LOCAL
    }

    /**
     * 读取自定义文档列表，优先读取 JSON 格式，旧版双独立键作为兼容回退
     */
    fun load(context: Context): List<DocItem> {
        val sp = EditorAppManager.getSaveStatus(context)
        val savedJson = sp.getString(KEY, null)
        if (savedJson.isNullOrBlank()) {
            return loadLegacy(sp)
        }
        return try {
            val jsonArray = JSONArray(savedJson)
            (0 until jsonArray.length()).mapNotNull { i ->
                val obj = jsonArray.optJSONObject(i) ?: return@mapNotNull null
                val url = obj.optString("url").trim()
                if (url.isEmpty()) return@mapNotNull null
                val name = obj.optString("name").trim()
                DocItem(typeOf(obj.optString("type")), url, name)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // JSON 坏了就退回旧键，别把用户的配置直接丢掉
            loadLegacy(sp)
        }
    }

    /**
     * 兼容旧版 custom_online_url / custom_local_url 两个独立键
     */
    private fun loadLegacy(sp: SharedPreferences): List<DocItem> {
        val online = sp.getString(LEGACY_ONLINE_KEY, null)
        val local = sp.getString(LEGACY_LOCAL_KEY, null)
        return listOfNotNull(
            online?.takeIf { it.isNotBlank() }?.let { DocItem(TYPE_ONLINE, normalizeOnlineUrl(it), "") },
            local?.takeIf { it.isNotBlank() }?.let { DocItem(TYPE_LOCAL, normalizeLocalUrl(it), "") }
        )
    }

    fun save(context: Context, items: List<DocItem>) {
        val jsonArray = JSONArray()
        items.forEach {
            jsonArray.put(JSONObject().apply {
                put("type", it.type)
                put("url", it.url)
                put("name", it.name)
            })
        }
        EditorAppManager.getSaveStatus(context).edit()
            .putString(KEY, jsonArray.toString())
            .apply()
    }

    /**
     * 规范化地址：
     *  - 本地：统一成 file:/// 形式（file://host/path、/path 都能处理）
     *  - 在线：缺少协议时补全 https://
     */
    fun normalizeUrl(type: String, url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        return if (type == TYPE_LOCAL) normalizeLocalUrl(trimmed) else normalizeOnlineUrl(trimmed)
    }

    private fun normalizeLocalUrl(raw: String): String {
        val normalized = raw.replace('\\', '/')
        if (normalized.startsWith("file://", ignoreCase = true)) {
            // 用 Uri 解析，file:///sdcard/a 和 file://localhost/sdcard/a 都能拿到 /sdcard/a
            val path = runCatching { Uri.parse(normalized).path }.getOrNull()
            if (!path.isNullOrEmpty()) return "file://$path"
            return "file:///" + normalized.removePrefix("file://").trimStart('/')
        }
        // content:// 之类的非文件地址原样保留
        if (normalized.contains("://")) return normalized
        return if (normalized.startsWith("/")) "file://$normalized" else "file:///$normalized"
    }

    private fun normalizeOnlineUrl(raw: String): String = when {
        raw.startsWith("http://", ignoreCase = true) -> raw
        raw.startsWith("https://", ignoreCase = true) -> raw
        else -> "https://$raw"
    }

    private fun typeOf(type: String): String =
        if (type == TYPE_LOCAL) TYPE_LOCAL else TYPE_ONLINE
}

class CustomOnlineUrlDialog(private val webView: WebView) {

    private val context: Context
        get() = webView.context

    private val accentColor = Color.parseColor("#4CAF50")
    private val cardBgColor = Color.parseColor("#F1F8E9")
    private val cardCorner = 16f

    /** 单条文档对应的控件，保存时直接读取，避免依赖视图层级 */
    private class ItemHolder(
        val nameInput: EditText,
        val onlineRadio: RadioButton,
        val localRadio: RadioButton,
        val urlInput: EditText
    )

    /** 生成卡片背景：圆角 + 淡绿色 */
    private fun cardBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(cardBgColor)
        cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, cardCorner,
            context.resources.displayMetrics
        )
    }

    fun show() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }

        fun hintFor(type: String): String =
            if (type == CustomDocsStore.TYPE_LOCAL) {
                "请输入本地路径，例如 /sdcard/文档/"
            } else {
                "请输入在线文档地址，例如 https://xxx.com/docs/"
            }

        // 添加单个文档条目（卡片式）
        fun addItem(type: String, url: String, name: String = "") {
            // 名称输入框
            val nameInput = EditText(context).apply {
                setText(name)
                hint = "名称（可选，例如 菜鸟网站）"
                setSingleLine()
                textSize = 13f
                setPadding(dp(8), dp(0), dp(8), dp(0))
                minHeight = dp(30)
            }

            // 地址输入框
            val urlInput = EditText(context).apply {
                setText(url)
                hint = hintFor(type)
                setSingleLine()
                textSize = 13f
                setPadding(dp(8), dp(0), dp(8), dp(0))
                minHeight = dp(30)
            }

            val onlineRadio = RadioButton(context).apply {
                id = View.generateViewId()
                text = CustomDocsStore.TYPE_ONLINE
                setTextColor(accentColor)
                textSize = 13f
            }

            val localRadio = RadioButton(context).apply {
                id = View.generateViewId()
                text = CustomDocsStore.TYPE_LOCAL
                setTextColor(accentColor)
                textSize = 13f
            }

            // 在线 / 本地单选放在卡片里
            val typeGroup = RadioGroup(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(onlineRadio)
                addView(localRadio)
                setOnCheckedChangeListener { _, checkedId ->
                    urlInput.hint = hintFor(
                        if (checkedId == localRadio.id) CustomDocsStore.TYPE_LOCAL else CustomDocsStore.TYPE_ONLINE
                    )
                }
                check(if (type == CustomDocsStore.TYPE_LOCAL) localRadio.id else onlineRadio.id)
            }

            // 左侧内容：类型选择 + 名称 + 地址（单选框放最上面）
            val contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(12), dp(6), dp(6), dp(6))
                addView(typeGroup)
                addView(nameInput)
                addView(urlInput)
            }

            // 卡片容器（先声明，删除按钮要引用它）
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                background = cardBackground()
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }

            // 右侧红色 X 删除按钮
            val deleteBtn = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#E53935"))
                textSize = 18f
                setPadding(dp(8), dp(4), dp(10), dp(4))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER_VERTICAL }
                setOnClickListener { container.removeView(card) }
            }

            card.addView(contentLayout)
            card.addView(deleteBtn)
            card.tag = ItemHolder(nameInput, onlineRadio, localRadio, urlInput)

            container.addView(card)
        }

        // 自定义文档源多的时候要能滚动，否则“保存/删除”会被挤出屏幕
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            addView(container)
        }

        CustomDocsStore.load(context).forEach { addItem(it.type, it.url, it.name) }
        if (container.childCount == 0) {
            // 一个都没有时给个空行，避免弹窗只显示标题
            addItem(CustomDocsStore.TYPE_ONLINE, "")
        }

        // 标题栏 右上角添加按钮
        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), 0)
            addView(TextView(context).apply {
                text = "自定义文档设置"
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply {
                text = "添加"
                setTextColor(Color.WHITE)
                setPadding(dp(16), dp(4), dp(16), dp(4))
                minHeight = 0
                minimumHeight = 0
                background = GradientDrawable().apply {
                    setColor(accentColor)
                    cornerRadius = dp(6).toFloat()
                }
                setOnClickListener {
                    addItem(CustomDocsStore.TYPE_ONLINE, "")
                    scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
                }
            })
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleBar)
            addView(scrollView)
        }

        AlertDialog.Builder(context)
            .setView(rootLayout)
            .setPositiveButton("保存") { _, _ -> save(container) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun save(container: LinearLayout) {
        val items = (0 until container.childCount)
            .mapNotNull { container.getChildAt(it).tag as? ItemHolder }
            .mapNotNull { holder ->
                val type =
                    if (holder.localRadio.isChecked) CustomDocsStore.TYPE_LOCAL else CustomDocsStore.TYPE_ONLINE
                val url = CustomDocsStore.normalizeUrl(type, holder.urlInput.text.toString())
                if (url.isEmpty()) null else {
                    val name = holder.nameInput.text.toString().trim()
                    CustomDocsStore.DocItem(type, url, name)
                }
            }
        CustomDocsStore.save(context, items)
        resetSelectionIfMissing(items)
    }

    /** 当前选中的自定义文档被删掉时，把选中状态退回默认的本地文档，避免下次启动白屏 */
    private fun resetSelectionIfMissing(items: List<CustomDocsStore.DocItem>) {
        val sp = EditorAppManager.getSaveStatus(context)
        if (sp.getString(EditorAppManager.DocumentSourceKEY, null) != EditorAppManager.CUSTOM_SOURCE) {
            return
        }
        val current = sp.getString(EditorAppManager.CUSTOM_SOURCE_URI_KEY, null)
        if (items.any { it.url == current }) {
            return
        }
        sp.edit()
            .putString(EditorAppManager.DocumentSourceKEY, DocumentSource.DOC_V1_LOCAL.name)
            .remove(EditorAppManager.CUSTOM_SOURCE_URI_KEY)
            .remove(EditorAppManager.CUSTOM_SOURCE_IS_LOCAL_KEY)
            .apply()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}