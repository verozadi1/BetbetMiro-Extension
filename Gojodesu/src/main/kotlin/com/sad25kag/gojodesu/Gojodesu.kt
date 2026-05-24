package com.sad25kag.gojodesu

import android.util.Base64
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

class Gojodesu : MainAPI() {
    override var mainUrl = "https://gojodesu.com"
    override var name = "Gojodesu🤖"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val hasDownloadSupport = false

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "page/%d/" to "Latest Release",
        "anime/page/%d/?order=latest&status=&sub=&type=" to "Latest Anime",
        "anime/page/%d/?order=popular&status=&sub=&type=" to "Popular",
        "anime/page/%d/?order=rating&status=&sub=&type=" to "Rating",

        "genres/action/page/%d/" to "Action",
        "genres/adventure/page/%d/" to "Adventure",
        "genres/comedy/page/%d/" to "Comedy",
        "genres/drama/page/%d/" to "Drama",
        "genres/ecchi/page/%d/" to "Ecchi",
        "genres/erotica/page/%d/" to "Erotica",
        "genres/fantasy/page/%d/" to "Fantasy",
        "genres/girls-love/page/%d/" to "Girls Love",
        "genres/horror/page/%d/" to "Horror",
        "genres/mystery/page/%d/" to "Mystery",
        "genres/romance/page/%d/" to "Romance",
        "genres/sci-fi/page/%d/" to "Sci-Fi",
        "genres/slice-of-life/page/%d/" to "Slice of Life",
        "genres/sports/page/%d/" to "Sports",
        "genres/supernatural/page/%d/" to "Supernatural",
        "genres/suspense/page/%d/" to "Suspense",

        "season/spring-2026/page/%d/" to "Spring 2026",
        "season/winter-2026/page/%d/" to "Winter 2026",
        "season/fall-2025/page/%d/" to "Fall 2025",
        "season/summer-2025/page/%d/" to "Summer 2025",
        "season/spring-2025/page/%d/" to "Spring 2025",
        "season/winter-2025/page/%d/" to "Winter 2025",

        "anime/page/%d/?order=latest&status=ongoing&sub=&type=" to "Ongoing",
        "anime/page/%d/?order=latest&status=completed&sub=&type=" to "Completed",
        "anime/page/%d/?order=latest&status=&sub=&type=tv" to "TV Series",
        "anime/page/%d/?order=latest&status=&sub=&type=movie" to "Movie",
        "anime/page/%d/?order=latest&status=&sub=&type=ova" to "OVA",
        "anime/page/%d/?order=latest&status=&sub=&type=ona" to "ONA",
        "anime/page/%d/?order=latest&status=&sub=&type=special" to "Special"
    )

    private val commonHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    private val playableHostHints = listOf(
        "kotakajaib",
        "turbosplayer",
        "strp2p",
        "rpmvid",
        "krakenfiles",
        "acefile",
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
        "pixeldrain",
        "ok.ru",
        "odnoklassniki",
        "blogger",
        "googlevideo",
        "gofile",
        "drive.google"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = fixUrl(request.data.format(page.coerceAtLeast(1)))
        val document = app.get(url, headers = commonHeaders, referer = mainUrl).document

        val items = document.select(
            "article, " +
                ".listupd article, " +
                ".bs article, " +
                ".postbody article, " +
                ".items article, " +
                ".bixbox article, " +
                "div[class*=bs]"
        ).mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(
            request.name,
            items,
            hasNext = document.selectFirst(
                "a.next, " +
                    ".pagination a:contains(Berikutnya), " +
                    ".pagination a:contains(Next), " +
                    "a.page-numbers:contains(»), " +
                    "a[href*='/page/${page + 1}/']"
            ) != null
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        if (q.isBlank()) return emptyList()

        val endpoints = listOf(
            "$mainUrl/?s=$q",
            "$mainUrl/anime/?s=$q"
        )

        val results = linkedMapOf<String, SearchResponse>()

        for (url in endpoints) {
            val document = runCatching {
                app.get(url, headers = commonHeaders, referer = mainUrl).document
            }.getOrNull() ?: continue

            document.select(
                "article, " +
                    ".listupd article, " +
                    ".bs article, " +
                    ".postbody article, " +
                    ".items article, " +
                    ".bixbox article"
            ).mapNotNull { it.toSearchResult() }
                .forEach { results[it.url] = it }

            if (results.isNotEmpty()) break
        }

        return results.values.toList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = commonHeaders, referer = mainUrl).document

        val rawTitle = document.selectFirst(
            "h1.entry-title, h1, .entry-title, meta[property=og:title]"
        )?.let { element ->
            if (element.hasAttr("content")) element.attr("content") else element.text()
        }?.trim()
            ?.ifBlank { null }
            ?: url.substringAfterLast("/").replace("-", " ")

        val title = rawTitle.cleanSeriesTitle()

        val poster = fixUrlNull(
            document.selectFirst(
                "img.wp-post-image, " +
                    ".thumb img, " +
                    ".poster img, " +
                    "meta[property=og:image], " +
                    "meta[name=twitter:image], " +
                    "picture img, " +
                    "img"
            )?.let { element ->
                if (element.hasAttr("content")) element.attr("content") else element.getImageAttr()
            }
        )

        val description = document.selectFirst(
            "div.entry-content p, " +
                "div.entry-content, " +
                ".desc, " +
                ".sinopsis, " +
                "meta[property=og:description], " +
                "meta[name=description]"
        )?.let { element ->
            if (element.hasAttr("content")) element.attr("content") else element.text()
        }?.trim()
            ?.takeIf { it.isNotBlank() }

        val tags = document.select(
            "a[href*='/genres/'], " +
                ".genre a, " +
                ".genres a, " +
                "a[href*='/type/']"
        ).map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val episodes = parseEpisodes(document, title, url)

        return newTvSeriesLoadResponse(title, url, guessType(title, url, tags), episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val visited = linkedSetOf<String>()
        val directLinks = linkedMapOf<String, String>()
        val extractorLinks = linkedSetOf<Pair<String, String>>()
        val queue = ArrayDeque<Pair<String, String>>()

        fun queueUrl(raw: String?, baseUrl: String, referer: String = baseUrl) {
            val normalized = normalizeAnyUrl(raw ?: return, baseUrl)
            if (normalized.isBlank() || isBadUrl(normalized)) return

            when {
                isDirectMedia(normalized) -> directLinks[normalized] = referer
                isLikelyPlayer(normalized) -> queue.add(normalized to referer)
            }
        }

        fun parseTextAndDoc(html: String, document: Document?, baseUrl: String, referer: String) {
            extractSubtitles(html, baseUrl, subtitleCallback)

            extractM3u8Urls(html).forEach { directLinks[normalizeAnyUrl(it, baseUrl)] = referer }
            extractMp4Urls(html).forEach { directLinks[normalizeAnyUrl(it, baseUrl)] = referer }
            extractPossibleUrls(html).forEach { queueUrl(it, baseUrl, referer) }
            extractBase64DecodedUrls(html).forEach { decoded ->
                extractM3u8Urls(decoded).forEach { directLinks[normalizeAnyUrl(it, baseUrl)] = referer }
                extractMp4Urls(decoded).forEach { directLinks[normalizeAnyUrl(it, baseUrl)] = referer }
                extractPossibleUrls(decoded).forEach { queueUrl(it, baseUrl, referer) }
                if (isLikelyPlayer(decoded)) queueUrl(decoded, baseUrl, referer)
            }

            val unpacked = runCatching {
                if (!getPacked(html).isNullOrEmpty()) getAndUnpack(html) else null
            }.getOrNull()

            if (!unpacked.isNullOrBlank()) {
                extractM3u8Urls(unpacked).forEach { directLinks[normalizeAnyUrl(it, baseUrl)] = referer }
                extractMp4Urls(unpacked).forEach { directLinks[normalizeAnyUrl(it, baseUrl)] = referer }
                extractPossibleUrls(unpacked).forEach { queueUrl(it, baseUrl, referer) }
            }

            document?.select(
                "iframe[src], iframe[data-src], iframe[data-litespeed-src], " +
                    "embed[src], source[src], video[src], video source[src], " +
                    "a[href], [data-url], [data-src], [data-link], [data-frame], [data-video], " +
                    "option[value], button[data-url], button[data-src]"
            )?.forEach { element ->
                val attrs = listOf(
                    element.attr("href"),
                    element.attr("src"),
                    element.attr("data-url"),
                    element.attr("data-src"),
                    element.attr("data-litespeed-src"),
                    element.attr("data-link"),
                    element.attr("data-video"),
                    element.attr("data-frame"),
                    element.attr("value")
                )

                attrs.forEach { raw ->
                    if (raw.isBlank()) return@forEach
                    queueUrl(raw, baseUrl, referer)

                    decodeBase64(raw)?.let { decoded ->
                        if (decoded.contains("<iframe", true) || decoded.contains("<source", true)) {
                            val decodedDoc = org.jsoup.Jsoup.parse(decoded)
                            parseTextAndDoc(decoded, decodedDoc, baseUrl, referer)
                        } else {
                            queueUrl(decoded, baseUrl, referer)
                            extractM3u8Urls(decoded).forEach { directLinks[normalizeAnyUrl(it, baseUrl)] = referer }
                            extractMp4Urls(decoded).forEach { directLinks[normalizeAnyUrl(it, baseUrl)] = referer }
                        }
                    }
                }
            }
        }

        val episodeResponse = app.get(
            data,
            referer = mainUrl,
            headers = commonHeaders,
            allowRedirects = true
        )
        parseTextAndDoc(episodeResponse.text, episodeResponse.document, data, data)

        // GojoDesu sering mengarahkan video via tombol/link Download ke Kotakajaib.
        episodeResponse.document.select(
            "a:contains(Download), a:contains(Server), a:contains(Nonton), a[href*='kotakajaib'], a[href*='turbosplayer']"
        ).forEach { anchor ->
            queueUrl(anchor.attr("href"), data, data)
            queueUrl(anchor.attr("data-url"), data, data)
            queueUrl(anchor.attr("data-src"), data, data)
        }

        var safety = 0
        while (queue.isNotEmpty() && safety++ < 100) {
            val (next, referer) = queue.removeFirst()
            if (!visited.add(next)) continue

            if (isDirectMedia(next)) {
                directLinks[next] = referer
                continue
            }

            if (isKnownExtractorHost(next)) {
                extractorLinks.add(next to referer)
            }

            val response = runCatching {
                app.get(
                    next,
                    referer = referer,
                    headers = commonHeaders,
                    allowRedirects = true,
                    timeout = 25L
                )
            }.getOrNull()

            if (response != null) {
                parseTextAndDoc(response.text, response.document, next, next)
            } else if (isKnownExtractorHost(next)) {
                extractorLinks.add(next to referer)
            }
        }

        var found = false

        directLinks.forEach { (link, referer) ->
            if (emitDirect(link, referer, callback)) found = true
        }

        extractorLinks.forEach { (link, referer) ->
            val success = runCatching {
                loadExtractor(link, referer, subtitleCallback, callback)
            }.getOrDefault(false)

            if (success) found = true
        }

        return found || directLinks.isNotEmpty() || extractorLinks.isNotEmpty()
    }

    private fun parseEpisodes(
        document: Document,
        seriesTitle: String,
        fallbackUrl: String
    ): List<Episode> {
        val episodes = linkedMapOf<String, Episode>()

        document.select("a[href]")
            .forEach { element ->
                val href = element.attr("href").trim()
                if (href.isBlank()) return@forEach

                val absoluteUrl = fixUrl(href)
                val path = absoluteUrl.substringAfter(mainUrl).trim('/')

                val episodeNumber = extractEpisodeNumber(element.text(), absoluteUrl)
                    ?: return@forEach

                val isEpisodeUrl = Regex(
                    """(^|/)?.*-episode-\d+/?$""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(path)

                if (!isEpisodeUrl) return@forEach

                episodes[absoluteUrl] = newEpisode(absoluteUrl) {
                    this.name = "Episode $episodeNumber"
                    this.episode = episodeNumber
                }
            }

        return episodes.values
            .sortedBy { it.episode ?: 9999 }
            .ifEmpty {
                listOf(
                    newEpisode(fallbackUrl) {
                        this.name = seriesTitle
                        this.episode = 1
                    }
                )
            }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst(
            "h2 a[href], " +
                "h3 a[href], " +
                ".entry-title a[href], " +
                ".tt a[href], " +
                "a[href]"
        ) ?: return null

        val href = fixUrl(anchor.attr("href").trim())
        if (!href.startsWith(mainUrl)) return null

        val title = listOf(
            selectFirst("h2")?.text()?.trim(),
            selectFirst("h3")?.text()?.trim(),
            selectFirst(".entry-title")?.text()?.trim(),
            selectFirst(".tt")?.text()?.trim(),
            anchor.attr("title").trim(),
            selectFirst("img[alt]")?.attr("alt")?.trim(),
            anchor.text().trim()
        ).firstOrNull {
            !it.isNullOrBlank() &&
                !it.equals("View All", true) &&
                !it.equals("Next", true) &&
                !it.equals("Berikutnya", true)
        }?.cleanSearchTitle() ?: return null

        val poster = fixUrlNull(selectFirst("img")?.getImageAttr())
        val type = guessType(title, href, emptyList())

        return if (type == TvType.AnimeMovie) {
            newMovieSearchResponse(title, href, TvType.AnimeMovie) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(title, href, type) {
                this.posterUrl = poster
            }
        }
    }

    private suspend fun emitDirect(
        url: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isBadUrl(url) || !isDirectMedia(url)) return false

        if (isHlsLike(url)) {
            generateM3u8(
                source = name,
                streamUrl = url,
                referer = referer
            ).forEach(callback)
        } else {
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = url,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = referer
                    this.quality = getQualityFromName(url).takeIf {
                        it != Qualities.Unknown.value
                    } ?: qualityFromUrl(url)
                    this.headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to referer,
                        "Accept" to "*/*"
                    )
                }
            )
        }

        return true
    }

    private fun collectUrlMatches(text: String, regex: Regex): List<String> {
        return regex.findAll(text.cleanEscapedUrl())
            .map { match ->
                match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value
            }
            .map { it.cleanEscapedUrl() }
            .distinct()
            .toList()
    }

    private fun extractM3u8Urls(text: String): List<String> {
        val urls = linkedSetOf<String>()

        collectUrlMatches(
            text,
            Regex("""(https?://[^"'\\\s<>]+?\.m3u8(?:\?[^"'\\\s<>]*)?)""", RegexOption.IGNORE_CASE)
        ).forEach { urls.add(it) }

        Regex("""https?%3A%2F%2F[^"'\\\s<>]+?\.m3u8[^"'\\\s<>]*""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { URLDecoder.decode(it.value, "UTF-8") }
            .map { it.cleanEscapedUrl() }
            .forEach { urls.add(it) }

        return urls.toList()
    }

    private fun extractMp4Urls(text: String): List<String> {
        val urls = linkedSetOf<String>()

        collectUrlMatches(
            text,
            Regex("""(https?://[^"'\\\s<>]+?\.mp4(?:\?[^"'\\\s<>]*)?)""", RegexOption.IGNORE_CASE)
        ).forEach { urls.add(it) }

        Regex("""https?%3A%2F%2F[^"'\\\s<>]+?\.mp4[^"'\\\s<>]*""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { URLDecoder.decode(it.value, "UTF-8") }
            .map { it.cleanEscapedUrl() }
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

        Regex("""window\.open\(['"]([^'"]+)['"]\)""", RegexOption.IGNORE_CASE)
            .findAll(clean)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { it.cleanEscapedUrl() }
            .forEach { urls.add(it) }

        return urls.toList()
    }

    private fun extractBase64DecodedUrls(text: String): List<String> {
        val decoded = linkedSetOf<String>()

        Regex("""atob\(['"]([^'"]{8,})['"]\)""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { match -> decodeBase64(match.groupValues[1]) }
            .forEach { decoded.add(it.cleanEscapedUrl()) }

        Regex("""(?:data-frame|data-url|data-src|value)=["']([A-Za-z0-9+/=]{20,})["']""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { match -> decodeBase64(match.groupValues[1]) }
            .forEach { decoded.add(it.cleanEscapedUrl()) }

        Regex("""['"]([A-Za-z0-9+/=]{60,})['"]""")
            .findAll(text)
            .mapNotNull { match -> decodeBase64(match.groupValues[1]) }
            .filter {
                it.contains("http", true) ||
                    it.contains(".m3u8", true) ||
                    it.contains(".mp4", true) ||
                    it.contains("turbosplayer", true) ||
                    it.contains("kotakajaib", true)
            }
            .forEach { decoded.add(it.cleanEscapedUrl()) }

        return decoded.toList()
    }

    private fun extractSubtitles(
        html: String,
        baseUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val candidates = linkedSetOf<String>()

        Regex("""https?://[^"'\\\s<>]+?\.(?:vtt|srt)(?:\?[^"'\\\s<>]*)?""", RegexOption.IGNORE_CASE)
            .findAll(html.cleanEscapedUrl())
            .map { it.value }
            .forEach { candidates.add(it) }

        Regex("""(?:subtitle|sub|captions?)\s*[:=]\s*['"]([^'"]+\.(?:vtt|srt)[^'"]*)['"]""", RegexOption.IGNORE_CASE)
            .findAll(html.cleanEscapedUrl())
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { normalizeAnyUrl(it, baseUrl) }
            .forEach { candidates.add(it) }

        candidates.distinct().forEach { sub ->
            subtitleCallback.invoke(SubtitleFile("Indonesia", sub))
        }
    }

    private fun normalizeAnyUrl(url: String, baseUrl: String): String {
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

    private fun decodeBase64(raw: String): String? {
        val clean = raw.trim().replace("\\s".toRegex(), "")
        if (clean.length < 8) return null

        return runCatching {
            String(Base64.decode(clean, Base64.DEFAULT))
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun isLikelyPlayer(url: String): Boolean {
        val lower = url.lowercase()
        if (isDirectMedia(lower)) return true
        if (isBadUrl(lower)) return false

        return playableHostHints.any { lower.contains(it) } ||
            lower.contains("/embed/") ||
            lower.contains("/file/") ||
            lower.contains("/v/") ||
            lower.contains("/player/") ||
            lower.contains("watch?")
    }

    private fun isKnownExtractorHost(url: String): Boolean {
        val lower = url.lowercase()
        if (isDirectMedia(lower)) return false

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

    private fun isDirectMedia(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") ||
            lower.contains(".mp4") ||
            lower.contains("googlevideo.com/videoplayback") ||
            lower.contains("blogger.googleusercontent.com") ||
            lower.contains("video-downloads.googleusercontent.com")
    }

    private fun isHlsLike(url: String): Boolean {
        return url.contains(".m3u8", true)
    }

    private fun isBadUrl(url: String): Boolean {
        val lower = url.lowercase()

        return lower.isBlank() ||
            lower.startsWith("javascript:") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("tel:") ||
            lower.startsWith("data:") ||
            lower.startsWith("blob:") ||
            lower.contains("facebook.com") ||
            lower.contains("twitter.com") ||
            lower.contains("x.com/") ||
            lower.contains("telegram") ||
            lower.contains("whatsapp") ||
            lower.contains("adsbygoogle") ||
            lower.contains("googlesyndication") ||
            lower.contains("doubleclick") ||
            lower.contains("analytics") ||
            lower.contains("histats") ||
            lower.contains("googletagmanager") ||
            lower.contains("cloudflareinsights") ||
            lower.contains("wp-content/themes") ||
            lower.contains("wp-json") ||
            lower.endsWith(".css") ||
            lower.endsWith(".js") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".svg")
    }

    private fun Element.getImageAttr(): String {
        return when {
            hasAttr("data-src") -> attr("abs:data-src").ifBlank { attr("data-src") }
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src").ifBlank { attr("data-lazy-src") }
            hasAttr("data-original") -> attr("abs:data-original").ifBlank { attr("data-original") }
            hasAttr("srcset") -> attr("abs:srcset").ifBlank { attr("srcset") }.substringBefore(" ")
            else -> attr("abs:src").ifBlank { attr("src") }
        }
    }

    private fun guessType(
        title: String,
        url: String,
        tags: List<String>
    ): TvType {
        return when {
            url.contains("movie", true) -> TvType.AnimeMovie
            title.contains("movie", true) -> TvType.AnimeMovie
            tags.any { it.equals("Movie", true) } -> TvType.AnimeMovie

            url.contains("ova", true) -> TvType.OVA
            title.contains("ova", true) -> TvType.OVA
            tags.any { it.equals("OVA", true) } -> TvType.OVA

            else -> TvType.Anime
        }
    }

    private fun extractEpisodeNumber(
        title: String,
        href: String
    ): Int? {
        return Regex("""(?:episode|eps?|ep)\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Regex("""(?:episode|eps?|ep)-?(\d+)""", RegexOption.IGNORE_CASE)
                .find(href)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            ?: Regex("""-episode-(\d+)""", RegexOption.IGNORE_CASE)
                .find(href)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
    }

    private fun qualityFromUrl(url: String): Int {
        return when {
            url.contains("2160", true) || url.contains("4k", true) -> Qualities.P2160.value
            url.contains("1080", true) -> Qualities.P1080.value
            url.contains("720", true) -> Qualities.P720.value
            url.contains("480", true) -> Qualities.P480.value
            url.contains("360", true) -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
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

    private fun String.cleanSeriesTitle(): String {
        return this
            .replace(Regex("""(?i)^nonton\s+anime\s+"""), "")
            .replace(Regex("""(?i)\s+subtitle\s+indonesia.*$"""), "")
            .replace(Regex("""(?i)\s+-\s+gojodesu.*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun String.cleanSearchTitle(): String {
        return this
            .replace(Regex("""(?i)\s+episode\s+\d+.*$"""), "")
            .replace(Regex("""(?i)\s+subtitle\s+indonesia.*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
