package com.sad25kag.autoembed

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

class AutoEmbedProvider : MainAPI() {
    override var mainUrl = "https://www.themoviedb.org"
    override var name = "AutoEmbed"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbApi = "https://api.themoviedb.org/3"
    private val tmdbApiKey = "b030404650f279792a8d3287232358e3"
    private val tmdbLocale = "language=id-ID&include_adult=false"

    override val mainPage = mainPageOf(
        "$tmdbApi/trending/movie/day?api_key=$tmdbApiKey&$tmdbLocale" to "Film Trending",
        "$tmdbApi/movie/popular?api_key=$tmdbApiKey&$tmdbLocale&region=ID" to "Film Populer",
        "$tmdbApi/movie/now_playing?api_key=$tmdbApiKey&$tmdbLocale&region=ID" to "Sedang Tayang",
        "$tmdbApi/movie/top_rated?api_key=$tmdbApiKey&$tmdbLocale" to "Film Rating Tertinggi",
        "$tmdbApi/movie/upcoming?api_key=$tmdbApiKey&$tmdbLocale&region=ID" to "Film Akan Datang",

        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=28" to "Film Aksi",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=12" to "Film Petualangan",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=16" to "Film Animasi",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=35" to "Film Komedi",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=80" to "Film Kriminal",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=99" to "Film Dokumenter",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=18" to "Film Drama",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=10751" to "Film Keluarga",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=14" to "Film Fantasi",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=27" to "Film Horor",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=9648" to "Film Misteri",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=10749" to "Film Romantis",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=878" to "Film Sci-Fi",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=53" to "Film Thriller",
        "$tmdbApi/discover/movie?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=10752" to "Film Perang",

        "$tmdbApi/trending/tv/day?api_key=$tmdbApiKey&$tmdbLocale" to "Serial Trending",
        "$tmdbApi/tv/popular?api_key=$tmdbApiKey&$tmdbLocale" to "Serial Populer",
        "$tmdbApi/tv/airing_today?api_key=$tmdbApiKey&$tmdbLocale" to "Tayang Hari Ini",
        "$tmdbApi/tv/on_the_air?api_key=$tmdbApiKey&$tmdbLocale" to "Sedang Berjalan",
        "$tmdbApi/tv/top_rated?api_key=$tmdbApiKey&$tmdbLocale" to "Serial Rating Tertinggi",

        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=10759" to "Serial Aksi & Petualangan",
        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=16" to "Serial Animasi",
        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=35" to "Serial Komedi",
        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=80" to "Serial Kriminal",
        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=99" to "Serial Dokumenter",
        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=18" to "Serial Drama",
        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=10751" to "Serial Keluarga",
        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=9648" to "Serial Misteri",
        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=10765" to "Serial Sci-Fi & Fantasi",
        "$tmdbApi/discover/tv?api_key=$tmdbApiKey&$tmdbLocale&sort_by=popularity.desc&with_genres=10768" to "Serial Perang & Politik"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(buildPagedUrl(request.data, page)).parsedSafe<TmdbResults>()
            ?: throw ErrorLoadingException("Respons TMDB tidak valid")

        val typeFromUrl = inferMediaTypeFromUrl(request.data)
        val items = response.results.orEmpty()
            .mapNotNull { media -> media.toSearchResponse(typeFromUrl) }
            .distinctBy { it.url }

        val hasNext = response.page != null &&
            response.totalPages != null &&
            response.page < response.totalPages &&
            page < 500

        return newHomePageResponse(
            HomePageList(request.name, items, isHorizontalImages = true),
            hasNext = hasNext
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$tmdbApi/search/multi?api_key=$tmdbApiKey&$tmdbLocale&query=${query.urlEncoded()}&page=1"
        return app.get(url).parsedSafe<TmdbResults>()?.results.orEmpty()
            .mapNotNull { media -> media.toSearchResponse() }
            .filter { it.type == TvType.Movie || it.type == TvType.TvSeries }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val media = parseSiteMediaUrl(url)
        val detailUrl = when (media.type) {
            "movie" -> "$tmdbApi/movie/${media.id}?api_key=$tmdbApiKey&$tmdbLocale&append_to_response=external_ids,credits,recommendations,videos"
            else -> "$tmdbApi/tv/${media.id}?api_key=$tmdbApiKey&$tmdbLocale&append_to_response=external_ids,credits,recommendations,videos"
        }

        val detail = app.get(detailUrl).parsedSafe<TmdbDetail>()
            ?: throw ErrorLoadingException("Respons detail TMDB tidak valid")

        val title = detail.title ?: detail.name ?: throw ErrorLoadingException("Judul tidak ditemukan")
        val poster = detail.posterPath.toPosterUrl()
        val background = detail.backdropPath.toBackdropUrl()
        val year = (detail.releaseDate ?: detail.firstAirDate)?.substringBefore("-")?.toIntOrNull()
        val tags = detail.genres.orEmpty()
            .mapNotNull { it.name?.translateGenre() }
            .takeIf { it.isNotEmpty() }

        val actors = detail.credits?.cast.orEmpty().mapNotNull { cast ->
            val actorName = cast.name ?: cast.originalName ?: return@mapNotNull null
            ActorData(
                actor = Actor(actorName, cast.profilePath.toPosterUrl()),
                roleString = cast.character
            )
        }.takeIf { it.isNotEmpty() }

        val recommendations = detail.recommendations?.results.orEmpty()
            .mapNotNull { it.toRecommendation(media.type) }
            .takeIf { it.isNotEmpty() }

        val trailers = detail.videos?.results.orEmpty()
            .mapNotNull { video ->
                if (video.site.equals("YouTube", true) && !video.key.isNullOrBlank()) {
                    "https://www.youtube.com/watch?v=${video.key}"
                } else {
                    null
                }
            }

        return if (media.type == "movie") {
            newMovieLoadResponse(
                title,
                "$mainUrl/movie/${media.id}",
                TvType.Movie,
                LinkData(
                    tmdbId = media.id,
                    imdbId = detail.externalIds?.imdbId,
                    type = "movie"
                ).toJson()
            ) {
                posterUrl = poster
                backgroundPosterUrl = background
                plot = detail.overview
                this.year = year
                duration = detail.runtime
                tags?.let { this.tags = it }
                detail.voteAverage?.let { score = Score.from10(it) }
                actors?.let { this.actors = it }
                recommendations?.let { this.recommendations = it }
                if (trailers.isNotEmpty()) addTrailer(trailers)
            }
        } else {
            val episodes = buildEpisodes(media.id, detail.externalIds?.imdbId)
            newTvSeriesLoadResponse(
                title,
                "$mainUrl/tv/${media.id}",
                TvType.TvSeries,
                episodes
            ) {
                posterUrl = poster
                backgroundPosterUrl = background
                plot = detail.overview
                this.year = year
                tags?.let { this.tags = it }
                detail.voteAverage?.let { score = Score.from10(it) }
                actors?.let { this.actors = it }
                recommendations?.let { this.recommendations = it }
                showStatus = detail.status.toShowStatus()
                if (trailers.isNotEmpty()) addTrailer(trailers)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<LinkData>(data)
        val emitted = mutableSetOf<String>()

        invokeWebsiteExtractors(
            linkData = linkData,
            subtitleCallback = subtitleCallback
        ) { link ->
            val key = "${link.name}|${link.url}"
            if (emitted.add(key)) callback(link)
        }

        return emitted.isNotEmpty()
    }

    private suspend fun buildEpisodes(tmdbId: Int, imdbId: String?): List<Episode> {
        val detail = app.get("$tmdbApi/tv/$tmdbId?api_key=$tmdbApiKey&$tmdbLocale").parsedSafe<TmdbDetail>()
            ?: return emptyList()

        val allEpisodes = mutableListOf<Episode>()
        detail.seasons.orEmpty()
            .filter { it.seasonNumber != null }
            .forEach { season ->
                val seasonNumber = season.seasonNumber ?: return@forEach
                val seasonData = app.get(
                    "$tmdbApi/tv/$tmdbId/season/$seasonNumber?api_key=$tmdbApiKey&$tmdbLocale"
                ).parsedSafe<TmdbSeasonDetail>() ?: return@forEach

                seasonData.episodes.orEmpty().forEach { episode ->
                    val episodeNumber = episode.episodeNumber ?: return@forEach
                    allEpisodes += newEpisode(
                        LinkData(
                            tmdbId = tmdbId,
                            imdbId = imdbId,
                            type = "tv",
                            season = seasonNumber,
                            episode = episodeNumber
                        ).toJson()
                    ) {
                        this.name = episode.name ?: "Episode $episodeNumber"
                        this.season = seasonNumber
                        this.episode = episodeNumber
                        this.posterUrl = episode.stillPath.toPosterUrl()
                        this.description = episode.overview
                        episode.voteAverage?.let { score = Score.from10(it) }
                    }.apply {
                        addDate(episode.airDate)
                    }
                }
            }

        return allEpisodes
    }

    private suspend fun invokeWebsiteExtractors(
        linkData: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val emitted = mutableSetOf<String>()

        suspend fun emitWrapped(server: WebsiteServer, link: ExtractorLink) {
            val key = "${link.name}|${link.url}"
            if (!emitted.add(key)) return

            callback.invoke(
                newExtractorLink(
                    source = server.name,
                    name = if (link.name.equals(server.name, ignoreCase = true)) {
                        server.name
                    } else {
                        "${server.name} - ${link.name}"
                    },
                    url = link.url,
                    type = link.type
                ) {
                    quality = link.quality
                    headers = link.headers
                    extractorData = link.extractorData
                    referer = link.referer
                }
            )
        }

        for (server in buildWebsiteServers(linkData)) {
            val before = emitted.size

            runCatching {
                loadExtractor(server.url, server.referer ?: server.url, subtitleCallback) { link ->
                    val key = "${server.name}|${link.name}|${link.url}"
                    if (emitted.add(key)) {
                        callback.invoke(
                            newExtractorLink(
                                source = server.name,
                                name = if (link.name.equals(server.name, ignoreCase = true)) {
                                    server.name
                                } else {
                                    "${server.name} - ${link.name}"
                                },
                                url = link.url,
                                type = link.type
                            ) {
                                quality = link.quality
                                headers = link.headers
                                extractorData = link.extractorData
                                referer = link.referer
                            }
                        )
                    }
                }
            }

            if (emitted.size == before) {
                crawlWebsiteServer(
                    server = server,
                    subtitleCallback = subtitleCallback
                ) { link ->
                    val key = "${server.name}|${link.name}|${link.url}"
                    if (emitted.add(key)) {
                        callback.invoke(link)
                    }
                }
            }
        }
    }

    private suspend fun crawlWebsiteServer(
        server: WebsiteServer,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val queue = ArrayDeque<Pair<String, String>>()
        val visited = mutableSetOf<String>()
        var emitted = false

        fun cleanUrl(raw: String?): String {
            return raw.orEmpty()
                .trim()
                .replace("\\/", "/")
                .replace("\\u002F", "/")
                .replace("\\u003A", ":")
                .replace("\\u0026", "&")
                .replace("\\u003D", "=")
                .replace("&amp;", "&")
                .replace("&#038;", "&")
                .replace("&quot;", "\"")
        }

        fun resolve(raw: String?, base: String): String? {
            val clean = cleanUrl(raw)
            if (clean.isBlank()) return null
            if (clean == "#" || clean.equals("none", true) || clean.equals("null", true)) return null
            if (clean.startsWith("javascript:", true) || clean.startsWith("data:", true) || clean.startsWith("blob:", true)) return null

            return runCatching {
                when {
                    clean.startsWith("http://", true) || clean.startsWith("https://", true) -> clean
                    clean.startsWith("//") -> "https:$clean"
                    else -> URI(base).resolve(clean).toString()
                }
            }.getOrNull()
        }

        fun isBlocked(raw: String): Boolean {
            val lower = raw.lowercase()
            return lower.contains("doubleclick") ||
                lower.contains("googlesyndication") ||
                lower.contains("google-analytics") ||
                lower.contains("googletagmanager") ||
                lower.contains("cloudflareinsights") ||
                lower.contains("facebook.com") ||
                lower.contains("twitter.com") ||
                lower.contains("x.com/") ||
                lower.contains("telegram") ||
                lower.contains("whatsapp") ||
                lower.endsWith(".css") ||
                lower.endsWith(".js") ||
                lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".png") ||
                lower.endsWith(".webp") ||
                lower.endsWith(".gif") ||
                lower.endsWith(".svg") ||
                lower.endsWith(".ico")
        }

        fun isDirectMedia(raw: String): Boolean {
            val lower = raw.lowercase()
            return lower.contains(".m3u8") ||
                lower.contains(".mp4") ||
                lower.contains(".webm") ||
                lower.contains("googlevideo.com/videoplayback") ||
                lower.contains("videoplayback?")
        }

        fun shouldCrawl(raw: String): Boolean {
            if (isBlocked(raw) || isDirectMedia(raw)) return false

            val lower = raw.lowercase()
            return lower.contains("autoembed") ||
                lower.contains("vidsrc") ||
                lower.contains("vsrc.su") ||
                lower.contains("embed.su") ||
                lower.contains("2embed") ||
                lower.contains("vidlink") ||
                lower.contains("rivestream") ||
                lower.contains("flicky") ||
                lower.contains("cinemaos") ||
                lower.contains("vidnest") ||
                lower.contains("moviesapi") ||
                lower.contains("multiembed") ||
                lower.contains("superembed") ||
                lower.contains("smashystream") ||
                lower.contains("streamtape") ||
                lower.contains("dood") ||
                lower.contains("filemoon") ||
                lower.contains("vidhide") ||
                lower.contains("vidguard") ||
                lower.contains("voe.sx") ||
                lower.contains("mixdrop") ||
                lower.contains("mp4upload") ||
                lower.contains("ok.ru") ||
                lower.contains("drive.google.com") ||
                lower.contains("googleusercontent.com")
        }

        fun queueUrl(raw: String?, base: String) {
            val resolved = resolve(raw, base) ?: return
            if (isBlocked(resolved)) return
            if (visited.contains(resolved)) return
            queue.add(resolved to base)
        }

        fun extractCandidatesFromText(text: String, base: String) {
            val cleaned = cleanUrl(text)

            Regex("""https?://[^"'<>\s\\]+""", RegexOption.IGNORE_CASE)
                .findAll(cleaned)
                .forEach { queueUrl(it.value, base) }

            Regex("""//[^"'<>\s\\]+""", RegexOption.IGNORE_CASE)
                .findAll(cleaned)
                .forEach { queueUrl("https:${it.value}", base) }

            Regex("""(?:file|src|url|source|video|hls|data-video|data-src|data-url)\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .findAll(cleaned)
                .forEach { queueUrl(it.groupValues[1], base) }

            Regex("""https?%3A%2F%2F[^"'<>\s\\]+""", RegexOption.IGNORE_CASE)
                .findAll(cleaned)
                .forEach { match ->
                    val decoded = runCatching {
                        URLDecoder.decode(match.value, "UTF-8")
                    }.getOrDefault(match.value)
                    queueUrl(decoded, base)
                }
        }

        fun emitDirect(raw: String, referer: String): Boolean {
            if (!isDirectMedia(raw) || isBlocked(raw)) return false

            val mediaType = if (raw.contains(".m3u8", true)) {
                ExtractorLinkType.M3U8
            } else {
                ExtractorLinkType.VIDEO
            }

            callback.invoke(
                newExtractorLink(
                    source = server.name,
                    name = server.name,
                    url = raw,
                    type = mediaType
                ) {
                    this.referer = referer
                    this.quality = getQualityFromName(raw).takeIf {
                        it != Qualities.Unknown.value
                    } ?: when {
                        raw.contains("1080", true) -> Qualities.P1080.value
                        raw.contains("720", true) -> Qualities.P720.value
                        raw.contains("480", true) -> Qualities.P480.value
                        raw.contains("360", true) -> Qualities.P360.value
                        else -> Qualities.Unknown.value
                    }
                    this.headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to referer,
                        "Origin" to runCatching {
                            val uri = URI(referer)
                            "${uri.scheme}://${uri.host}"
                        }.getOrDefault(server.referer ?: server.url)
                    )
                }
            )

            return true
        }

        queueUrl(server.url, server.referer ?: server.url)

        var safety = 0
        while (queue.isNotEmpty() && safety++ < 55) {
            val (next, referer) = queue.removeFirst()
            if (!visited.add(next)) continue

            if (emitDirect(next, referer)) {
                emitted = true
                continue
            }

            if (!shouldCrawl(next)) {
                runCatching {
                    loadExtractor(next, referer, subtitleCallback) { link ->
                        callback.invoke(
                            newExtractorLink(
                                source = server.name,
                                name = if (link.name.equals(server.name, ignoreCase = true)) server.name else "${server.name} - ${link.name}",
                                url = link.url,
                                type = link.type
                            ) {
                                quality = link.quality
                                headers = link.headers
                                extractorData = link.extractorData
                                this.referer = link.referer
                            }
                        )
                        emitted = true
                    }
                }
                continue
            }

            val response = runCatching {
                app.get(
                    next,
                    referer = referer,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to referer,
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
                    ),
                    timeout = 20L
                )
            }.getOrNull() ?: continue

            val doc = response.document

            doc.select(
                "iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], " +
                    "source[src], video[src], video[data-src], embed[src], object[data], " +
                    "a[href], [data-video], [data-src], [data-url], [data-iframe], [data-embed], option[value]"
            ).forEach { element ->
                queueUrl(element.attr("src"), next)
                queueUrl(element.attr("data-src"), next)
                queueUrl(element.attr("data-litespeed-src"), next)
                queueUrl(element.attr("data-lazy-src"), next)
                queueUrl(element.attr("data"), next)
                queueUrl(element.attr("href"), next)
                queueUrl(element.attr("data-video"), next)
                queueUrl(element.attr("data-url"), next)
                queueUrl(element.attr("data-iframe"), next)
                queueUrl(element.attr("data-embed"), next)
                queueUrl(element.attr("value"), next)
            }

            extractCandidatesFromText(response.text, next)
        }

        return emitted
    }

    private fun buildWebsiteServers(linkData: LinkData): List<WebsiteServer> {
        val tmdbId = linkData.tmdbId ?: return emptyList()
        val imdbId = linkData.imdbId
        val season = linkData.season
        val episode = linkData.episode

        fun movieUrl(domain: String, path: String) = "https://$domain$path"
        fun tvUrl(domain: String, path: String) = "https://$domain$path"

        return if (season == null || episode == null) {
            buildList {
                add(WebsiteServer("AutoEmbed", "https://player.autoembed.cc/embed/movie/$tmdbId"))
                imdbId?.let { add(WebsiteServer("AutoEmbed IMDB", "https://player.autoembed.cc/embed/movie/$it")) }
                add(WebsiteServer("AutoEmbed S2", "https://player.autoembed.cc/embed/movie/$tmdbId?server=2"))
                add(WebsiteServer("AutoEmbed S3", "https://player.autoembed.cc/embed/movie/$tmdbId?server=3"))
                add(WebsiteServer("AutoEmbed S4", "https://player.autoembed.cc/embed/movie/$tmdbId?server=4"))

                add(WebsiteServer("VidSrc Embed RU", movieUrl("vidsrc-embed.ru", "/embed/movie?tmdb=$tmdbId")))
                add(WebsiteServer("VidSrc Embed SU", movieUrl("vidsrc-embed.su", "/embed/movie?tmdb=$tmdbId")))
                add(WebsiteServer("VidSrc ME SU", movieUrl("vidsrcme.su", "/embed/movie?tmdb=$tmdbId")))
                add(WebsiteServer("VSrc", movieUrl("vsrc.su", "/embed/movie?tmdb=$tmdbId")))
                imdbId?.let {
                    add(WebsiteServer("VidSrc Embed RU IMDB", movieUrl("vidsrc-embed.ru", "/embed/movie?imdb=$it")))
                    add(WebsiteServer("VidSrc Embed SU IMDB", movieUrl("vidsrc-embed.su", "/embed/movie?imdb=$it")))
                    add(WebsiteServer("VSrc IMDB", movieUrl("vsrc.su", "/embed/movie?imdb=$it")))
                }

                add(WebsiteServer("Embed SU", "https://embed.su/embed/movie/$tmdbId"))
                add(WebsiteServer("2Embed", "https://www.2embed.cc/embed/$tmdbId"))
                add(WebsiteServer("Vidlink", "https://vidlink.pro/movie/$tmdbId"))
                add(WebsiteServer("RiveStream", "https://rivestream.net/embed?type=movie&id=$tmdbId"))
                add(WebsiteServer("Flicky", "https://flicky.host/embed/movie/?id=$tmdbId"))
                add(WebsiteServer("Cinemaos", "https://cinemaos.tech/player/$tmdbId"))
                add(WebsiteServer("Vidnest", "https://vidnest.fun/movie/$tmdbId"))
                add(WebsiteServer("Bravo", "https://moviesapi.club/movie/$tmdbId"))

                // Fallback lama: beberapa extractor Cloudstream masih mengenali pola domain ini.
                add(WebsiteServer("VidSrc TO", "https://vidsrc.to/embed/movie/$tmdbId"))
                add(WebsiteServer("VidSrc CC", "https://vidsrc.cc/v2/embed/movie/$tmdbId"))
                add(WebsiteServer("VidSrc RIP", "https://vidsrc.rip/embed/movie/$tmdbId"))
                imdbId?.let {
                    add(WebsiteServer("VidSrc TO IMDB", "https://vidsrc.to/embed/movie/$it"))
                    add(WebsiteServer("DrivePlayer", "https://godriveplayer.com/player.php?imdb=$it"))
                    add(WebsiteServer("SuperEmbed", "https://multiembed.mov/?video_id=$it"))
                }
            }
        } else {
            buildList {
                add(WebsiteServer("AutoEmbed", "https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode"))
                imdbId?.let { add(WebsiteServer("AutoEmbed IMDB", "https://player.autoembed.cc/embed/tv/$it/$season/$episode")) }
                add(WebsiteServer("AutoEmbed S2", "https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode?server=2"))
                add(WebsiteServer("AutoEmbed S3", "https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode?server=3"))
                add(WebsiteServer("AutoEmbed S4", "https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode?server=4"))

                add(WebsiteServer("VidSrc Embed RU", tvUrl("vidsrc-embed.ru", "/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode")))
                add(WebsiteServer("VidSrc Embed SU", tvUrl("vidsrc-embed.su", "/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode")))
                add(WebsiteServer("VidSrc ME SU", tvUrl("vidsrcme.su", "/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode")))
                add(WebsiteServer("VSrc", tvUrl("vsrc.su", "/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode")))
                imdbId?.let {
                    add(WebsiteServer("VidSrc Embed RU IMDB", tvUrl("vidsrc-embed.ru", "/embed/tv?imdb=$it&season=$season&episode=$episode")))
                    add(WebsiteServer("VidSrc Embed SU IMDB", tvUrl("vidsrc-embed.su", "/embed/tv?imdb=$it&season=$season&episode=$episode")))
                    add(WebsiteServer("VSrc IMDB", tvUrl("vsrc.su", "/embed/tv?imdb=$it&season=$season&episode=$episode")))
                }

                add(WebsiteServer("Embed SU", "https://embed.su/embed/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("2Embed", "https://www.2embed.cc/embedtv/$tmdbId/$season/$episode"))
                add(WebsiteServer("Vidlink", "https://vidlink.pro/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("RiveStream", "https://rivestream.net/embed?type=tv&id=$tmdbId&season=$season&episode=$episode"))
                add(WebsiteServer("Flicky", "https://flicky.host/embed/tv/?id=$tmdbId/$season/$episode"))
                add(WebsiteServer("Cinemaos", "https://cinemaos.tech/player/$tmdbId/$season/$episode"))
                add(WebsiteServer("Vidnest", "https://vidnest.fun/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("Bravo", "https://moviesapi.club/tv/$tmdbId/$season/$episode"))

                // Fallback lama: beberapa extractor Cloudstream masih mengenali pola domain ini.
                add(WebsiteServer("VidSrc TO", "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("VidSrc CC", "https://vidsrc.cc/v2/embed/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("VidSrc RIP", "https://vidsrc.rip/embed/tv/$tmdbId/$season/$episode"))
                imdbId?.let {
                    add(WebsiteServer("SuperEmbed", "https://multiembed.mov/?video_id=$it&s=$season&e=$episode"))
                }
            }
        }
    }

    private fun buildPagedUrl(url: String, page: Int): String {
        if (page <= 1) return url
        val cleanUrl = url.replace(Regex("([?&])page=\\d+"), "\$1").trimEnd('?', '&')
        val joiner = if (cleanUrl.contains("?")) "&" else "?"
        return "$cleanUrl${joiner}page=$page"
    }

    private fun parseSiteMediaUrl(url: String): SiteMedia {
        val clean = url.substringBefore("?")
        val parts = clean.trimEnd('/').split("/")
        val type = parts.getOrNull(parts.lastIndex - 1) ?: throw ErrorLoadingException("Tipe media tidak valid")
        val id = parts.lastOrNull()?.toIntOrNull() ?: throw ErrorLoadingException("ID media tidak valid")
        return SiteMedia(type = type, id = id)
    }

    private fun inferMediaTypeFromUrl(url: String): String? {
        return when {
            url.contains("/movie/", ignoreCase = true) || url.contains("/discover/movie", ignoreCase = true) -> "movie"
            url.contains("/tv/", ignoreCase = true) || url.contains("/discover/tv", ignoreCase = true) -> "tv"
            url.contains("/trending/movie", ignoreCase = true) -> "movie"
            url.contains("/trending/tv", ignoreCase = true) -> "tv"
            else -> null
        }
    }

    private fun TmdbMedia.toSearchResponse(defaultMediaType: String? = null): SearchResponse? {
        val resolvedMediaType = mediaType ?: defaultMediaType ?: when {
            !title.isNullOrBlank() -> "movie"
            !name.isNullOrBlank() -> "tv"
            else -> null
        } ?: return null

        if (resolvedMediaType != "movie" && resolvedMediaType != "tv") return null

        val resolvedTitle = title ?: name ?: return null
        val resolvedId = id ?: return null
        val year = (releaseDate ?: firstAirDate)?.substringBefore("-")?.toIntOrNull()

        return if (resolvedMediaType == "movie") {
            newMovieSearchResponse(resolvedTitle, "$mainUrl/movie/$resolvedId", TvType.Movie) {
                posterUrl = posterPath.toPosterUrl()
                this.year = year
                voteAverage?.let { score = Score.from10(it) }
            }
        } else {
            newTvSeriesSearchResponse(resolvedTitle, "$mainUrl/tv/$resolvedId", TvType.TvSeries) {
                posterUrl = posterPath.toPosterUrl()
                this.year = year
                voteAverage?.let { score = Score.from10(it) }
            }
        }
    }

    private fun TmdbMedia.toRecommendation(defaultMediaType: String? = null): SearchResponse? {
        val mediaType = this.mediaType ?: defaultMediaType ?: if (!title.isNullOrBlank()) "movie" else "tv"
        return toSearchResponse(mediaType)
    }

    private fun String?.toPosterUrl(): String? {
        if (this.isNullOrBlank()) return null
        return if (startsWith("/")) "https://image.tmdb.org/t/p/w500$this" else this
    }

    private fun String?.toBackdropUrl(): String? {
        if (this.isNullOrBlank()) return null
        return if (startsWith("/")) "https://image.tmdb.org/t/p/original$this" else this
    }

    private fun String?.toShowStatus(): ShowStatus? {
        return when (this) {
            "Returning Series", "In Production", "Planned" -> ShowStatus.Ongoing
            "Ended", "Canceled" -> ShowStatus.Completed
            else -> null
        }
    }

    private fun String.translateGenre(): String {
        return when (trim().lowercase(Locale.ROOT)) {
            "action" -> "Aksi"
            "action & adventure" -> "Aksi & Petualangan"
            "adventure" -> "Petualangan"
            "animation" -> "Animasi"
            "comedy" -> "Komedi"
            "crime" -> "Kriminal"
            "documentary" -> "Dokumenter"
            "drama" -> "Drama"
            "family" -> "Keluarga"
            "fantasy" -> "Fantasi"
            "history" -> "Sejarah"
            "horror" -> "Horor"
            "kids" -> "Anak-anak"
            "music" -> "Musik"
            "mystery" -> "Misteri"
            "news" -> "Berita"
            "reality" -> "Reality"
            "romance" -> "Romantis"
            "science fiction" -> "Sci-Fi"
            "sci-fi & fantasy" -> "Sci-Fi & Fantasi"
            "soap" -> "Sinetron"
            "talk" -> "Talk Show"
            "thriller" -> "Thriller"
            "tv movie" -> "Film TV"
            "war" -> "Perang"
            "war & politics" -> "Perang & Politik"
            "western" -> "Western"
            else -> trim()
        }
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    data class SiteMedia(
        val type: String,
        val id: Int,
    )

    data class LinkData(
        val tmdbId: Int? = null,
        val imdbId: String? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
    )

    data class WebsiteServer(
        val name: String,
        val url: String,
        val referer: String? = null,
    )

    data class TmdbGenres(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class TmdbVideo(
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("site") val site: String? = null,
    )

    data class TmdbVideos(
        @JsonProperty("results") val results: List<TmdbVideo>? = null,
    )

    data class TmdbExternalIds(
        @JsonProperty("imdb_id") val imdbId: String? = null,
    )

    data class TmdbCast(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class TmdbCredits(
        @JsonProperty("cast") val cast: List<TmdbCast>? = null,
    )

    data class TmdbResults(
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("results") val results: List<TmdbMedia>? = null,
        @JsonProperty("total_pages") val totalPages: Int? = null,
    )

    data class TmdbMedia(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
    )

    data class TmdbSeason(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class TmdbEpisode(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("air_date") val airDate: String? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
    )

    data class TmdbSeasonDetail(
        @JsonProperty("episodes") val episodes: List<TmdbEpisode>? = null,
    )

    data class TmdbDetail(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: List<TmdbGenres>? = null,
        @JsonProperty("external_ids") val externalIds: TmdbExternalIds? = null,
        @JsonProperty("credits") val credits: TmdbCredits? = null,
        @JsonProperty("recommendations") val recommendations: TmdbResults? = null,
        @JsonProperty("videos") val videos: TmdbVideos? = null,
        @JsonProperty("seasons") val seasons: List<TmdbSeason>? = null,
    )
}
