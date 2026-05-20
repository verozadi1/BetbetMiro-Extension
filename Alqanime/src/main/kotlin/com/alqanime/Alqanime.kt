package com.alqanime

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element
import java.net.URLDecoder

class Alqanime : MainAPI() {
    override var mainUrl = "https://alqanime.net"
    override var name = "Alqanime"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String): TvType = when {
            t.contains("Movie", true) -> TvType.AnimeMovie
            t.contains("OVA", true) || t.contains("Special", true) -> TvType.OVA
            else -> TvType.Anime
        }

        fun getStatus(t: String): ShowStatus = when {
            t.contains("Completed", true) || t.contains("Tamat", true) -> ShowStatus.Completed
            t.contains("Ongoing", true) -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Referer" to mainUrl,
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/%d/" to "Rilisan Terbaru",
        "$mainUrl/popular/page/%d/" to "Popular",
        "$mainUrl/advanced-search/page/%d/?status=ongoing&order=update" to "Sedang Tayang",
        "$mainUrl/advanced-search/page/%d/?status=completed&order=update" to "Selesai Tayang",
        "$mainUrl/advanced-search/page/%d/?type[]=movie&order=update" to "Movie Anime",

        // A
        "$mainUrl/tag/action/page/%d/" to "Action",
        "$mainUrl/tag/adult-cast/page/%d/" to "Adult Cast",
        "$mainUrl/tag/adventure/page/%d/" to "Adventure",
        "$mainUrl/tag/anthropomorphic/page/%d/" to "Anthropomorphic",
        "$mainUrl/tag/avant-garde/page/%d/" to "Avant Garde",
        "$mainUrl/tag/award-winning/page/%d/" to "Award Winning",

        // B
        "$mainUrl/tag/boys-love/page/%d/" to "Boys Love",

        // C
        "$mainUrl/tag/cars/page/%d/" to "Cars",
        "$mainUrl/tag/cgdct/page/%d/" to "CGDCT",
        "$mainUrl/tag/childcare/page/%d/" to "Childcare",
        "$mainUrl/tag/combat-sports/page/%d/" to "Combat Sports",
        "$mainUrl/tag/comedy/page/%d/" to "Comedy",
        "$mainUrl/tag/crossdressing/page/%d/" to "Crossdressing",

        // D
        "$mainUrl/tag/delinquents/page/%d/" to "Delinquents",
        "$mainUrl/tag/dementia/page/%d/" to "Dementia",
        "$mainUrl/tag/demons/page/%d/" to "Demons",
        "$mainUrl/tag/detective/page/%d/" to "Detective",
        "$mainUrl/tag/donghua/page/%d/" to "Donghua",
        "$mainUrl/tag/drama/page/%d/" to "Drama",

        // E
        "$mainUrl/tag/ecchi/page/%d/" to "Ecchi",
        "$mainUrl/tag/educational/page/%d/" to "Educational",
        "$mainUrl/tag/erotica/page/%d/" to "Erotica",

        // F
        "$mainUrl/tag/fantasy/page/%d/" to "Fantasy",

        // G
        "$mainUrl/tag/gag-humor/page/%d/" to "Gag Humor",
        "$mainUrl/tag/girls-love/page/%d/" to "Girls Love",
        "$mainUrl/tag/gore/page/%d/" to "Gore",
        "$mainUrl/tag/gourmet/page/%d/" to "Gourmet",

        // H
        "$mainUrl/tag/harem/page/%d/" to "Harem",
        "$mainUrl/tag/hentong/page/%d/" to "Hentong",
        "$mainUrl/tag/high-stakes-game/page/%d/" to "High Stakes Game",
        "$mainUrl/tag/historical/page/%d/" to "Historical",
        "$mainUrl/tag/horror/page/%d/" to "Horror",

        // I
        "$mainUrl/tag/idols-female/page/%d/" to "Idols Female",
        "$mainUrl/tag/idols-male/page/%d/" to "Idols Male",
        "$mainUrl/tag/isekai/page/%d/" to "Isekai",
        "$mainUrl/tag/iyashikei/page/%d/" to "Iyashikei",

        // J
        "$mainUrl/tag/josei/page/%d/" to "Josei",

        // K
        "$mainUrl/tag/kids/page/%d/" to "Kids",
        "$mainUrl/tag/korea/page/%d/" to "Korea",

        // L
        "$mainUrl/tag/love-polygon/page/%d/" to "Love Polygon",
        "$mainUrl/tag/love-status-quo/page/%d/" to "Love Status Quo",

        // M
        "$mainUrl/tag/magic/page/%d/" to "Magic",
        "$mainUrl/tag/magical-sex-shift/page/%d/" to "Magical Sex Shift",
        "$mainUrl/tag/mahou-shoujo/page/%d/" to "Mahou Shoujo",
        "$mainUrl/tag/malaysia/page/%d/" to "Malaysia",
        "$mainUrl/tag/martial-arts/page/%d/" to "Martial Arts",
        "$mainUrl/tag/mecha/page/%d/" to "Mecha",
        "$mainUrl/tag/medical/page/%d/" to "Medical",
        "$mainUrl/tag/military/page/%d/" to "Military",
        "$mainUrl/tag/music/page/%d/" to "Music",
        "$mainUrl/tag/mystery/page/%d/" to "Mystery",
        "$mainUrl/tag/mythology/page/%d/" to "Mythology",

        // O
        "$mainUrl/tag/organized-crime/page/%d/" to "Organized Crime",
        "$mainUrl/tag/otaku-culture/page/%d/" to "Otaku Culture",

        // P
        "$mainUrl/tag/parody/page/%d/" to "Parody",
        "$mainUrl/tag/performing-arts/page/%d/" to "Performing Arts",
        "$mainUrl/tag/pets/page/%d/" to "Pets",
        "$mainUrl/tag/police/page/%d/" to "Police",
        "$mainUrl/tag/psychological/page/%d/" to "Psychological",

        // R
        "$mainUrl/tag/racing/page/%d/" to "Racing",
        "$mainUrl/tag/reincarnation/page/%d/" to "Reincarnation",
        "$mainUrl/tag/reverse-harem/page/%d/" to "Reverse Harem",
        "$mainUrl/tag/romance/page/%d/" to "Romance",

        // S
        "$mainUrl/tag/samurai/page/%d/" to "Samurai",
        "$mainUrl/tag/school/page/%d/" to "School",
        "$mainUrl/tag/sci-fi/page/%d/" to "Sci-Fi",
        "$mainUrl/tag/seinen/page/%d/" to "Seinen",
        "$mainUrl/tag/shoujo/page/%d/" to "Shoujo",
        "$mainUrl/tag/shounen/page/%d/" to "Shounen",
        "$mainUrl/tag/showbiz/page/%d/" to "Showbiz",
        "$mainUrl/tag/slice-of-life/page/%d/" to "Slice of Life",
        "$mainUrl/tag/space/page/%d/" to "Space",
        "$mainUrl/tag/sports/page/%d/" to "Sports",
        "$mainUrl/tag/strategy-game/page/%d/" to "Strategy Game",
        "$mainUrl/tag/super-power/page/%d/" to "Super Power",
        "$mainUrl/tag/supernatural/page/%d/" to "Supernatural",
        "$mainUrl/tag/survival/page/%d/" to "Survival",
        "$mainUrl/tag/suspense/page/%d/" to "Suspense",

        // T
        "$mainUrl/tag/team-sports/page/%d/" to "Team Sports",
        "$mainUrl/tag/thriller/page/%d/" to "Thriller",
        "$mainUrl/tag/time-travel/page/%d/" to "Time Travel",

        // U
        "$mainUrl/tag/urban-fantasy/page/%d/" to "Urban Fantasy",
        "$mainUrl/tag/us/page/%d/" to "US",

        // V
        "$mainUrl/tag/vampire/page/%d/" to "Vampire",
        "$mainUrl/tag/video-game/page/%d/" to "Video Game",
        "$mainUrl/tag/villainess/page/%d/" to "Villainess",
        "$mainUrl/tag/visual-arts/page/%d/" to "Visual Arts",

        // W
        "$mainUrl/tag/workplace/page/%d/" to "Workplace"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data.format(page), headers = commonHeaders).document
        val selector = "div.listupd:not(.popularslider) article.bs"
        val home = document.select(selector).mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst(".ntitle")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val typeText = this.selectFirst(".typez")?.text()?.trim() ?: ""

        val epNum = this.selectFirst("a")?.attr("title")
            ?.let {
                Regex("Episode\\s*\\((\\d+)\\)", RegexOption.IGNORE_CASE)
                    .find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }

        val rating = this.selectFirst("div.numscore")?.text()?.trim()

        return newAnimeSearchResponse(title, href, getType(typeText)) {
            this.posterUrl = posterUrl
            addDubStatus("Sub Indo", epNum)
            this.score = Score.from10(rating)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", headers = commonHeaders).document
        return document.select("article.bs").mapNotNull { it.toSearchResult() }
    }
}