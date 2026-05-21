package com.Dramabox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class Dramabox : MainAPI() {
    override var mainUrl = "https://www.dramabox.com/in"
    private val apiUrl = "https://db.hafizhibnusyam.my.id"
    override var name = "DramaBox👌"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "/api/dramas/indo" to "Drama Dub Indo",
        "/api/dramas/trending" to "Trending",
        "/api/dramas/must-sees" to "Must Sees",
        "/api/dramas/hidden-gems" to "Hidden Gems",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = fetchDramaList(request.data, if (page < 1) 1 else page)
        val items = response?.data.orEmpty().mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        return newHomePageResponse(HomePageList(request.name, items, false), response?.meta?.pagination?.hasMore ?: items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val keyword = query.trim()
        if (keyword.isBlank()) return emptyList()

        val url = "$apiUrl/api/search?keyword=${URLEncoder.encode(keyword, "UTF-8")}&page=1&size=50"
        val body = executeWithRetry {
            rateLimitDelay(moduleName = "Dramabox")
            app.get(url, timeout = AutoUsedConstants.DEFAULT_TIMEOUT).text
        }

        val response = tryParseJson<DramaListResponse>(body)
        return response?.data.orEmpty().mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val openData = parseOpenData(url)
        val dramaId = openData?.id?.trim()?.takeIf { it.isNotBlank() }
            ?: extractDramaId(url)
            ?: throw ErrorLoadingException("ID DramaBox tidak ditemukan")

        // Pakai cara file awal: ID tetap jadi kunci utama untuk API.
        // Bedanya sekarang URL web dibuat valid, bukan /drama/_ID.
        val detail = fetchDramaDetail(dramaId)

        val title = cleanTitle(
            detail?.title
                ?: openData?.title
                ?: extractTitleFromUrl(url)
                ?: "DramaBox"
        ).ifBlank { "DramaBox" }

        val poster = detail?.coverImage?.takeIf { it.isNotBlank() }
            ?: openData?.coverImage?.takeIf { it.isNotBlank() }

        val episodeCount = detail?.episodeCount?.takeIf { it > 0 }
            ?: inferEpisodeCount(dramaId)
            // Jangan lempar "Episode tidak ditemukan" saat endpoint count gagal.
            // Tetap tampilkan daftar episode fallback agar episode 1 bisa dicoba seperti provider awal.
            .takeIf { it > 0 }
            ?: FALLBACK_EPISODE_COUNT

        val episodes = (1..episodeCount).map { ep ->
            newEpisode(LoadData(bookId = dramaId, episodeNo = ep).toJson()) {
                this.name = "Episode $ep"
                this.episode = ep
                this.posterUrl = poster
            }
        }

        return newTvSeriesLoadResponse(title, buildDramaWebUrl(title, dramaId), TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = detail?.introduction
            this.tags = detail?.tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = parseJson<LoadData>(data)
        val dramaId = parsed.bookId ?: return false
        val episodeNo = parsed.episodeNo ?: return false

        val chapter = fetchChapterForEpisode(dramaId, episodeNo)
        val streams = chapter?.streamUrl.orEmpty()
            .filter { it.url?.isNotBlank() == true }
            .distinctBy { it.url }
            .sortedByDescending { it.quality ?: 0 }

        if (streams.isEmpty()) return false

        streams.forEach { s ->
            callback.invoke(
                newExtractorLink(
                    name,
                    "DramaBox ${s.quality?.let { "${it}p" } ?: "Auto"}",
                    s.url!!,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = s.quality ?: Qualities.Unknown.value
                    this.referer = "$mainUrl/"
                }
            )
        }
        return true
    }

    private fun cleanTitle(raw: String): String =
        raw.replace(Regex("\\((Sulih Suara|Dub Indo|Indonesian Sub|Sub Indo)\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    private fun DramaItem.toSearchResult(): SearchResponse? {
        val dramaId = id?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val cleanName = cleanTitle(title ?: "DramaBox").ifBlank { "DramaBox" }

        // Pakai URL web yang valid untuk mencegah 404:
        // https://www.dramabox.com/in/drama/41000110445/forever-was-a-lie
        return newTvSeriesSearchResponse(cleanName, buildDramaWebUrl(cleanName, dramaId), TvType.AsianDrama) {
            this.posterUrl = coverImage
        }
    }

    private suspend fun fetchDramaList(path: String, page: Int): DramaListResponse? {
        return try {
            val url = "${if (path.startsWith("http")) path else "$apiUrl$path"}${if (path.contains("?")) "&" else "?"}page=$page"
            val body = executeWithRetry {
                rateLimitDelay(moduleName = "Dramabox")
                app.get(url, timeout = AutoUsedConstants.DEFAULT_TIMEOUT).text
            }
            tryParseJson<DramaListResponse>(body)
        } catch (e: Exception) {
            // FIX #5: Log errors instead of silently swallowing them
            logError("Dramabox", "fetchDramaList failed for path=$path page=$page", e)
            null
        }
    }

    // Returns DramaItem? directly instead of DramaDetailResponse?.
    // FIX: API sometimes returns {"data": {...}} and sometimes just {...} without wrapper.
    // Previously the code only tried the wrapped format — if the API returned the object
    // directly, data would be null and "Drama tidak ditemukan" was always thrown.
    // Now we try both formats: wrapped first, then unwrapped as fallback.
    // Also logs the raw body so you can see exactly what the API returned when debugging.
    private suspend fun fetchDramaDetail(dramaId: String): DramaItem? {
        return try {
            val url = "$apiUrl/api/dramas/$dramaId"
            val body = executeWithRetry {
                rateLimitDelay(moduleName = "Dramabox")
                app.get(url, timeout = AutoUsedConstants.DEFAULT_TIMEOUT).text
            }
            logDebug("Dramabox", "fetchDramaDetail[$dramaId] raw body: ${body.take(300)}")

            // Try wrapped format {"data": {...}} first
            val wrapped = tryParseJson<DramaDetailResponse>(body)?.data
            if (wrapped?.id != null) return wrapped

            // Fall back to direct object format {...}
            val direct = tryParseJson<DramaItem>(body)
            if (direct?.id != null) return direct

            logError("Dramabox", "fetchDramaDetail[$dramaId]: both parse formats returned null. Body: ${body.take(500)}")
            null
        } catch (e: Exception) {
            logError("Dramabox", "fetchDramaDetail failed for id=$dramaId", e)
            null
        }
    }

    // FIX #1: Changed app.post() to app.get().
    // The endpoint uses query string parameters (?book_id=...&episode=...),
    // which is a GET-style API. Using POST with no body was returning
    // 405 Method Not Allowed / empty response, causing all video loading to fail.
    private suspend fun fetchChapterForEpisode(dramaId: String, episodeNo: Int): ChapterContent? {
        return try {
            val url = "$apiUrl/api/chapters/video?book_id=$dramaId&episode=$episodeNo"
            val body = executeWithRetry {
                rateLimitDelay(moduleName = "Dramabox")
                app.get(url, timeout = AutoUsedConstants.DEFAULT_TIMEOUT).text
            }

            val typed = tryParseJson<ChapterResponse>(body)
            val typedChapters = typed?.allChapters().orEmpty()
            typedChapters.firstOrNull { it.chapterIndex?.toIntOrNull() == episodeNo && it.streamUrl?.isNotEmpty() == true }
                ?: typedChapters.firstOrNull { it.streamUrl?.isNotEmpty() == true }
                ?: parseChaptersFromJson(body).let { chapters ->
                    chapters.firstOrNull { it.chapterIndex?.toIntOrNull() == episodeNo && it.streamUrl?.isNotEmpty() == true }
                        ?: chapters.firstOrNull { it.streamUrl?.isNotEmpty() == true }
                }
        } catch (e: Exception) {
            logError("Dramabox", "fetchChapterForEpisode failed id=$dramaId ep=$episodeNo", e)
            null
        }
    }

    // FIX #1 (same): inferEpisodeCount also used app.post() on the same endpoint.
    private suspend fun inferEpisodeCount(dramaId: String): Int {
        return try {
            val url = "$apiUrl/api/chapters/video?book_id=$dramaId&episode=1"
            val body = executeWithRetry {
                rateLimitDelay(moduleName = "Dramabox")
                app.get(url, timeout = AutoUsedConstants.DEFAULT_TIMEOUT).text
            }

            val typed = tryParseJson<ChapterResponse>(body)
            val typedMax = typed?.allChapters()
                .orEmpty()
                .mapNotNull { it.chapterIndex?.toIntOrNull() }
                .maxOrNull() ?: 0

            maxOf(typedMax, inferEpisodeCountFromRawJson(body))
        } catch (e: Exception) {
            logError("Dramabox", "inferEpisodeCount failed for id=$dramaId", e)
            0
        }
    }


    private fun parseOpenData(raw: String): DramaOpenData? {
        return runCatching { parseJson<DramaOpenData>(raw) }.getOrNull()
    }

    private fun extractDramaId(raw: String): String? {
        parseOpenData(raw)?.id?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        // Format baru/valid: /in/drama/41000110445/forever-was-a-lie
        Regex("""/drama/(\d{6,})(?:/|$|\?)""")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // Format lama dari file awal: /in/drama/_41000110445
        raw.substringAfterLast("_", "")
            .substringBefore("?")
            .substringBefore("&")
            .trim()
            .takeIf { it.isNotBlank() && it.any { char -> char.isDigit() } }
            ?.let { return it }

        // Fallback jika data cuma mengandung angka ID.
        return Regex("""\d{6,}""")
            .find(raw)
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractTitleFromUrl(raw: String): String? {
        val slug = raw.substringAfter("/drama/", "")
            .substringAfter("/", "")
            .substringBefore("?")
            .substringBefore("&")
            .replace("-", " ")
            .trim()

        return slug.takeIf { it.isNotBlank() && !it.all { char -> char.isDigit() } }
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun buildDramaWebUrl(title: String?, dramaId: String): String {
        val slug = title
            ?.let(::cleanTitle)
            ?.lowercase()
            ?.replace(Regex("""['’`]+"""), "")
            ?.replace("&", " and ")
            ?.replace(Regex("""[^a-z0-9]+"""), "-")
            ?.trim('-')
            ?.takeIf { it.isNotBlank() }
            ?: "dramabox"

        return "$mainUrl/drama/$dramaId/$slug"
    }

    private fun ChapterResponse.allChapters(): List<ChapterContent> {
        return data.orEmpty() + extras.orEmpty()
    }

    private fun inferEpisodeCountFromRawJson(body: String): Int {
        return runCatching {
            val root = JSONObject(body)
            val explicitCount = root.findMaxKnownEpisodeCount()
            val parsedChapters = parseChaptersFromJson(body)
                .mapNotNull { it.chapterIndex?.toIntOrNull() }
                .maxOrNull() ?: 0

            maxOf(explicitCount, parsedChapters)
        }.getOrDefault(0)
    }

    private fun JSONObject.findMaxKnownEpisodeCount(): Int {
        var max = 0
        val countKeys = setOf(
            "episode_count",
            "episodeCount",
            "chapter_count",
            "chapterCount",
            "total_episode",
            "totalEpisode",
            "total_episodes",
            "totalEpisodes",
            "total_chapter",
            "totalChapter",
            "total_chapters",
            "totalChapters",
            "max_episode",
            "maxEpisode"
        )

        keys().forEach { key ->
            val value = opt(key)
            if (key in countKeys) {
                max = maxOf(max, value.toString().toIntOrNull() ?: 0)
            }

            when (value) {
                is JSONObject -> max = maxOf(max, value.findMaxKnownEpisodeCount())
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.opt(i)
                        if (item is JSONObject) max = maxOf(max, item.findMaxKnownEpisodeCount())
                    }
                }
            }
        }

        return max
    }

    private fun parseChaptersFromJson(body: String): List<ChapterContent> {
        return runCatching {
            val root = JSONObject(body)
            val result = mutableListOf<ChapterContent>()

            fun parseAny(value: Any?) {
                when (value) {
                    is JSONObject -> {
                        value.toChapterContentOrNull()?.let { result.add(it) }
                        value.keys().forEach { key -> parseAny(value.opt(key)) }
                    }
                    is JSONArray -> {
                        for (i in 0 until value.length()) parseAny(value.opt(i))
                    }
                }
            }

            parseAny(root)
            result.distinctBy { "${it.chapterIndex}-${it.streamUrl?.firstOrNull()?.url}" }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.toChapterContentOrNull(): ChapterContent? {
        val index = optString("chapter_index")
            .ifBlank { optString("chapterIndex") }
            .ifBlank { optString("episode") }
            .ifBlank { optString("episode_no") }
            .ifBlank { optString("episodeNo") }
            .ifBlank { optString("index") }
            .takeIf { it.isNotBlank() }

        val streams = parseStreamItems(
            opt("stream_url")
                ?: opt("streamUrl")
                ?: opt("streams")
                ?: opt("video")
                ?: opt("videos")
        )

        if (index == null && streams.isEmpty()) return null
        return ChapterContent(index, streams)
    }

    private fun parseStreamItems(raw: Any?): List<StreamItem> {
        val result = mutableListOf<StreamItem>()

        fun parseOne(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val url = value.optString("url")
                        .ifBlank { value.optString("file") }
                        .ifBlank { value.optString("video_url") }
                        .ifBlank { value.optString("videoUrl") }

                    val quality = value.optInt("quality", -1)
                        .takeIf { it > 0 }
                        ?: value.optString("quality")
                            .filter { it.isDigit() }
                            .toIntOrNull()

                    if (url.isNotBlank()) result.add(StreamItem(quality, url))

                    value.keys().forEach { key ->
                        val child = value.opt(key)
                        if (child is JSONObject || child is JSONArray) parseOne(child)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) parseOne(value.opt(i))
                }
                is String -> {
                    if (value.startsWith("http", true)) result.add(StreamItem(null, value))
                }
            }
        }

        parseOne(raw)
        return result.distinctBy { it.url }
    }

    companion object {
        private const val FALLBACK_EPISODE_COUNT = 50
    }

    data class DramaListResponse(@JsonProperty("data") val data: List<DramaItem>? = null, @JsonProperty("meta") val meta: ResponseMeta? = null)
    data class DramaDetailResponse(@JsonProperty("data") val data: DramaItem? = null)
    data class DramaItem(@JsonProperty("id") val id: String? = null, @JsonProperty("title") val title: String? = null, @JsonProperty("cover_image") val coverImage: String? = null, @JsonProperty("introduction") val introduction: String? = null, @JsonProperty("tags") val tags: List<String>? = null, @JsonProperty("episode_count") val episodeCount: Int? = null)
    data class ResponseMeta(@JsonProperty("pagination") val pagination: Pagination? = null)
    data class Pagination(@JsonProperty("has_more") val hasMore: Boolean? = null)
    data class ChapterResponse(@JsonProperty("data") val data: List<ChapterContent>? = null, @JsonProperty("extras") val extras: List<ChapterContent>? = null)
    data class ChapterContent(@JsonProperty("chapter_index") val chapterIndex: String? = null, @JsonProperty("stream_url") val streamUrl: List<StreamItem>? = null)
    data class StreamItem(@JsonProperty("quality") val quality: Int? = null, @JsonProperty("url") val url: String? = null)
    data class DramaOpenData(@JsonProperty("id") val id: String? = null, @JsonProperty("title") val title: String? = null, @JsonProperty("coverImage") val coverImage: String? = null)
    data class LoadData(@JsonProperty("bookId") val bookId: String? = null, @JsonProperty("episodeNo") val episodeNo: Int? = null)
}
