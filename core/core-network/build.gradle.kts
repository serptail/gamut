plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gamut.core.network"
    compileSdk = 35
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "SYNC_BASE_URL", "\"${providers.gradleProperty("syncBaseUrl").orElse("http://10.0.2.2:8080/").get()}\"")
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-common"))
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
