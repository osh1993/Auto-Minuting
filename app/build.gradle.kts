plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.autominuting"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.autominuting"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Plaud SDK 인증 키 (local.properties에서 읽음)
        val plaudAppKey: String = project.findProperty("PLAUD_APP_KEY")?.toString() ?: ""
        val plaudAppSecret: String = project.findProperty("PLAUD_APP_SECRET")?.toString() ?: ""
        buildConfigField("String", "PLAUD_APP_KEY", "\"$plaudAppKey\"")
        buildConfigField("String", "PLAUD_APP_SECRET", "\"$plaudAppSecret\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Guava 버전 충돌 방지 (Plaud SDK가 28.2 요구)
configurations.all {
    resolutionStrategy.force("com.google.guava:guava:28.2-android")
}

dependencies {
    // Plaud SDK (AAR) -- D-01: 1차 오디오 수집 경로
    implementation(files("libs/plaud-sdk.aar"))
    implementation(libs.guava)

    // Retrofit + OkHttp -- D-02: Cloud API 폴백 경로
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Compose BOM - 모든 Compose 라이브러리 버전 통합 관리
    implementation(platform(libs.compose.bom))
    implementation(libs.material3)
    implementation(libs.material.icons.extended)

    // 네비게이션
    implementation(libs.navigation.compose)

    // Room 데이터베이스
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt 의존성 주입
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // WorkManager 백그라운드 작업
    implementation(libs.work.runtime.ktx)

    // DataStore 설정 저장
    implementation(libs.datastore.preferences)

    // 코루틴
    implementation(libs.coroutines.android)

    // AndroidX 코어
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)
}
