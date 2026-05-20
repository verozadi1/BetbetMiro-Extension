package com.bstation

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.URLDecoder

class BstationProvider : MainAPI() {

    override var mainUrl = "https://www.bstation.com"
    override var name = "Bstation"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    // Headers agar tidak dianggap robot
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    )

    // Auto-detect kategori dari website
    override suspend fun getMainPage(page: Int, request: MainPageRequest): List<HomePageList> {
        val document = app.get(mainUrl, headers = commonHeaders).document
        val categories = document.select("div.categories a")
        val homePageLists = mutableListOf<HomePageList>()

        for (cat in categories) {
            val catName = cat.text()
            val catUrl = cat.attr("href")
            val catDoc = app.get(catUrl, headers = commonHeaders).document
            val items = catDoc.select("div.latest-item").map {
                val title = it.select("h3.title").text()
                val url = it.select("a").attr("href")
                val poster = it.select("img").attr("src")
                newAnimeSearchResponse(title, url, poster, detectType(url))
            }
            if (items.isNotEmpty()) {
                homePageLists.add(HomePageList(catName, items))
            }
        }

        return homePageLists
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?keyword=${query.encodeURL()}"
        val document = app.get(searchUrl, headers = commonHeaders).document
        return document.select("div.search-item").map {
            val title = it.select("h3.title").text()
            val url = it.select("a").attr("href")
            val poster = it.select("img").attr("src")
            newAnimeSearchResponse(title, url, poster, detectType(url))
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = commonHeaders).document
        val title = document.select("h1.title").text()
        val poster = document.select("div.poster img").attr("src")
        val genres = document.select("div.genres a").map { it.text() }
        val description = document.select("div.description").text()
        val episodes = document.select("ul.episodes li a").map {
            Episode(it.text(), it.attr("href"))
        }

        val loadResponse = newTvSeriesLoadResponse(title, url, detectType(url)) {
            this.posterUrl = poster
            this.genres = genres
            this.plot = description
            episodes.forEach { ep -> addEpisode(ep.name, ep.url) }
        }

        return loadResponse
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = commonHeaders).document
        document.select("video source").forEach {
            val videoUrl = it.attr("src")
            callback(ExtractorLink(videoUrl, "Bstation", videoUrl, true))
        }
        return true
    }

    private fun detectType(url: String): TvType {
        return when {
            url.contains("/anime/") -> TvType.Anime
            url.contains("/movie/") -> TvType.Movie
            else -> TvType.TvSeries
        }
    }

    data class Episode(val name: String, val url: String)
}