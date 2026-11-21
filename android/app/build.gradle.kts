import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val jiaApiBaseUrl = providers.gradleProperty("JIA_API_BASE_URL").orElse("")
val googleWebClientId = providers.gradleProperty("JIA_GOOGLE_WEB_CLIENT_ID").orElse("")
val jiaApplicationId = providers.gradleProperty("JIA_APPLICATION_ID").orElse("com.jia.academy.app").get()
val jiaAppCode = providers.gradleProperty("JIA_APP_CODE").orElse("JIA_ANDROID").get()
val jiaDisplayName = providers.gradleProperty("JIA_DISPLAY_NAME").orElse("JIA - Java Interview Academy").get()
val jiaVersionCode = providers.gradleProperty("JIA_VERSION_CODE").orElse("1").get().toInt()
val jiaVersionName = providers.gradleProperty("JIA_VERSION_NAME").orElse("0.1.0").get()

require(jiaApplicationId.matches(Regex("[A-Za-z][A-Za-z0-9_]*([.][A-Za-z][A-Za-z0-9_]*)+"))) {
    "JIA_APPLICATION_ID must be a valid Android application ID"
}
require(jiaAppCode.matches(Regex("[A-Z][A-Z0-9_]{2,39}"))) {
    "JIA_APP_CODE must be a registered uppercase app code"
}
require(jiaVersionCode > 0 && jiaVersionName.matches(Regex("[0-9]{1,5}([.][0-9]{1,5}){2}"))) {
    "JIA_VERSION_CODE must be positive and JIA_VERSION_NAME must use MAJOR.MINOR.PATCH"
}

android {
    namespace = "com.jia.academy.app"
    compileSdk = 36
    defaultConfig {
        applicationId = jiaApplicationId
        minSdk = 26
        targetSdk = 36
        versionCode = jiaVersionCode
        versionName = jiaVersionName
        resValue("string", "app_name", jiaDisplayName)
        buildConfigField("String", "JIA_APP_CODE", "\"$jiaAppCode\"")
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
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
