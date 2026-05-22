package com.juraganfilm

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.Normalizer

class JuraganFilmProvider : MainAPI() {
    override var mainUrl = base64Decode("aHR0cHM6Ly96MS5pZGxpeGt1LmNvbQ==")
    override var name = "JuraganFilm"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/api/movies?page=%d&limit=36&sort=createdAt" to "Movie Terbaru",
        "$mainUrl/api/series?page=%d&limit=36&sort=createdAt" to "Series Terbaru",

        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&type=movie" to "Browse Movie",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&type=series" to "Browse Series",

        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=netflix" to "Netflix",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=disney-plus" to "Disney+",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=hbo" to "HBO",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=prime-video" to "Amazon Prime",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=apple-tv-plus" to "Apple TV+",

        "$mainUrl/api/browse?page=%d&limit=36&sort=popular" to "Popular",
        "$mainUrl/api/browse?page=%d&limit=36&sort=rating" to "Rating Tertinggi",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=action" to "Action",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=adventure" to "Adventure",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=animation" to "Animation",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=comedy" to "Comedy",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=crime" to "Crime",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=documentary" to "Documentary",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=drama" to "Drama",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=fantasy" to "Fantasy",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=horror" to "Horror",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=romance" to "Romance",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=science-fiction" to "Sci-Fi",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&genre=thriller" to "Thriller"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = request.data.format(page.coerceAtLeast(1))
        val res = runCatching {
            app.get(url, timeout = 10000L).parsedSafe<ApiResponse>()
        }.getOrNull()

        val home = res?.data.orEmpty()
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }

        return newHomePageResponse(
            request.name,
            home,
            hasNext = home.isNotEmpty()
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query, 1)?.items
    }

    override suspend fun search(
        query: String,
        page: Int
    ): SearchResponseList? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$mainUrl/api/search?q=$q&page=${page.coerceAtLeast(1)}&limit=12"

        val res = runCatching {
            app.get(url, timeout = 10000L).parsedSafe<SearchApiResponse>()
        }.getOrNull() ?: return emptyList<SearchResponse>().toNewSearchResponseList()

        return res.results
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }
            .toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val data = app.get(url, timeout = 10000L).parsedSafe<DetailResponse>()
            ?: throw ErrorLoadingException("Invalid JSON")

        val title = data.title ?: data.name ?: "Unknown"
        val poster = data.posterPath?.toTmdbPoster("w500")
        val backdrop = data.backdropPath?.toTmdbPoster("w780")
        val logoUrl = data.logoPath?.toTmdbPoster("w500")
        val year = (data.releaseDate ?: data.firstAirDate)?.substringBefore("-")?.toIntOrNull()
        val tags = data.genres.orEmpty().mapNotNull { it.name }.distinct()
        val actors = data.cast.orEmpty().mapNotNull {
            val actorName = it.name ?: return@mapNotNull null
            Actor(actorName, it.profilePath?.toTmdbPoster("w185"))
        }

        val trailer = data.trailerUrl
        val rating = data.voteAverage
        val recommendations = getRecommendations(data)

        return if (!data.seasons.isNullOrEmpty()) {
            val episodes = getEpisodes(data)

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.logoUrl = logoUrl
                this.year = year
                this.plot = data.overview
                this.tags = tags
                this.score = Score.from10(rating?.toString())
                addActors(actors)
                addTrailer(trailer)
                addTMDbId(data.tmdbId)
                addImdbId(data.imdbId)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(
                    id = data.id.orEmpty(),
                    type = "movie"
                ).toJson()
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.logoUrl = logoUrl
                this.year = year
                this.plot = data.overview
                this.tags = tags
                this.score = Score.from10(rating?.toString())
                addActors(actors)
                addTrailer(trailer)
                addTMDbId(data.tmdbId)
                addImdbId(data.imdbId)
                this.recommendations = recommendations
            }
        }
    }

    private suspend fun getRecommendations(data: DetailResponse): List<SearchResponse> {
        val relatedUrl = if (!data.seasons.isNullOrEmpty()) {
            "$mainUrl/api/series/${data.slug}/related"
        } else {
            "$mainUrl/api/movies/${data.slug}/related"
        }

        return runCatching {
            app.get(relatedUrl, referer = mainUrl)
                .parsedSafe<ApiResponse>()
                ?.data
                ?.mapNotNull { it.toSearchResponse() }
                ?.distinctBy { it.url }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private suspend fun getEpisodes(data: DetailResponse): List<Episode> {
        val episodes = mutableListOf<Episode>()

        data.firstSeason?.episodes.orEmpty().forEach { ep ->
            episodes.add(ep.toEpisode(data.firstSeason?.seasonNumber))
        }

        data.seasons.orEmpty().forEach { season ->
            val seasonNum = season.seasonNumber ?: return@forEach
            if (seasonNum == data.firstSeason?.seasonNumber) return@forEach

            val seasonUrl = "$mainUrl/api/series/${data.slug}/season/$seasonNum"
            val seasonData = runCatching {
                app.get(seasonUrl, referer = mainUrl)
                    .parsedSafe<SeasonWrapper>()
                    ?.season
            }.getOrNull()

            seasonData?.episodes.orEmpty().forEach { ep ->
                episodes.add(ep.toEpisode(seasonNum))
            }
        }

        return episodes.distinctBy { it.data }.sortedWith(
            compareBy<Episode> { it.season ?: 0 }.thenBy { it.episode ?: 0 }
        )
    }

    private fun EpisodeData.toEpisode(seasonNumber: Int?): Episode {
        return newEpisode(
            LoadData(
                id = id.orEmpty(),
                type = "episode"
            ).toJson()
        ) {
            this.name = name
            this.season = seasonNumber
            this.episode = episodeNumber
            this.description = overview
            this.runTime = runtime
            this.score = Score.from10(voteAverage?.toString())
            this.posterUrl = stillPath?.toTmdbPoster("w300")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = runCatching {
            AppUtils.parseJson<LoadData>(data)
        }.getOrNull() ?: return false

        val res = runCatching {
            app.get("$mainUrl/api/watch/play-info/${parsed.type}/${parsed.id}", timeout = 10000L)
                .parsedSafe<Res>()
        }.getOrNull() ?: return false

        val redeemUrl = res.redeemUrl ?: return false
        val claim = res.claim.orEmpty()

        val body = """
            {
                "claim": "$claim"
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val iframeResponse = runCatching {
            app.post(
                redeemUrl,
                requestBody = body,
                referer = mainUrl,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json"
                )
            ).parsedSafe<Iframe>()
        }.getOrNull() ?: return false

        var found = false

        iframeResponse.url
            ?.takeIf { it.isNotBlank() }
            ?.let { streamUrl ->
                generateM3u8(
                    source = name,
                    streamUrl = streamUrl,
                    referer = mainUrl
                ).forEach(callback)
                found = true
            }

        iframeResponse.subtitles.orEmpty().forEach { subtitle ->
            val label = subtitle.label ?: subtitle.lang ?: "Subtitle"
            val path = subtitle.path ?: return@forEach

            subtitleCallback(
                newSubtitleFile(
                    label,
                    path
                )
            )
        }

        return found
    }

    private fun ApiItem.toSearchResponse(): SearchResponse? {
        val title = title ?: name ?: return null
        val poster = posterPath?.toTmdbPoster("w342")
        val year = (releaseDate ?: firstAirDate)?.substringBefore("-")?.toIntOrNull()
        val rating = voteAverage
        val qualityValue = getSearchQuality(quality)

        val link = when (contentType) {
            "movie" -> "$mainUrl/api/movies/$slug"
            "tv_series", "series" -> "$mainUrl/api/series/$slug"
            else -> {
                if (!firstAirDate.isNullOrBlank()) "$mainUrl/api/series/$slug"
                else "$mainUrl/api/movies/$slug"
            }
        }

        return if (link.contains("/api/movies/")) {
            newMovieSearchResponse(title, link, TvType.Movie) {
                this.posterUrl = poster
                this.year = year
                this.quality = qualityValue
                this.score = Score.from10(rating)
            }
        } else {
            newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
                this.quality = qualityValue
                this.score = Score.from10(rating)
            }
        }
    }

    private fun String.toTmdbPoster(size: String): String {
        return if (startsWith("http", true)) this else "https://image.tmdb.org/t/p/$size$this"
    }
}

fun getSearchQuality(check: String?): SearchQuality? {
    val s = check ?: return null
    val u = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
    val patterns = listOf(
        Regex("\\b(4k|ds4k|uhd|2160p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.FourK,
        Regex("\\b(hdts|hdcam|hdtc)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HdCam,
        Regex("\\b(camrip|cam[- ]?rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip,
        Regex("\\b(cam)\\b", RegexOption.IGNORE_CASE) to SearchQuality.Cam,
        Regex("\\b(web[- ]?dl|webrip|webdl)\\b", RegexOption.IGNORE_CASE) to SearchQuality.WebRip,
        Regex("\\b(bluray|bdrip|blu[- ]?ray)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,
        Regex("\\b(1440p|qhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,
        Regex("\\b(1080p|fullhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,
        Regex("\\b(720p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.SD,
        Regex("\\b(hdrip|hdtv)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,
        Regex("\\b(dvd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.DVD,
        Regex("\\b(hq)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HQ,
        Regex("\\b(rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip
    )

    for ((regex, quality) in patterns) {
        if (regex.containsMatchIn(u)) return quality
    }

    return null
}

data class ApiResponse(
    @JsonProperty("data") val data: List<ApiItem> = emptyList()
)

data class SearchApiResponse(
    @JsonProperty("results") val results: List<ApiItem> = emptyList()
)

data class ApiItem(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("posterPath") val posterPath: String? = null,
    @JsonProperty("poster_path") val posterPathAlt: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("release_date") val releaseDateAlt: String? = null,
    @JsonProperty("firstAirDate") val firstAirDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDateAlt: String? = null,
    @JsonProperty("contentType") val contentType: String? = null,
    @JsonProperty("content_type") val contentTypeAlt: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("voteAverage") val voteAverage: String? = null,
    @JsonProperty("vote_average") val voteAverageAlt: String? = null
) {
    val finalPosterPath: String? get() = posterPath ?: posterPathAlt
    val finalReleaseDate: String? get() = releaseDate ?: releaseDateAlt
    val finalFirstAirDate: String? get() = firstAirDate ?: firstAirDateAlt
    val finalContentType: String? get() = contentType ?: contentTypeAlt
    val finalVoteAverage: String? get() = voteAverage ?: voteAverageAlt
}

data class DetailResponse(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("posterPath") val posterPath: String? = null,
    @JsonProperty("poster_path") val posterPathAlt: String? = null,
    @JsonProperty("backdropPath") val backdropPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPathAlt: String? = null,
    @JsonProperty("logoPath") val logoPath: String? = null,
    @JsonProperty("logo_path") val logoPathAlt: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("release_date") val releaseDateAlt: String? = null,
    @JsonProperty("firstAirDate") val firstAirDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDateAlt: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("genres") val genres: List<Genre>? = null,
    @JsonProperty("cast") val cast: List<Cast>? = null,
    @JsonProperty("trailerUrl") val trailerUrl: String? = null,
    @JsonProperty("trailer_url") val trailerUrlAlt: String? = null,
    @JsonProperty("voteAverage") val voteAverage: String? = null,
    @JsonProperty("vote_average") val voteAverageAlt: String? = null,
    @JsonProperty("tmdbId") val tmdbId: Int? = null,
    @JsonProperty("tmdb_id") val tmdbIdAlt: Int? = null,
    @JsonProperty("imdbId") val imdbId: String? = null,
    @JsonProperty("imdb_id") val imdbIdAlt: String? = null,
    @JsonProperty("seasons") val seasons: List<Season>? = null,
    @JsonProperty("firstSeason") val firstSeason: Season? = null,
    @JsonProperty("first_season") val firstSeasonAlt: Season? = null
) {
    val posterPathFinal: String? get() = posterPath ?: posterPathAlt
    val backdropPathFinal: String? get() = backdropPath ?: backdropPathAlt
    val logoPathFinal: String? get() = logoPath ?: logoPathAlt
    val releaseDateFinal: String? get() = releaseDate ?: releaseDateAlt
    val firstAirDateFinal: String? get() = firstAirDate ?: firstAirDateAlt
    val trailerUrlFinal: String? get() = trailerUrl ?: trailerUrlAlt
    val voteAverageFinal: String? get() = voteAverage ?: voteAverageAlt
    val tmdbIdFinal: Int? get() = tmdbId ?: tmdbIdAlt
    val imdbIdFinal: String? get() = imdbId ?: imdbIdAlt
    val firstSeasonFinal: Season? get() = firstSeason ?: firstSeasonAlt
}

data class Genre(
    @JsonProperty("name") val name: String? = null
)

data class Cast(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("profilePath") val profilePath: String? = null,
    @JsonProperty("profile_path") val profilePathAlt: String? = null
)

data class SeasonWrapper(
    @JsonProperty("season") val season: Season? = null
)

data class Season(
    @JsonProperty("seasonNumber") val seasonNumber: Int? = null,
    @JsonProperty("season_number") val seasonNumberAlt: Int? = null,
    @JsonProperty("episodes") val episodes: List<EpisodeData>? = null
)

data class EpisodeData(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("episodeNumber") val episodeNumber: Int? = null,
    @JsonProperty("episode_number") val episodeNumberAlt: Int? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("voteAverage") val voteAverage: String? = null,
    @JsonProperty("vote_average") val voteAverageAlt: String? = null,
    @JsonProperty("stillPath") val stillPath: String? = null,
    @JsonProperty("still_path") val stillPathAlt: String? = null
)

data class LoadData(
    @JsonProperty("id") val id: String,
    @JsonProperty("type") val type: String
)

data class Res(
    @JsonProperty("claim") val claim: String? = null,
    @JsonProperty("redeemUrl") val redeemUrl: String? = null,
    @JsonProperty("redeem_url") val redeemUrlAlt: String? = null
)

data class Iframe(
    @JsonProperty("code") val code: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("expiresAt") val expiresAt: Long? = null,
    @JsonProperty("expires_at") val expiresAtAlt: Long? = null,
    @JsonProperty("subtitles") val subtitles: List<Subtitle> = emptyList(),
    @JsonProperty("videoId") val videoId: String? = null,
    @JsonProperty("video_id") val videoIdAlt: String? = null
)

data class Subtitle(
    @JsonProperty("lang") val lang: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("path") val path: String? = null
)