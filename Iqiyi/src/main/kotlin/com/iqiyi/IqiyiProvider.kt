package com.iqiyi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element
import java.net.URLEncoder

class IqiyiProvider : MainAPI() {
    override var mainUrl = "https://www.iq.com"
    override var name = "iQIYI"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = false

    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
        TvType.Others
    )

    private val locale = "id_id"

    override val mainPage = mainPageOf(
        "$mainUrl/?lang=$locale" to "Rekomendasi",
        "$mainUrl/services-rights?lang=$locale" to "Semua Peringkat",
        "$mainUrl/drama?lang=$locale" to "Drama",
        "$mainUrl/movie?lang=$locale" to "Film",
        "$mainUrl/variety-show?lang=$locale" to "Variety Show",
        "$mainUrl/anime?lang=$locale" to "Anime",
        "$mainUrl/documentary?lang=$locale" to "Dokumenter",
        "$mainUrl/child?lang=$locale" to "Anak-anak",

        // Category/search pages. These keep the provider useful when a channel page is too dynamic.
        "search:Fate Chooses You" to "Trending Drama",
        "search:Korean Drama" to "Drama Korea",
        "search:Chinese Drama" to "Drama China",
        "search:Thai Drama" to "Drama Thailand",
        "search:Romance" to "Romantis",
        "search:Action" to "Aksi",
        "search:Fantasy" to "Fantasi",
        "search:Wuxia" to "Wuxia",
        "search:Comedy" to "Komedi",
        "search:Horror" to "Horor",
        "search:Mystery" to "Misteri",
        "search:One Piece" to "Anime Populer"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) {
            return newHomePageResponse(
                HomePageList(request.name, emptyList()),
                hasNext = false
            )
        }

        val items = if (request.data.startsWith("search:")) {
            search(request.data.removePrefix("search:").trim()).take(30)
        } else {
            val document = app.get(
                request.data,
                headers = defaultHeaders,
                referer = "$mainUrl/?lang=$locale"
            ).document

            parseCards(document.body())
                .distinctBy { it.url }
                .take(40)
        }

        return newHomePageResponse(
            HomePageList(request.name, items),
            hasNext = false
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        val encoded = query.urlEncoded()
        val candidates = listOf(
            "$mainUrl/search?query=$encoded&lang=$locale",
            "$mainUrl/search?keyword=$encoded&lang=$locale",
            "$mainUrl/search?q=$encoded&lang=$locale"
        )

        for (url in candidates) {
            val results = runCatching {
                app.get(url, headers = defaultHeaders, referer = "$mainUrl/?lang=$locale")
                    .document
                    .body()
                    .let { parseCards(it) }
                    .filter { it.name.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true).not() }
                    .distinctBy { it.url }
                    .take(50)
            }.getOrDefault(emptyList())

            if (results.isNotEmpty()) return results
        }

        return emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val fixedUrl = url.withLocale()
        val document = app.get(
            fixedUrl,
            headers = defaultHeaders,
            referer = "$mainUrl/?lang=$locale"
        ).document

        val title = document.selectFirst("meta[property=og:title], meta[name=title]")
            ?.attr("content")
            ?.cleanTitle()
            ?: document.selectFirst("h1")
                ?.text()
                ?.cleanTitle()
            ?: throw ErrorLoadingException("Judul iQIYI tidak ditemukan")

        val poster = document.selectFirst("meta[property=og:image], meta[name=twitter:image]")
            ?.attr("content")
            ?.normalizeImageUrl()
            ?: findPoster(document.body(), fixedUrl)

        val description = document.selectFirst("meta[property=og:description], meta[name=description]")
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("[class*=description], [class*=desc], .album-intro, .intro")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val tags = document.select("a[href*='channel'], a[href*='genre'], a[href*='tag'], span")
            .map { it.text().trim() }
            .filter { it.length in 2..35 }
            .filterNot { it.contains("Login", true) || it.contains("Favorit", true) }
            .distinct()
            .take(12)

        val trailer = document.selectFirst("a[href*='youtube.com'], a[href*='youtu.be']")
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }

        val episodes = document.select("a[href*='/play/'], a[href*='/short/']")
            .mapNotNull { it.toEpisodeOrNull(fixedUrl) }
            .distinctBy { it.data }
            .filterNot { it.name?.contains("Trailer", ignoreCase = true) == true }
            .take(300)

        val recommendations = parseCards(document.body())
            .distinctBy { it.url }
            .filterNot { it.url == fixedUrl }
            .take(20)

        val tvType = inferType(fixedUrl, title, tags, episodes.size)

        if (episodes.size > 1 && tvType != TvType.Movie) {
            return newTvSeriesLoadResponse(title, fixedUrl, tvType, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }

        val movieData = episodes.firstOrNull()?.data ?: fixedUrl
        return newMovieLoadResponse(title, fixedUrl, tvType, movieData) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // iQIYI uses its official web/app player and protected delivery for many titles.
        // This provider intentionally does not bypass DRM, VIP login, regional locks, or app-only playback.
        // Metadata, homepage, search, detail pages, and episode lists are still available.
        return false
    }

    private fun parseCards(root: Element): List<SearchResponse> {
        return root.select("a[href*='/album/'], a[href*='/play/']")
            .mapNotNull { it.toSearchResultOrNull() }
    }

    private fun Element.toSearchResultOrNull(): SearchResponse? {
        val href = attr("href")
            .takeIf { it.isNotBlank() }
            ?.toAbsoluteIqUrl()
            ?.withLocale()
            ?: return null

        if (!href.contains("/album/") && !href.contains("/play/")) return null

        val title = attr("title").trim()
            .ifBlank { selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty() }
            .ifBlank { text().trim() }
            .cleanTitle()

        if (title.isBlank()) return null
        if (title.length < 2) return null
        if (title.isUiText()) return null

        val poster = findPoster(this, href)

        val type = inferType(href, title, emptyList(), 0)
        return when (type) {
            TvType.Movie -> newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
            }
            else -> newTvSeriesSearchResponse(title, href, type) {
                posterUrl = poster
            }
        }
    }

    private fun Element.toEpisodeOrNull(baseUrl: String): Episode? {
        val href = attr("href")
            .takeIf { it.isNotBlank() }
            ?.toAbsoluteIqUrl()
            ?.withLocale()
            ?: return null

        val rawTitle = attr("title").trim()
            .ifBlank { selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty() }
            .ifBlank { text().trim() }
            .cleanTitle()

        if (rawTitle.isBlank() || rawTitle.isUiText()) return null
        if (href == baseUrl) return null

        val poster = findPoster(this, href)
        val epNumber = Regex("""(?i)(?:episode|ep|eps|e)\s*\.?\s*(\d{1,4})""")
            .find(rawTitle)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Regex("""\b(\d{1,4})\b""")
                .find(rawTitle)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

        return newEpisode(href) {
            name = rawTitle
            episode = epNumber
            posterUrl = poster
        }
    }

    private fun inferType(url: String, title: String, tags: List<String>, episodeCount: Int): TvType {
        val lower = (url + " " + title + " " + tags.joinToString(" ")).lowercase()

        return when {
            lower.contains("/anime") || lower.contains("anime") -> TvType.Anime
            lower.contains("anak") || lower.contains("kids") || lower.contains("cartoon") -> TvType.Cartoon
            lower.contains("/movie") || lower.contains("film") || lower.contains("movie") || (url.contains("/play/") && episodeCount <= 1) -> TvType.Movie
            lower.contains("drama") || lower.contains("korea") || lower.contains("china") || lower.contains("thai") -> TvType.AsianDrama
            episodeCount > 1 -> TvType.TvSeries
            else -> TvType.AsianDrama
        }
    }

    private fun findPoster(element: Element, pageUrl: String): String? {
        val boxes = listOfNotNull(
            element,
            element.parent(),
            element.parent()?.parent(),
            element.parent()?.parent()?.parent()
        ).distinct()

        for (box in boxes) {
            extractImage(box, pageUrl)?.let { return it }
            box.select("img, source, div, span").forEach { child ->
                extractImage(child, pageUrl)?.let { return it }
            }
        }

        return null
    }

    private fun extractImage(element: Element, pageUrl: String): String? {
        val attrs = listOf(
            "src",
            "data-src",
            "data-original",
            "data-lazy-src",
            "data-image",
            "data-img",
            "poster"
        )

        attrs.forEach { attr ->
            val value = element.attr(attr).trim()
            if (value.isImageCandidate()) return value.normalizeImageUrl(pageUrl)
        }

        listOf("srcset", "data-srcset").forEach { attr ->
            val src = element.attr(attr)
                .split(",")
                .map { it.trim().substringBefore(" ").trim() }
                .firstOrNull { it.isImageCandidate() }
            if (!src.isNullOrBlank()) return src.normalizeImageUrl(pageUrl)
        }

        val style = element.attr("style")
        Regex("""url\((['"]?)(.*?)\1\)""", RegexOption.IGNORE_CASE)
            .find(style)
            ?.groupValues
            ?.getOrNull(2)
            ?.trim()
            ?.takeIf { it.isImageCandidate() }
            ?.let { return it.normalizeImageUrl(pageUrl) }

        return null
    }

    private fun String.isImageCandidate(): Boolean {
        if (isBlank()) return false
        if (startsWith("data:", true)) return false
        if (contains("logo", true) || contains("avatar", true) || contains("icon", true)) return false
        return contains(".jpg", true) ||
            contains(".jpeg", true) ||
            contains(".png", true) ||
            contains(".webp", true) ||
            contains("pic", true) ||
            contains("image", true)
    }

    private fun String.normalizeImageUrl(baseUrl: String = mainUrl): String {
        val value = replace("\\u002F", "/")
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .trim()

        return when {
            value.startsWith("http://", true) || value.startsWith("https://", true) -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "$mainUrl$value"
            else -> "${baseUrl.substringBeforeLast("/", mainUrl)}/$value"
        }
    }

    private fun String.toAbsoluteIqUrl(): String {
        val value = trim()
        return when {
            value.startsWith("http://", true) || value.startsWith("https://", true) -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "$mainUrl$value"
            else -> "$mainUrl/$value"
        }
    }

    private fun String.withLocale(): String {
        return when {
            contains("lang=", true) -> this
            contains("?") -> "$this&lang=$locale"
            else -> "$this?lang=$locale"
        }
    }

    private fun String.cleanTitle(): String {
        return replace(Regex("""\s+"""), " ")
            .replace("– iQIYI | iQ.com", "", ignoreCase = true)
            .replace("- iQIYI | iQ.com", "", ignoreCase = true)
            .replace("Tonton online", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .trim(' ', '-', '|')
    }

    private fun String.isUiText(): Boolean {
        val ui = listOf(
            "favorit",
            "login",
            "signup",
            "bahasa",
            "histori",
            "download app",
            "join vip",
            "vip",
            "lebih banyak",
            "rekomendasi",
            "pencarian populer",
            "pengaturan",
            "apple",
            "google"
        )
        val lower = lowercase()
        return ui.any { lower == it || lower.contains(it) } || length > 160
    }

    private fun String.urlEncoded(): String = runCatching {
        URLEncoder.encode(this, "UTF-8")
    }.getOrDefault(this)

    private val defaultHeaders = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "User-Agent" to USER_AGENT,
        "Referer" to "$mainUrl/?lang=$locale"
    )
}
