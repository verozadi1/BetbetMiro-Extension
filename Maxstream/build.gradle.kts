version = 1

cloudstream {
    authors = listOf("BetbetMiro")
    language = "id"
    description = "Provider CloudStream untuk MAXStream TV. Mendukung halaman publik MAXStream tanpa bypass login atau DRM."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 3

    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=maxstream.tv&sz=%size%"
}