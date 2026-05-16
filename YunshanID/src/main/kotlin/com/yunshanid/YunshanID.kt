package com.yunshanid

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.*

class YunshanID : MainAPI() {
    override var mainUrl = "https://yunshanid.site"
    override var name = "Yunshan ID 🏔️"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    companion object {
        const val API_BASE = "https://yunshanid.site/api"
    }

    // Mengelompokkan kategori menu utama di CloudStream
    override val mainPage = mainPageOf(
        "latest" to "Rilisan Terbaru",
        "ongoing" to "Donghua Ongoing",
        "completed" to "Donghua Completed",
        "movie" to "Donghua Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$API_BASE/donghuas").text
        val items = tryParseJson<List<YunshanItem>>(response) ?: emptyList()
        
        // Memfilter list data lokal berdasarkan kategori menu yang diklik
        val filteredItems = when (request.data) {
            "ongoing" -> items.filter { it.status?.contains("On-Going", true) == true }
            "completed" -> items.filter { it.status?.contains("Completed", true) == true }
            "movie" -> items.filter { it.type?.contains("Movie", true) == true }
            else -> items // "latest" menampilkan semua
        }

        val homeResults = filteredItems.map { item ->
            newAnimeSearchResponse(item.title, item.id.toString(), TvType.Anime) {
                this.posterUrl = item.posterUrl ?: item.poster
            }
        }

        return newHomePageResponse(HomePageList(request.name, homeResults), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$API_BASE/donghuas").text
        val items = tryParseJson<List<YunshanItem>>(response) ?: emptyList()

        // Mencari judul donghua yang cocok (case-insensitive)
        return items.filter { it.title.contains(query, true) }.map { item ->
            newAnimeSearchResponse(item.title, item.id.toString(), TvType.Anime) {
                this.posterUrl = item.posterUrl ?: item.poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url
        
        // FIX: Mengambil langsung dari list utama untuk menghindari error kegagalan struktural parsing di /api/donghua/id
        val response = app.get("$API_BASE/donghuas").text
        val items = tryParseJson<List<YunshanItem>>(response) ?: emptyList()
        
        // Cari data donghua spesifik yang dicari berdasarkan ID
        val item = items.find { it.id.toString() == id } 
            ?: throw ErrorLoadingException("Detail Donghua tidak ditemukan")

        val title = item.title
        val poster = item.posterUrl ?: item.poster
        val description = item.synopsis
        val tags = item.genres // Menampilkan daftar genre/tag secara lengkap

        val tvType = if (item.type?.contains("Movie", true) == true) TvType.Movie else TvType.TvSeries

        if (tvType == TvType.Movie) {
            return newMovieLoadResponse(title, url, TvType.AnimeMovie, "$id-1") {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        } else {
            // Mengurutkan deretan nomor episode dari yang paling kecil ke besar
            val episodes = item.episodesMap?.sorted()?.map { epNum ->
                newEpisode("$id-$epNum") {
                    this.name = "Episode $epNum"
                    this.episode = epNum
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("-")
        if (parts.size < 2) return false
        val animeId = parts[0]
        val epNum = parts[1]

        // Mengambil respons teks dari player internal watch
        val watchResponse = app.get("$API_BASE/watch/$animeId/$epNum").text

        // FIX: Bersihkan karakter pelindung escape backslash (\/) bawaan database JSON
        // Agar pencarian Regex tidak memotong string URL utama
        val cleanResponse = watchResponse.replace("\\/", "/")

        // Menggunakan regex aman penangkap string link di dalam tanda kutip database
        val linkRegex = """https?://[^\s"']+""".toRegex()
        val foundLinks = linkRegex.findAll(cleanResponse).map { it.value }.toList()

        var linkFound = false
        for (link in foundLinks) {
            // Bersihkan sisa karakter penutup string jika terikut di ujung URL
            val cleanLink = link.trim().removeSuffix("\\").removeSuffix("\"").removeSuffix("'")
            
            // Oper tautan ke sistem pemutar video bawaan CloudStream (seperti OkRu)
            if (cleanLink.contains("ok.ru") || cleanLink.contains("odnoklassniki") || cleanLink.contains("video")) {
                loadExtractor(cleanLink, subtitleCallback, callback)
                linkFound = true
            }
        }

        // Jalur Cadangan: Jika tidak ada filter yang cocok, paksa muat URL pertama yang valid
        if (!linkFound && foundLinks.isNotEmpty()) {
            val primaryLink = foundLinks.first().trim().removeSuffix("\\").removeSuffix("\"").removeSuffix("'")
            if (primaryLink.startsWith("http")) {
                loadExtractor(primaryLink, subtitleCallback, callback)
                linkFound = true
            }
        }

        return linkFound
    }
}

// Model Data Class penampung data JSON terintegrasi
data class YunshanItem(
    val id: Int,
    val title: String,
    val synopsis: String? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    val poster: String? = null,
    val status: String? = null,
    val type: String? = null,
    val genres: List<String>? = null,
    @JsonProperty("episodes_map") val episodesMap: List<Int>? = null
)