import com.lagradost.cloudstream3.gradle.CloudstreamExtension

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.github.recloudstream")

cloudstream {
    // Properti ini adalah yang paling aman dan tidak akan bentrok dengan Gradle
    pluginId = "Yunshanid"
    pluginName = "Yunshanid"
    pluginClass = "com.Yunshanid.YunshanidPlugin"
    description = "Dibuat oleh BetbetMiro untuk Yunshanid"
    authors = listOf("BetbetMiro")
}

dependencies {
    val cloudstreamVersion = "latest-SNAPSHOT"
    compileOnly("com.github.recloudstream:cloudstream3:$cloudstreamVersion")
}
