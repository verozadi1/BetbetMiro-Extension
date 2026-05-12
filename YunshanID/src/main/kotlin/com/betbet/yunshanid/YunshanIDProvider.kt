package com.betbet.yunshanid

            loadExtractor(
                YunshanUtils.fixUrl(iframe),
                data,
                subtitleCallback,
                callback
            )
        }

        val m3u8Regex = Regex("""https?:\\/\\/[^\"']+\\.m3u8[^\"']*""")

        val html = document.html()

        m3u8Regex.findAll(html).forEach {

            callback.invoke(
                YunshanExtractor.buildLink(
                    source = name,
                    url = it.value.replace("\\/", "/"),
                    referer = data
                )
            )
        }

        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {

        val title =
            this.selectFirst("h2")?.text()?.trim()
                ?: return null

        val href =
            YunshanUtils.fixUrl(
                this.selectFirst("a")?.attr("href") ?: return null
            )

        val poster =
            this.selectFirst("img")?.attr("src")

        return newAnimeSearchResponse(
            title,
            href,
            TvType.Anime
        ) {
            posterUrl = poster
        }
    }
}