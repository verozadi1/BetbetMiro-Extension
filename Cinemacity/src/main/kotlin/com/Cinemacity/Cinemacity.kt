package com.Cinemacity

import android.util.Log
import com.google.gson.Gson
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

class Cinemacity : MainAPI() {
    override var mainUrl = "https://cinemacity.pro"
    override var name = "CinemaCiry"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Origin" to mainUrl,
        "Accept" to "application/json, text/javascript, */*; q=0.01"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/api/v1/catalog/movie/cinemacity.pro/last-added.json" to "Last Added Movies",
        "$mainUrl/api/v1/catalog/series/cinemacity.pro/last-added.json" to "Last Added Series",
        "$mainUrl/api/v1/catalog/movie/cinemacity.pro/trending.json" to "Trending Movies",
        "$mainUrl/api/v1/catalog/series/cinemacity.pro/trending.json" to "Trending Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get(request.data, headers = commonHeaders).text
        val json = AppUtils.parseJson<SearchResponseList>(res)
        val home = json.metas?.mapNotNull { it.toSearchResult() } ?: emptyList()
        return newHomePageResponse(request.name, home)
    }

    private fun ResponseData.Meta.toSearchResult(): SearchResponse? {
        val id = id ?: return null
        val type = if (type == "movie") TvType.Movie else TvType.TvSeries
        return newAnimeSearchResponse(name ?: "", AppUtils.toJson(Data(type.toType(), id)), type) {
            this.posterUrl = poster ?: rawPosterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryEncoded = URLEncoder.encode(query, "UTF-8")
        val movieSearch = app.get("$mainUrl/api/v1/catalog/movie/cinemacity.pro/search=$queryEncoded.json", headers = commonHeaders).text
        val seriesSearch = app.get("$mainUrl/api/v1/catalog/series/cinemacity.pro/search=$queryEncoded.json", headers = commonHeaders).text

        val movies = AppUtils.parseJson<SearchResponseList>(movieSearch).metas?.mapNotNull { it.toSearchResult() } ?: emptyList()
        val series = AppUtils.parseJson<SearchResponseList>(seriesSearch).metas?.mapNotNull { it.toSearchResult() } ?: emptyList()

        return movies + series
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = AppUtils.parseJson<Data>(url)
        val type = data.type
        val id = data.id

        val res = app.get("$mainUrl/api/v1/meta/$type/$id.json", headers = commonHeaders).text
        val json = AppUtils.parseJson<ResponseData>(res).meta ?: return null

        val title = json.name ?: ""
        val poster = json.poster ?: json.rawPosterUrl
        val description = json.description
        val year = json.year
        val rating = json.imdbRating?.toLongOrNull()
        val genres = json.genres
        val status = if (json.status == "Ongoing") ShowStatus.Ongoing else ShowStatus.Completed

        val trailer = json.trailers?.firstOrNull { it.type == "trailer" }?.source?.let {
            if (it.startsWith("http")) it else "https://www.youtube.com/watch?v=$it"
        }

        val actors = json.appExtras?.cast?.map { Actor(it.name ?: "", it.photo) } ?: emptyList()

        val recommendations = emptyList<SearchResponse>()

        val episodes = mutableListOf<Episode>()

        if (type == "movie") {
            episodes.add(
                Episode(
                    data = AppUtils.toJson(Data("movie", id)),
                    name = title,
                )
            )
        } else {
            json.videos?.forEach {
                episodes.add(
                    Episode(
                        data = AppUtils.toJson(Data("series", it.id ?: "")),
                        name = it.name,
                        season = it.season,
                        episode = it.episode,
                        posterUrl = it.thumbnail,
                        description = it.overview,
                    )
                )
            }
        }

        return newAnimeLoadResponse(title, url, if (type == "movie") TvType.Movie else TvType.TvSeries) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genres
            this.year = year?.split("-")?.firstOrNull()?.toIntOrNull()
            this.rating = rating?.toInt()
            this.showStatus = status
            this.recommendations = recommendations
            this.addActors(actors)
            if (trailer != null) this.addTrailer(trailer)
            addEpisodes(DubStatus.Subbed, episodes)
            this.dataUrl = AppUtils.toJson(Data(type, id))
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val (type, id) = AppUtils.parseJson<Data>(data)

        val streamUrl = "$mainUrl/api/v1/stream/$type/$id.json"
        val res = app.get(streamUrl, headers = commonHeaders).text
        
        if (res.isBlank() || !res.contains("streams")) return false
        
        val streams = JSONObject(res).optJSONArray("streams") ?: return false

        for (i in 0 until streams.length()) {
            val stream = streams.getJSONObject(i)
            val url = stream.optString("url")
            val externalUrl = stream.optString("externalUrl")
            val infoHash = stream.optString("infoHash")

            val linkToProcess = when {
                url.isNotBlank() -> url
                externalUrl.isNotBlank() -> externalUrl
                else -> ""
            }

            if (linkToProcess.isNotBlank()) {
                if (linkToProcess.contains("cinemacity.pro")) {
                    CinemaUtils.getDownloadLinks(linkToProcess).forEach { (link, quality, lang) ->
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "$name - $lang",
                                url = link,
                                referer = "$mainUrl/",
                                quality = quality,
                                type = ExtractorLinkType.VIDEO
                            )
                        )
                    }
                } else if (linkToProcess.startsWith("http")) {
                    loadExtractor(linkToProcess, "$mainUrl/", subtitleCallback, callback)
                }
            }
        }
        return true
    }

    data class Data(val type: String, val id: String)
    fun String.toType(): String = if (this == "movie") "movie" else "series"
}

object CinemaUtils {
    private const val baseUrl = "https://cinemacity.pro"

    suspend fun getDownloadLinks(url: String): List<Triple<String, Int, String>> {
        val id = url.substringAfter("id=").substringBefore("&")
        val downloadUrl = "$baseUrl/download?id=$id"
        
        val res = app.get(downloadUrl, headers = mapOf("User-Agent" to "Mozilla/5.0")).text
        
        // Regex yang diperbaiki agar lebih tahan terhadap perubahan struktur skrip
        val videoDataRegex = Regex("""const\s+videos\s*=\s*(\[[\s\S]*?\]);""")
        val match = videoDataRegex.find(res) ?: return emptyList()
        val jsonString = match.groupValues[1]

        val results = mutableListOf<Triple<String, Int, String>>()
        val gson = Gson()
        val videoList = gson.fromJson(jsonString, Array<Map<String, Any>>::collect { it }::class.java)

        videoList.forEach { videoMap ->
            val videoPath = videoMap["video"] as? String ?: return@forEach
            val audioPath = videoMap["audio"] as? String ?: ""
            val subtitlePaths = videoMap["subtitle"] as? String ?: ""
            val lang = videoMap["lang"] as? String ?: "Unknown"
            val resLabel = videoMap["res"] as? String ?: "720"
            val quality = getQualityFromString(resLabel)

            // Konstruksi URL download akhir CinemaCity
            val finalUrl = "$baseUrl/download?action=download&video=${URLEncoder.encode(videoPath, "UTF-8")}&audio=${URLEncoder.encode(audioPath, "UTF-8")}&subtitle=${URLEncoder.encode(subtitlePaths, "UTF-8")}&name=Video"
            
            results.add(Triple(finalUrl, quality, lang))
        }

        return results
    }
}
