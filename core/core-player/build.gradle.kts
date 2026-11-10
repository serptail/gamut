plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gamut.core.player"
    compileSdk = 35
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.hilt.android)
    api(libs.androidx.media3.session)
    ksp(libs.hilt.compiler)
}
