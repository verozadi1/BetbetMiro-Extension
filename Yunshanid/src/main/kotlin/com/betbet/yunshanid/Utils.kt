package com.betbet.yunshanid

import org.jsoup.nodes.Element

fun Element.safeText(vararg queries: String): String? {
    queries.forEach {
        val text = selectFirst(it)?.text()?.trim()
        if (!text.isNullOrBlank()) return text
    }
    return null
}

fun Element.safeAttr(
    attr: String,
    vararg queries: String
): String? {

    queries.forEach {
        val value = selectFirst(it)?.attr(attr)
        if (!value.isNullOrBlank()) return value
    }

    return null
}