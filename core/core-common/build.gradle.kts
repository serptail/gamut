plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gamut.core.common"
    compileSdk = 35
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation("junit:junit:4.13.2")
}
