package com.oppadrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.jsoup.nodes.Element

class OppadramaProvider : MainAPI() {

    override var mainUrl = "http://45.11.57.199"
    override var name = "OppaDrama"
    override var lang = "id"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Home",
        "series/" to "Series",
        "movie/" to "Movie"
    )

    // ---------------- MAIN PAGE ----------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val url = if (request.data.isBlank()) {
            "$mainUrl/"
        } else {
            "$mainUrl/${request.data}?page=$page"
        }

        val doc = app.get(url).document

        // 🔥 SUPER FLEXIBLE SELECTOR (INI KUNCI FIX)
        val items = doc.select("article, .item, .grid-item, .movie, .series, a[href*='series'], a[href*='movie']")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    // ---------------- SEARCH ----------------
    override suspend fun search(query: String): List<SearchResponse> {

        val doc = app.get("$mainUrl/?s=$query").document

        return doc.select("article, .item, a[href]")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    // ---------------- PARSER ----------------
    private fun Element.toSearchResult(): SearchResponse? {

        val a = if (this.tagName() == "a") this else this.selectFirst("a") ?: return null

        val href = fixUrl(a.attr("href"))
        val title = a.attr("title").ifBlank {
            this.selectFirst("h1,h2,h3,.title,.tt")?.text()
        } ?: return null

        val poster = this.selectFirst("img")?.attr("src")

        val isSeries = href.contains("series", true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    // ---------------- LOAD ----------------
    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url).document

        val title = doc.selectFirst("h1,h2,.entry-title")?.text().orEmpty()
        val poster = doc.selectFirst("img")?.attr("src")
        val plot = doc.select("p").text()

        val episodes = doc.select("a[href*='episode'], .eplister a, li a")
            .mapIndexedNotNull { index, it ->
                val link = fixUrl(it.attr("href"))

                if (link.isBlank()) return@mapIndexedNotNull null

                newEpisode(link) {
                    this.name = "Episode ${index + 1}"
                    this.episode = index + 1
                }
            }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    // ---------------- LOAD LINKS ----------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document

        // 🔥 AMBIL SEMUA POSSIBLE PLAYER
        val links = doc.select("iframe, video source, script, a[href*='player'], a[href*='embed']")
            .mapNotNull {
                when {
                    it.tagName() == "iframe" -> it.attr("src")
                    it.tagName() == "source" -> it.attr("src")
                    else -> it.attr("href")
                }
            }
            .filter { it.isNotBlank() }

        if (links.isEmpty()) return false

        links.forEach { url ->
            loadExtractor(url, data, subtitleCallback, callback)
        }

        return true
    }
}