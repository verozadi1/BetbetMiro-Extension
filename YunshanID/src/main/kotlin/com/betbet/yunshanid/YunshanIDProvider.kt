package com.betbet.yunshanid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class YunshanIDProvider : MainAPI() {

    override var mainUrl = "https://yunshanid.site"

    override var name = "YunshanID"

    override val hasMainPage = true

    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "$mainUrl/ongoing/" to "Donghua Ongoing",
        "$mainUrl/complete/" to "Donghua Complete"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val document = app.get(request.data).document

        val home = document.select("article")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            request.name,
            home
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val document = app.get(
            "$mainUrl/?s=$query"
        ).document

        return document.select("article")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim()
            ?: return newMovieLoadResponse(
                "Unknown",
                url,
                TvType.Anime,
                url
            )

        val poster = document.selectFirst("img")?.attr("src")

        val episodes = document.select(".eplister li")
            .mapIndexed { index, element ->

                val episodeUrl =
                    element.selectFirst("a")
                        ?.attr("href")
                        ?: ""

                newEpisode(episodeUrl) {
                    name = element.text()
                    episode = index + 1
                }
            }
            .reversed()

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Anime
        ) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val iframe = document
            .selectFirst("iframe")
            ?.attr("src")

        if (!iframe.isNullOrBlank()) {

            loadExtractor(
                iframe,
                data,
                subtitleCallback,
                callback
            )
        }

        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {

        val title =
            this.selectFirst("h2")
                ?.text()
                ?.trim()
                ?: return null

        val href =
            this.selectFirst("a")
                ?.attr("href")
                ?: return null

        val poster =
            this.selectFirst("img")
                ?.attr("src")

        return newAnimeSearchResponse(
            title,
            href,
            TvType.Anime
        ) {
            posterUrl = poster
        }
    }
}