<!-- GSD:project-start source:PROJECT.md -->
## Project

**Auto Minuting**

Plaud 녹음기에서 동기화되는 음성 파일을 가로채어 로컬에 저장하고, 삼성 Galaxy AI 온보드 전사 기능으로 텍스트로 변환한 뒤, NotebookLM에 소스로 등록하여 회의록을 자동 생성하는 Android 네이티브 앱. 회의 참석 후 녹음기를 연결하면 회의록이 자동으로 완성되는 원클릭 파이프라인을 목표로 한다.

**Core Value:** 녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

### Constraints

- **플랫폼**: Android 네이티브 (Kotlin) — Galaxy AI API 접근성 및 시스템 레벨 통합 필요
- **하드웨어 의존**: Plaud 녹음기 + 삼성 갤럭시 스마트폰 필수
- **리버스 엔지니어링 리스크**: Plaud 앱 업데이트 시 파일 경로/통신 프로토콜 변경 가능성
- **Galaxy AI 가용성**: Galaxy AI 전사 기능이 지원되는 기기/OS 버전에 한정
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Recommended Stack
### Core Platform
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Kotlin | 2.3.20 | Main dev lang | Android official, Coroutines/Flow async optimal | HIGH |
| Android Gradle Plugin | 9.1.0 | Build system | Latest stable Mar 2026, API 36 support | HIGH |
| Gradle | 9.3.1 | Build tool | AGP 9.1.0 compatible default | HIGH |
| Min SDK | API 31 (Android 12) | Min supported | createOnDeviceSpeechRecognizer API 31+ | MEDIUM |
| Target SDK | API 36 | Target API | Play Store latest req | HIGH |
| JDK | 17 | Compile target | AGP 9.1.0 JDK requirement | HIGH |
### UI Layer
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Jetpack Compose BOM | 2026.03.00 | UI framework | Declarative UI, better prod vs XML. 2026 stable | HIGH |
| Material 3 | 1.4.0 | Design system | Integrates Samsung One UI via Material You | HIGH |
| Compose Navigation | (BOM linked) | Screen nav | Compose native nav, type-safe routes | HIGH |
### Data Storage
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Room | 2.8.4 | Local DB | Metadata storage. Compile-time SQL, Coroutines/Flow | HIGH |
| DataStore | 1.2.1 | Settings | User prefs. SharedPreferences replacement | HIGH |
| Local filesystem | - | Audio/text files | Large audio files stored as files not DB | HIGH |
### BLE Communication
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Android BLE API (BluetoothGatt) | Platform API | Plaud recorder BLE | Native API most stable. Better debugging vs wrappers | HIGH |
| CompanionDeviceManager | API 26+ | Device pairing | System UI for BLE discovery/pairing | MEDIUM |
### Speech-to-Text (STT)
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Android SpeechRecognizer API | Platform API | On-device STT entry | May access Galaxy AI via SpeechRecognizer | LOW |
| Samsung Galaxy AI Transcription | - | Korean STT | On-device, no network, excellent Korean accuracy | LOW |
### NotebookLM Integration
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| NotebookLM MCP Server | (confirmed) | AI meeting minutes | MCP server configured. source_add, note_create APIs | MEDIUM |
| OkHttp / Retrofit | latest stable | HTTP client | Communication with MCP server | HIGH |
### DI / Architecture
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Hilt | 1.3.0 | DI | Android official DI, perfect Jetpack integration | HIGH |
| ViewModel + StateFlow | Jetpack | State management | Natural Compose integration, lifecycle-safe | HIGH |
| WorkManager | 2.11.1 | Background work | Scheduling/retry, battery optimization | HIGH |
### Background Processing
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Foreground Service | Platform API | BLE connection | Android 14+ foregroundServiceType required (connectedDevice) | HIGH |
| WorkManager | 2.11.1 | Transcription/upload | Deferrable work. Built-in retry | HIGH |
| FileObserver | Platform API | File sync detection | File system change monitoring | MEDIUM |
### File Processing
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| MediaCodec / AudioFormat | Platform API | Audio decoding | Decode Plaud output (WAV/MP3) | MEDIUM |
| ContentResolver + SAF | Platform API | External file access | Scoped Storage file access | HIGH |
### Testing
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| JUnit 5 | 5.11+ | Unit tests | Good coroutine compat | HIGH |
| Turbine | 1.2+ | Flow testing | StateFlow/SharedFlow utility | HIGH |
| MockK | 1.13+ | Mocking | Kotlin native mocking | HIGH |
| Compose UI Test | BOM linked | UI tests | Semantics-based testing | HIGH |
### KSP
| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| KSP | 2.3.20-* | Annotation processing | Room/Hilt compilers. 2x+ faster than kapt | HIGH |
## Alternatives Considered
| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| UI | Jetpack Compose | XML Views | New project: XML less productive |
| DI | Hilt | Koin | Hilt has compile-time verification |
| DB | Room | SQLDelight | Room better Jetpack integration |
| BLE | Native API | RxAndroidBle | Reverse engineering needs low-level |
| HTTP | OkHttp/Retrofit | Ktor Client | Retrofit is Android standard |
| Navigation | Compose Navigation | Voyager/Decompose | Official solution stable |
| Settings | DataStore | SharedPreferences | DataStore has coroutine/Flow |
| Build | KSP | KAPT | KAPT maintenance mode |
| STT | Galaxy AI | Whisper (local) | Galaxy AI on-device optimized |
## Installation
- Compose BOM: androidx.compose:compose-bom:2026.03.00
- Material3: androidx.compose.material3:material3
- Navigation: androidx.navigation:navigation-compose:2.9.+
- Room: androidx.room:room-runtime:2.8.4
- DataStore: androidx.datastore:datastore-preferences:1.2.1
- Hilt: com.google.dagger:hilt-android:2.56+
- WorkManager: androidx.work:work-runtime-ktx:2.11.1
- Retrofit: com.squareup.retrofit2:retrofit:2.11.+
- OkHttp: com.squareup.okhttp3:okhttp:4.12.+
- Coroutines: org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.+
## Sources
- Android Jetpack Releases: https://developer.android.com/jetpack/androidx/releases - HIGH
- Compose BOM Mapping: https://developer.android.com/develop/ui/compose/bom/bom-mapping - HIGH
- Android Gradle Plugin: https://developer.android.com/build/releases/gradle-plugin - HIGH
- Kotlin Releases: https://kotlinlang.org/docs/releases.html - HIGH
- Android BLE: https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview - HIGH
- Room: https://developer.android.com/training/data-storage/room - HIGH
- SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer - MEDIUM
- Samsung Galaxy AI SDK: Official docs access failed - LOW
- NotebookLM API: No official REST API - MEDIUM
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

## 마일스톤 완료 후처리 (자동 실행)

`/gsd:complete-milestone` 완료 직후 사용자가 별도로 요청하지 않아도 반드시 아래 순서로 실행한다:

1. **app/build.gradle.kts 버전 업데이트**: `versionCode`와 `versionName`을 마일스톤 버전으로 갱신
2. **MANUAL.md 갱신**: `docs/MANUAL.md` — 버전 번호·날짜·신기능·문제해결 섹션 업데이트
3. **QUICK_START.md 갱신**: `docs/QUICK_START.md` — 신기능·핵심 팁 표 갱신
4. **Release APK 빌드**: `./gradlew assembleRelease`
5. **기기 설치**: `adb install -r <apk경로>` (기존 앱 제거 후 재설치가 필요하면 uninstall 먼저)
6. **GitHub Release 생성**: `gh release create vX.Y <apk파일> --title "..." --notes "..."`
7. **README 업데이트**: APK 다운로드 표에 새 버전 행 추가, 버전 히스토리 갱신
8. **git commit + push**: 문서·버전 변경사항 커밋 후 `git push origin master`



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
