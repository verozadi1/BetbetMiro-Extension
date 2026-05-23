package com.sad25kag.anoboy

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import java.net.URLEncoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Anoboy : MainAPI() {
    override var mainUrl = "https://anoboy.be"
    override var name = "AnoBoy"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
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
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "" to "Update Terbaru",
        "anime/?order=update&status=&type=" to "Anime Terbaru",
        "anime/?order=add&status=&type=" to "Baru Ditambahkan",
        "anime/?order=&status=ongoing&type=" to "Sedang Tayang",
        "anime/?order=&status=completed&type=" to "Tamat",
        "anime/?order=&status=&type=movie" to "Movie",
        "anime/?order=&status=&type=ova" to "OVA",
        "genres/action/" to "Aksi",
        "genres/adventure/" to "Petualangan",
        "genres/comedy/" to "Komedi",
        "genres/fantasy/" to "Fantasi",
        "genres/isekai/" to "Isekai",
        "genres/romance/" to "Romantis",
        "genres/school/" to "Sekolah",
        "genres/shounen/" to "Shounen",
        "genres/slice-of-life/" to "Slice of Life"
    )

    private fun buildPageUrl(data: String, page: Int): String {
        val raw = data.trim().trimStart('/')
        if (raw.isBlank()) return if (page <= 1) mainUrl else "$mainUrl/page/$page/"

        return if (raw.contains('?')) {
            val path = raw.substringBefore('?').trim('/')
            val query = raw.substringAfter('?')
            if (page <= 1) "$mainUrl/$path/?$query" else "$mainUrl/$path/page/$page/?$query"
        } else {
            val path = raw.trim('/')
            if (page <= 1) "$mainUrl/$path/" else "$mainUrl/$path/page/$page/"
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = buildPageUrl(request.data, page)
        val document = app.get(url).document

        val primary = document.select(
            "article.bs, div.bs, div.listupd article, div.listupd div.bs, " +
                "a[href]:has(div.amv), a[href]:has(div#amv), .venz ul li, .latest a[href]"
        ).filterNot { it.parents().hasClass("side_home") }

        val fallback = if (primary.isNotEmpty()) primary else {
            document.select("main a[href], .postbody a[href], .bixbox a[href]")
                .filter { element ->
                    val href = element.attr("href")
                    val text = element.text()
                    href.contains(mainUrl, true) &&
                        !href.contains("/genres/", true) &&
                        !href.contains("/season/", true) &&
                        !href.contains("/studio/", true) &&
                        text.length > 8
                }
        }

        val items = fallback.mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        val hasNext = document.selectFirst(
            ".wp-pagenavi a.nextpostslink, a.next, a[rel=next], a[href*='/page/${page + 1}/']"
        ) != null

        return newHomePageResponse(
            listOf(HomePageList(request.name, items, isHorizontalImages = true)),
            hasNext
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = attr("href").ifBlank { selectFirst("a[href]")?.attr("href").orEmpty() }
        if (link.isBlank()) return null

        val title = attr("title").trim().ifBlank {
            selectFirst("h3.ibox1, h3.ibox, h2, h3, .tt, .entry-title")?.text()?.trim().orEmpty()
        }.ifBlank {
            selectFirst("img")?.attr("alt")?.trim().orEmpty()
        }.ifBlank {
            text().trim()
        }
        if (title.isBlank()) return null

        val isMovie = link.contains("/anime-movie/", true) || link.contains("/live-action-movie/", true) || title.contains("movie", true)
        val isOva = title.contains("ova", true) || title.contains("special", true)
        
        val tvType = when {
            isMovie -> TvType.AnimeMovie
            isOva -> TvType.OVA
            else -> TvType.Anime
        }
        
        val poster = selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }

        return newAnimeSearchResponse(title, fixUrl(link), tvType) {
            posterUrl = poster
        }
    }

    private fun Element.toLegacySearchResult(): SearchResponse? {
        val link = selectFirst("a")?.attr("href") ?: return null
        val title = selectFirst("div.tt")?.text()?.trim()
            ?: selectFirst("a")?.attr("title")?.trim()
            ?: return null
            
        val isMovie = link.contains("/anime-movie/", true) || link.contains("/live-action-movie/", true) || title.contains("movie", true)
        val isOva = title.contains("ova", true) || title.contains("special", true)
        
        val tvType = when {
            isMovie -> TvType.AnimeMovie
            isOva -> TvType.OVA
            else -> TvType.Anime
        }
        
        val poster = selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }
        return newAnimeSearchResponse(title, fixUrl(link), tvType) {
            posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val document = app.get("$mainUrl/?s=$encodedQuery").document
        val modernResults = document.select(
            "article.bs, div.bs, div.listupd article, div.listupd div.bs, " +
                "a[href]:has(div.amv), a[href]:has(div#amv), .venz ul li, .latest a[href]"
        ).mapNotNull { it.toSearchResult() }
        if (modernResults.isNotEmpty()) return modernResults.distinctBy { it.url }
        return document.select("div.listupd article.bs, article.bs, div.bs")
            .mapNotNull { it.toLegacySearchResult() }
            .distinctBy { it.url }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val href = if (tagName() == "a") {
            attr("href")
        } else {
            selectFirst("a[href]")?.attr("href").orEmpty()
        }
        if (href.isBlank()) return null

        val title = selectFirst("h3.ibox1, h3.ibox, h2, h3, .entry-title")?.text()?.trim()
            ?: selectFirst("div.tt, .tt")?.text()?.trim()
            ?: attr("title").trim().ifBlank { null }
            ?: return null

        val isMovie = href.contains("/anime-movie/", true) || href.contains("/live-action-movie/", true) || title.contains("movie", true)
        val isOva = title.contains("ova", true) || title.contains("special", true)
        
        val tvType = when {
            isMovie -> TvType.AnimeMovie
            isOva -> TvType.OVA
            else -> TvType.Anime
        }
        
        val posterUrl = selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }

        return newAnimeSearchResponse(title, fixUrl(href), tvType) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title, h2.entry-title")?.text()?.trim().orEmpty()
        val poster = document
            .selectFirst("div.column-three-fourth > img, div.column-content > img, div.bigcontent img, div.entry-content img, .thumb img, .poster img, .info-content img")
            ?.getImageAttr()
            ?.let { fixUrlNull(it) }

        val description = (
            document.selectFirst("div.unduhan:not(:has(table))")
                ?.text()
                ?.trim()
                ?.ifBlank { null }
                ?: document.select("div.entry-content p").joinToString("\n") { it.text() }
            )
            .trim()

        val tableRows = document.select("div.unduhan table tr")
        fun getTableValue(label: String): String? {
            return tableRows.firstOrNull {
                it.selectFirst("th")?.text()?.contains(label, true) == true
            }?.selectFirst("td")?.text()?.trim()
        }

        val year = Regex("/(20\\d{2})/")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val duration = getTableValue("Durasi")?.let { text ->
            val hours = Regex("(\\d+)\\s*(jam|hr)", RegexOption.IGNORE_CASE)
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
            val minutes = Regex("(\\d+)\\s*(menit|min)", RegexOption.IGNORE_CASE)
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
            hours * 60 + minutes
        }

        val tags = getTableValue("Genre")
            ?.split(",", "/")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val actors = emptyList<String>()
        val rating = document.selectFirst("div.rating strong")
            ?.text()
            ?.replace("Rating", "")
            ?.trim()
            ?.toDoubleOrNull()
            ?: getTableValue("Score")?.toDoubleOrNull()
        val trailer = document.selectFirst("div.bixbox.trailer iframe, iframe[src*=\"youtube.com\"], iframe[src*=\"youtu.be\"]")
            ?.attr("src")
        val status = getStatus(getTableValue("Status"))

        val recommendations = document.select(
            "a[href]:has(div.amv), a[href]:has(div#amv), div.listupd article.bs, " +
                "article.bs, div.bs, .bixbox a[href]"
        )
            .mapNotNull { it.toRecommendResult() }
            .distinctBy { it.url }

        val castList = emptyList<ActorData>()

        val episodeElements = document.select(
            "div.singlelink ul.lcp_catlist li a, div.eplister ul li a, " +
                "div.bixbox.bxcl ul li a, .episodelist ul li a, ul li a[href*='episode']"
        )
        val seasonHeaders = document.select("div.hq")

        fun normalizeTitle(raw: String): String {
            var titleText = raw.trim()
            titleText = titleText.replace("\\[(Streaming|Download)\\]".toRegex(RegexOption.IGNORE_CASE), "")
            titleText = titleText.replace("(Streaming|Download)".toRegex(RegexOption.IGNORE_CASE), "")
            return titleText.trim()
        }

        fun filterStreamingIfAvailable(elements: List<Element>): List<Element> {
            val hasStreaming = elements.any { anchor ->
                val text = anchor.text()
                val href = anchor.attr("href")
                text.contains("streaming", true) ||
                    href.contains("streaming", true)
            }

            if (hasStreaming) {
                return elements.filter { anchor ->
                    val text = anchor.text()
                    val href = anchor.attr("href")
                    text.contains("streaming", true) || href.contains("streaming", true)
                }
            }

            val hasDownload = elements.any { anchor ->
                val text = anchor.text()
                val href = anchor.attr("href")
                text.contains("download", true) ||
                    href.contains("download", true) ||
                    href.contains("/download/", true)
            }

            if (hasDownload) {
                val nonDownloadElements = elements.filterNot { anchor ->
                    val text = anchor.text()
                    val href = anchor.attr("href")
                    text.contains("download", true) ||
                        href.contains("download", true) ||
                        href.contains("/download/", true)
                }
                if (nonDownloadElements.isNotEmpty()) {
                    return nonDownloadElements
                }
            }

            return elements
        }

        val seasonGroups = buildList {
            for (header in seasonHeaders) {
                val seasonNum = Regex("Season\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(header.text())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                var sibling = header.nextElementSibling()
                while (sibling != null &&
                    !sibling.hasClass("singlelink") &&
                    !sibling.hasClass("eplister")
                ) {
                    sibling = sibling.nextElementSibling()
                }
                val anchors = sibling
                    ?.select("ul.lcp_catlist li a, ul li a")
                    ?.toList()
                    ?: emptyList()
                if (anchors.isNotEmpty()) {
                    add(seasonNum to anchors)
                }
            }
        }

        val groupedElements = if (seasonGroups.isNotEmpty()) {
            seasonGroups.flatMap { (seasonNum, anchors) ->
                filterStreamingIfAvailable(anchors).map { seasonNum to it }
            }
        } else {
            filterStreamingIfAvailable(episodeElements.toList()).map { null to it }
        }

        val episodes = groupedElements
            .reversed()
            .mapIndexed { index, (seasonNum, aTag) ->
                val href = fixUrl(aTag.attr("href"))
                val rawTitle = aTag.text().trim()
                val cleanedTitle = normalizeTitle(rawTitle)
                val episodeNumber = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(rawTitle)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: Regex("episode[-\\s]?(\\d+)", RegexOption.IGNORE_CASE)
                        .find(href)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                    ?: if (seasonNum != null && !rawTitle.contains("Episode", true)) 1 else (index + 1)

                newEpisode(href) {
                    name = if (cleanedTitle.isBlank()) "Episode $episodeNumber" else cleanedTitle
                    episode = episodeNumber
                    if (seasonNum != null) this.season = seasonNum
                }
            }

        fun isValidEpisodeUrl(raw: String?): Boolean {
            val clean = raw?.trim().orEmpty()
            return clean.isNotBlank() &&
                clean != "#" &&
                !clean.equals("none", true) &&
                !clean.startsWith("javascript", true)
        }

        fun parseEpisodeNumber(rawTitle: String, sourceUrl: String?): Int? {
            val fromTitle = Regex("\\b(?:episode|ep)\\s*[-:]?\\s*(\\d+)\\b", RegexOption.IGNORE_CASE)
                .find(rawTitle)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            if (fromTitle != null) return fromTitle

            val fromBtHd = Regex("\\bbt\\s*-?\\s*hd\\s*[-:]?\\s*(\\d+)\\b", RegexOption.IGNORE_CASE)
                .find(rawTitle)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            if (fromBtHd != null) return fromBtHd

            val fromData = sourceUrl?.let { link ->
                Regex("[?&](?:data5|ep|episode)=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(link)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
            if (fromData != null) return fromData

            return null
        }

        fun isQualityOnlyLabel(rawTitle: String): Boolean {
            val label = rawTitle.trim()
            if (label.isBlank()) return false
            if (Regex("\\b(?:episode|ep|ova|special)\\b", RegexOption.IGNORE_CASE).containsMatchIn(label)) {
                return false
            }
            if (Regex("\\bbt\\s*-?\\s*hd\\b", RegexOption.IGNORE_CASE).containsMatchIn(label)) {
                return false
            }
            return Regex("\\b(?:\\d{3,4}p?|240|360|480|720|1080|1k|2k|4k)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(label) ||
                Regex("\\bpc\\s*\\d+\\b", RegexOption.IGNORE_CASE).containsMatchIn(label) ||
                Regex("\\b\\d+\\s*-\\s*\\d+\\b").containsMatchIn(label)
        }

        val episodeNumberFromUrl = Regex("(?:episode|ep)[-\\s]?(\\d+)", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Regex("\\b(?:episode|ep)\\s*(\\d+)\\b", RegexOption.IGNORE_CASE)
                .find(title)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

        fun encodeEpisodeData(referer: String?, payload: String): String {
            if (referer.isNullOrBlank()) return payload
            return "anoboyref::$referer:::$payload"
        }

        fun buildServerEpisodes(
            doc: org.jsoup.nodes.Document,
            pageReferer: String = url
        ): List<Episode> {
            val serverGroups = doc.select("div.satu, div.dua, div.tiga, div.empat, div.lima, div.enam")
            val anchors = serverGroups.flatMap { group -> group.select("a[data-video]") }
            val fallbackAnchors = if (anchors.isNotEmpty()) anchors else doc.select("a[data-video]")
            if (fallbackAnchors.isEmpty()) return emptyList()

            val hasExplicitEpisodeLabels = fallbackAnchors.any { anchor ->
                val rawTitle = anchor.text().trim()
                val sourceUrl = anchor.attr("data-video").ifBlank { anchor.attr("href") }
                parseEpisodeNumber(rawTitle, sourceUrl) != null
            }
            val qualityLikeCount = fallbackAnchors.count { isQualityOnlyLabel(it.text()) }
            val shouldCollapseToSingleEpisode = episodeNumberFromUrl != null &&
                !hasExplicitEpisodeLabels &&
                qualityLikeCount >= (fallbackAnchors.size - 1).coerceAtLeast(1)

            val episodesByNumber = LinkedHashMap<Int, MutableList<Pair<String, String>>>()
            fallbackAnchors.forEachIndexed { index, anchor ->
                val dataVideo = anchor.attr("data-video").ifBlank { anchor.attr("href") }
                if (!isValidEpisodeUrl(dataVideo)) return@forEachIndexed

                val rawTitle = anchor.text().trim()
                val episodeNumber = when {
                    shouldCollapseToSingleEpisode -> episodeNumberFromUrl
                    else -> parseEpisodeNumber(rawTitle, dataVideo)
                        ?: if (isQualityOnlyLabel(rawTitle) && episodeNumberFromUrl != null && fallbackAnchors.size <= 6) {
                            episodeNumberFromUrl
                        } else {
                            index + 1
                        }
                }
                val resolvedUrl = fixUrl(dataVideo)
                val cleanedTitle = if (shouldCollapseToSingleEpisode) "" else normalizeTitle(rawTitle)

                episodesByNumber
                    .getOrPut(episodeNumber) { mutableListOf() }
                    .add(resolvedUrl to cleanedTitle)
            }

            return episodesByNumber
                .toSortedMap()
                .mapNotNull { (episodeNumber, entries) ->
                    val urls = entries.map { it.first }.distinct()
                    val title = entries.map { it.second }.firstOrNull { it.isNotBlank() }
                        ?: "Episode $episodeNumber"
                    if (urls.isEmpty()) return@mapNotNull null

                    val data = if (urls.size == 1) urls.first() else {
                        "multi::" + urls.joinToString("||")
                    }

                    newEpisode(encodeEpisodeData(pageReferer, data)) {
                        name = title
                        episode = episodeNumber
                    }
                }
        }

        fun buildEpisodesFromAnchors(
            elements: List<Element>,
            seasonNum: Int? = null
        ): List<Episode> {
            return elements
                .mapIndexed { index, aTag ->
                    val href = fixUrl(aTag.attr("href"))
                    val rawTitle = aTag.text().trim()
                    val cleanedTitle = normalizeTitle(rawTitle)
                    val episodeNumber = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                        .find(rawTitle)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?: Regex("episode[-\\s]?(\\d+)", RegexOption.IGNORE_CASE)
                            .find(href)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()
                        ?: if (seasonNum != null && !rawTitle.contains("Episode", true)) 1 else (index + 1)

                    newEpisode(href) {
                        name = if (cleanedTitle.isBlank()) "Episode $episodeNumber" else cleanedTitle
                        episode = episodeNumber
                        if (seasonNum != null) this.season = seasonNum
                    }
                }
        }

        suspend fun fetchNestedEpisodePage(href: String): org.jsoup.nodes.Document? {
            val referers = listOf(url, mainUrl, null).distinct()
            for (referer in referers) {
                val nested = runCatching {
                    if (referer != null) app.get(href, referer = referer).document else app.get(href).document
                }.getOrNull()
                if (nested != null) return nested
            }
            return null
        }

        suspend fun buildNestedEpisodesFromStreamingLink(href: String): List<Episode> {
            val nestedDocument = fetchNestedEpisodePage(href) ?: return emptyList()

            val serverEpisodesFromNested = buildServerEpisodes(nestedDocument, href)
            if (serverEpisodesFromNested.isNotEmpty()) return serverEpisodesFromNested

            val nestedEpisodeAnchors = nestedDocument.select(
                "div.singlelink ul.lcp_catlist li a, div.eplister ul li a, " +
                    "div.bixbox.bxcl ul li a, .episodelist ul li a, ul li a[href*='episode']"
            )
            val filteredNestedAnchors = filterStreamingIfAvailable(nestedEpisodeAnchors.toList())
            return if (filteredNestedAnchors.isNotEmpty()) {
                buildEpisodesFromAnchors(filteredNestedAnchors.reversed())
            } else {
                emptyList()
            }
        }

        val serverEpisodes = buildServerEpisodes(document, url)
        val nestedStreamEpisodes = groupedElements.singleOrNull()?.let { (_, anchor) ->
            val href = fixUrl(anchor.attr("href"))
            val rawTitle = anchor.text().trim()
            val isGenericStreamingPage = href != url &&
                (rawTitle.contains("streaming", true) || href.contains("streaming", true)) &&
                parseEpisodeNumber(rawTitle, href) == null

            if (!isGenericStreamingPage) {
                emptyList()
            } else {
                buildNestedEpisodesFromStreamingLink(href)
            }
        } ?: emptyList()
        val useServerEpisodes = seasonHeaders.isEmpty() && serverEpisodes.isNotEmpty() && episodes.size <= 1
        val finalEpisodes = when {
            nestedStreamEpisodes.isNotEmpty() -> nestedStreamEpisodes
            useServerEpisodes -> serverEpisodes
            else -> episodes
        }

        val altTitles = listOfNotNull(
            title,
            document.selectFirst("span:matchesOwn(Judul Inggris:)")?.ownText()?.trim(),
            document.selectFirst("span:matchesOwn(Judul Jepang:)")?.ownText()?.trim(),
            document.selectFirst("span:matchesOwn(Judul Asli:)")?.ownText()?.trim(),
        ).distinct()

        val malIdFromPage = document.selectFirst("a[href*=\"myanimelist.net/anime/\"]")
            ?.attr("href")
            ?.substringAfter("/anime/", "")
            ?.substringBefore("/")
            ?.toIntOrNull()
        val aniIdFromPage = document.selectFirst("a[href*=\"anilist.co/anime/\"]")
            ?.attr("href")
            ?.substringAfter("/anime/", "")
            ?.substringBefore("/")
            ?.toIntOrNull()

        val defaultType = if (url.contains("/anime-movie/", true)) TvType.AnimeMovie else TvType.Anime
        val parsedType = getType(getTableValue("Tipe"))
        val type = if (episodes.isNotEmpty()) {
            TvType.Anime
        } else if (defaultType == TvType.AnimeMovie) {
            TvType.AnimeMovie
        } else {
            parsedType
        }

        val tracker = APIHolder.getTracker(altTitles, TrackerType.getTypes(type), year, true)

        return if (finalEpisodes.isNotEmpty()) {
            newAnimeLoadResponse(title, url, type) {
                posterUrl = tracker?.image ?: poster
                backgroundPosterUrl = tracker?.cover
                this.year = year
                this.plot = description
                this.tags = tags
                showStatus = status
                this.recommendations = recommendations
                this.duration = duration ?: 0
                addEpisodes(DubStatus.Subbed, finalEpisodes)
                rating?.let { addScore(it.toString(), 10) }
                addActors(actors)
                if (castList.isNotEmpty()) this.actors = castList
                addTrailer(trailer)
                addMalId(malIdFromPage ?: tracker?.malId)
                addAniListId(aniIdFromPage ?: tracker?.aniId?.toIntOrNull())
            }
        } else {
            newMovieLoadResponse(title, url, type, url) {
                posterUrl = tracker?.image ?: poster
                backgroundPosterUrl = tracker?.cover
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                this.duration = duration ?: 0
                rating?.let { addScore(it.toString(), 10) }
                addActors(actors)
                if (castList.isNotEmpty()) this.actors = castList
                addTrailer(trailer)
                addMalId(malIdFromPage ?: tracker?.malId)
                addAniListId(aniIdFromPage ?: tracker?.aniId?.toIntOrNull())
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val refererPrefix = "anoboyref::"
        val multiPrefix = "multi::"
        val browserHeaders = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
        )
        val mediaHeaders = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*"
        )

        val hasEmbeddedReferer = data.startsWith(refererPrefix)
        val resolvedData = if (hasEmbeddedReferer) {
            data.removePrefix(refererPrefix)
        } else {
            data
        }
        val refererParts = if (hasEmbeddedReferer) {
            resolvedData.split(":::", limit = 2)
        } else {
            emptyList()
        }
        val extractedReferer = if (hasEmbeddedReferer && refererParts.size == 2 && refererParts[0].isNotBlank()) {
            refererParts[0]
        } else {
            null
        }
        val requestData = if (extractedReferer != null && refererParts.size == 2) {
            refererParts[1]
        } else {
            resolvedData
        }

        val isMulti = requestData.startsWith(multiPrefix)
        val requestReferer = extractedReferer ?: if (isMulti) mainUrl else requestData
        val discoveredUrls = linkedSetOf<String>()
        val queuedUrls = ArrayDeque<Pair<String, String>>()
        val crawledUrls = mutableSetOf<String>()
        var foundLinks = false

        fun isValidUrl(raw: String?): Boolean {
            val clean = raw?.trim().orEmpty()
            return clean.isNotBlank() &&
                clean != "#" &&
                !clean.equals("none", true) &&
                !clean.startsWith("javascript", true)
        }

        fun resolveUrl(raw: String?, base: String): String? {
            if (!isValidUrl(raw)) return null

            var clean = raw!!.trim()
                .replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .trim('"', '\'', ' ')

            if (clean.contains("/uploads/stream/", true) && clean.contains("data=", true)) {
                clean = clean.replace(" ", "+")
            }

            return try {
                when {
                    clean.startsWith("http://", true) || clean.startsWith("https://", true) -> clean
                    clean.startsWith("//") -> "https:$clean"
                    clean.startsWith("/") -> {
                        val origin = Regex("""^https?://[^/]+""")
                            .find(base)
                            ?.value
                            ?: mainUrl
                        "$origin$clean"
                    }
                    else -> URI(base).resolve(clean).toString()
                }
            } catch (_: Exception) {
                try {
                    fixUrl(clean)
                } catch (_: Exception) {
                    null
                }
            }
        }

        fun queueUrl(raw: String?, base: String, referer: String = base) {
            val resolved = resolveUrl(raw, base) ?: return
            if (discoveredUrls.add(resolved)) queuedUrls.add(resolved to referer)
        }

        fun isDirectMedia(url: String): Boolean {
            val lower = url.lowercase()
            return lower.contains(".m3u8", true) ||
                lower.contains(".mp4", true) ||
                lower.contains(".webm", true) ||
                lower.contains("googlevideo.com/videoplayback", true) ||
                lower.contains("blogger.googleusercontent.com", true)
        }

        fun isKnownEmbed(url: String): Boolean {
            val lower = url.lowercase()
            return lower.contains("blogger.com/video.g") ||
                lower.contains("blogger.googleusercontent.com") ||
                lower.contains("yourupload.com/embed/") ||
                lower.contains("yourupload.com/watch/") ||
                lower.contains("dood.") ||
                lower.contains("doodstream") ||
                lower.contains("streamtape") ||
                lower.contains("filemoon") ||
                lower.contains("streamwish") ||
                lower.contains("wishfast") ||
                lower.contains("vidhide") ||
                lower.contains("vidguard") ||
                lower.contains("voe.") ||
                lower.contains("mixdrop") ||
                lower.contains("mp4upload") ||
                lower.contains("ok.ru") ||
                lower.contains("sendvid") ||
                lower.contains("mega.nz")
        }

        fun extractRawUrls(text: String): List<String> {
            val clean = text
                .replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")

            val results = linkedSetOf<String>()

            Regex(
                """https?://[^"'\\\s<>]+?(?:\.m3u8|\.mp4|\.webm)(?:\?[^"'\\\s<>]*)?""",
                RegexOption.IGNORE_CASE
            ).findAll(clean).forEach { results.add(it.value) }

            Regex(
                """//[^"'\\\s<>]+?(?:\.m3u8|\.mp4|\.webm)(?:\?[^"'\\\s<>]*)?""",
                RegexOption.IGNORE_CASE
            ).findAll(clean).forEach { results.add("https:${it.value}") }

            Regex(
                """https?://[^"'\\\s<>]+?(?:blogger\.com/video\.g|blogger\.googleusercontent\.com|yourupload\.com/(?:embed|watch)|dood(?:stream)?\.|streamtape|filemoon|streamwish|wishfast|vidhide|vidguard|voe\.|mixdrop|mp4upload|ok\.ru|sendvid|mega\.nz)[^"'\\\s<>]*""",
                RegexOption.IGNORE_CASE
            ).findAll(clean).forEach { results.add(it.value) }

            Regex(
                """(?:file|src|source|url|data-video|data-src|data-url|data-embed|data-iframe)\s*[:=]\s*["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).findAll(clean)
                .mapNotNull { it.groupValues.getOrNull(1) }
                .filter { it.startsWith("http", true) || it.startsWith("//") || it.startsWith("/") }
                .forEach { results.add(it) }

            return results.toList()
        }

        suspend fun emitDirectMedia(url: String, referer: String): Boolean {
            val lower = url.lowercase()
            return try {
                when {
                    lower.contains(".m3u8", true) -> {
                        generateM3u8(
                            source = name,
                            streamUrl = url,
                            referer = referer,
                            headers = mediaHeaders + mapOf("Referer" to referer),
                            name = name
                        ).forEach(callback)
                        true
                    }

                    lower.contains(".mp4", true) ||
                        lower.contains(".webm", true) ||
                        lower.contains("googlevideo.com/videoplayback", true) ||
                        lower.contains("blogger.googleusercontent.com", true) -> {
                        callback(
                            newExtractorLink(
                                source = name,
                                name = name,
                                url = url,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = referer
                                this.quality = getQualityFromName(url)
                                this.headers = mediaHeaders + mapOf("Referer" to referer)
                            }
                        )
                        true
                    }

                    else -> false
                }
            } catch (_: Exception) {
                false
            }
        }

        suspend fun emitOrLoad(url: String, referer: String): Boolean {
            if (emitDirectMedia(url, referer)) return true

            return try {
                loadExtractor(url, referer, subtitleCallback, callback)
            } catch (_: Exception) {
                false
            }
        }

        fun extractFromDoc(baseUrl: String, doc: org.jsoup.nodes.Document) {
            doc.select(
                "iframe#mediaplayer, iframe#videoembed, div.player-embed iframe, " +
                    "iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src]"
            ).forEach { queueUrl(it.getIframeAttr(), baseUrl) }

            doc.select(
                "video[src], video[data-src], video source[src], source[src], " +
                    "a[href*='.m3u8'], a[href*='.mp4'], a[href*='.webm']"
            ).forEach { element ->
                queueUrl(element.attr("src"), baseUrl)
                queueUrl(element.attr("data-src"), baseUrl)
                queueUrl(element.attr("href"), baseUrl)
            }

            doc.select(
                "a[href*=\"yourupload.com/embed/\"], a[href*=\"yourupload.com/watch/\"], " +
                    "a[href*=\"www.yourupload.com/embed/\"], a[href*=\"www.yourupload.com/watch/\"], " +
                    "a[href*=\"blogger.com/video.g\"], a[href*=\"blogger.googleusercontent.com\"], " +
                    "a[href*=\"dood\"], a[href*=\"streamtape\"], a[href*=\"filemoon\"], " +
                    "a[href*=\"streamwish\"], a[href*=\"wishfast\"], a[href*=\"vidhide\"], " +
                    "a[href*=\"vidguard\"], a[href*=\"voe.\"], a[href*=\"mixdrop\"], a[href*=\"mp4upload\"]"
            ).forEach { queueUrl(it.attr("href"), baseUrl) }

            doc.select(
                "a[href*=\"/uploads/stream/embed.php\"], " +
                    "a[href*=\"/uploads/acbatch.php\"], " +
                    "a[href*=\"/uploads/adsbatch\"], " +
                    "a[href*=\"/uploads/yupbatch\"], " +
                    "a[href*=\"/uploads/\"]"
            ).forEach { queueUrl(it.attr("href"), baseUrl) }

            doc.select(
                "#fplay a#allmiror[data-video], #fplay a[data-video], a#allmiror[data-video], " +
                    "a[data-video], [data-video], [data-embed], [data-iframe], [data-url], [data-src]"
            ).forEach { el ->
                queueUrl(el.attr("data-video"), baseUrl)
                queueUrl(el.attr("data-embed"), baseUrl)
                queueUrl(el.attr("data-iframe"), baseUrl)
                queueUrl(el.attr("data-url"), baseUrl)
                queueUrl(el.attr("data-src"), baseUrl)
                queueUrl(el.attr("href"), baseUrl)
            }

            doc.select("div.download a.udl[href], div.download a[href], div.dlbox li span.e a[href]")
                .forEach { queueUrl(it.attr("href"), baseUrl) }

            doc.select("select.mirror option[value], option[value]").forEach { option ->
                val value = option.attr("value").trim()
                if (value.isBlank()) return@forEach

                if (value.startsWith("http", true) || value.startsWith("/") || value.startsWith("//")) {
                    queueUrl(value, baseUrl)
                    return@forEach
                }

                runCatching {
                    val decodedHtml = base64Decode(value.replace("\\s".toRegex(), ""))
                    val decodedDoc = Jsoup.parse(decodedHtml)
                    decodedDoc.select("iframe[src], iframe[data-src], source[src], video[src], a[href], [data-video], [data-src], [data-url]")
                        .forEach { el ->
                            queueUrl(el.getIframeAttr(), baseUrl)
                            queueUrl(el.attr("src"), baseUrl)
                            queueUrl(el.attr("href"), baseUrl)
                            queueUrl(el.attr("data-video"), baseUrl)
                            queueUrl(el.attr("data-src"), baseUrl)
                            queueUrl(el.attr("data-url"), baseUrl)
                        }
                    extractRawUrls(decodedHtml).forEach { queueUrl(it, baseUrl) }
                }
            }

            doc.select("script").forEach { script ->
                val scriptData = script.data().ifBlank { script.html() }
                extractRawUrls(scriptData).forEach { queueUrl(it, baseUrl) }
            }

            extractRawUrls(doc.html()).forEach { queueUrl(it, baseUrl) }
        }

        fun shouldCrawl(url: String): Boolean {
            val lower = url.lowercase()
            if (isDirectMedia(url) || isKnownEmbed(url)) return false

            val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
            val mainHost = runCatching { URI(mainUrl).host.orEmpty().lowercase() }.getOrDefault("")

            return host == mainHost ||
                host.endsWith(".anoboy.be") ||
                host.contains("anoboy") ||
                lower.contains("/uploads/") ||
                lower.contains("adsbatch") ||
                lower.contains("yupbatch") ||
                lower.contains("acbatch") ||
                lower.contains("/stream/embed.php")
        }

        suspend fun fetchDoc(pageUrl: String, referer: String): org.jsoup.nodes.Document? {
            val referers = listOf(referer, requestReferer, mainUrl).filter { it.isNotBlank() }.distinct()
            for (ref in referers) {
                val doc = runCatching {
                    app.get(
                        pageUrl,
                        headers = browserHeaders,
                        referer = ref,
                        timeout = 20L
                    ).document
                }.getOrNull()
                if (doc != null) return doc
            }
            return null
        }

        if (isMulti) {
            requestData.removePrefix(multiPrefix)
                .split("||")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { queueUrl(it, mainUrl, requestReferer) }
        } else {
            queueUrl(requestData, requestReferer, requestReferer)

            val document = runCatching {
                app.get(
                    requestData,
                    headers = browserHeaders,
                    referer = requestReferer,
                    timeout = 20L
                ).document
            }.getOrNull()

            if (document != null) {
                extractFromDoc(requestData, document)
            }
        }

        var safety = 0
        while (queuedUrls.isNotEmpty() && safety++ < 160) {
            val (next, referer) = queuedUrls.removeFirst()

            if (isDirectMedia(next) || isKnownEmbed(next)) {
                if (emitOrLoad(next, referer)) foundLinks = true
                continue
            }

            if (!shouldCrawl(next) || !crawledUrls.add(next)) continue

            val nestedDoc = fetchDoc(next, referer) ?: continue
            extractFromDoc(next, nestedDoc)
        }

        discoveredUrls.distinct().forEach { link ->
            if (emitOrLoad(link, requestReferer)) foundLinks = true
        }

        return foundLinks
    }

    private fun Element.getImageAttr(): String {
        val result = when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
        return if (result.isBlank()) attr("src").substringBefore(" ") else result
    }

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { it?.isNotEmpty() == true }
            ?: this?.attr("data-src").takeIf { it?.isNotEmpty() == true }
            ?: this?.attr("src")
    }
}