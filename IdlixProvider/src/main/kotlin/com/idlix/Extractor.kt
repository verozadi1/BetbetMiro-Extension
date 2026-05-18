package com.idlix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

class Jeniusplay : ExtractorApi() {
    override var name = "Jeniusplay"
    override var mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace(" ", "%20")
        try {
            // Ambil teks HTML mentah sebagai lapis pertahanan pertama
            val responseText = app.get(cleanUrl, referer = referer).text
            val cleanHtml = responseText.replace("\\/", "/")

            // STRATEGI BYPASS 1: Cari langsung apakah ada link m3u8/mp4/txt nangkring di HTML halaman utama
            val directStream = Regex("""https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*\.(?:m3u8|mp4|txt)[-a-zA-Z0-9+&@#/%=~_|]*""")
                .findAll(cleanHtml)
                .map { it.value }
                .firstOrNull { !it.contains("jeniusplay.com/player/") && !it.contains("ajax.php") && !it.contains("index.php") }

            if (directStream != null) {
                val finalStreamUrl = directStream.replace(".txt", ".m3u8")
                generateM3u8(name, finalStreamUrl, cleanUrl).forEach(callback)
                return
            }

            // STRATEGI BYPASS 2: Tembak API Player via POST (ajax.php atau index.php otomatis dideteksi)
            val hash = cleanUrl.split("/").last().substringAfter("data=")
            val endpoint = if (responseText.contains("ajax.php")) "$mainUrl/player/ajax.php?data=$hash&do=getVideo" else "$mainUrl/player/index.php?data=$hash&do=getVideo"

            val postResponse = app.post(
                url = endpoint,
                data = mapOf("hash" to hash, "r" to "$referer"),
                referer = cleanUrl,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).text
            
            val unescapedPost = postResponse.replace("\\/", "/")
            
            // UNIVERSAL SNIFFER: Ambil streaming link apa saja dari response JSON tanpa .parsed API kaku (anti runtime crash)
            val extractedUrl = Regex("""https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*\.(?:m3u8|mp4|txt)[-a-zA-Z0-9+&@#/%=~_|]*""")
                .find(unescapedPost)?.value

            if (extractedUrl != null) {
                val finalStreamUrl = extractedUrl.replace(".txt", ".m3u8")
                generateM3u8(name, finalStreamUrl, cleanUrl).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e(name, "Extraction failed for $cleanUrl: ${e.message}")
        }
    }

    data class ResponseSource(
        @JsonProperty("videoSource") val videoSource: String,
    )
}

class Majorplay : ExtractorApi() {
    override var name = "Majorplay"
    override var mainUrl = "https://majorplay.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val domain = "https://" + java.net.URI(url).host
            val document = app.get(url, referer = domain).document
            val htmlContent = document.html().replace("\\/", "/")
            
            // UNIVERSAL SNIFFER: Scrape link .m3u8/.mp4 langsung dari isi HTML untuk menembus perubahan skrip player
            var m3uLink = document.select("source").attr("src").trim()
            if (m3uLink.isEmpty()) {
                m3uLink = Regex("""https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*\.(?:m3u8|mp4)[-a-zA-Z0-9+&@#/%=~_|]*""")
                    .find(htmlContent)?.value ?: ""
            }

            if (m3uLink.isNotEmpty()) {
                generateM3u8(name, m3uLink, domain).forEach(callback)
            }

            // Ekstraksi Subtitle Internal Majorplay
            val scripts = document.selectFirst("script:containsData(subtitles)")?.data() ?: return
            val subRegex = Regex("""\\"label\\":\\"([^\\"]*?)\\"[^}]*?\\"path\\":\\"([^\\"]*?)\\\"""")

            subRegex.findAll(scripts).forEach { match ->
                val label = match.groupValues[1]
                var vttUrl = match.groupValues[2].replace("\\/", "/")

                if (!vttUrl.startsWith("http")) {
                    vttUrl = domain.trimEnd('/') + "/" + vttUrl.trimStart('/')
                }
                subtitleCallback.invoke(
                    SubtitleFile(label, vttUrl)
                )
            }
        } catch (e: Exception) {
            Log.e(name, "Extraction failed: ${e.message}")
        }
    }
}