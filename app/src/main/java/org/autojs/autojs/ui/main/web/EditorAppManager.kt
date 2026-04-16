package org.autojs.autojs.ui.main.web

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.webkit.WebView
import androidx.fragment.app.Fragment
import org.autojs.autojs.ui.widget.SwipeRefreshWebView
import org.autojs.autojs.ui.widget.fillMaxSize


import fi.iki.elonen.NanoHTTPD
import java.io.File

class EditorAppManager : Fragment() {


    val swipeRefreshWebView by lazy {
        val context = requireContext()
        SwipeRefreshWebView(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return swipeRefreshWebView.apply {
            loadHomeDocument(this.webView)
            fillMaxSize()
        }
    }

    companion object {
        const val TAG = "EditorAppManager"
        const val DocumentSourceKEY = "DocumentSource"



        private var saveStatus: SharedPreferences? = null

        var server: NanoHTTPD? = null
        var isCopying = false
        @Synchronized
        fun getSaveStatus(context: Context): SharedPreferences {
            if (saveStatus == null) {
                saveStatus = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            }
            return saveStatus!!
        }

        fun loadHomeDocument(webView: WebView) {
            val saveStatus = getSaveStatus(webView.context)
            val name = saveStatus.getString(DocumentSourceKEY, DocumentSource.DOC_V1_LOCAL.name)
            switchDocument(
                webView, try {
                    DocumentSource.valueOf(name!!)
                } catch (e: Exception) {
                    DocumentSource.DOC_V1_LOCAL
                }
            )
        }

        fun switchDocument(webView: WebView, documentSource: DocumentSource) {
            // 关闭旧服务器
            if (server != null) {
                try { server!!.stop() } catch (e: Exception) {}
                server = null
            }

            // ====================== 【只在 index.html 不存在时全量复制】 ======================
            if (documentSource == DocumentSource.DOC_V2_LOCAL) {
                val sdRoot = File(documentSource.uri.removePrefix("file://"))
                val indexFile = File(sdRoot, "index.html") // 看这里！

                // 只有 不存在 index.html 才全量复制！
                if (!indexFile.exists() && !isCopying) {
                    isCopying = true
                    val assetRoot = "docs/v1"
                    val assets = webView.context.assets

                    fun copy(assetPath: String, targetDir: File) {
                        try {
                            val files = assets.list(assetPath) ?: return
                            for (name in files) {
                                val currAsset = "$assetPath/$name"
                                val targetFile = File(targetDir, name)
                                val isDir = try { assets.list(currAsset)?.isNotEmpty() == true } catch(e: Exception) { false }

                                if (isDir) {
                                    targetFile.mkdirs()
                                    copy(currAsset, targetFile)
                                } else {
                                    if (!targetFile.exists()) {
                                        targetFile.parentFile?.mkdirs()
                                        assets.open(currAsset).use { i ->
                                            targetFile.outputStream().use { o -> i.copyTo(o) }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    sdRoot.mkdirs()
                    copy(assetRoot, sdRoot)
                    isCopying = false

                }
            }
            // ================================================================================

            if (documentSource.isLocal) {
                // 本地：统一走服务器
                webView.webViewClient = android.webkit.WebViewClient()

                server = object : NanoHTTPD(8080) {
                    override fun serve(session: IHTTPSession): Response {
                        val path = session.uri.removePrefix("/").ifEmpty { "index.html" }

                        return if (documentSource.uri.startsWith("file://")) {
                            // SD卡
                            val sdPath = documentSource.uri.removePrefix("file://")
                            val file = File(sdPath, path)

                            // 懒加载补缺失文件（不覆盖）
                            if (!file.exists()) {
                                try {
                                    val assetPath = "docs/v1/$path"
                                    val input = webView.context.assets.open(assetPath)
                                    file.parentFile?.mkdirs()
                                    file.outputStream().use { input.copyTo(it) }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            // 文件仍不存在则返回 404
                            if (!file.exists()) {
                                return newFixedLengthResponse(
                                    Response.Status.NOT_FOUND,
                                    "text/plain",
                                    "Not found: $path"
                                )
                            }

                            newChunkedResponse(Response.Status.OK, getMimeType(file.name), file.inputStream())
                        } else {
                            // assets
                            val assetPath = "${documentSource.uri}/$path"
                            val input = webView.context.assets.open(assetPath)
                            newChunkedResponse(Response.Status.OK, getMimeType(path), input)
                        }
                    }

                    fun getMimeType(path: String): String {
                        return when {
                            path.endsWith(".html") -> "text/html"
                            path.endsWith(".css") -> "text/css"
                            path.endsWith(".js") -> "application/javascript"
                            path.endsWith(".png") -> "image/png"
                            path.endsWith(".jpg") -> "image/jpeg"
                            else -> "text/plain"
                        }
                    }
                }
                // 安全启动
                try {
                    server!!.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 统一加载服务器
                webView.loadUrl("http://localhost:8080/")
            } else {
                // 在线：直接加载
                val url = if (documentSource == DocumentSource.DOC_V3) {
                    // 自定义在线文档 → 读取本地存储
                    val customUrl = getSaveStatus(webView.context)
                        .getString("custom_online_url", "")
                    // 为空则使用枚举默认值
                    customUrl?.takeIf { it.isNotBlank() } ?: documentSource.uri
                } else {
                    documentSource.uri
                }
                webView.loadUrl(url)
            }

            // 保存选择
            getSaveStatus(webView.context).edit()
                .putString(DocumentSourceKEY, documentSource.name)
                .apply()
        }



    }
}