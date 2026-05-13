package Yunshanid

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.*
import android.content.Context

@CloudstreamPlugin
class YunshanidPlugin : Plugin() {
    override fun load() {
        registerMainAPI(YunshanidProvider())
        
        // Pasukan Extractor Lengkap
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(Krakenfiles())
        registerExtractorAPI(Mediafire())
        registerExtractorAPI(Mp4Upload())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(StreamWish())
        registerExtractorAPI(VidHidePro())
        registerExtractorAPI(Voe())
        registerExtractorAPI(DoodLaExtractor())
        registerExtractorAPI(Blogger())
        registerExtractorAPI(OkRuExtractor())
    }
}
