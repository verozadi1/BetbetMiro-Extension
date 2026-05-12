import com.lagradost.cloudstream3.gradle.CloudstreamExtension

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3.gradle")
}

// Konfigurasi Cloudstream
cloudstream {
    // Gunakan setMainClass jika mainClass = tidak terbaca
    setMainClass("com.betbet.yunshanid.YunshanIDPlugin")
    
    // Gunakan properti yang didukung oleh plugin gradle cloudstream
    name = "YunshanID"
    description = "Donghua & Anime provider dari YunshanID"
    authors = listOf("Betbet")
    language = "id"
    
    status = 1

    tvTypes = listOf(
        "Anime",
        "TvSeries"
    )
}

android {
    namespace = "com.betbet.yunshanid"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
    }
}
