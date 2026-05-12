package com.betbet.yunshanid

object YunshanUtils {

    fun fixUrl(url: String): String {
        return if (url.startsWith("http")) {
            url
        } else {
            "${DomainManager.getMainDomain()}$url"
        }
    }
}