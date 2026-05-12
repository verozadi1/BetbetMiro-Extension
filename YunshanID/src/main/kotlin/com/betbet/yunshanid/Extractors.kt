package com.betbet.yunshanid

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

object YunshanExtractor {

    fun buildLink(
        source: String,
        url: String,
        referer: String
    ): ExtractorLink {

        return ExtractorLink(
            source = source,
            name = source,
            url = url,
            referer = referer,
            quality = Qualities.Unknown.value,
            isM3u8 = url.contains("m3u8")
        )
    }
}