package com.pusatfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import org.jsoup.nodes.Element

class Pusatfilm : MainAPI() {

    // URL sudah diperbarui ke versi v3 terbaru
    override var mainUrl = "https://v3.pusatfilm21info.com"
    override var name = "Pusatfilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/film-terbaru/page/%d/" to "Terbaru",
        "$mainUrl/trending/page/%d/" to "Trending",
        "$mainUrl/series-terbaru/page/%d/" to "TV Series",
        "$mainUrl/genre/action/page/%d/" to "Action"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data.format(page)).document
        val home = doc.select("article").mapNotNull { article ->
            val a = article.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href")
            val title = article.selectFirst(".title")?.text() ?: article.attr("title")
            val poster = article.selectFirst("img")?.getImageAttr()

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
        return newHomePageResponse(listOf(HomePageList(request.name, home)), hasNext = home.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("article").mapNotNull { article ->
            val a = article.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href")
            val title = article.selectFirst(".title")?.text() ?: article.attr("title")
            val poster = article.selectFirst("img")?.getImageAttr()

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text() ?: ""
        val poster = doc.selectFirst("img.wp-post-image")?.getImageAttr()
        val description = doc.selectFirst("div.entry-content")?.text()
        
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val iframeEl = document.selectFirst("iframe")
        val iframe = iframeEl?.attr("src")?.let { httpsify(it) }

        if (!iframe.isNullOrBlank()) {
            loadExtractor(iframe, data, subtitleCallback, callback)
        }
        return true
    }

    private fun Element.getImageAttr(): String {
        return this.attr("data-src").ifEmpty { this.attr("src") }
    }
}