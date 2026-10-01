plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.wechat.uiprobe"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.wechat.uiprobe"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true; warningsAsErrors = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
