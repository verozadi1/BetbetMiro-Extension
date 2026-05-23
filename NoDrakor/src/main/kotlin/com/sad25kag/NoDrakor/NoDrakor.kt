package com.sad25kag.nodrakor

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

class NoDrakor : MainAPI() {
    override var mainUrl = "http://129.212.202.202"
    override var name = "NoDrakor"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "" to "Terbaru",

        "category/drama-korea/" to "Drama Korea",
        "category/drakor/" to "Drakor",
        "category/drama-china/" to "Drama China",
        "category/drachin/" to "Drachin",
        "category/drama-jepang/" to "Drama Jepang",
        "category/j-drama/" to "J-Drama",
        "category/drama-thailand/" to "Drama Thailand",
        "category/thai-drama/" to "Thai Drama",
        "category/drama-india/" to "Drama India",
        "category/series/" to "Series",
        "category/movie/" to "Movie",
        "category/film/" to "Film",
        "category/ongoing/" to "Ongoing",
        "category/complete/" to "Complete",
        "category/batch/" to "Batch",
        "category/variety-show/" to "Variety Show",
        "category/reality-show/" to "Reality Show",

        "genre/action/" to "Action",
        "genre/adventure/" to "Adventure",
        "genre/comedy/" to "Comedy",
        "genre/crime/" to "Crime",
        "genre/drama/" to "Drama",
        "genre/family/" to "Family",
        "genre/fantasy/" to "Fantasy",
        "genre/historical/" to "Historical",
        "genre/history/" to "History",
        "genre/horror/" to "Horror",
        "genre/law/" to "Law",
        "genre/medical/" to "Medical",
        "genre/melodrama/" to "Melodrama",
        "genre/military/" to "Military",
        "genre/mystery/" to "Mystery",
        "genre/political/" to "Political",
        "genre/romance/" to "Romance",
        "genre/school/" to "School",
        "genre/sci-fi/" to "Sci-Fi",
        "genre/slice-of-life/" to "Slice of Life",
        "genre/sport/" to "Sport",
        "genre/suspense/" to "Suspense",
        "genre/thriller/" to "Thriller",
        "genre/web-drama/" to "Web Drama",

        "country/korea/" to "Korea",
        "country/south-korea/" to "South Korea",
        "country/china/" to "China",
        "country/japan/" to "Japan",
        "country/thailand/" to "Thailand",
        "country/taiwan/" to "Taiwan",
        "country/hong-kong/" to "Hong Kong",
        "country/india/" to "India",

        "year/2026/" to "2026",
        "year/2025/" to "2025",
        "year/2024/" to "2024",
        "year/2023/" to "2023",
        "year/2022/" to "2022",
        "year/2021/" to "2021",
        "year/2020/" to "2020",
        "year/2019/" to "2019",
        "year/2018/" to "2018",
        "year/2017/" to "2017",
        "year/2016/" to "2016",
        "year/2015/" to "2015"
    )

    private val headers = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = buildPageUrl(request.data, page)

        val document = app.get(
            url,
            headers = headers,
            timeout = 20L
        ).document

        val items = parseCards(document)
            .distinctBy { it.url }

        return newHomePageResponse(
            request.name,
            items,
            hasNext = hasNextPage(document, page)
        )
    }

    private fun buildPageUrl(
        path: String,
        page: Int
    ): String {
        val clean = path.trim().trim('/')

        return when {
            clean.isBlank() && page <= 1 -> mainUrl
            clean.isBlank() -> "$mainUrl/page/$page/"
            page <= 1 -> "$mainUrl/$clean/"
            else -> "$mainUrl/$clean/page/$page/"
        }
    }

    private fun hasNextPage(
        document: Document,
        page: Int
    ): Boolean {
        return document.selectFirst(
            "a.next, " +
                "a[rel=next], " +
                ".pagination a:contains(Next), " +
                ".nav-links a:contains(Next), " +
                ".page-numbers.next, " +
                "a[href*='/page/${page + 1}/'], " +
                "a[href*='paged=${page + 1}']"
        ) != null
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val results = linkedMapOf<String, SearchResponse>()

        document.select(
            "article:has(a):has(img), " +
                ".post:has(a):has(img), " +
                ".item:has(a):has(img), " +
                ".movie-item:has(a):has(img), " +
                ".ml-item:has(a):has(img), " +
                ".result-item:has(a):has(img), " +
                ".bs:has(a):has(img), " +
                ".listupd article:has(a):has(img), " +
                ".items article:has(a):has(img), " +
                ".content article:has(a):has(img), " +
                ".module article:has(a):has(img), " +
                ".grid div:has(a):has(img), " +
                ".box div:has(a):has(img)"
        ).forEach { element ->
            element.toSearchResult()?.let { item ->
                results[item.url] = item
            }
        }

        if (results.isEmpty()) {
            document.select(
                "h2 a[href], " +
                    "h3 a[href], " +
                    ".entry-title a[href], " +
                    ".title a[href], " +
                    "a[href]:has(img)"
            ).forEach { element ->
                element.toSearchResult()?.let { item ->
                    results[item.url] = item
                }
            }
        }

        return results.values.toList()
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = if (this.`is`("a[href]")) {
            this
        } else {
            selectFirst(
                "h2 a[href], " +
                    "h3 a[href], " +
                    ".entry-title a[href], " +
                    ".title a[href], " +
                    "a[href]:has(img), " +
                    "a[href]"
            ) ?: return null
        }

        val href = fixUrlNull(anchor.attr("href")) ?: return null

        if (!href.startsWith(mainUrl)) return null
        if (isBlockedUrl(href)) return null

        val image = selectFirst("img") ?: anchor.selectFirst("img")
        val poster = fixUrlNull(image?.getImageAttr())
            ?.takeIf { !isBadImage(it) }

        val title = listOf(
            selectFirst("h1")?.text(),
            selectFirst("h2")?.text(),
            selectFirst("h3")?.text(),
            selectFirst(".entry-title")?.text(),
            selectFirst(".title")?.text(),
            selectFirst("[class*=title]")?.text(),
            anchor.attr("title"),
            image?.attr("alt"),
            anchor.text()
        ).firstOrNull {
            !it.isNullOrBlank() &&
                !isBadTitle(it)
        }?.cleanTitle() ?: return null

        if (title.length < 2) return null

        val type = guessType(href, text(), title)

        return if (type == TvType.TvSeries || type == TvType.AsianDrama) {
            newTvSeriesSearchResponse(
                title,
                href,
                type
            ) {
                posterUrl = poster
                year = extractYear(title) ?: extractYear(text())
            }
        } else {
            newMovieSearchResponse(
                title,
                href,
                TvType.Movie
            ) {
                posterUrl = poster
                year = extractYear(title) ?: extractYear(text())
            }
        }
    }

    private fun isBlockedUrl(url: String): Boolean {
        val path = url.substringAfter(mainUrl).trim('/').lowercase()

        if (path.isBlank()) return true

        val blockedPrefixes = listOf(
            "category/",
            "genre/",
            "tag/",
            "country/",
            "year/",
            "author/",
            "page/",
            "search",
            "privacy",
            "dmca",
            "contact",
            "sitemap",
            "feed",
            "wp-content/",
            "wp-json/",
            "wp-admin/",
            "login",
            "register",
            "iklan",
            "ads"
        )

        return blockedPrefixes.any {
            path == it.trimEnd('/') || path.startsWith(it)
        }
    }

    override suspend fun search(
        query: String,
        page: Int
    ): SearchResponseList {
        val keyword = query.trim()

        if (keyword.isBlank()) {
            return newSearchResponseList(emptyList(), hasNext = false)
        }

        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val attempts = listOf(
            if (page <= 1) "$mainUrl/?s=$encoded" else "$mainUrl/page/$page/?s=$encoded",
            if (page <= 1) "$mainUrl/search/$encoded/" else "$mainUrl/search/$encoded/page/$page/",
            if (page <= 1) "$mainUrl/?search=$encoded" else "$mainUrl/page/$page/?search=$encoded"
        )

        var bestResults: List<SearchResponse> = emptyList()
        var hasNext = false

        for (url in attempts) {
            val document = runCatching {
                app.get(
                    url,
                    headers = headers,
                    timeout = 20L
                ).document
            }.getOrNull() ?: continue

            val results = parseCards(document)
                .distinctBy { it.url }

            if (results.isNotEmpty()) {
                bestResults = results
                hasNext = hasNextPage(document, page)
                break
            }
        }

        return newSearchResponseList(
            bestResults,
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(
            url,
            headers = headers,
            timeout = 20L
        ).document

        val title = document.selectFirst("h1.entry-title, h1, .entry-title")
            ?.text()
            ?.cleanTitle()
            ?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast("/")
                .replace("-", " ")
                .cleanTitle()

        val poster = getPoster(document)
        val plot = getPlot(document)
        val htmlText = document.text()
        val year = extractYear(title) ?: extractYear(htmlText)
        val duration = parseDuration(htmlText)
        val rating = parseRating(htmlText)

        val tags = document.select(
            "a[href*='/category/'], " +
                "a[href*='/genre/'], " +
                "a[href*='/tag/'], " +
                ".genres a, " +
                ".genre a, " +
                ".tagcloud a"
        ).map { it.text().cleanTitle() }
            .filter {
                it.isNotBlank() &&
                    !isBadTitle(it)
            }
            .distinct()
            .take(20)

        val actors = document.select(
            "a[href*='/cast/'], " +
                "a[href*='/pemain/'], " +
                "a[href*='/actor/'], " +
                ".cast a, " +
                ".actors a"
        ).map { it.text().cleanTitle() }
            .filter {
                it.isNotBlank() &&
                    !isBadTitle(it)
            }
            .distinct()
            .map { Actor(it) }

        val trailer = document.selectFirst(
            "a[href*='youtube.com'], " +
                "a[href*='youtu.be'], " +
                "iframe[src*='youtube.com'], " +
                "iframe[src*='youtu.be']"
        )?.let { element ->
            element.attr("href").ifBlank { element.attr("src") }
        }?.takeIf { it.isNotBlank() }

        val recommendations = document.select(
            ".related article:has(a):has(img), " +
                ".film-terkait article:has(a):has(img), " +
                ".serieslist article:has(a):has(img), " +
                ".items article:has(a):has(img), " +
                ".listupd article:has(a):has(img)"
        ).mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
            .filter { it.url != url }
            .take(20)

        val episodes = parseEpisodeLinks(document, url, poster, plot)
        val type = guessType(url, htmlText, title)

        return if (type == TvType.Movie && episodes.size <= 1) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration ?: 0
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newTvSeriesLoadResponse(
                title,
                url,
                type,
                episodes.ifEmpty {
                    listOf(
                        newEpisode(url) {
                            name = title
                            episode = 1
                            posterUrl = poster
                            description = plot
                            duration?.let { runTime = it }
                        }
                    )
                }
            ) {
                posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration ?: 0
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private fun parseEpisodeLinks(
        document: Document,
        currentUrl: String,
        poster: String?,
        plot: String?
    ): List<Episode> {
        val links = linkedMapOf<String, Episode>()

        document.select(
            "a[href*='episode'], " +
                "a[href*='eps'], " +
                "a[href*='ep-'], " +
                "a[href*='/season-'], " +
                ".episodios a[href], " +
                ".episodes a[href], " +
                ".eplister a[href], " +
                ".les-content a[href], " +
                ".season a[href], " +
                ".serial a[href], " +
                ".bixbox a[href], " +
                ".eplister li a[href]"
        ).forEachIndexed { index, element ->
            val href = fixUrlNull(element.attr("href")) ?: return@forEachIndexed

            if (!href.startsWith(mainUrl)) return@forEachIndexed
            if (isBlockedUrl(href)) return@forEachIndexed

            val text = element.text().cleanTitle()
            val epNumber = extractEpisodeNumber(text, href) ?: index + 1
            val seasonNumber = extractSeasonNumber(text, href)

            links[href] = newEpisode(href) {
                name = text.ifBlank { "Episode $epNumber" }
                episode = epNumber
                season = seasonNumber
                posterUrl = poster
                description = plot
            }
        }

        if (links.isEmpty()) {
            links[currentUrl] = newEpisode(currentUrl) {
                name = "Episode 1"
                episode = 1
                posterUrl = poster
                description = plot
            }
        }

        return links.values
            .distinctBy { it.data }
            .sortedWith(
                compareBy<Episode> { it.season ?: 1 }
                    .thenBy { it.episode ?: 1 }
            )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(
            data,
            headers = headers,
            referer = mainUrl,
            timeout = 18L
        )

        val document = response.document
        val html = response.text.cleanEscaped()

        val directLinks = linkedSetOf<String>()
        val embedLinks = linkedSetOf<String>()

        collectDooplayAjaxLinks(
            document = document,
            pageUrl = data,
            directLinks = directLinks,
            embedLinks = embedLinks
        )

        document.select(
            "iframe[src], " +
                "iframe[data-src], " +
                "iframe[data-litespeed-src], " +
                "embed[src], " +
                "source[src], " +
                "video[src], " +
                "video source[src]"
        ).forEach { element ->
            val raw = element.attr("data-litespeed-src")
                .ifBlank { element.attr("data-src") }
                .ifBlank { element.attr("src") }
                .trim()

            if (raw.isNotBlank() && !shouldSkipUrl(raw)) {
                addCandidate(raw, data, directLinks, embedLinks)
            }
        }

        document.select("a[href]").forEach { element ->
            val href = element.attr("href").trim()
            val text = element.text().lowercase()

            if (
                href.isBlank() ||
                shouldSkipUrl(href) ||
                text.contains("trailer") ||
                href.startsWith("#") ||
                href.startsWith("javascript", true)
            ) {
                return@forEach
            }

            if (isLikelyPlayable(href)) {
                addCandidate(href, data, directLinks, embedLinks)
            }
        }

        extractPlayableUrls(html).forEach { raw ->
            addCandidate(raw, data, directLinks, embedLinks)
        }

        var found = false

        directLinks
            .filterNot { isAdUrl(it) }
            .distinct()
            .sortedWith(
                compareBy<String> { if (isHlsLike(it)) 0 else 1 }
                    .thenBy { hostPriority(it) }
            )
            .forEach { link ->
                emitDirectLink(
                    link = link,
                    referer = data,
                    callback = callback
                )
                found = true
            }

        if (found) return true

        prioritizeEmbeds(embedLinks)
            .take(8)
            .forEach { embed ->
                val success = loadExtractor(
                    embed,
                    data,
                    subtitleCallback,
                    callback
                )

                if (success) return true

                resolveNestedLinks(embed, data).forEach { nested ->
                    val fixed = normalizeUrl(nested, embed)
                        .replace(".txt", ".m3u8")

                    when {
                        isAdUrl(fixed) -> Unit

                        isHlsLike(fixed) ||
                            fixed.contains(".mp4", true) ||
                            fixed.contains(".webm", true) -> {
                            emitDirectLink(
                                link = fixed,
                                referer = embed,
                                callback = callback
                            )
                            return true
                        }

                        fixed.startsWith("http", true) && !shouldSkipUrl(fixed) -> {
                            val nestedSuccess = loadExtractor(
                                fixed,
                                embed,
                                subtitleCallback,
                                callback
                            )

                            if (nestedSuccess) return true
                        }
                    }
                }
            }

        return false
    }

    private suspend fun collectDooplayAjaxLinks(
        document: Document,
        pageUrl: String,
        directLinks: MutableSet<String>,
        embedLinks: MutableSet<String>
    ) {
        val options = document.select(
            ".dooplay_player_option[data-post][data-nume][data-type], " +
                ".player-option[data-post][data-nume][data-type], " +
                "li[data-post][data-nume][data-type], " +
                "div[data-post][data-nume][data-type], " +
                "span[data-post][data-nume][data-type]"
        )

        if (options.isEmpty()) return

        val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"

        options.forEach { option ->
            val post = option.attr("data-post").trim()
            val nume = option.attr("data-nume").trim()
            val type = option.attr("data-type").trim()

            if (post.isBlank() || nume.isBlank() || type.isBlank()) return@forEach

            val ajaxText = runCatching {
                app.post(
                    ajaxUrl,
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to post,
                        "nume" to nume,
                        "type" to type
                    ),
                    referer = pageUrl,
                    headers = headers + mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                    ),
                    timeout = 15L
                ).text.cleanEscaped()
            }.getOrNull().orEmpty()

            if (ajaxText.isBlank()) return@forEach

            val embedUrl = runCatching {
                AppUtils.parseJson<DooplayAjaxResponse>(ajaxText).embedUrlFinal
            }.getOrNull()

            if (!embedUrl.isNullOrBlank()) {
                val decoded = decodeIframe(embedUrl)
                addCandidate(decoded, pageUrl, directLinks, embedLinks)

                Jsoup.parse(decoded)
                    .select("iframe[src], iframe[data-src], source[src], video[src]")
                    .forEach { element ->
                        val raw = element.attr("data-src")
                            .ifBlank { element.attr("src") }
                            .trim()

                        addCandidate(raw, pageUrl, directLinks, embedLinks)
                    }
            }

            extractPlayableUrls(ajaxText).forEach { raw ->
                addCandidate(raw, pageUrl, directLinks, embedLinks)
            }

            Jsoup.parse(ajaxText)
                .select("iframe[src], iframe[data-src], source[src], video[src]")
                .forEach { element ->
                    val raw = element.attr("data-src")
                        .ifBlank { element.attr("src") }
                        .trim()

                    addCandidate(raw, pageUrl, directLinks, embedLinks)
                }
        }
    }

    private fun decodeIframe(value: String): String {
        val clean = value.cleanEscaped()

        return when {
            clean.contains("<iframe", true) -> clean
            clean.startsWith("http", true) -> clean
            else -> runCatching {
                URLDecoder.decode(clean, "UTF-8")
            }.getOrDefault(clean)
        }
    }

    private suspend fun resolveNestedLinks(
        url: String,
        referer: String
    ): List<String> {
        if (shouldSkipUrl(url)) return emptyList()

        val text = runCatching {
            app.get(
                url,
                headers = headers,
                referer = referer,
                timeout = 12L
            ).text.cleanEscaped()
        }.getOrNull().orEmpty()

        if (text.isBlank()) return emptyList()

        return extractPlayableUrls(text)
    }

    private fun addCandidate(
        raw: String,
        baseUrl: String,
        directLinks: MutableSet<String>,
        embedLinks: MutableSet<String>
    ) {
        if (raw.isBlank()) return

        val fixed = normalizeUrl(raw.cleanEscaped(), baseUrl)
            .replace(".txt", ".m3u8")

        if (fixed.isBlank() || isAdUrl(fixed)) return

        when {
            isHlsLike(fixed) ||
                fixed.contains(".mp4", true) ||
                fixed.contains(".webm", true) -> directLinks.add(fixed)

            fixed.startsWith("http", true) &&
                !shouldSkipUrl(fixed) -> embedLinks.add(fixed)
        }
    }

    private suspend fun emitDirectLink(
        link: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ) {
        if (isAdUrl(link)) return

        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = link,
                type = if (isHlsLike(link)) {
                    ExtractorLinkType.M3U8
                } else {
                    ExtractorLinkType.VIDEO
                }
            ) {
                this.referer = referer
                this.quality = getQualityFromName(link).takeIf {
                    it != Qualities.Unknown.value
                } ?: qualityFromUrl(link)
            }
        )
    }

    private fun extractPlayableUrls(text: String): List<String> {
        val urls = linkedSetOf<String>()
        val clean = text.cleanEscaped()

        Regex(
            """https?://[^"'\\\s<>]+?\.(?:m3u8|mp4|webm|txt)(?:\?[^"'\\\s<>]*)?""",
            RegexOption.IGNORE_CASE
        ).findAll(clean)
            .map { it.value.cleanEscaped().replace(".txt", ".m3u8") }
            .filterNot { isAdUrl(it) }
            .forEach { urls.add(it) }

        Regex(
            """//[^"'\\\s<>]+?\.(?:m3u8|mp4|webm|txt)(?:\?[^"'\\\s<>]*)?""",
            RegexOption.IGNORE_CASE
        ).findAll(clean)
            .map { "https:${it.value.cleanEscaped().replace(".txt", ".m3u8")}" }
            .filterNot { isAdUrl(it) }
            .forEach { urls.add(it) }

        Regex(
            """https?%3A%2F%2F[^"'\\\s<>]+?(?:\.m3u8|\.mp4|\.webm|\.txt)[^"'\\\s<>]*""",
            RegexOption.IGNORE_CASE
        ).findAll(clean)
            .map {
                runCatching {
                    URLDecoder.decode(it.value, "UTF-8")
                }.getOrDefault(it.value)
            }
            .map { it.cleanEscaped().replace(".txt", ".m3u8") }
            .filterNot { isAdUrl(it) }
            .forEach { urls.add(it) }

        Regex(
            """(?:file|src|source|url|videoSource|videoUrl|video_url|playUrl|play_url|hls|hlsUrl|hls_url|embedUrl|embed_url)\s*[:=]\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).findAll(clean)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { it.cleanEscaped().replace(".txt", ".m3u8") }
            .filter { isLikelyPlayable(it) }
            .filterNot { isAdUrl(it) }
            .forEach { urls.add(it) }

        Regex(
            """https?://[^"'\\\s<>]+?(?:embed|player|stream|majorplay|pm21|dm21|4meplayer|veev|hglink|hgcloud|luluvdoo|filemoon|streamwish|wishfast|dood|streamtape|vidhide|vidguard|voe|mixdrop|mp4upload)[^"'\\\s<>]*""",
            RegexOption.IGNORE_CASE
        ).findAll(clean)
            .map { it.value.cleanEscaped() }
            .filterNot { isAdUrl(it) }
            .forEach { urls.add(it) }

        return urls.toList()
    }

    private fun isLikelyPlayable(url: String): Boolean {
        return url.contains(".m3u8", true) ||
            url.contains(".mp4", true) ||
            url.contains(".webm", true) ||
            url.contains(".txt", true) ||
            url.contains("embed", true) ||
            url.contains("player", true) ||
            url.contains("stream", true) ||
            url.contains("majorplay", true) ||
            url.contains("pm21", true) ||
            url.contains("dm21", true) ||
            url.contains("4meplayer", true) ||
            url.contains("veev", true) ||
            url.contains("hglink", true) ||
            url.contains("hgcloud", true) ||
            url.contains("luluvdoo", true) ||
            url.contains("filemoon", true) ||
            url.contains("streamwish", true) ||
            url.contains("wishfast", true) ||
            url.contains("dood", true) ||
            url.contains("streamtape", true) ||
            url.contains("vidhide", true) ||
            url.contains("vidguard", true) ||
            url.contains("voe", true) ||
            url.contains("mixdrop", true) ||
            url.contains("mp4upload", true)
    }

    private fun prioritizeEmbeds(links: Collection<String>): List<String> {
        return links
            .filterNot { isAdUrl(it) }
            .filterNot { shouldSkipUrl(it) }
            .distinct()
            .sortedWith(
                compareBy<String> { hostPriority(it) }
                    .thenBy { it.length }
            )
    }

    private fun hostPriority(url: String): Int {
        val value = url.lowercase()

        return when {
            value.contains("majorplay") -> 0
            value.contains("4meplayer") -> 1
            value.contains("pm21") -> 2
            value.contains("dm21") -> 3
            value.contains("hglink") -> 4
            value.contains("hgcloud") -> 5
            value.contains("luluvdoo") -> 6
            value.contains("veev") -> 7
            value.contains("filemoon") -> 8
            value.contains("streamwish") -> 9
            value.contains("wishfast") -> 10
            value.contains("dood") -> 11
            value.contains("streamtape") -> 12
            value.contains("vidhide") -> 13
            value.contains("vidguard") -> 14
            value.contains("voe") -> 15
            value.contains("mixdrop") -> 16
            value.contains("mp4upload") -> 17
            value.contains("embed") -> 30
            value.contains("player") -> 31
            value.contains("stream") -> 32
            else -> 50
        }
    }

    private fun normalizeUrl(
        url: String,
        baseUrl: String
    ): String {
        val clean = url.cleanEscaped()

        return when {
            clean.startsWith("http", true) -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> {
                val origin = Regex("""^https?://[^/]+""")
                    .find(baseUrl)
                    ?.value
                    ?: mainUrl
                "$origin$clean"
            }

            else -> runCatching {
                URI(baseUrl).resolve(clean).toString()
            }.getOrDefault(clean)
        }
    }

    private fun getPoster(document: Document): String? {
        return fixUrlNull(
            document.selectFirst(
                "meta[property=og:image], " +
                    "meta[name=twitter:image], " +
                    ".poster img, " +
                    ".thumb img, " +
                    ".post-thumbnail img, " +
                    ".cover img, " +
                    "article img, " +
                    "img.wp-post-image, " +
                    "img"
            )?.let { element ->
                when {
                    element.hasAttr("content") -> element.attr("content")
                    else -> element.getImageAttr()
                }
            }
        )?.takeIf { !isBadImage(it) }
    }

    private fun getPlot(document: Document): String? {
        return document.selectFirst(
            "meta[name=description], " +
                "meta[property=og:description], " +
                ".entry-content p, " +
                ".summary p, " +
                ".sinopsis, " +
                ".desc, " +
                ".description, " +
                "article p"
        )?.let { element ->
            element.attr("content")
                .ifBlank { element.text() }
                .cleanTitle()
                .takeIf { it.length > 30 }
        }
    }

    private fun Element.getImageAttr(): String? {
        fun fromSrcSet(value: String?): String? {
            if (value.isNullOrBlank()) return null

            return value
                .split(",")
                .map { it.trim().substringBefore(" ") }
                .lastOrNull {
                    it.isNotBlank() &&
                        !isBadImage(it)
                }
        }

        val raw = fromSrcSet(attr("data-srcset"))
            ?: fromSrcSet(attr("data-lazy-srcset"))
            ?: fromSrcSet(attr("srcset"))
            ?: attr("abs:data-src").takeIf { it.isNotBlank() }
            ?: attr("abs:data-lazy-src").takeIf { it.isNotBlank() }
            ?: attr("abs:data-original").takeIf { it.isNotBlank() }
            ?: attr("abs:data-full").takeIf { it.isNotBlank() }
            ?: attr("abs:src").takeIf { it.isNotBlank() }
            ?: attr("data-src").takeIf { it.isNotBlank() }
            ?: attr("data-lazy-src").takeIf { it.isNotBlank() }
            ?: attr("src").takeIf { it.isNotBlank() }

        return raw
            ?.trim()
            ?.takeIf { !isBadImage(it) }
    }

    private fun guessType(
        url: String,
        body: String,
        title: String
    ): TvType {
        val text = "$url $body $title"

        return when {
            text.contains("movie", true) ||
                text.contains("film", true) -> TvType.Movie

            text.contains("korea", true) ||
                text.contains("drakor", true) ||
                text.contains("china", true) ||
                text.contains("drachin", true) ||
                text.contains("japan", true) ||
                text.contains("thailand", true) -> TvType.AsianDrama

            else -> TvType.TvSeries
        }
    }

    private fun extractYear(text: String?): Int? {
        return Regex("""\b(19|20)\d{2}\b""")
            .find(text.orEmpty())
            ?.value
            ?.toIntOrNull()
    }

    private fun parseDuration(text: String): Int? {
        return Regex("""(\d+)\s*(?:min|menit)""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun parseRating(text: String): String? {
        return Regex("""\b([0-9](?:\.[0-9])?|10(?:\.0)?)\b""")
            .findAll(text)
            .map { it.groupValues[1] }
            .firstOrNull()
    }

    private fun extractEpisodeNumber(
        text: String,
        href: String
    ): Int? {
        return Regex("""(?:episode|eps?|ep)\s*[-:]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find("$text $href")
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Regex("""\b(\d{1,4})\b""")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
    }

    private fun extractSeasonNumber(
        text: String,
        href: String
    ): Int? {
        return Regex("""(?:season|s)\s*[-:]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find("$text $href")
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun isHlsLike(url: String): Boolean {
        return url.contains(".m3u8", true) ||
            (
                url.contains("majorplay", true) &&
                    url.contains("config", true) &&
                    url.contains(".json", true)
                )
    }

    private fun shouldSkipUrl(url: String): Boolean {
        val value = url.lowercase()

        return value.contains("facebook.com") ||
            value.contains("twitter.com") ||
            value.contains("telegram") ||
            value.contains("whatsapp") ||
            value.contains("youtube.com") ||
            value.contains("youtu.be") ||
            value.contains("trailer") ||
            value.contains("ads") ||
            value.contains("banner") ||
            value.contains("download") ||
            value.contains("mailto:")
    }

    private fun isAdUrl(url: String): Boolean {
        return url.contains("vast", true) ||
            url.contains("preroll", true) ||
            url.contains("doubleclick", true) ||
            url.contains("googlesyndication", true) ||
            url.contains("popads", true) ||
            url.contains("onclick", true) ||
            url.contains("adskeeper", true) ||
            url.contains("adsterra", true) ||
            url.contains("/ads/", true) ||
            url.contains("banner", true) ||
            url.contains("tracking", true) ||
            url.contains("analytics", true)
    }

    private fun isBadImage(url: String): Boolean {
        val value = url.lowercase()

        return value.isBlank() ||
            value.startsWith("data:image") ||
            value.contains("blank") ||
            value.contains("placeholder") ||
            value.contains("default") ||
            value.contains("no-image") ||
            value.contains("noimage") ||
            value.contains("loader") ||
            value.contains("loading") ||
            value.contains("lazy") ||
            value.contains("spacer") ||
            value.contains("logo") ||
            value.contains("favicon") ||
            value.contains("banner") ||
            value.endsWith(".svg")
    }

    private fun isBadTitle(text: String): Boolean {
        val value = text.cleanTitle().lowercase()

        return value.isBlank() ||
            value == "home" ||
            value == "beranda" ||
            value == "search" ||
            value == "login" ||
            value == "register" ||
            value == "trailer" ||
            value == "download" ||
            value == "iklan" ||
            value == "ads" ||
            value == "next" ||
            value == "previous" ||
            value == "prev"
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

    private fun String.cleanEscaped(): String {
        return this
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .trim()
    }

    private fun String.cleanTitle(): String {
        return this
            .replace(Regex("""\s+NoDrakor\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\|\s+.*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DooplayAjaxResponse(
    @JsonProperty("embed_url") val embedUrl: String? = null,
    @JsonProperty("embedUrl") val embedUrlAlt: String? = null,
    @JsonProperty("url") val url: String? = null
) {
    val embedUrlFinal: String?
        get() = embedUrl ?: embedUrlAlt ?: url
}