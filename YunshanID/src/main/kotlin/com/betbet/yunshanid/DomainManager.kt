package com.betbet.yunshanid

object DomainManager {

    private val domains = listOf(
        "https://yunshanid.site"
    )

    fun getMainDomain(): String {
        return domains.first()
    }
}