package com.sad25kag.gomunime

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

const val HOME_URL = "https://gomunime.top"

class Gomunime : MainAPI() {
    override var mainUrl = HOME_URL
    override var name = "Gomunime"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "__home__" to "Beranda",
        "status/ongoing?page=%d" to "Ongoing",
        "status/completed?page=%d" to "Tamat",
        "type/movie?page=%d" to "Movie",
        "type/ova?page=%d" to "OVA",
        "type/ona?page=%d" to "ONA",
        "type/special?page=%d" to "Special",
        "koleksi/anime-skor-mal-tertinggi?page=%d" to "Top Rated",

        // Diambil dari struktur genre aktif Gomunime saat ini.
        "genre/fantasy?page=%d" to "Fantasy",
        "genre/action?page=%d" to "Action",
        "genre/comedy?page=%d" to "Comedy",
        "genre/shounen?page=%d" to "Shounen",
        "genre/romance?page=%d" to "Romance",
        "genre/adventure?page=%d" to "Adventure",
        "genre/school?page=%d" to "School",
        "genre/seinen?page=%d" to "Seinen",
        "genre/isekai?page=%d" to "Isekai",
        "genre/drama?page=%d" to "Drama",
        "genre/adult-cast?page=%d" to "Adult Cast",
        "genre/supernatural?page=%d" to "Supernatural",
        "genre/reincarnation?page=%d" to "Reincarnation",
        "genre/sci-fi?page=%d" to "Sci-Fi",
        "genre/suspense?page=%d" to "Suspense",
        "genre/historical?page=%d" to "Historical",
        "genre/military?page=%d" to "Military",
        "genre/shoujo?page=%d" to "Shoujo",
        "genre/slice-of-life?page=%d" to "Slice of Life",
        "genre/mystery?page=%d" to "Mystery",
        "genre/ecchi?page=%d" to "Ecchi",
        "genre/horror?page=%d" to "Horror",
        "genre/music?page=%d" to "Music",
        "genre/sports?page=%d" to "Sports"
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = buildPageUrl(request.data, page)

        val document = app.get(
            url,
            headers = headers,
            referer = mainUrl,
            timeout = 20L
        ).document

        val items = document.parseCards()
            .distinctBy { it.url }

        return newHomePageResponse(
            request.name,
            items,
            hasNext = document.hasNextPage(page) || items.isNotEmpty()
        )
    }

    private fun buildPageUrl(data: String, page: Int): String {
        val safePage = if (page <= 0) 1 else page

        return when {
            data == "__home__" && safePage <= 1 -> mainUrl
            data == "__home__" -> "$mainUrl?page=$safePage"
            data.contains("%d") -> "$mainUrl/${data.format(safePage)}"
            data.startsWith("http", true) -> data
            safePage <= 1 -> "$mainUrl/${data.trimStart('/')}"
            data.contains("?") -> {
                val path = data.substringBefore("?").trim('/')
                val query = data.substringAfter("?")
                "$mainUrl/$path?page=$safePage&$query"
            }
            else -> "$mainUrl/${data.trim('/')}/page/$safePage"
        }
    }

    private fun Document.hasNextPage(page: Int): Boolean {
        return selectFirst(
            "a[rel=next], " +
                "a.next, " +
                ".pagination a:contains(Next), " +
                "a[href*='page=${page + 1}'], " +
                "a[href*='/page/${page + 1}']"
        ) != null
    }

    private fun Document.parseCards(): List<SearchResponse> {
        val results = linkedMapOf<String, SearchResponse>()

        select(
            "article:has(a[href]), " +
                ".grid a[href]:has(img), " +
                ".card:has(a[href]), " +
                ".anime-card:has(a[href]), " +
                ".swiper-slide:has(a[href]), " +
                "a[href]:has(img)"
        ).forEach { element ->
            element.toSearchResult()?.let { item ->
                results[item.url] = item
            }
        }

        if (results.isEmpty()) {
            select("main a[href], .container a[href]").forEach { element ->
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
            selectFirst("a[href]:has(img), h2 a[href], h3 a[href], a[href]") ?: return null
        }

        val href = fixUrlNull(anchor.attr("href")) ?: return null
        if (!href.startsWith(mainUrl)) return null
        if (isNavigationUrl(href)) return null

        val image = selectFirst("img") ?: anchor.selectFirst("img")

        val rawTitle = listOf(
            selectFirst("h1")?.text(),
            selectFirst("h2")?.text(),
            selectFirst("h3")?.text(),
            selectFirst(".title")?.text(),
            selectFirst(".entry-title")?.text(),
            anchor.attr("title"),
            image?.attr("alt"),
            anchor.text()
        ).firstOrNull { !it.isNullOrBlank() }
            ?: return null

        val title = rawTitle.cleanTitle()
        if (title.length < 2 || title.isUiNoise()) return null

        val poster = fixUrlNull(image?.imageAttr())

        return newAnimeSearchResponse(
            title,
            href,
            guessTypeFromUrlOrTitle(href, title)
        ) {
            posterUrl = poster
        }
    }

    private fun isNavigationUrl(url: String): Boolean {
        val path = url.substringAfter(mainUrl, "").substringBefore("?").trim('/').lowercase()

        if (path.isBlank()) return true

        val blockedExact = setOf(
            "download",
            "manifest.json",
            "favicon.ico",
            "firebase-messaging-sw.js",
            "sw.js"
        )

        if (path in blockedExact) return true

        val blockedPrefixes = listOf(
            "genre/",
            "genres/",
            "status/",
            "type/",
            "koleksi/",
            "tag/",
            "tags",
            "search",
            "page/",
            "api/",
            "storage/",
            "images/",
            "icons/",
            "build/",
            "login",
            "register",
            "privacy",
            "dmca",
            "contact"
        )

        return blockedPrefixes.any { path == it.trimEnd('/') || path.startsWith(it) }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val keyword = query.trim()
        if (keyword.isBlank()) return emptyList()

        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val attempts = listOf(
            "$mainUrl/search?q=$encoded",
            "$mainUrl/?s=$encoded",
            "$mainUrl/search/$encoded"
        )

        for (url in attempts) {
            val document = runCatching {
                app.get(
                    url,
                    headers = headers,
                    referer = mainUrl,
                    timeout = 20L
                ).document
            }.getOrNull() ?: continue

            val results = document.parseCards()
                .distinctBy { it.url }

            if (results.isNotEmpty()) return results
        }

        return emptyList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(
            url,
            headers = headers,
            referer = mainUrl,
            timeout = 20L
        ).document

        val title = document.selectFirst("h1, h1.entry-title, .entry-title, meta[property=og:title]")
            ?.let { element ->
                if (element.hasAttr("content")) element.attr("content") else element.text()
            }
            ?.cleanTitle()
            ?.replace(Regex("""\s+\|\s*Gomunime.*$""", RegexOption.IGNORE_CASE), "")
            ?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast("/")
                .replace("-", " ")
                .cleanTitle()

        val poster = fixUrlNull(
            document.selectFirst(
                "meta[property=og:image], " +
                    "meta[name=twitter:image], " +
                    ".poster img, " +
                    ".thumb img, " +
                    "img.wp-post-image, " +
                    "main img"
            )?.let { element ->
                if (element.hasAttr("content")) element.attr("content") else element.imageAttr()
            }
        )

        val smallInfoText = document.select(
            ".badge-brand, " +
                ".badge-glass, " +
                ".badge-warning, " +
                ".spe span, " +
                ".info-content span, " +
                "a[href*='/type/'], " +
                "a[href*='/status/']"
        ).joinToString(" ") { it.text() }

        val tags = document.select(
            "a[href*='/genre/'], " +
                "a[href*='/genres/'], " +
                ".genres a, " +
                ".genre a"
        ).map { it.text().trim() }
            .filter { it.isNotBlank() && !it.isUiNoise() }
            .distinct()

        val plot = document.selectFirst(
            "meta[property=og:description], " +
                "meta[name=description], " +
                ".entry-content p, " +
                ".entry-content, " +
                ".desc, " +
                ".sinopsis, " +
                "main p"
        )?.let { element ->
            if (element.hasAttr("content")) element.attr("content") else element.text()
        }?.trim()
            ?.takeIf { it.isNotBlank() && it.length > 20 }

        val episodes = document.getEpisodes(url)
        val type = getType(smallInfoText, url, title, episodes)

        val shouldBeMovie = type == TvType.AnimeMovie && episodes.size <= 1 && isRealMoviePage(
            url = url,
            title = title,
            smallInfo = smallInfoText
        )

        return if (shouldBeMovie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.AnimeMovie,
                episodes.firstOrNull()?.data ?: url
            ) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        } else {
            newTvSeriesLoadResponse(
                title,
                url,
                type.takeIf { it != TvType.AnimeMovie } ?: TvType.Anime,
                episodes
            ) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.showStatus = getStatus(smallInfoText)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return loadGomunimeLinks(
            data = data,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
    }

    private fun Document.getEpisodes(url: String): List<Episode> {
        val episodes = linkedMapOf<String, Episode>()

        select(
            "section:contains(Pilih Episode) a[href], " +
                "div:contains(Pilih Episode) a[href], " +
                "a[href*='episode-'], " +
                "a:contains(Episode), " +
                "a:contains(Nonton Episode), " +
                "div.eplister ul li a[href], " +
                "ul.episodios li a[href], " +
                ".episodelist a[href], " +
                ".episode-list a[href]"
        ).forEachIndexed { index, anchor ->
            val href = fixUrlNull(anchor.attr("href")) ?: return@forEachIndexed
            if (!href.startsWith(mainUrl)) return@forEachIndexed

            val text = anchor.text().trim()
            val epNum = extractEpisodeNumber(text, href) ?: index + 1
            val looksEpisode = href.contains("episode-", true) ||
                text.contains("episode", true) ||
                text.contains("nonton episode", true) ||
                text.matches(Regex("""\d+"""))

            if (!looksEpisode) return@forEachIndexed

            episodes[href] = newEpisode(href) {
                episode = epNum
                name = "Episode $epNum"
            }
        }

        return episodes.values
            .sortedBy { it.episode ?: Int.MAX_VALUE }
            .ifEmpty {
                listOf(
                    newEpisode(url) {
                        episode = 1
                        name = "Episode 1"
                    }
                )
            }
    }

    private fun extractEpisodeNumber(text: String, url: String): Int? {
        val source = "$text $url"

        return Regex("""(?i)episode[-\s]*(\d+)""")
            .find(source)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Regex("""(?i)[?&]ep=(\d+)""")
                .find(source)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            ?: text.trim().toIntOrNull()
    }

    private fun getType(
        smallInfo: String?,
        url: String,
        title: String,
        episodes: List<Episode>
    ): TvType {
        val value = "${smallInfo.orEmpty()} $url $title"

        if (episodes.size > 1) return TvType.Anime

        return when {
            value.contains("ova", true) -> TvType.OVA
            value.contains("ona", true) -> TvType.OVA
            value.contains("special", true) -> TvType.OVA
            isRealMoviePage(url, title, smallInfo.orEmpty()) -> TvType.AnimeMovie
            else -> TvType.Anime
        }
    }

    private fun guessTypeFromUrlOrTitle(url: String, title: String): TvType {
        val value = "$url $title"

        return when {
            value.contains("ova", true) -> TvType.OVA
            value.contains("ona", true) -> TvType.OVA
            value.contains("special", true) -> TvType.OVA
            value.contains("/type/movie", true) -> TvType.AnimeMovie
            value.contains(" movie", true) -> TvType.AnimeMovie
            else -> TvType.Anime
        }
    }

    private fun getStatus(text: String?): ShowStatus? {
        val value = text.orEmpty()

        return when {
            value.contains("ongoing", true) -> ShowStatus.Ongoing
            value.contains("completed", true) || value.contains("tamat", true) -> ShowStatus.Completed
            else -> null
        }
    }

    private fun isRealMoviePage(
        url: String,
        title: String,
        smallInfo: String
    ): Boolean {
        val value = "$url $title $smallInfo"

        return value.contains("/type/movie", true) ||
            value.contains(" Movie ", true) ||
            value.contains("Full Movie", true) ||
            title.endsWith("Movie", true)
    }

    private fun Element.imageAttr(): String? {
        return attr("data-src")
            .ifBlank { attr("data-lazy-src") }
            .ifBlank { attr("data-original") }
            .ifBlank { attr("src") }
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun String.cleanTitle(): String {
        return replace(Regex("""(?i)\bNonton\b"""), "")
            .replace(Regex("""(?i)\bSub(?:title)?\s*Indo(?:nesia)?\b"""), "")
            .replace(Regex("""(?i)\bHD\b"""), "")
            .replace(Regex("""(?i)\bdi\s+Gomunime\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '—', '|')
    }

    private fun String.isUiNoise(): Boolean {
        val clean = trim().lowercase()

        return clean.isBlank() ||
            clean in setOf(
                "home",
                "ongoing",
                "tamat",
                "movies",
                "movie",
                "genre",
                "genre populer",
                "top rated",
                "download app",
                "play",
                "info",
                "lihat semua",
                "semua",
                "pilih episode",
                "gdrive",
                "yup"
            )
    }
}
