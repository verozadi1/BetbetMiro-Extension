version = 24

cloudstream {
    language = "id"
    authors = listOf("BetbetMiro")
    description = "Idlix provider for ryangoslingfrance.com with expanded categories, country/year filters, Dooplay AJAX playback, prioritized host extraction, faster loading, subtitle parsing, and hardened fallback."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime",
        "AsianDrama"
    )

    isCrossPlatform = false
    iconUrl = "https://www.google.com/s2/favicons?domain=ryangoslingfrance.com&sz=%size%"
}