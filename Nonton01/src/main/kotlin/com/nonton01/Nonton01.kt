package com.nonton01

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class Nonton01 : MainAPI() {
    override var mainUrl = "https://01nonton.com"
    override var name = "01Nonton"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "movies" to "Movies",
        "film-semi" to "Film Semi",
        "drakor" to "Drakor",
        "dracin" to "Dracin",

        "genre/action" to "Action",
        "genre/adventure" to "Adventure",
        "genre/animation" to "Animation",
        "genre/comedy" to "Comedy",
        "genre/crime" to "Crime",
        "genre/drama" to "Drama",
        "genre/family" to "Family",
        "genre/fantasy" to "Fantasy",
        "genre/history" to "History",
        "genre/horror" to "Horror",
        "genre/mystery" to "Mystery",
        "genre/romance" to "Romance",
        "genre/science-fiction" to "Sci-Fi",
        "genre/thriller" to "Thriller",
        "genre/war" to "War"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = buildPageUrl(request.data, page)
        val document = app.get(url).document

        val home = document.select(
            "article, " +
                ".post, " +
                ".item, " +
                ".movie, " +
                ".film, " +
                ".ml-item, " +
                ".grid-item, " +
                ".box, " +
                ".result-item, " +
                ".latest-post, " +
                ".content article, " +
                ".movie-list article, " +
                ".movies-list article, " +
                "div[class*=movie], " +
                "div[class*=film]"
        ).mapNotNull { element ->
            element.toSearchResult()
        }.distinctBy { it.url }

        return newHomePageResponse(
            request.name,
            home,
            hasNext = hasNextPage(document, page)
        )
    }

    private fun buildPageUrl(
        path: String,
        page: Int
    ): String {
        val cleanPath = path.trim('/')

        return when {
            page <= 1 && cleanPath.isBlank() -> mainUrl
            page <= 1 -> "$mainUrl/$cleanPath/"
            cleanPath.isBlank() -> "$mainUrl/page/$page/"
            else -> "$mainUrl/$cleanPath/page/$page/"
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = if (this.`is`("a[href]")) {
            this
        } else {
            selectFirst(
                "a[href][title], " +
                    "h2 a[href], " +
                    "h3 a[href], " +
                    ".title a[href], " +
                    ".entry-title a[href], " +
                    ".movie-title a[href], " +
                    ".film-title a[href], " +
                    ".poster a[href], " +
                    ".thumb a[href], " +
                    "a[href]"
            ) ?: return null
        }

        val href = fixUrlSafe(anchor.attr("href"))
        if (!href.startsWith(mainUrl)) return null
        if (isBlockedUrl(href)) return null

        val title = listOf(
            selectFirst("h1")?.text(),
            selectFirst("h2")?.text(),
            selectFirst("h3")?.text(),
            selectFirst(".title")?.text(),
            selectFirst(".entry-title")?.text(),
            selectFirst(".movie-title")?.text(),
            selectFirst(".film-title")?.text(),
            anchor.attr("title"),
            selectFirst("img[alt]")?.attr("alt"),
            anchor.text()
        ).firstOrNull {
            !it.isNullOrBlank() &&
                !it.equals("Home", true) &&
                !it.equals("Next", true) &&
                !it.equals("Previous", true) &&
                !it.equals("Movies", true) &&
                !it.equals("Film Semi", true) &&
                !it.equals("Drakor", true) &&
                !it.equals("Dracin", true) &&
                !it.equals("Search", true)
        }?.cleanTitle() ?: return null

        if (title.length < 2) return null

        val poster = selectFirst(
            "img[data-src], " +
                "img[data-lazy-src], " +
                "img[data-original], " +
                "img[srcset], " +
                "img[src]"
        )?.getImageAttr()
            ?.let { fixUrlSafe(it) }

        return newMovieSearchResponse(
            title,
            href,
            getTypeFromUrl(href, title, emptyList())
        ) {
            posterUrl = poster
        }
    }

    private fun isBlockedUrl(url: String): Boolean {
        val path = url.substringAfter(mainUrl, "").lowercase().trimEnd('/')

        if (path.isBlank()) return true

        val exactBlocked = setOf(
            "/movies",
            "/film-semi",
            "/drakor",
            "/dracin",
            "/genre",
            "/country",
            "/year",
            "/tag",
            "/category",
            "/privacy",
            "/dmca",
            "/contact",
            "/terms",
            "/about",
            "/download-apk"
        )

        if (exactBlocked.contains(path)) return true

        val prefixBlocked = listOf(
            "/page/",
            "/search",
            "/feed",
            "/wp-json",
            "/wp-content",
            "/wp-admin"
        )

        return prefixBlocked.any { path.startsWith(it) }
    }

    override suspend fun search(
        query: String,
        page: Int
    ): SearchResponseList {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val searchUrl = if (page <= 1) {
            "$mainUrl/?s=$q"
        } else {
            "$mainUrl/page/${page.coerceAtLeast(1)}/?s=$q"
        }

        val document = app.get(searchUrl).document

        val results = document.select(
            "article, " +
                ".post, " +
                ".item, " +
                ".movie, " +
                ".film, " +
                ".ml-item, " +
                ".grid-item, " +
                ".box, " +
                ".result-item, " +
                ".search-result, " +
                ".content article, " +
                "div[class*=movie], " +
                "div[class*=film]"
        ).mapNotNull { element ->
            element.toSearchResult()
        }.distinctBy { it.url }

        return newSearchResponseList(
            results,
            hasNext = hasNextPage(document, page)
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst(
            "h1, " +
                "h1.entry-title, " +
                ".entry-title, " +
                ".movie-title, " +
                ".film-title, " +
                ".video-title, " +
                "meta[property=og:title]"
        )?.let { element ->
            when {
                element.hasAttr("content") -> element.attr("content")
                else -> element.text()
            }
        }?.cleanTitle()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val poster = document.selectFirst(
            "meta[property=og:image], " +
                "meta[name=twitter:image], " +
                "video[poster], " +
                ".poster img, " +
                ".thumb img, " +
                ".player img, " +
                ".entry-content img, " +
                "img.wp-post-image"
        )?.let { element ->
            when {
                element.hasAttr("content") -> element.attr("content")
                element.hasAttr("poster") -> element.attr("poster")
                else -> element.getImageAttr()
            }
        }?.let { fixUrlSafe(it) }

        val plot = document.selectFirst(
            "meta[property=og:description], " +
                "meta[name=description], " +
                ".entry-content p, " +
                ".entry-content, " +
                ".description, " +
                ".sinopsis, " +
                ".summary, " +
                ".movie-desc"
        )?.let { element ->
            when {
                element.hasAttr("content") -> element.attr("content")
                else -> element.text()
            }
        }?.cleanPlot()

        val tags = document.select(
            "a[href*='/genre/'], " +
                "a[href*='/category/'], " +
                "a[href*='/tag/'], " +
                ".genres a, " +
                ".genre a, " +
                ".tags a, " +
                ".category a"
        ).map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val recommendations = document.select(
            ".related article, " +
                ".related-posts article, " +
                ".related a[href], " +
                ".post, " +
                ".item, " +
                ".movie, " +
                ".film"
        ).mapNotNull { element ->
            element.toSearchResult()
        }.filter { it.url != url }
            .distinctBy { it.url }

        return newMovieLoadResponse(
            title,
            url,
            getTypeFromUrl(url, title, tags),
            url
        ) {
            posterUrl = poster
            this.plot = plot
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data)
        val document = response.document
        val directLinks = linkedSetOf<String>()
        val embedLinks = linkedSetOf<String>()

        document.select(
            "video source[src], " +
                "source[src], " +
                "video[src], " +
                "a[href$=.mp4], " +
                "a[href*=.mp4], " +
                "a[href*=.m3u8], " +
                "a[href*=.txt]"
        ).forEach { element ->
            val raw = element.attr("src")
                .ifBlank { element.attr("href") }
                .trim()

            if (raw.isBlank()) return@forEach

            val fixed = fixUrlSafe(raw, data).replace(".txt", ".m3u8")

            if (
                fixed.contains(".m3u8", true) ||
                fixed.contains(".mp4", true)
            ) {
                directLinks.add(fixed)
            }
        }

        extractVideoUrls(response.text).forEach { raw ->
            directLinks.add(fixUrlSafe(raw, data).replace(".txt", ".m3u8"))
        }

        document.select(
            "iframe[src], " +
                "embed[src], " +
                "a[href*='jeniusplay'], " +
                "a[href*='majorplay'], " +
                "a[href*='dood'], " +
                "a[href*='streamtape'], " +
                "a[href*='filemoon'], " +
                "a[href*='vidhide'], " +
                "a[href*='voe'], " +
                "a[href*='mixdrop'], " +
                "a[href*='streamwish'], " +
                "a[href*='wish'], " +
                "a[href*='earnvid'], " +
                "a[href*='filelions'], " +
                "a[href*='vidguard']"
        ).forEach { element ->
            val raw = element.attr("src")
                .ifBlank { element.attr("href") }
                .trim()

            if (raw.isNotBlank()) {
                embedLinks.add(fixUrlSafe(raw, data))
            }
        }

        var found = false

        directLinks.forEach { link ->
            if (link.contains(".m3u8", true)) {
                generateM3u8(
                    source = name,
                    streamUrl = link,
                    referer = data
                ).forEach(callback)
            } else {
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = link,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        referer = data
                        quality = getQualityFromName(link).takeIf {
                            it != Qualities.Unknown.value
                        } ?: qualityFromUrl(link)
                    }
                )
            }

            found = true
        }

        embedLinks.forEach { embed ->
            loadExtractor(
                embed,
                data,
                subtitleCallback,
                callback
            )

            found = true
        }

        return found
    }

    private fun hasNextPage(
        document: org.jsoup.nodes.Document,
        page: Int
    ): Boolean {
        return document.selectFirst(
            "a[rel=next], " +
                ".pagination a:contains(Next), " +
                ".pagination a:contains(Berikutnya), " +
                ".pagination a[href*='/page/${page + 1}'], " +
                "a[href*='/page/${page + 1}/'], " +
                "a[href*='paged=${page + 1}']"
        ) != null
    }

    private fun Element.getImageAttr(): String? {
        return when {
            hasAttr("data-src") -> attr("abs:data-src").ifBlank { attr("data-src") }
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src").ifBlank { attr("data-lazy-src") }
            hasAttr("data-original") -> attr("abs:data-original").ifBlank { attr("data-original") }
            hasAttr("srcset") -> attr("abs:srcset").ifBlank { attr("srcset") }.substringBefore(" ")
            hasAttr("src") -> attr("abs:src").ifBlank { attr("src") }
            else -> null
        }
    }

    private fun fixUrlSafe(
        url: String,
        baseUrl: String = mainUrl
    ): String {
        val clean = url.cleanEscaped()

        return when {
            clean.startsWith("http://", true) ||
                clean.startsWith("https://", true) -> clean

            clean.startsWith("//") -> "https:$clean"

            clean.startsWith("/") -> {
                val origin = Regex("""^https?://[^/]+""")
                    .find(baseUrl)
                    ?.value
                    ?: mainUrl

                origin.trimEnd('/') + clean
            }

            else -> runCatching {
                URI(baseUrl).resolve(clean).toString()
            }.getOrElse {
                mainUrl.trimEnd('/') + "/" + clean.trimStart('/')
            }
        }
    }

    private fun extractVideoUrls(text: String): List<String> {
        val urls = linkedSetOf<String>()

        Regex(
            """https?://[^"'\\\s<>]+?\.(?:m3u8|mp4|txt)(?:\?[^"'\\\s<>]*)?""",
            RegexOption.IGNORE_CASE
        ).findAll(text)
            .map { it.value.cleanEscaped() }
            .forEach { urls.add(it.replace(".txt", ".m3u8")) }

        Regex(
            """(?:file|source|src|url|videoSource)\s*[:=]\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { it.cleanEscaped().replace(".txt", ".m3u8") }
            .filter {
                it.contains(".m3u8", true) ||
                    it.contains(".mp4", true) ||
                    it.contains(".txt", true)
            }
            .forEach { urls.add(it) }

        return urls.toList()
    }

    private fun getTypeFromUrl(
        url: String,
        title: String,
        tags: List<String>
    ): TvType {
        val check = (url + " " + title + " " + tags.joinToString(" ")).lowercase()

        return when {
            check.contains("drakor") ||
                check.contains("dracin") ||
                check.contains("drama korea") ||
                check.contains("drama china") ||
                check.contains("drama cina") -> TvType.AsianDrama

            check.contains("series") ||
                check.contains("tv series") ||
                check.contains("episode") -> TvType.TvSeries

            else -> TvType.Movie
        }
    }

    private fun String.cleanTitle(): String {
        return this
            .replace(Regex("""\s+-\s+01Nonton.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\|\s+01Nonton.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Subtitle\s+Indonesia.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Sub\s+Indo.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun String.cleanPlot(): String? {
        return this
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun String.cleanEscaped(): String {
        return this
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .trim()
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
}