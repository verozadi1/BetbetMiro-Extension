package com.melongmovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Melongmovie : MainAPI() {

    override var mainUrl = "http://139.59.189.160"
    override var name = "Melongmovie"
    override var lang = "id"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "latest-movies/page/%d/" to "Latest Movies",
        "advanced-search/page/%d/?order=latest&type[]=post" to "All Movies"
    )

    // ---------------- MAIN PAGE ----------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val url = "$mainUrl/${request.data}".format(page)
        val doc = app.get(url).document

        val items = doc.select("article, .box, .post, .item, div.los article.box")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    // ---------------- SEARCH ----------------
    override suspend fun search(query: String): List<SearchResponse> {

        val doc = app.get("$mainUrl/?s=$query").document

        return doc.select("article, .box, .post, .item, div.los article.box")
            .mapNotNull { it.toSearchResult() }
    }

    // ---------------- PARSER ----------------
    private fun Element.toSearchResult(): SearchResponse? {

        val a = this.selectFirst("a") ?: return null
        val href = fixUrl(a.attr("href"))
        val title = a.attr("title").ifBlank {
            this.selectFirst("h1,h2,h3,.entry-title,.tt")?.text()
        } ?: return null

        val poster = this.selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    // ---------------- LOAD ----------------
    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url).document

        val title = doc.selectFirst("h1, h2, .entry-title")?.text().orEmpty()
        val poster = doc.selectFirst("img")?.attr("src")
        val plot = doc.select("p").text()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    // ---------------- LOAD LINKS (FIX INTI) ----------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document
        var found = false

        // 1. iframe utama
        doc.select("iframe").forEach {
            val src = it.attr("src")
            if (src.isNotBlank()) {
                loadExtractor(src, data, subtitleCallback, callback)
                found = true
            }
        }

        // 2. embed holder fallback
        doc.select("div#embed_holder iframe, div.embed iframe").forEach {
            val src = it.attr("src")
            if (src.isNotBlank()) {
                loadExtractor(src, data, subtitleCallback, callback)
                found = true
            }
        }

        // 3. mirror fallback (kalau ada)
        doc.select("ul.mirror a, a[data-href]").forEach {
            val url = it.attr("href").ifBlank { it.attr("data-href") }

            if (url.isNotBlank()) {
                try {
                    val mirrorDoc = app.get(url).document

                    mirrorDoc.select("iframe").forEach { iframe ->
                        val src = iframe.attr("src")
                        if (src.isNotBlank()) {
                            loadExtractor(src, url, subtitleCallback, callback)
                            found = true
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return found
    }
}