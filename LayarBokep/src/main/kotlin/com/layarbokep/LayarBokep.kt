package com.layarbokep

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
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
import java.net.URLEncoder

class LayarBokep : MainAPI() {
    override var mainUrl = "https://layarbokep-mobile.ubuntumysec.workers.dev"
    override var name = "LayarBokep"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "category/amateur" to "Amateur",
        "category/asia" to "Asia",
        "category/barat" to "Barat",
        "category/cosplay" to "Cosplay",
        "category/japan" to "Japan",
        "category/jav" to "JAV",
        "category/korea" to "Korea",
        "category/viral" to "Viral",
        "category/indo" to "Indo",
        "category/bokep-indo" to "Bokep Indo",
        "category/uncensored" to "Uncensored"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = buildPageUrl(request.data, page)

        val document = app.get(url).document

        val home = document.select(
            "article, " +
                "div.video-item, " +
                ".video-item, " +
                ".post, " +
                ".item, " +
                ".grid article, " +
                ".content article, " +
                "a[href]"
        ).mapNotNull { element ->
            element.toSearchResult()
        }.distinctBy { it.url }

        return newHomePageResponse(
            request.name,
            home,
            hasNext = document.selectFirst(
                "a[rel=next], " +
                    ".pagination a:contains(Next), " +
                    ".pagination a:contains(Berikutnya), " +
                    "a[href*='/page/${page + 1}'], " +
                    "a[href*='paged=${page + 1}']"
            ) != null || home.isNotEmpty()
        )
    }

    private fun buildPageUrl(
        path: String,
        page: Int
    ): String {
        val cleanPath = path.trim('/')

        return when {
            page <= 1 && cleanPath.isBlank() -> mainUrl
            page <= 1 -> "$mainUrl/$cleanPath"
            cleanPath.isBlank() -> "$mainUrl/page/$page"
            else -> "$mainUrl/$cleanPath/page/$page"
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = if (this.`is`("a[href]")) {
            this
        } else {
            selectFirst("a[href]") ?: return null
        }

        val href = fixUrlNull(anchor.attr("href")) ?: return null

        if (!href.startsWith(mainUrl)) return null
        if (isBlockedUrl(href)) return null

        val title = listOf(
            selectFirst("h2")?.text()?.trim(),
            selectFirst("h3")?.text()?.trim(),
            selectFirst(".title")?.text()?.trim(),
            selectFirst(".entry-title")?.text()?.trim(),
            anchor.attr("title").trim(),
            selectFirst("img[alt]")?.attr("alt")?.trim(),
            anchor.text().trim()
        ).firstOrNull {
            !it.isNullOrBlank() &&
                !it.equals("Home", true) &&
                !it.equals("Next", true) &&
                !it.equals("Previous", true) &&
                !it.equals("Search", true)
        }?.cleanTitle() ?: return null

        if (title.length < 3) return null

        val poster = fixUrlNull(
            selectFirst("img")?.getImageAttr()
        )

        return newMovieSearchResponse(
            title,
            href,
            TvType.NSFW
        ) {
            posterUrl = poster
        }
    }

    private fun isBlockedUrl(url: String): Boolean {
        val blocked = listOf(
            "/category/",
            "/tag/",
            "/page/",
            "/search",
            "/privacy",
            "/dmca",
            "/contact",
            "/terms"
        )

        val path = url.substringAfter(mainUrl).lowercase()

        return path.isBlank() || blocked.any { path.startsWith(it) }
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
                "div.video-item, " +
                ".video-item, " +
                ".post, " +
                ".item, " +
                ".grid article, " +
                ".content article, " +
                "a[href]"
        ).mapNotNull { element ->
            element.toSearchResult()
        }.distinctBy { it.url }

        return newSearchResponseList(
            results,
            hasNext = document.selectFirst(
                "a[rel=next], " +
                    ".pagination a:contains(Next), " +
                    ".pagination a:contains(Berikutnya), " +
                    "a[href*='/page/${page + 1}']"
            ) != null
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

        val poster = fixUrlNull(
            document.selectFirst(
                "meta[property=og:image], " +
                    "video[poster], " +
                    ".player img, " +
                    "div.player img, " +
                    "img.wp-post-image"
            )?.let { element ->
                when {
                    element.hasAttr("content") -> element.attr("content")
                    element.hasAttr("poster") -> element.attr("poster")
                    else -> element.getImageAttr()
                }
            }
        )

        val plot = document.selectFirst(
            "meta[property=og:description], " +
                ".entry-content p, " +
                ".entry-content, " +
                ".description, " +
                ".video-description"
        )?.let { element ->
            when {
                element.hasAttr("content") -> element.attr("content")
                else -> element.text()
            }
        }?.trim()
            ?.takeIf { it.isNotBlank() }

        val tags = document.select(
            "a[href*='/category/'], " +
                "a[href*='/tag/'], " +
                ".tags a, " +
                ".category a"
        ).map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val recommendations = document.select(
            "article, " +
                ".related a[href], " +
                ".related-posts a[href], " +
                ".post, " +
                ".item"
        ).mapNotNull { element ->
            element.toSearchResult()
        }.filter { it.url != url }
            .distinctBy { it.url }

        return newMovieLoadResponse(
            title,
            url,
            TvType.NSFW,
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
        val document = app.get(data).document
        val directLinks = linkedSetOf<String>()
        val embedLinks = linkedSetOf<String>()

        document.select(
            "video source[src], " +
                "source[src], " +
                "video[src], " +
                "a[href$=.mp4], " +
                "a[href*=.mp4], " +
                "a[href*=.m3u8]"
        ).forEach { element ->
            val raw = element.attr("src")
                .ifBlank { element.attr("href") }
                .trim()

            if (raw.isBlank()) return@forEach

            val fixed = fixUrl(raw)

            if (fixed.contains(".m3u8", true) || fixed.contains(".mp4", true)) {
                directLinks.add(fixed)
            }
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
                "a[href*='mixdrop']"
        ).forEach { element ->
            val raw = element.attr("src")
                .ifBlank { element.attr("href") }
                .trim()

            if (raw.isNotBlank()) {
                embedLinks.add(fixUrl(raw))
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
                        } ?: Qualities.Unknown.value
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

    private fun Element.getImageAttr(): String? {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("data-original") -> attr("abs:data-original")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            hasAttr("src") -> attr("abs:src")
            else -> null
        }
    }

    private fun String.cleanTitle(): String {
        return this
            .replace(Regex("""\s+-\s+LayarBokep.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}