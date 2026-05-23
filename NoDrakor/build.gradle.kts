version = 1

cloudstream {
    language = "id"
    authors = listOf("BetbetMiro")
    description = "NoDrakor provider for 129.212.202.202 with expanded drama categories, search, detail parsing, episode parsing, Dooplay AJAX playback, iframe fallback, and public HLS/MP4 extraction."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1

    tvTypes = listOf(
        "TvSeries",
        "AsianDrama",
        "Movie"
    )

    isCrossPlatform = false
    iconUrl = "https://www.google.com/s2/favicons?domain=129.212.202.202&sz=%size%"
}