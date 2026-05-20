package com.bstation

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class BstationProvider : MainAPI() {
    override var mainUrl = "https://www.bstation.me"
    override var name = "Bstation"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to mainUrl
    )

    override val mainPage = mainPageOf(
        "" to "Update Terbaru",
        "category/anime/page/%d/" to "Anime Indonesia",
        "category/movie/page/%d/" to "Film Anime",
        "category/drama/page/%d/" to "Drama",
        "category/action/page/%d/" to "Aksi",
        "category/adventure/page/%d/" to "Petualangan",
        "category/romance/page/%d/" to "Romantis",
        "category/fantasy/page/%d/" to "Fantasi",
        "category/isekai/page/%d/" to "Isekai",
        "category/mystery/page/%d/" to "Misteri",
        "category/slice-of-life/page/%d/" to "Slice of Life"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl, headers = commonHeaders).document
        val homeList = mutableListOf<HomePageList>()
        mainPage.forEach { (path, title) ->
            val listPage = app.get(mainUrl + path.replace("%d", page.toString()), headers = commonHeaders).document
            val items = listPage.select("div.item") // sesuaikan selector sesuai website
                .mapNotNull { element ->
                    try {
                        val title = element.selectFirst("h3.title")?.text() ?: return@mapNotNull null
                        val url = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                        val poster = element.selectFirst("img")?.attr("src") ?: ""
                        MovieSearchResponse(title, url, TvType.Anime, poster)
                    } catch (_: Exception) { null }
                }
            homeList.add(HomePageList(title, items))
        }
        return HomePageResponse(homeList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", headers = commonHeaders).document
        return document.select("div.item").mapNotNull {
            val title = it.selectFirst("h3.title")?.text() ?: return@mapNotNull null
            val url = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src") ?: ""
            MovieSearchResponse(title, url, TvType.Anime, poster)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = commonHeaders).document
        val title = document.selectFirst("h1.title")?.text() ?: "No Title"
        val poster = document.selectFirst("div.poster img")?.attr("src") ?: ""
        val episodes = document.select("ul.episodes li a").map {
            val epTitle = it.text()
            val epUrl = it.attr("href")
            Episode(epTitle, epUrl)
        }
        return LoadResponse(title, url, TvType.Anime, poster, episodes)
    }

    override suspend fun loadLinks(
        data: Episode,
        subtitle: Boolean,
        quality: String
    ): List<ExtractorLink> {
        val document = app.get(data.url, headers = commonHeaders).document
        return document.select("iframe").mapNotNull {
            val src = it.attr("src")
            if (src.isNotBlank()) {
                ExtractorLink(src, "Bstation", src, false)
            } else null
        }
    }
}