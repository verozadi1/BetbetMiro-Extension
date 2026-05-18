// ! Bu araç @Kraptor123 tarafından | @Cs-GizliKeyif için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
// FIX #2: Replaced Plugin() with BasePlugin().
// Plugin is deprecated in newer CloudStream3. BasePlugin is the correct base class
// and aligns with the official extension template. Using Plugin() may cause
// "class is not abstract and does not implement abstract member" errors on newer builds.
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class EPornerPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(EPorner())
    }
}
