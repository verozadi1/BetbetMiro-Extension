version = 11

cloudstream {
    language = "id"
    authors = listOf("BetbetMiro")
    description = "JuraganFilm / Idlix movie, series, anime, and drama provider"

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
    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/idlix.png"
}