package com.sad25kag.nodrakor

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class NoDrakorPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(NoDrakor())
    }
}