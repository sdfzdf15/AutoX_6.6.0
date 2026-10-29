package org.autojs.autojs.ui.main.web

enum class DocumentSource(val sourceName: String, val uri: String, val isLocal: Boolean = false) {
    DOC_V1("在线文档(sdfzdf15)", "https://sdfzdf15.github.io/AutoxDocs/"),
    DOC_V2("在线文档(五云文档)", "https://www.wuyunai.com/docs/v8/"),
    DOC_V1_LOCAL("本地文档", "docs/v1", true)
}
