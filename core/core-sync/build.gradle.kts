import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val props = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun cfg(key: String): String = props.getProperty(key) ?: System.getenv(key) ?: ""

android {
    namespace = "com.gamut.core.sync"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "SUPABASE_URL", "\"${cfg("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${cfg("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${cfg("GOOGLE_WEB_CLIENT_ID")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-database"))
    implementation(project(":feature:feature-library"))

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.room.runtime)

    implementation(libs.hilt.android)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.storage)

    ksp(libs.hilt.compiler)
}
