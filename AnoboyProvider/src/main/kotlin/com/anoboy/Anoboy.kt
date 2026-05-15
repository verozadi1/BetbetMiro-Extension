package com.anoboy

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Anoboy : MainAPI() {
    override var mainUrl = "https://ww1.anoboy.boo"
    override var name = "AnoBoy"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Anime, TvType.AnimeMovie, TvType.OVA
    )

    companion object {
        fun getType(t: String?): TvType {
            if (t == null) return TvType.Anime
            return when {
                t.contains("Tv", true) -> TvType.Anime
                t.contains("Movie", true) -> TvType.AnimeMovie
                t.contains("OVA", true) -> TvType.OVA
                t.contains("Special", true) -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus {
            if (t == null) return ShowStatus.Completed
            return when {
                t.contains("Ongoing", true) -> ShowStatus.Ongoing
                t.contains("Completed", true) || t.contains("Tamat", true) -> ShowStatus.Completed
                else -> ShowStatus.Completed
            }
        }
    }

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept" to "*/*"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/%d/" to "Update Terbaru",
        "$mainUrl/category/anime-ongoing/page/%d/" to "Anime Ongoing",
        "$mainUrl/category/anime-tamat/page/%d/" to "Anime Tamat",
        "$mainUrl/category/anime-movie/page/%d/" to "Anime Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data.format(page), headers = commonHeaders).document
        val home = document.select("div.column-content article, .listupd article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst(".entry-title, .title, h3")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", headers = commonHeaders).document
        return document.select("article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = commonHeaders).document

        val title = document.selectFirst(".entry-title, .name")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".poster img, .thumb img")?.getImageAttr()
        val description = document.selectFirst(".entry-content, .sinopsis")?.text()?.trim()
        
        val infoText = document.select(".parameter, .info-content").text()
        val type = getType(infoText)
        val status = getStatus(infoText)

        val episodes = mutableListOf<Episode>()
        
        // Cek daftar episode di halaman detail
        val epElements = document.select(".eplister ul li a, .column-content a[href*='/20']")
        if (epElements.isNotEmpty()) {
            epElements.distinctBy { it.attr("href") }.forEachIndexed { i, a ->
                val href = fixUrlNull(a.attr("href")) ?: return@forEachIndexed
                val name = a.text().trim().ifBlank { "Episode ${i + 1}" }
                episodes.add(newEpisode(href) {
                    this.name = name
                    this.episode = i + 1
                })
            }
        }

        // Fallback jika halaman ini adalah halaman video langsung
        if (episodes.isEmpty()) {
            episodes.add(newEpisode(url) {
                this.name = title
                this.episode = 1
            })
        }

        return newAnimeLoadResponse(title, url, type) {
            this.posterUrl = poster
            this.plot = description
            this.showStatus = status
            addEpisodes(DubStatus.Subbed, episodes.reversed())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = commonHeaders).document

        // Ambil semua link dari tombol mirror dan iframe
        val potentialLinks = mutableListOf<String>()
        
        // 1. Dari Tombol Mirror
        document.select(".mirrorstream a, .vmirror a, .mirror a, .column-content a").forEach {
            it.attr("href").takeIf { h -> h.isNotEmpty() }?.let { href -> potentialLinks.add(href) }
            it.attr("data-src").takeIf { s -> s.isNotEmpty() }?.let { src -> potentialLinks.add(src) }
        }

        // 2. Dari Iframe langsung
        document.select("iframe").forEach {
            it.getIframeAttr()?.let { src -> potentialLinks.add(src) }
        }

        potentialLinks.distinct().forEach { link ->
            val fixedLink = fixUrl(link)
            
            // Bypass link yang sering digunakan Anoboy untuk ads/tracking
            if (fixedLink.contains("base64")) {
                try {
                    val decoded = java.net.URLDecoder.decode(fixedLink.substringAfter("r="), "UTF-8")
                    loadExtractor(fixUrl(decoded), data, subtitleCallback, callback)
                } catch (e: Exception) { }
            } else if (fixedLink.startsWith("http")) {
                loadExtractor(fixedLink, data, subtitleCallback, callback)
            }
        }

        return true
    }

    private fun Element.getImageAttr(): String {
        return this.attr("abs:data-src").ifBlank {
            this.attr("abs:src").ifBlank {
                this.attr("src")
            }
        }
    }

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { !it.isNullOrBlank() }
            ?: this?.attr("data-src").takeIf { !it.isNullOrBlank() }
            ?: this?.attr("src")
    }
}
