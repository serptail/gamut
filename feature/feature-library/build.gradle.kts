plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose); alias(libs.plugins.hilt); alias(libs.plugins.ksp) }
android {
    namespace = "com.gamut.feature.library"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
dependencies { implementation(project(":core:core-model")); implementation(project(":core:core-database")); implementation(project(":core:core-designsystem")); implementation(project(":feature:feature-resolver")); implementation(platform(libs.androidx.compose.bom)); implementation(libs.androidx.compose.ui); implementation(libs.androidx.compose.material3); implementation(libs.androidx.compose.icons); implementation(libs.androidx.lifecycle.viewmodel.compose); implementation(libs.hilt.android); implementation(libs.coil.compose); implementation(libs.androidx.activity.compose); ksp(libs.hilt.compiler) }
