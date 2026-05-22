package com.gomunime

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLDecoder

suspend fun loadGomunimeLinks(
    data: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val startResponse = app.get(data, referer = "https://gomunime.top/")
    val startDocument = startResponse.document
    val startHtml = startResponse.text

    val visited = linkedSetOf<String>()
    val playerLinks = linkedSetOf<String>()
    val m3u8Links = linkedMapOf<String, String>()
    val directVideos = linkedMapOf<String, String>()

    collectSourcesFromPage(
        document = startDocument,
        html = startHtml,
        baseUrl = data,
        outPlayers = playerLinks,
        outM3u8 = m3u8Links,
        outDirect = directVideos,
        referer = data
    )

    startDocument.select("select.mirror option[value]").forEach { option ->
        val value = option.attr("value").trim()
        if (value.isBlank()) return@forEach

        decodeIframeUrl(value)?.let { decoded ->
            val normalized = normalizeUrl(decoded, data)

            when {
                normalized.contains(".m3u8", true) -> m3u8Links[normalized] = data
                normalized.isDirectVideo() -> directVideos[normalized] = data
                else -> playerLinks.add(normalized)
            }
        }
    }

    playerLinks.toList().forEach { player ->
        crawlPlayer(
            url = player,
            referer = data,
            visited = visited,
            outM3u8 = m3u8Links,
            outDirect = directVideos,
            depth = 0
        )
    }

    m3u8Links.forEach { (m3u8, referer) ->
        generateM3u8(
            source = "Gomunime",
            streamUrl = m3u8,
            referer = referer
        ).forEach(callback)
    }

    directVideos.forEach { (video, referer) ->
        callback(
            newExtractorLink(
                source = "Gomunime",
                name = "Gomunime",
                url = video,
                type = if (video.contains(".m3u8", true)) {
                    ExtractorLinkType.M3U8
                } else {
                    ExtractorLinkType.VIDEO
                }
            ) {
                this.referer = referer
                this.quality = qualityFromUrl(video)
            }
        )
    }

    if (m3u8Links.isNotEmpty() || directVideos.isNotEmpty()) {
        return true
    }

    var fallbackSent = false

    playerLinks.forEach { player ->
        loadExtractor(player, data, subtitleCallback, callback)
        fallbackSent = true
    }

    return fallbackSent
}

private suspend fun crawlPlayer(
    url: String,
    referer: String,
    visited: MutableSet<String>,
    outM3u8: MutableMap<String, String>,
    outDirect: MutableMap<String, String>,
    depth: Int
) {
    if (depth > 5) return

    val cleanUrl = normalizeUrl(url, referer)
    if (cleanUrl in visited) return
    visited.add(cleanUrl)

    val response = runCatching {
        app.get(
            cleanUrl,
            referer = referer,
            allowRedirects = true
        )
    }.getOrNull() ?: return

    val currentUrl = response.url
    val html = response.text
    val document = response.document

    if (html.trimStart().startsWith("#EXTM3U")) {
        outM3u8[currentUrl] = referer
        return
    }

    val nextPlayers = linkedSetOf<String>()

    collectSourcesFromPage(
        document = document,
        html = html,
        baseUrl = currentUrl,
        outPlayers = nextPlayers,
        outM3u8 = outM3u8,
        outDirect = outDirect,
        referer = cleanUrl
    )

    val unpacked = runCatching {
        if (!getPacked(html).isNullOrEmpty()) getAndUnpack(html) else null
    }.getOrNull()

    if (!unpacked.isNullOrBlank()) {
        collectSourcesFromText(
            text = unpacked,
            baseUrl = currentUrl,
            outPlayers = nextPlayers,
            outM3u8 = outM3u8,
            outDirect = outDirect,
            referer = cleanUrl
        )
    }

    extractBase64DecodedTexts(html).forEach { decoded ->
        collectSourcesFromText(
            text = decoded,
            baseUrl = currentUrl,
            outPlayers = nextPlayers,
            outM3u8 = outM3u8,
            outDirect = outDirect,
            referer = cleanUrl
        )
    }

    nextPlayers
        .filter { it !in visited }
        .forEach { next ->
            crawlPlayer(
                url = next,
                referer = currentUrl,
                visited = visited,
                outM3u8 = outM3u8,
                outDirect = outDirect,
                depth = depth + 1
            )
        }
}

private fun collectSourcesFromPage(
    document: Document,
    html: String,
    baseUrl: String,
    outPlayers: MutableSet<String>,
    outM3u8: MutableMap<String, String>,
    outDirect: MutableMap<String, String>,
    referer: String
) {
    document.select(
        "iframe[src], " +
            "embed[src], " +
            "source[src], " +
            "video[src], " +
            "a[href], " +
            "[data-url], " +
            "[data-src], " +
            "[data-link], " +
            "[data-video], " +
            "[data-file]"
    ).forEach { element ->
        val raw = element.attr("src")
            .ifBlank { element.attr("href") }
            .ifBlank { element.attr("data-url") }
            .ifBlank { element.attr("data-src") }
            .ifBlank { element.attr("data-link") }
            .ifBlank { element.attr("data-video") }
            .ifBlank { element.attr("data-file") }
            .trim()

        if (raw.isBlank()) return@forEach

        val normalized = normalizeUrl(raw, baseUrl)

        when {
            normalized.contains(".m3u8", true) -> outM3u8[normalized] = referer
            normalized.isDirectVideo() -> outDirect[normalized] = referer
            isLikelyPlayerUrl(normalized) -> outPlayers.add(normalized)
        }
    }

    document.select("select.mirror option[value]").forEach { option ->
        val value = option.attr("value").trim()
        if (value.isBlank()) return@forEach

        decodeIframeUrl(value)?.let { decoded ->
            val normalized = normalizeUrl(decoded, baseUrl)

            when {
                normalized.contains(".m3u8", true) -> outM3u8[normalized] = referer
                normalized.isDirectVideo() -> outDirect[normalized] = referer
                isLikelyPlayerUrl(normalized) -> outPlayers.add(normalized)
            }
        }
    }

    collectSourcesFromText(
        text = html,
        baseUrl = baseUrl,
        outPlayers = outPlayers,
        outM3u8 = outM3u8,
        outDirect = outDirect,
        referer = referer
    )
}

private fun collectSourcesFromText(
    text: String,
    baseUrl: String,
    outPlayers: MutableSet<String>,
    outM3u8: MutableMap<String, String>,
    outDirect: MutableMap<String, String>,
    referer: String
) {
    extractM3u8Urls(text, baseUrl).forEach { m3u8 ->
        outM3u8[m3u8] = referer
    }

    extractPossibleUrls(text).forEach { raw ->
        val normalized = normalizeUrl(raw, baseUrl)

        when {
            normalized.contains(".m3u8", true) -> outM3u8[normalized] = referer
            normalized.isDirectVideo() -> outDirect[normalized] = referer
            isLikelyPlayerUrl(normalized) -> outPlayers.add(normalized)
        }
    }

    findFileVariables(text).forEach { raw ->
        val normalized = normalizeUrl(raw, baseUrl)

        when {
            normalized.contains(".m3u8", true) -> outM3u8[normalized] = referer
            normalized.isDirectVideo() -> outDirect[normalized] = referer
            isLikelyPlayerUrl(normalized) -> outPlayers.add(normalized)
        }
    }
}

private fun decodeIframeUrl(encoded: String): String? {
    val decoded = runCatching {
        base64Decode(encoded).trim()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

    return when {
        decoded.startsWith("http", true) -> decoded
        decoded.startsWith("//") -> "https:$decoded"
        decoded.contains("<iframe", true) || decoded.contains("src=", true) -> {
            Regex("""src=["']([^"']+)["']""")
                .find(decoded)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
        }
        decoded.contains(".m3u8", true) -> decoded
        else -> null
    }
}

private fun extractM3u8Urls(
    text: String,
    baseUrl: String
): List<String> {
    val urls = linkedSetOf<String>()

    Regex("""https?://[^"'\\\s<>]+\.m3u8[^"'\\\s<>]*""")
        .findAll(text)
        .map { it.value.cleanEscapedUrl() }
        .forEach { urls.add(it) }

    Regex("""https?%3A%2F%2F[^"'\\\s<>]+?\.m3u8[^"'\\\s<>]*""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .map {
            runCatching {
                URLDecoder.decode(it.value, "UTF-8")
            }.getOrDefault(it.value)
        }
        .map { it.cleanEscapedUrl() }
        .forEach { urls.add(it) }

    Regex("""["']([^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .map { normalizeUrl(it, baseUrl) }
        .forEach { urls.add(it) }

    Regex("""(?:file|src|url|source|hls|video)\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .map { normalizeUrl(it, baseUrl) }
        .forEach { urls.add(it) }

    return urls.toList()
}

private fun extractPossibleUrls(text: String): List<String> {
    val urls = linkedSetOf<String>()

    Regex("""https?:\\?/\\?/[^"'\\\s<>]+""")
        .findAll(text)
        .map { it.value.cleanEscapedUrl() }
        .forEach { urls.add(it) }

    Regex("""['"]((?:https?:)?//[^'"]+)['"]""")
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .map { it.cleanEscapedUrl() }
        .forEach { urls.add(it) }

    Regex("""(?:href|src|file|url|source|hls|video)\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .map { it.cleanEscapedUrl() }
        .forEach { urls.add(it) }

    return urls.toList()
}

private fun findFileVariables(text: String): List<String> {
    val urls = linkedSetOf<String>()

    Regex("""file\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .forEach { urls.add(it.cleanEscapedUrl()) }

    Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .forEach { urls.add(it.cleanEscapedUrl()) }

    Regex("""source\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .forEach { urls.add(it.cleanEscapedUrl()) }

    return urls.toList()
}

private fun extractBase64DecodedTexts(text: String): List<String> {
    val decoded = linkedSetOf<String>()

    Regex("""atob\(["']([^"']{12,})["']\)""")
        .findAll(text)
        .mapNotNull { match ->
            runCatching {
                base64Decode(match.groupValues[1]).trim()
            }.getOrNull()
        }
        .filter { it.contains("http", true) || it.contains(".m3u8", true) }
        .forEach { decoded.add(it.cleanEscapedUrl()) }

    Regex("""["']([A-Za-z0-9+/=]{40,})["']""")
        .findAll(text)
        .mapNotNull { match ->
            runCatching {
                base64Decode(match.groupValues[1]).trim()
            }.getOrNull()
        }
        .filter {
            it.contains("http", true) ||
                it.contains(".m3u8", true) ||
                it.contains("play.php", true)
        }
        .forEach { decoded.add(it.cleanEscapedUrl()) }

    return decoded.toList()
}

private fun Document.findDirectVideoSource(): String? {
    val candidates = sequenceOf(
        selectFirst("video source[src]")?.attr("src"),
        selectFirst("source[src*='googlevideo']")?.attr("src"),
        selectFirst("source[src*='.mp4']")?.attr("src"),
        selectFirst("source[src*='.m3u8']")?.attr("src"),
        selectFirst("video[src]")?.attr("src"),
        Regex("""https?://[^"' ]+googlevideo\.com/videoplayback[^"' ]*""")
            .find(html())?.value,
        Regex("""https?://[^"' ]+\.mp4[^"' ]*""")
            .find(html())?.value,
        Regex("""https?://[^"' ]+\.m3u8[^"' ]*""")
            .find(html())?.value
    )

    return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
}

private fun isLikelyPlayerUrl(url: String): Boolean {
    return url.contains("googlevideo", true) ||
        url.contains("blogger", true) ||
        url.contains("blogspot", true) ||
        url.contains("mp4upload", true) ||
        url.contains("stream", true) ||
        url.contains("desustream", true) ||
        url.contains("kotakajaib", true) ||
        url.contains("turbosplayer", true) ||
        url.contains("drive.google", true) ||
        url.contains("lh3.googleusercontent", true) ||
        url.contains("/embed/", true) ||
        url.contains("/player/", true) ||
        url.contains("/file/", true) ||
        url.contains("play.php", true) ||
        url.contains(".mp4", true) ||
        url.contains(".m3u8", true)
}

private fun String.isDirectVideo(): Boolean {
    return contains(".mp4", true) ||
        contains("googlevideo.com/videoplayback", true) ||
        contains("lh3.googleusercontent.com", true)
}

private fun normalizeUrl(
    url: String,
    baseUrl: String
): String {
    val clean = url.cleanEscapedUrl()

    return when {
        clean.startsWith("http", true) -> clean
        clean.startsWith("//") -> "https:$clean"
        clean.startsWith("/") -> {
            val origin = Regex("""^https?://[^/]+""")
                .find(baseUrl)
                ?.value
                ?: "https://gomunime.top"
            "$origin$clean"
        }
        else -> {
            runCatching {
                URI(baseUrl).resolve(clean).toString()
            }.getOrElse {
                val origin = Regex("""^https?://[^/]+""")
                    .find(baseUrl)
                    ?.value
                    ?: "https://gomunime.top"
                "$origin/$clean"
            }
        }
    }
}

private fun String.cleanEscapedUrl(): String {
    return this
        .replace("\\/", "/")
        .replace("\\u0026", "&")
        .replace("&amp;", "&")
        .trim()
}

private fun qualityFromUrl(url: String): Int {
    return when {
        url.contains("1080", true) -> Qualities.P1080.value
        url.contains("720", true) -> Qualities.P720.value
        url.contains("480", true) -> Qualities.P480.value
        url.contains("360", true) -> Qualities.P360.value
        Regex("""itag=(37|96|137)""").containsMatchIn(url) -> Qualities.P1080.value
        Regex("""itag=(22|59)""").containsMatchIn(url) -> Qualities.P720.value
        Regex("""itag=18""").containsMatchIn(url) -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}