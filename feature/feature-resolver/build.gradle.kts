plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.hilt); alias(libs.plugins.ksp) }
android {
    namespace = "com.gamut.feature.resolver"
    compileSdk = 35
    compileOptions { isCoreLibraryDesugaringEnabled = true }
    defaultConfig { multiDexEnabled = true }
}
dependencies {
    implementation(project(":core:core-model")); implementation(project(":core:core-network")); implementation(project(":core:core-database")); implementation(project(":core:core-common")); implementation(project(":core:core-player"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.work.runtime); implementation(libs.retrofit.core); implementation(libs.youtubedl.android); implementation(libs.newpipe.extractor); implementation(libs.okhttp.logging); implementation(libs.kotlinx.serialization.json); implementation(libs.hilt.android); ksp(libs.hilt.compiler)
}
