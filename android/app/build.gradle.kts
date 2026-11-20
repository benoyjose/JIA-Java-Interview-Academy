import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val jiaApiBaseUrl = providers.gradleProperty("JIA_API_BASE_URL").orElse("")
val googleWebClientId = providers.gradleProperty("JIA_GOOGLE_WEB_CLIENT_ID").orElse("")

android {
    namespace = "com.jia.academy.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.jia.academy.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "JIA_API_BASE_URL", "\"${jiaApiBaseUrl.get()}\"")
        buildConfigField("String", "JIA_GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.get()}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
