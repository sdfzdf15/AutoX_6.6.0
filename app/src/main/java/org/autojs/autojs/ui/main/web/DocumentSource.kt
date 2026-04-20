package org.autojs.autojs.ui.main.web

enum class DocumentSource(val sourceName: String, val uri: String, val isLocal: Boolean = false) {
    DOC_V1("在线文档1(dayudada)", "https://autoxjs.dayudada.com/"),
    DOC_V2("在线文档2(sdfzdf15)", "https://sdfzdf15.github.io/AutoxDocs/"),
    DOC_V3("在线文档3(五云文档)", "https://www.wuyunai.com/docs/v8/"),
    DOC_V4("在线文档4(自定义文档)", "https://autoxjs.dayudada.com/"),
    DOC_V1_LOCAL("本地文档1(自带目录)", "docs/v1", true),
    DOC_V2_LOCAL("本地文档2(脚本目录)", "file:///sdcard/脚本/docs/v1/", true)

}