package com.betbet.yunshanid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class YunshanidProvider : MainAPI() {

    override var mainUrl = "https://yunshanid.site"
    override var name = "Yunshanid"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.Movie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/ongoing/" to "Ongoing",
        "$mainUrl/completed/" to "Completed",
        "$mainUrl/category/movie/" to "Movie"
    )

    // =========================
    // MAIN PAGE
    // =========================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url =
            if (page == 1) request.data
            else "${request.data}page/$page/"

        val document = app.get(url).document

        val home = mutableListOf<SearchResponse>()

        val selectors = listOf(
            "article",
            ".bs",
            ".bsx",
            ".listupd .bs"
        )

        selectors.forEach { selector ->
            document.select(selector).mapNotNullTo(home) {
                it.toSearchResult()
            }
        }

        return newHomePageResponse(
            request.name,
            home.distinctBy { it.url }
        )
    }

    // =========================
    // SEARCH
    // =========================

    override suspend fun search(query: String): List<SearchResponse> {

        val document =
            app.get("$mainUrl/?s=$query").document

        return document.select(
            "article, .bs, .bsx"
        ).mapNotNull {
            it.toSearchResult()
        }
    }

    // =========================
    // SEARCH RESULT
    // =========================

    private fun Element.toSearchResult(): SearchResponse? {

        val title = this.selectFirst(
            "h2, .tt, .entry-title"
        )?.text()?.trim()
            ?: return null

        val href = this.selectFirst("a")
            ?.attr("href")
            ?: return null

        val poster = this.selectFirst("img")
            ?.attr("data-src")
            ?: this.selectFirst("img")
                ?.attr("src")

        val type = when {
            title.contains("movie", true) -> TvType.Movie
            else -> TvType.Anime
        }

        return newAnimeSearchResponse(
            title,
            href,
            type
        ) {
            posterUrl = poster
        }
    }

    // =========================
    // LOAD DETAIL
    // =========================

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val title =
            document.selectFirst(
                "h1.entry-title, .entry-title"
            )?.text()?.trim()
                ?: "No Title"

        val poster =
            document.selectFirst(
                ".thumb img, .infox img, img"
            )?.attr("src")

        val description =
            document.selectFirst(
                ".entry-content p, .desc, .synp"
            )?.text()

        // Genres
        val tags = document.select(
            ".genxed a, .mgen a, .info-content a"
        ).map {
            it.text()
        }

        // Episodes
        var episodes = document.select(
            ".eplister li, #chapterlist li, .episodelist li"
        ).mapIndexed { index, ep ->

            val epName =
                ep.selectFirst("a")
                    ?.text()
                    ?.trim()

            val epUrl =
                ep.selectFirst("a")
                    ?.attr("href")
                    ?: ""

            Episode(
                epUrl,
                epName
            ).apply {
                episode = index + 1
            }
        }

        // fallback movie / batch
        if (episodes.isEmpty()) {

            episodes = listOf(
                Episode(
                    url,
                    "Full Movie"
                )
            )
        }

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Anime
        ) {

            posterUrl = poster
            plot = description
            tags = tags

            addEpisodes(
                DubStatus.Subbed,
                episodes.reversed()
            )
        }
    }

    // =========================
    // LOAD LINKS
    // =========================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(
            data,
            referer = mainUrl
        ).document

        val servers = mutableListOf<String>()

        // iframe biasa
        document.select("iframe").forEach {
            val src = it.attr("src")
            if (src.isNotBlank())
                servers.add(src)
        }

        // tombol server
        document.select(
            "a[href], .mobius option"
        ).forEach {

            val href = it.attr("href")

            if (
                href.contains("dood", true) ||
                href.contains("mp4upload", true) ||
                href.contains("stream", true)
            ) {
                servers.add(href)
            }
        }

        // ambil unique
        servers.distinct().forEach { server ->

            try {

                loadExtractor(
                    server,
                    data,
                    subtitleCallback,
                    callback
                )

            } catch (_: Exception) {
            }
        }

        return true
    }
}