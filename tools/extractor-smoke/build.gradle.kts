plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("com.gamut.smoke.MainKt")
}

dependencies {
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
}
