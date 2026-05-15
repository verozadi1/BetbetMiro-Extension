package com.animesail

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addKitsuId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.jsoup.Jsoup

class AnimeSailProvider : MainAPI() {
    override var mainUrl = "https://154.26.137.28"
    override var name = "AnimeSail"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        private val mapper: ObjectMapper by lazy {
            ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    private val turnstileInterceptor = TurnstileInterceptor("_as_turnstile")

    private suspend fun request(url: String, ref: String? = null): NiceResponse {
        return app.get(
            url,
            interceptor = turnstileInterceptor,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/121 Mobile Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            ),
            referer = ref
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/rilisan-anime-terbaru/page/" to "Ongoing Anime",
        "$mainUrl/rilisan-donghua-terbaru/page/" to "Ongoing Donghua",
        "$mainUrl/movie-terbaru/page/" to "Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = request(request.data + page).document
        val home = document.select("article").map { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title = when {
                title.contains("-episode") -> title.substringBefore("-episode")
                title.contains("-movie") -> title.substringBefore("-movie")
                else -> title
            }
            "$mainUrl/anime/$title"
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val rawHref = fixUrlNull(this.selectFirst("a")?.attr("href")).toString()
        val href = getProperAnimeLink(rawHref)

        val rawTitle = this.selectFirst(".tt > h2")?.text().orEmpty()

        val title = rawTitle
            .replace(Regex("(?i)Episode\\s?\\d+"), "")
            .replace(Regex("(?i)Sub(tilte)? Indonesia"), "")
            .trim()

        val posterUrl = fixUrlNull(this.selectFirst("div.limit img")?.attr("src"))
        val epNum = Regex("(\\d+)").find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = request("$mainUrl/?s=$query").document
        return document.select("article").map { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = request(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val poster = document.selectFirst("div.entry-content img")?.attr("src")
        val typeText = document.selectFirst(".spe")?.text().orEmpty()

        val type = if (typeText.contains("Movie", true)) TvType.AnimeMovie else TvType.Anime

        val episodes = document.select("ul.daftar li").mapNotNull {
            val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val name = it.text()
            val epNum = Regex("(\\d+)").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()

            newEpisode(link) {
                this.name = name
                this.episode = epNum
            }
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            this.posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = request(data).document

        val sources = mutableSetOf<String>()

        // =========================
        // OPTION PLAYER (FIXED)
        // =========================
        document.select(".mobius option, .mirror option").forEach { el ->

            val raw =
                el.attr("data-em")
                    .ifBlank { el.attr("value") }
                    .ifBlank { el.text() }

            if (raw.isBlank()) return@forEach

            val decoded = runCatching { base64Decode(raw) }.getOrNull() ?: raw
            val iframe = Jsoup.parse(decoded).select("iframe").attr("src")

            if (iframe.isNotBlank()) {
                sources.add(fixUrl(iframe))
            }
        }

        // =========================
        // DIRECT IFAME
        // =========================
        document.select("iframe[src]").forEach {
            val src = it.attr("src")
            if (src.isNotBlank()) sources.add(src)
        }

        // =========================
        // DATA ATTRIBUTES
        // =========================
        document.select("[data-video], a[href]").forEach {
            val url = it.attr("data-video").ifBlank { it.attr("href") }
            if (url.startsWith("http")) sources.add(url)
        }

        // =========================
        // SCRIPT FALLBACK
        // =========================
        document.select("script").forEach {
            Regex("""https?://[^'"]+""")
                .findAll(it.html())
                .forEach { m ->
                    sources.add(m.value)
                }
        }

        // =========================
        // EXECUTE
        // =========================
        sources.forEach { url ->
            loadExtractor(url, data, subtitleCallback, callback)
        }

        return sources.isNotEmpty()
    }
}