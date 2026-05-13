package Yunshanid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class YunshanidProvider : MainAPI() {

    override var mainUrl = "https://yunshanid.site"
    override var name = "Yunshanid"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Android 10) AppleWebKit/537.36 Chrome/124.0",
        "Referer" to "$mainUrl/"
    )

    // ---------------- MAIN PAGE ----------------

    override val mainPage = mainPageOf(
        "" to "Update",
        "category/movie/page/%d/" to "Movie",
        "category/tv-series/page/%d/" to "TV Series",
        "category/anime/page/%d/" to "Anime"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val path = if (page == 1)
            request.data.replace("/page/%d/", "/")
        else
            request.data.format(page)

        val doc = app.get("$mainUrl/$path", headers = headers).document

        val items = doc.select("article, .bs")
            .mapNotNull { it.toSearchItemSafe() }
            .distinctBy { it.url }

        return newHomePageResponse(
            listOf(HomePageList(request.name, items)),
            hasNext = items.isNotEmpty()
        )
    }

    // ---------------- SEARCH ----------------

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", headers = headers).document

        return doc.select("article, .bs")
            .mapNotNull { it.toSearchItemSafe() }
    }

    // ---------------- SAFE PARSER ----------------

    private fun Element.toSearchItemSafe(): SearchResponse? {
        return try {
            val title = selectFirst(".tt, h2, .title")?.text()?.trim()
                ?: return null

            val url = selectFirst("a")?.attr("href")
                ?: return null

            val poster = selectFirst("img")?.attr("src")
                ?: selectFirst("img")?.attr("data-src")

            val typeText = select(".type").text().lowercase()

            val type = when {
                typeText.contains("tv") -> TvType.TvSeries
                typeText.contains("anime") -> TvType.Anime
                else -> TvType.Movie
            }

            if (type == TvType.Movie) {
                newMovieSearchResponse(title, fixUrl(url)) {
                    this.posterUrl = poster
                }
            } else {
                newAnimeSearchResponse(title, fixUrl(url), type) {
                    this.posterUrl = poster
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- LOAD DETAIL ----------------

    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url, headers = headers).document

        val title = doc.selectFirst("h1, .entry-title")?.text()
            ?: "Unknown"

        val poster = doc.selectFirst(".poster img, .thumb img")?.attr("src")

        val plot = doc.selectFirst(".entry-content p")?.text()

        val tags = doc.select(".genre a")
            .map { it.text() }

        val episodes = doc.select(".eplister li, .list-episode li")
            .mapIndexedNotNull { index, el ->

                val epUrl = el.selectFirst("a")?.attr("href")
                    ?: return@mapIndexedNotNull null

                val epName = el.text().ifBlank {
                    "Episode ${index + 1}"
                }

                newEpisode(fixUrl(epUrl)) {
                    this.name = epName
                    this.episode = index + 1
                }
            }.reversed()

        return if (episodes.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        }
    }

    // ---------------- LOAD LINKS (STABLE CORE) ----------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        return try {
            val doc = app.get(data, headers = headers).document

            val seen = hashSetOf<String>()
            var found = false

            val elements = doc.select(
                "iframe, video source, a[href], .btn-download, .mirror-option option"
            )

            elements.forEach { el ->

                val src = when {
                    el.tagName() == "iframe" -> el.attr("src")
                    el.tagName() == "source" -> el.attr("src")
                    el.tagName() == "option" -> el.attr("value")
                    else -> el.attr("href")
                }

                if (!src.isNullOrBlank() &&
                    src.startsWith("http") &&
                    seen.add(src)
                ) {
                    runCatching {
                        loadExtractor(
                            src,
                            data,
                            subtitleCallback,
                            callback
                        )
                        found = true
                    }
                }
            }

            found
        } catch (e: Exception) {
            false
        }
    }
}