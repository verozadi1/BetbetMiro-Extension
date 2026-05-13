package com.Melolo

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class Melolo : MainAPI() {

    override var mainUrl = "https://api.tmthreader.com"
    override var name = "Melolo"
    override var lang = "id"

    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.AsianDrama
    )

    private val aid = "645713"
    private val catalogBase = "https://melolo-api-azure.vercel.app/api/melolo"

    override val mainPage = mainPageOf(
        "latest" to "Terbaru",
        "trending" to "Trending",
        "q:romansa" to "Romansa",
        "q:aksi" to "Aksi",
        "q:mafia" to "Mafia",
        "q:balas dendam" to "Balas Dendam",
        "q:pernikahan" to "Pernikahan",
        "q:drama periode" to "Drama Periode"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val isSearchCategory = request.data.startsWith("q:")

        if (page > 1 && !isSearchCategory) {
            return newHomePageResponse(HomePageList(request.name, emptyList()), false)
        }

        val books = when {
            isSearchCategory -> fetchSearchPage(
                request.data.removePrefix("q:").trim(),
                limit = 20,
                offset = (page - 1) * 20
            ).first

            request.data == "trending" -> fetchTrending()

            else -> fetchLatest()
        }

        val items = books.mapNotNull { b ->
            val id = b.book_id ?: return@mapNotNull null
            val name = b.book_name ?: return@mapNotNull null

            newTvSeriesSearchResponse(
                name,
                "$mainUrl/series/$id",
                TvType.TvSeries
            ) {
                this.posterUrl = b.thumb_url
            }
        }

        return newHomePageResponse(
            HomePageList(request.name, items),
            false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return fetchSearch(query, 20, 0).mapNotNull { b ->
            val id = b.book_id ?: return@mapNotNull null
            val name = b.book_name ?: return@mapNotNull null

            newTvSeriesSearchResponse(
                name,
                "$mainUrl/series/$id",
                TvType.TvSeries
            ) {
                this.posterUrl = b.thumb_url
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val items = fetchSearchPage(
            query,
            20,
            (page - 1) * 20
        ).first.mapNotNull { b ->
            val id = b.book_id ?: return@mapNotNull null
            val name = b.book_name ?: return@mapNotNull null

            newTvSeriesSearchResponse(
                name,
                "$mainUrl/series/$id",
                TvType.TvSeries
            ) {
                this.posterUrl = b.thumb_url
            }
        }

        return items.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {

        val bookId = url.substringAfterLast("/").substringBefore("?")
        val detail = fetchDetail(bookId)

        val episodes = detail.video_list
            .filter { it.disable_play != true }
            .mapNotNull { ep ->
                val vid = ep.vid ?: return@mapNotNull null
                val index = ep.vid_index ?: return@mapNotNull null

                newEpisode(
                    EpisodeData(
                        bookId,
                        detail.series_id_str ?: bookId,
                        vid,
                        index,
                        detail.video_platform ?: 3
                    ).toJson()
                ) {
                    this.name = "Episode $index"
                    this.posterUrl = ep.cover
                    this.episode = index
                }
            }
            .sortedBy { it.episode ?: 0 }

        return newTvSeriesLoadResponse(
            detail.series_title ?: "Melolo",
            url,
            TvType.TvSeries,
            episodes
        ) {
            this.plot = detail.series_intro
            this.posterUrl = detail.series_cover
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val ep = tryParseJson<EpisodeData>(data) ?: return false

        val body = """
            {
                "video_id":"${ep.vid}",
                "biz_param":{
                    "video_id_type":0,
                    "device_level":1,
                    "video_platform":${ep.videoPlatform}
                },
                "NovelCommonParam":{
                    "app_language":"id",
                    "sys_language":"id",
                    "user_language":"id",
                    "language":"id",
                    "region":"ID",
                    "time_zone":"Asia/Jakarta"
                }
            }
        """.trimIndent()

        val headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to "Mozilla/5.0",
            "Referer" to mainUrl,
            "Origin" to mainUrl,
            "Accept" to "application/json, text/plain, */*",
            "X-Requested-With" to "XMLHttpRequest"
        )

        val responseText = try {
            app.post(
                "$mainUrl/novel/player/video_model/v1/?aid=$aid",
                requestBody = body.toRequestBody("application/json".toMediaType()),
                headers = headers
            ).text
        } catch (e: Exception) {
            return false
        }

        if (responseText.isBlank()) return false
        if (!responseText.trim().startsWith("{")) return false

        val resp = tryParseJson<PlayerVideoModelResponse>(responseText)

        val main = resp?.data?.main_url
        val backup = resp?.data?.backup_url

        listOfNotNull(main, backup)
            .distinct()
            .forEach { url ->
                callback(
                    newExtractorLink(
                        name,
                        "Melolo",
                        url,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.quality = Qualities.Unknown.value
                        this.referer = mainUrl
                        this.headers = mapOf("User-Agent" to "Mozilla/5.0")
                    }
                )
            }

        return !main.isNullOrBlank() || !backup.isNullOrBlank()
    }

    // ---------------- API ----------------

    private suspend fun fetchLatest(): List<CatalogBook> =
        try {
            val res = app.get("$catalogBase/latest").text
            tryParseJson<CatalogLatestResponse>(res)?.books.orEmpty()
        } catch (_: Exception) { emptyList() }

    private suspend fun fetchTrending(): List<CatalogBook> =
        try {
            val res = app.get("$catalogBase/trending").text
            tryParseJson<CatalogTrendingResponse>(res)?.books.orEmpty()
        } catch (_: Exception) { emptyList() }

    private suspend fun fetchSearch(query: String, limit: Int, offset: Int): List<CatalogBook> =
        try {
            val url = "$catalogBase/search?query=${URLEncoder.encode(query, "UTF-8")}&limit=$limit&offset=$offset"
            val res = app.get(url).text
            tryParseJson<CatalogSearchResponse>(res)
                ?.data?.search_data?.flatMap { it.books }.orEmpty()
        } catch (_: Exception) { emptyList() }

    private suspend fun fetchSearchPage(query: String, limit: Int, offset: Int)
            : Pair<List<CatalogBook>, Boolean> =
        try {
            val url = "$catalogBase/search?query=${URLEncoder.encode(query, "UTF-8")}&limit=$limit&offset=$offset"
            val res = app.get(url).text
            val resp = tryParseJson<CatalogSearchResponse>(res)

            val books = resp?.data?.search_data?.flatMap { it.books }.orEmpty()
            books to (resp?.data?.has_more == true)
        } catch (_: Exception) {
            emptyList<CatalogBook>() to false
        }

    private suspend fun fetchDetail(bookId: String): CatalogVideoData {
        val res = app.get("$catalogBase/detail/$bookId").text
        return tryParseJson<CatalogDetailResponse>(res)?.data?.video_data
            ?: throw ErrorLoadingException("No detail data")
    }

    // ---------------- MODELS ----------------

    data class CatalogLatestResponse(@JsonProperty("books") val books: List<CatalogBook>)
    data class CatalogTrendingResponse(@JsonProperty("books") val books: List<CatalogBook>)
    data class CatalogSearchResponse(@JsonProperty("data") val data: CatalogSearchData?)
    data class CatalogSearchData(
        @JsonProperty("has_more") val has_more: Boolean?,
        @JsonProperty("search_data") val search_data: List<CatalogSearchBlock>
    )
    data class CatalogSearchBlock(@JsonProperty("books") val books: List<CatalogBook>)

    data class CatalogBook(
        @JsonProperty("book_id") val book_id: String?,
        @JsonProperty("book_name") val book_name: String?,
        @JsonProperty("thumb_url") val thumb_url: String?
    )

    data class CatalogDetailResponse(@JsonProperty("data") val data: CatalogDetailData?)
    data class CatalogDetailData(@JsonProperty("video_data") val video_data: CatalogVideoData?)

    data class CatalogVideoData(
        @JsonProperty("series_id_str") val series_id_str: String?,
        @JsonProperty("series_title") val series_title: String?,
        @JsonProperty("series_intro") val series_intro: String?,
        @JsonProperty("series_cover") val series_cover: String?,
        @JsonProperty("video_list") val video_list: List<CatalogEpisode>,
        @JsonProperty("video_platform") val video_platform: Int?
    )

    data class CatalogEpisode(
        @JsonProperty("vid") val vid: String?,
        @JsonProperty("vid_index") val vid_index: Int?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("disable_play") val disable_play: Boolean?
    )

    data class PlayerVideoModelResponse(@JsonProperty("data") val data: PlayerVideoModelData?)
    data class PlayerVideoModelData(
        @JsonProperty("main_url") val main_url: String?,
        @JsonProperty("backup_url") val backup_url: String?
    )

    data class EpisodeData(
        @JsonProperty("bookId") val bookId: String,
        @JsonProperty("seriesId") val seriesId: String,
        @JsonProperty("vid") val vid: String,
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("videoPlatform") val videoPlatform: Int = 3
    )
}