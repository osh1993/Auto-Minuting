import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.autominuting"
    compileSdk = 36

    ndkVersion = "27.2.12479018"

    signingConfigs {
        create("release") {
            storeFile = file("autominuting-release.jks")
            storePassword = "autominuting2026"
            keyAlias = "autominuting"
            keyPassword = "autominuting2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    defaultConfig {
        applicationId = "com.autominuting"
        minSdk = 31
        targetSdk = 36
        versionCode = 8
        versionName = "8.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        // Google OAuth Web Client ID (local.properties에서 읽음)
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(localPropsFile.inputStream())
        }
        val googleOAuthWebClientId: String = localProps.getProperty("GOOGLE_OAUTH_WEB_CLIENT_ID", "")
        buildConfigField("String", "GOOGLE_OAUTH_WEB_CLIENT_ID", "\"$googleOAuthWebClientId\"")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Release APK 파일명에 버전 번호 포함 (AutoMinuting-v6.0-release.apk)
base.archivesName.set("AutoMinuting-v${android.defaultConfig.versionName}")

dependencies {
    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Material Design (XML 테마용)
    implementation(libs.material)

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
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // WorkManager 백그라운드 작업
    implementation(libs.work.runtime.ktx)

    // DataStore 설정 저장
    implementation(libs.datastore.preferences)

    // AI (Gemini) -- 회의록 생성
    implementation(libs.generativeai)

    // Google 인증 (OAuth) -- Phase 12
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // 보안 (암호화 저장소)
    implementation(libs.security.crypto)

    // Custom Tabs (NotebookLM 웹 연동)
    implementation(libs.browser)

    // 코루틴
    implementation(libs.coroutines.android)

    // AndroidX 코어
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)

    // 테스트
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
