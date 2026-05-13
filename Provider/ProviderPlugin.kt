package com.baseprovider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class BaseProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(BaseProvider())
        
        // Register specific extractors
        ProviderEkstraktors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
