import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val naverMapNcpKeyId = localProperties.getProperty("NAVER_MAP_NCP_KEY_ID").orEmpty()

// 환경변수(CI) 우선, 없으면 local.properties(로컬 개발자용).
fun releaseSigningProperty(name: String): String? =
    System.getenv(name)?.takeIf(String::isNotBlank) ?: localProperties.getProperty(name)?.takeIf(String::isNotBlank)

val releaseStoreFilePath = releaseSigningProperty("RELEASE_KEYSTORE_PATH")
val releaseStorePassword = releaseSigningProperty("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(releaseStoreFilePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.rafaam11.businfo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rafaam11.businfo"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.6.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["naverMapNcpKeyId"] = naverMapNcpKeyId
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.naver.map)
    implementation(libs.glance.appwidget)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.room.testing)
}
