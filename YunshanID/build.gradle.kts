import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.api.Project

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        // Memastikan menggunakan versi plugin terbaru
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.21")
    }
}

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.github.recloudstream")

// Gunakan blok cloudstream {} atau configure<CloudstreamExtension> dengan properti langsung
configure<CloudstreamExtension> {
    id = "Yunshanid"
    name = "Yunshanid"
    pluginClass = "com.Yunshanid.YunshanidPlugin"
    description = "Dibuat oleh BetbetMiro untuk menonton konten dari Yunshanid"
    // Gunakan authors (plural) karena biasanya sistem mengharapkan List
    authors = listOf("BetbetMiro")
}

dependencies {
    val cloudstreamVersion = "latest-SNAPSHOT"
    compileOnly("com.github.recloudstream:cloudstream3:$cloudstreamVersion")
}