package com.gudangfilm

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

class Movearnpre : Dingtezuni() {
    override var name = "Movearnpre"
    override var mainUrl = "https://movearnpre.com"
}

class Minochinos : Dingtezuni() {
    override var name = "Minochinos"
    override var mainUrl = "https://minochinos.com"
}

class Mivalyo : Dingtezuni() {
    override var name = "Mivalyo"
    override var mainUrl = "https://mivalyo.com"
}

class Ryderjet : Dingtezuni() {
    override var name = "Ryderjet"
    override var mainUrl = "https://ryderjet.com"
}

class Bingezove : Dingtezuni() {
    override var name = "Bingezove"
    override var mainUrl = "https://bingezove.com"
}

open class Dingtezuni : EarnvidsLike() {
    override var name = "Dingtezuni"
    override var mainUrl = "https://dingtezuni.com"
}

open class Dintezuvio : EarnvidsLike() {
    override var name = "Dintezuvio"
    override var mainUrl = "https://dintezuvio.com"
}

open class EarnvidsLike : ExtractorApi() {
    override var name = "Earnvids"
    override var mainUrl = "https://dingtezuni.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = getEmbedUrl(url)
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val response = runCatching {
            app.get(embedUrl, referer = referer ?: "$mainUrl/", headers = headers)
        }.getOrNull() ?: return

        val html = response.text.cleanEscaped()
        val found = linkedSetOf<String>()

        extractM3u8Urls(html).forEach { found.add(normalizeUrl(it, embedUrl)) }

        val unpacked = runCatching {
            if (!getPacked(html).isNullOrEmpty()) getAndUnpack(html) else null
        }.getOrNull()

        if (!unpacked.isNullOrBlank()) {
            val script = if (unpacked.contains("var links")) {
                unpacked.substringAfter("var links")
            } else {
                unpacked
            }.cleanEscaped()

            extractM3u8Urls(script).forEach { found.add(normalizeUrl(it, embedUrl)) }
        }

        response.document.selectFirst("script:containsData(sources:)")
            ?.data()
            ?.cleanEscaped()
            ?.let { script ->
                extractM3u8Urls(script).forEach { found.add(normalizeUrl(it, embedUrl)) }
            }

        found.forEach { m3u8 ->
            generateM3u8(
                source = name,
                streamUrl = m3u8,
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            url.contains("/f/") -> url.replace("/f/", "/v/")
            else -> url
        }
    }
}

class Hglink : StreamWishExtractor() {
    override val name = "Hglink"
    override val mainUrl = "https://hglink.to"
}

class Ghbrisk : StreamWishExtractor() {
    override val name = "Ghbrisk"
    override val mainUrl = "https://ghbrisk.com"
}

class Dhcplay : StreamWishExtractor() {
    override var name = "DHC Play"
    override var mainUrl = "https://dhcplay.com"
}

class Streamcasthub : VidStack() {
    override var name = "Streamcasthub"
    override var mainUrl = "https://live.streamcasthub.store"
    override var requiresReferer = true
}

class Dm21embed : VidStack() {
    override var name = "Dm21embed"
    override var mainUrl = "https://dm21.embed4me.vip"
    override var requiresReferer = true
}

class Dm21upns : VidStack() {
    override var name = "Dm21upns"
    override var mainUrl = "https://dm21.upns.live"
    override var requiresReferer = true
}

class Pm21p2p : VidStack() {
    override var name = "Pm21p2p"
    override var mainUrl = "https://pm21.p2pplay.pro"
    override var requiresReferer = true
}

class Dm21 : VidStack() {
    override var name = "Dm21"
    override var mainUrl = "https://dm21.embed4me.vip"
    override var requiresReferer = true
}

class Meplayer : VidStack() {
    override var name = "Meplayer"
    override var mainUrl = "https://video.4meplayer.com"
    override var requiresReferer = true
}

class Veev : ExtractorApi() {
    override val name = "Veev"
    override val mainUrl = "https://veev.to"
    override val requiresReferer = false

    private val pattern = Regex("""(?://|\.)(?:veev|kinoger|poophq|doods)\.(?:to|pw|com)/[ed]/([0-9A-Za-z]+)""")

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mediaId = pattern.find(url)?.groupValues?.getOrNull(1) ?: return
        val pageUrl = "$mainUrl/e/$mediaId"

        val html = runCatching {
            app.get(pageUrl, headers = mapOf("User-Agent" to DEFAULT_UA)).text
        }.getOrNull() ?: return

        val encRegex = Regex("""[.\s'](?:fc|_vvto\[[^]]*)(?:['\]]*)?\s*[:=]\s*['"]([^'"]+)""")
        val foundValues = encRegex.findAll(html).map { it.groupValues[1] }.toList()

        for (f in foundValues.reversed()) {
            val ch = runCatching { veevDecode(f) }.getOrNull() ?: continue
            if (ch == f) continue

            val dlUrl = "$mainUrl/dl?op=player_api&cmd=gi&file_code=$mediaId&r=$mainUrl&ch=$ch&ie=1"
            val responseText = runCatching {
                app.get(dlUrl, headers = mapOf("User-Agent" to DEFAULT_UA)).text
            }.getOrNull() ?: continue

            val file = runCatching {
                JSONObject(responseText).optJSONObject("file")
            }.getOrNull() ?: continue

            if (file.optString("file_status") != "OK") continue

            val dv = file.optJSONArray("dv")?.optJSONObject(0)?.optString("s") ?: continue
            val rules = buildArray(ch).firstOrNull() ?: emptyList()
            val decoded = runCatching {
                decodeUrl(veevDecode(dv), rules)
            }.getOrNull() ?: continue

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = decoded,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            return
        }
    }

    private fun veevDecode(etext: String): String {
        val result = StringBuilder()
        val lut = HashMap<Int, String>()
        var n = 256
        var c = etext[0].toString()
        result.append(c)

        for (char in etext.drop(1)) {
            val code = char.code
            val nc = if (code < 256) char.toString() else lut[code] ?: (c + c[0])
            result.append(nc)
            lut[n++] = c + nc[0]
            c = nc
        }

        return result.toString()
    }

    private fun jsInt(x: Char): Int = x.digitToIntOrNull() ?: 0

    private fun buildArray(encoded: String): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        val iterator = encoded.iterator()

        fun nextIntOrZero(): Int {
            return if (iterator.hasNext()) jsInt(iterator.nextChar()) else 0
        }

        var count = nextIntOrZero()

        while (count != 0) {
            val row = mutableListOf<Int>()

            repeat(count) {
                row.add(nextIntOrZero())
            }

            result.add(row.reversed())
            count = nextIntOrZero()
        }

        return result
    }

    private fun decodeUrl(encoded: String, rules: List<Int>): String {
        var text = encoded

        for (rule in rules) {
            if (rule == 1) text = text.reversed()

            val arr = text.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()

            text = arr.toString(Charsets.UTF_8)
                .replace("dXRmOA==", "")
        }

        return text
    }
}

data class FileSource(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null
)

private fun extractM3u8Urls(text: String): List<String> {
    val urls = linkedSetOf<String>()

    Regex("""https?://[^"'\\\s<>]+\.m3u8[^"'\\\s<>]*""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .map { it.value.cleanEscaped() }
        .forEach { urls.add(it) }

    Regex("""["']([^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .map { it.cleanEscaped() }
        .forEach { urls.add(it) }

    Regex(""":\s*["']([^"']*m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .map { it.cleanEscaped() }
        .forEach { urls.add(it) }

    Regex("""https?%3A%2F%2F[^"'\\\s<>]+?\.m3u8[^"'\\\s<>]*""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .map {
            runCatching {
                URLDecoder.decode(it.value, "UTF-8")
            }.getOrDefault(it.value)
        }
        .map { it.cleanEscaped() }
        .forEach { urls.add(it) }

    return urls.toList()
}

private fun normalizeUrl(url: String, baseUrl: String): String {
    val clean = url.cleanEscaped()

    return when {
        clean.startsWith("http", true) -> clean
        clean.startsWith("//") -> "https:$clean"
        clean.startsWith("/") -> {
            val origin = Regex("""^https?://[^/]+""")
                .find(baseUrl)
                ?.value
                ?: ""
            "$origin$clean"
        }
        else -> runCatching {
            URI(baseUrl).resolve(clean).toString()
        }.getOrDefault(fixUrl(clean))
    }
}

private fun String.cleanEscaped(): String {
    return replace("\\/", "/")
        .replace("\\u0026", "&")
        .replace("&amp;", "&")
        .trim()
}