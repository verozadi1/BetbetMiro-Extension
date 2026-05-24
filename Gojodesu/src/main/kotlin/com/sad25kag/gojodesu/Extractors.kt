package com.sad25kag.gojodesu

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder

open class Kotakajaib : ExtractorApi() {
    override val name = "Kotakajaib"
    override val mainUrl = "https://kotakajaib.me"
    override val requiresReferer = true

    private val hostHints = listOf(
        "kotakajaib",
        "turbosplayer",
        "strp2p",
        "rpmvid",
        "filemoon",
        "streamwish",
        "wishfast",
        "vidhide",
        "vidguard",
        "voe",
        "dood",
        "streamtape",
        "mixdrop",
        "mp4upload",
        "krakenfiles",
        "acefile",
        "googlevideo",
        "blogger"
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to (referer ?: "https://gojodesu.com/")
        )

        val visited = linkedSetOf<String>()
        val directHls = linkedMapOf<String, String>()
        val queue = ArrayDeque<Pair<String, String>>()
        val extractorFallback = linkedSetOf<Pair<String, String>>()

        fun queueUrl(raw: String?, baseUrl: String, pageReferer: String = baseUrl) {
            val resolved = normalizeUrl(raw ?: return, baseUrl)
            if (resolved.isBlank() || isBadUrl(resolved)) return

            when {
                resolved.contains(".m3u8", true) -> directHls[resolved] = pageReferer
                isLikelyPlayer(resolved) -> queue.add(resolved to pageReferer)
            }
        }

        fun parsePage(html: String, baseUrl: String, pageReferer: String) {
            extractM3u8Urls(html).forEach { directHls[normalizeUrl(it, baseUrl)] = pageReferer }
            extractPossibleUrls(html).forEach { queueUrl(it, baseUrl, pageReferer) }

            val unpacked = runCatching {
                if (!getPacked(html).isNullOrEmpty()) getAndUnpack(html) else null
            }.getOrNull()

            if (!unpacked.isNullOrBlank()) {
                extractM3u8Urls(unpacked).forEach { directHls[normalizeUrl(it, baseUrl)] = pageReferer }
                extractPossibleUrls(unpacked).forEach { queueUrl(it, baseUrl, pageReferer) }
                extractBase64DecodedUrls(unpacked).forEach { queueUrl(it, baseUrl, pageReferer) }
            }

            val doc = Jsoup.parse(html)
            doc.select(
                "ul#dropdown-server li a[data-frame], " +
                    "a[data-frame], [data-frame], [data-url], [data-src], [data-video], " +
                    "iframe[src], iframe[data-src], embed[src], source[src], video[src], a[href], option[value]"
            ).forEach { element ->
                val attrs = listOf(
                    element.attr("data-frame"),
                    element.attr("data-url"),
                    element.attr("data-src"),
                    element.attr("data-video"),
                    element.attr("src"),
                    element.attr("href"),
                    element.attr("value")
                )

                attrs.forEach { raw ->
                    if (raw.isBlank()) return@forEach
                    queueUrl(raw, baseUrl, pageReferer)

                    decodeBase64(raw)?.let { decoded ->
                        if (decoded.contains(".m3u8", true)) {
                            extractM3u8Urls(decoded).forEach { directHls[normalizeUrl(it, baseUrl)] = pageReferer }
                        } else {
                            queueUrl(decoded, baseUrl, pageReferer)
                            extractPossibleUrls(decoded).forEach { queueUrl(it, baseUrl, pageReferer) }
                        }
                    }
                }
            }

            extractBase64DecodedUrls(html).forEach { decoded ->
                if (decoded.contains(".m3u8", true)) {
                    extractM3u8Urls(decoded).forEach { directHls[normalizeUrl(it, baseUrl)] = pageReferer }
                } else {
                    queueUrl(decoded, baseUrl, pageReferer)
                    extractPossibleUrls(decoded).forEach { queueUrl(it, baseUrl, pageReferer) }
                }
            }
        }

        queueUrl(url, referer ?: "https://gojodesu.com/", referer ?: "https://gojodesu.com/")

        var safety = 0
        while (queue.isNotEmpty() && safety++ < 80) {
            val (next, pageReferer) = queue.removeFirst()
            if (!visited.add(next)) continue

            if (next.contains(".m3u8", true)) {
                directHls[next] = pageReferer
                continue
            }

            if (isKnownExtractorHost(next)) {
                extractorFallback.add(next to pageReferer)
            }

            val response = runCatching {
                app.get(
                    next,
                    referer = pageReferer,
                    headers = headers,
                    allowRedirects = true,
                    timeout = 25L
                )
            }.getOrNull()

            if (response != null) {
                parsePage(response.text, next, next)
            }
        }

        directHls.forEach { (m3u8, m3u8Referer) ->
            generateM3u8(
                source = name,
                streamUrl = m3u8,
                referer = m3u8Referer
            ).forEach(callback)
        }

        if (directHls.isNotEmpty()) return

        extractorFallback.forEach { (frame, frameReferer) ->
            loadExtractor(frame, frameReferer, subtitleCallback, callback)
        }
    }

    private fun extractM3u8Urls(text: String): List<String> {
        val urls = linkedSetOf<String>()
        val clean = text.cleanEscapedUrl()

        Regex("""https?://[^"'\\\s<>]+\.m3u8[^"'\\\s<>]*""", RegexOption.IGNORE_CASE)
            .findAll(clean)
            .map { it.value.cleanEscapedUrl() }
            .forEach { urls.add(it) }

        Regex("""https?%3A%2F%2F[^"'\\\s<>]+?\.m3u8[^"'\\\s<>]*""", RegexOption.IGNORE_CASE)
            .findAll(clean)
            .map { URLDecoder.decode(it.value, "UTF-8").cleanEscapedUrl() }
            .forEach { urls.add(it) }

        return urls.toList()
    }

    private fun extractPossibleUrls(text: String): List<String> {
        val urls = linkedSetOf<String>()
        val clean = text.cleanEscapedUrl()

        Regex("""https?:\\?/\\?/[^"'\\\s<>]+""", RegexOption.IGNORE_CASE)
            .findAll(clean)
            .map { it.value.cleanEscapedUrl() }
            .forEach { urls.add(it) }

        Regex("""['"]((?:https?:)?//[^'"]+)['"]""", RegexOption.IGNORE_CASE)
            .findAll(clean)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { it.cleanEscapedUrl() }
            .forEach { urls.add(it) }

        Regex("""(?:file|src|url|source|hls|video|videoUrl|streamUrl|embed|embedUrl)\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
            .findAll(clean)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { it.cleanEscapedUrl() }
            .forEach { urls.add(it) }

        Regex("""atob\(['"]([^'"]+)['"]\)""", RegexOption.IGNORE_CASE)
            .findAll(clean)
            .mapNotNull { match -> decodeBase64(match.groupValues[1]) }
            .forEach { urls.add(it.cleanEscapedUrl()) }

        return urls.toList()
    }

    private fun extractBase64DecodedUrls(text: String): List<String> {
        val decoded = linkedSetOf<String>()

        Regex("""atob\(['"]([^'"]{8,})['"]\)""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { match -> decodeBase64(match.groupValues[1]) }
            .forEach { decoded.add(it.cleanEscapedUrl()) }

        Regex("""['"]([A-Za-z0-9+/=]{60,})['"]""")
            .findAll(text)
            .mapNotNull { match -> decodeBase64(match.groupValues[1]) }
            .filter {
                it.contains("http", true) ||
                    it.contains(".m3u8", true) ||
                    it.contains("turbosplayer", true) ||
                    it.contains("kotakajaib", true)
            }
            .forEach { decoded.add(it.cleanEscapedUrl()) }

        return decoded.toList()
    }

    private fun decodeBase64(raw: String): String? {
        val clean = raw.trim().replace("\\s".toRegex(), "")
        if (clean.length < 8) return null

        return runCatching {
            String(Base64.decode(clean, Base64.DEFAULT))
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun normalizeUrl(url: String, baseUrl: String): String {
        val clean = url.cleanEscapedUrl()
        if (clean.isBlank()) return ""

        return runCatching {
            when {
                clean.startsWith("http://", true) || clean.startsWith("https://", true) -> clean
                clean.startsWith("//") -> "https:$clean"
                clean.startsWith("/") -> {
                    val origin = URI(baseUrl)
                    "${origin.scheme}://${origin.host}$clean"
                }
                else -> URI(baseUrl).resolve(clean).toString()
            }
        }.getOrDefault(clean)
    }

    private fun isLikelyPlayer(url: String): Boolean {
        val lower = url.lowercase()
        if (isBadUrl(lower)) return false

        return lower.contains(".m3u8") ||
            hostHints.any { lower.contains(it) } ||
            lower.contains("/embed/") ||
            lower.contains("/file/") ||
            lower.contains("/v/") ||
            lower.contains("/player/")
    }

    private fun isKnownExtractorHost(url: String): Boolean {
        val lower = url.lowercase()
        return listOf(
            "filemoon",
            "streamwish",
            "wishfast",
            "vidhide",
            "vidguard",
            "voe",
            "dood",
            "streamtape",
            "mixdrop",
            "mp4upload",
            "krakenfiles",
            "acefile",
            "pixeldrain",
            "ok.ru",
            "odnoklassniki",
            "drive.google"
        ).any { lower.contains(it) }
    }

    private fun isBadUrl(url: String): Boolean {
        val lower = url.lowercase()

        return lower.isBlank() ||
            lower.startsWith("javascript:") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("tel:") ||
            lower.startsWith("data:") ||
            lower.startsWith("blob:") ||
            lower.contains("adsbygoogle") ||
            lower.contains("googlesyndication") ||
            lower.contains("doubleclick") ||
            lower.contains("analytics") ||
            lower.contains("histats") ||
            lower.contains("googletagmanager") ||
            lower.contains("cloudflareinsights") ||
            lower.contains("wp-content/themes") ||
            lower.endsWith(".css") ||
            lower.endsWith(".js") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".svg")
    }

    private fun String.cleanEscapedUrl(): String {
        return this
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003D", "=")
            .replace("\\u003A", ":")
            .replace("&amp;", "&")
            .trim()
    }
}
