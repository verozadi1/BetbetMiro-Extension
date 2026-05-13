package Yunshanid

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class YunshanidPlugin : Plugin() {
    // Kita balikkan ke load() tanpa parameter karena library kamu maunya begitu
    override fun load() {
        registerMainAPI(YunshanidProvider())
    }
}
