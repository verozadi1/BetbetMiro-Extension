package com.betbet.yunshanid

import com.fasterxml.jackson.annotation.JsonProperty

data class EpisodeData(
    @JsonProperty("url")
    val url: String,

    @JsonProperty("name")
    val name: String
)