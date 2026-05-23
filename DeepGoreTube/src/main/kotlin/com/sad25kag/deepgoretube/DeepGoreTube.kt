package com.sad25kag.deepgoretube

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DeepGoreTube : MainAPI() {
    override var mainUrl = "https://deepgoretube.site"
    override var name = "DeepGoreTube"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "home" to "Home"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            "$mainUrl/${request.data}/"
        } else {
            "$mainUrl/${request.data}/page/$page/"
        }

        val document = app.get(url).document
        
        val home = document.select("article, .post, .video-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = listOf(HomePageList(name = request.name, list = home)),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val title = this.selectFirst("h2, .title, .entry-title")?.text() ?: ""
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("article, .post, .video-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title, .entry-title")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("video")?.attr("poster") ?: document.selectFirst("img")?.attr("src"))
        val description = document.selectFirst(".entry-content p, .description")?.text() ?: ""

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // PERBAIKAN 1: Menukar posisi parameter subtitleCallback dan callback
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document

        val videoSrc = document.selectFirst("video source")?.attr("src") ?: document.selectFirst("video")?.attr("src")

        if (videoSrc != null) {
            // PERBAIKAN 2: Menggunakan newExtractorLink menggantikan ExtractorLink
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = fixUrl(videoSrc),
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = videoSrc.contains(".m3u8")
                )
            )
        } else {
            val iframeSrc = document.selectFirst("iframe")?.attr("src")
            if (iframeSrc != null) {
                // Di sini posisi subtitleCallback dan callback juga mengikuti perubahan urutan terbaru
                loadExtractor(fixUrl(iframeSrc), mainUrl, subtitleCallback, callback)
            }
        }

        return true
    }
}