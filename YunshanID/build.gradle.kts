import com.lagradost.cloudstream3.gradle.CloudstreamExtension

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    [span_19](start_span)// Pastikan ini sama persis dengan package di file Plugin kamu[span_19](end_span)
    mainClass = "com.betbet.yunshanid.YunshanIDPlugin" 
    
    name = "YunshanID"
    [span_20](start_span)description = "Donghua & Anime provider dari YunshanID"[span_20](end_span)
    [span_21](start_span)authors = listOf("Betbet")[span_21](end_span)
    [span_22](start_span)language = "id"[span_22](end_span)
    
    [span_23](start_span)status = 1[span_23](end_span)

    [span_24](start_span)tvTypes = listOf("Anime", "TvSeries")[span_24](end_span)
}

dependencies {
    // Tambahkan library standar jika diperlukan (biasanya sudah include di template)
    implementation(kotlin("stdlib"))
}
