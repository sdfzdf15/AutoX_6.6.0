package org.autojs.autojs.ui.main.web

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import fi.iki.elonen.NanoHTTPD
import org.autojs.autojs.ui.widget.SwipeRefreshWebView
import org.autojs.autojs.ui.widget.fillMaxSize
import java.io.File
import java.io.FileNotFoundException

/**
 * 文档 WebView 管理 Fragment。
 *
 * 支持三种文档来源：
 *  - 在线文档：直接 loadUrl
 *  - APK 内置 assets 文档（docs/v1）：NanoHTTPD + AssetManager
 *  - 用户自定义的本地目录：NanoHTTPD + File
 *
 * 本地文档统一由本地回环 HTTP 服务提供：
 *  - 只监听 127.0.0.1，不会把文件暴露到局域网
 *  - 同一个目录复用同一个服务实例，不会每次 loadHomeDocument 都换端口
 *  - 对外通过 [localDocBaseUrl] / [docUrl] 暴露地址，调用方不要自己拼端口
 */
class EditorAppManager : Fragment() {

    val swipeRefreshWebView: SwipeRefreshWebView by lazy {
        SwipeRefreshWebView(requireContext())
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

    override fun onDestroy() {
        // 注意：不能放在 onDestroyView 里。
        // ViewPager2 切走页面时也会触发 onDestroyView，而 EditorView 里的文档抽屉
        // 可能还在使用同一个本地服务，提前 stop 会把它一起干掉。
        if (activity?.isFinishing == true) {
            stopServer()
        }
        super.onDestroy()
    }

    companion object {

        const val TAG = "EditorAppManager"

        const val DocumentSourceKEY = "DocumentSource"

        /** 选中自定义文档源时 DocumentSourceKEY 的取值 */
        const val CUSTOM_SOURCE = "CUSTOM_SOURCE"

        /** 选中自定义文档源时保存的地址 */
        const val CUSTOM_SOURCE_URI_KEY = "CustomSourceUri"

        /** 选中自定义文档源时保存的地址是否为本地路径 */
        const val CUSTOM_SOURCE_IS_LOCAL_KEY = "CustomSourceIsLocal"

        private const val MIME_TEXT = "text/plain; charset=utf-8"

        private var saveStatus: SharedPreferences? = null

        /** 当前正在运行的本地文档服务（同一 root 复用） */
        @Volatile
        private var localServer: LocalServer? = null

        /** 创建 / 停止服务统一用这把锁 */
        private val serverLock = Any()

        private class LocalServer(
            val root: String,
            val baseUrl: String,
            val server: NanoHTTPD
        )

        @Synchronized
        fun getSaveStatus(context: Context): SharedPreferences {
            if (saveStatus == null) {
                // 用 applicationContext 避免静态引用 Activity 泄漏
                saveStatus = context.applicationContext
                    .getSharedPreferences(TAG, Context.MODE_PRIVATE)
            }
            return saveStatus!!
        }

        /** 停止本地服务（一般只在退出 / 切换目录时调用） */
        fun stopServer() {
            synchronized(serverLock) {
                localServer?.let { entry ->
                    runCatching { entry.server.stop() }
                }
                localServer = null
            }
        }

        /** 加载首页文档：根据保存的文档来源加载，支持内置源与自定义源 */
        fun loadHomeDocument(webView: WebView) {
            val (uri, isLocal) = resolveCurrentSource(webView.context)
            openDocument(webView, uri, isLocal)
        }

        /** 当前文档源的地址（自定义源返回用户填写的地址） */
        fun currentSourceUri(context: Context): String = resolveCurrentSource(context).first

        /** 当前文档源是否为本地文档 */
        fun isCurrentSourceLocal(context: Context): Boolean = resolveCurrentSource(context).second

        /**
         * 当前是本地文档时返回本地服务的根地址（例如 http://127.0.0.1:12345/），
         * 在线文档返回 null。调用方必须在拼接相对路径时使用，不要再写死端口。
         */
        fun localDocBaseUrl(webView: WebView): String? {
            val (uri, isLocal) = resolveCurrentSource(webView.context)
            if (!isLocal) return null
            return openDocument(webView, uri, true)
        }

        /** 把根地址和相对路径安全地拼起来（自动处理斜杠） */
        fun docUrl(base: String, path: String): String =
            base.trimEnd('/') + "/" + path.trimStart('/')

        /** 切换到内置文档源 */
        fun switchDocument(webView: WebView, documentSource: DocumentSource) {
            if (openDocument(webView, documentSource.uri, documentSource.isLocal) == null) {
                // 加载失败就不要把坏源写进去，否则下次启动还是白屏
                return
            }
            getSaveStatus(webView.context).edit()
                .putString(DocumentSourceKEY, documentSource.name)
                .apply()
        }

        /** 切换到自定义文档源 */
        fun switchDocument(webView: WebView, uri: String, isLocal: Boolean) {
            if (openDocument(webView, uri, isLocal) == null) {
                return
            }
            getSaveStatus(webView.context).edit()
                .putString(DocumentSourceKEY, CUSTOM_SOURCE)
                .putString(CUSTOM_SOURCE_URI_KEY, uri)
                .putBoolean(CUSTOM_SOURCE_IS_LOCAL_KEY, isLocal)
                .apply()
        }

        private fun resolveCurrentSource(context: Context): Pair<String, Boolean> {
            val sp = getSaveStatus(context)
            val name = sp.getString(DocumentSourceKEY, DocumentSource.DOC_V1_LOCAL.name)
            if (name == CUSTOM_SOURCE) {
                val uri = sp.getString(CUSTOM_SOURCE_URI_KEY, null)
                if (!uri.isNullOrBlank()) {
                    return uri to sp.getBoolean(CUSTOM_SOURCE_IS_LOCAL_KEY, false)
                }
            }
            val source = resolveBuiltinSource(name)
            return source.uri to source.isLocal
        }

        private fun resolveBuiltinSource(name: String?): DocumentSource = try {
            DocumentSource.valueOf(name ?: DocumentSource.DOC_V1_LOCAL.name)
        } catch (e: Exception) {
            DocumentSource.DOC_V1_LOCAL
        }

        /**
         * 加载文档，返回真正加载的地址；失败返回 null。
         * 在线：直接 loadUrl；本地：确保服务已启动后再 loadUrl。
         */
        private fun openDocument(webView: WebView, uri: String, isLocal: Boolean): String? {
            if (!isLocal) {
                if (uri.isBlank()) return null
                webView.loadUrl(uri)
                return uri
            }
            val entry = ensureLocalServer(webView.context, uri)
            if (entry == null) {
                webView.loadUrl("about:blank")
                return null
            }
            webView.loadUrl(entry.baseUrl)
            return entry.baseUrl
        }

        /** 同一目录复用同一个服务，避免每次加载都换端口导致别的页面失效 */
        private fun ensureLocalServer(context: Context, uri: String): LocalServer? =
            synchronized(serverLock) {
                val root = normalizeLocalRoot(uri)

                val current = localServer
                if (current != null && current.root == root && current.server.isAlive) {
                    return@synchronized current
                }

                current?.let { runCatching { it.server.stop() } }
                localServer = null

                val server = buildServer(context, root)
                try {
                    // 0 = 随机端口；true = 守护线程，避免 stop 漏调时线程残留
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    runCatching { server.stop() }
                    return@synchronized null
                }

                LocalServer(
                    root = root,
                    baseUrl = "http://127.0.0.1:${server.listeningPort}/",
                    server = server
                ).also { localServer = it }
            }

        /** 把用户填的本地地址统一成：绝对文件路径 或 assets 目录（如 docs/v1） */
        private fun normalizeLocalRoot(uri: String): String {
            val raw = uri.trim()
            if (raw.startsWith("file://", ignoreCase = true)) {
                val path = runCatching { Uri.parse(raw).path }.getOrNull()
                if (!path.isNullOrEmpty()) {
                    // file://host/path -> /path；去掉尾斜杠，保证同一目录只有一种写法
                    return path.trimEnd('/').ifEmpty { "/" }
                }
                return "/" + raw.removePrefix("file://").trimStart('/')
            }
            if (raw.startsWith("/")) return raw.trimEnd('/').ifEmpty { "/" }    // 绝对路径
            return raw.trimEnd('/')                                             // assets 目录（docs/v1）
        }

        private fun buildServer(context: Context, root: String): NanoHTTPD {
            val assets = context.assets
            val isFileMode = root.startsWith("/")
            val canonicalRoot: File? = if (isFileMode) {
                try {
                    File(root).canonicalFile
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            // 固定 127.0.0.1：NanoHTTPD(int) 会绑定 0.0.0.0，同局域网的人能直接访问
            return object : NanoHTTPD("127.0.0.1", 0) {

                override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
                    val path = (session.uri ?: "").removePrefix("/").ifEmpty { "index.html" }

                    // 防路径穿越（本地目录和 assets 都挡一下）
                    if (path.split('/').any { it == ".." }) {
                        return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.FORBIDDEN,
                            MIME_TEXT,
                            "Forbidden"
                        )
                    }

                    return try {
                        if (isFileMode) serveFile(path) else serveAsset(path)
                    } catch (e: FileNotFoundException) {
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.NOT_FOUND,
                            MIME_TEXT,
                            "Not found: $path"
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.INTERNAL_ERROR,
                            MIME_TEXT,
                            "Server error: ${e.message}"
                        )
                    }
                }

                private fun serveFile(path: String): NanoHTTPD.Response {
                    val rootDir = canonicalRoot ?: return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.NOT_FOUND,
                        MIME_TEXT,
                        "Root not found"
                    )

                    var file = File(rootDir, path).canonicalFile

                    // 防路径穿越
                    if (file.path != rootDir.path &&
                        !file.path.startsWith(rootDir.path + File.separator)
                    ) {
                        return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.FORBIDDEN,
                            MIME_TEXT,
                            "Forbidden"
                        )
                    }

                    // 目录 -> index.html
                    if (file.isDirectory) {
                        file = File(file, "index.html")
                    }

                    if (!file.isFile) {
                        return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.NOT_FOUND,
                            MIME_TEXT,
                            "Not found: $path"
                        )
                    }

                    // MIME 用最终文件名算，否则目录首页会变成 octet-stream 被下载
                    return NanoHTTPD.newChunkedResponse(
                        NanoHTTPD.Response.Status.OK,
                        getMimeType(file.name),
                        file.inputStream()
                    )
                }

                private fun serveAsset(path: String): NanoHTTPD.Response {
                    val base = root.trim('/')
                    val assetPath = if (base.isEmpty()) path else "$base/$path"
                    return try {
                        NanoHTTPD.newChunkedResponse(
                            NanoHTTPD.Response.Status.OK,
                            getMimeType(path),
                            assets.open(assetPath)
                        )
                    } catch (e: FileNotFoundException) {
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.NOT_FOUND,
                            MIME_TEXT,
                            "Not found: $path"
                        )
                    }
                }
            }
        }

        /** 根据文件扩展名获取 MIME 类型 */
        fun getMimeType(path: String): String {
            val ext = path.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "html", "htm" -> "text/html; charset=utf-8"
                "css" -> "text/css; charset=utf-8"
                "js", "mjs" -> "application/javascript; charset=utf-8"
                "json", "map" -> "application/json; charset=utf-8"
                "md", "markdown" -> "text/markdown; charset=utf-8"
                "xml" -> "application/xml; charset=utf-8"
                "txt" -> "text/plain; charset=utf-8"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "svg" -> "image/svg+xml"
                "webp" -> "image/webp"
                "ico" -> "image/x-icon"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                "ttf" -> "font/ttf"
                "otf" -> "font/otf"
                "wasm" -> "application/wasm"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
        }
    }
}