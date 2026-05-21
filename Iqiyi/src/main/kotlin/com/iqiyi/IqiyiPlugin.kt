package com.iqiyi

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class IqiyiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(IqiyiProvider())
    }
}
