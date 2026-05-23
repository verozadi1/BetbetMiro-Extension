version = 1

cloudstream {
    language = "id"
    authors = listOf("BetbetMiro")
    description = "PasarBokep provider for pasarbokep.com with homepage rows, adult categories, search, detail parsing, related videos, iframe fallback, and direct playback extraction."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1

    tvTypes = listOf(
        "NSFW"
    )

    isCrossPlatform = false
    iconUrl = "https://www.google.com/s2/favicons?domain=pasarbokep.com&sz=%size%"
}