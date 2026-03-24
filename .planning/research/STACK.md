# Technology Stack

**Project:** Auto Minuting
**Researched:** 2026-03-24

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

No third-party BLE wrappers needed. Low-level GATT access essential for Plaud protocol reverse engineering.

### Speech-to-Text (STT)

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Android SpeechRecognizer API | Platform API | On-device STT entry | May access Galaxy AI via SpeechRecognizer | LOW |
| Samsung Galaxy AI Transcription | - | Korean STT | On-device, no network, excellent Korean accuracy | LOW |

**CRITICAL WARNING - Galaxy AI access path uncertain:**

No official SDK/API for third-party apps confirmed as of March 2026. Possible paths:

1. Android SpeechRecognizer API (try first): createOnDeviceSpeechRecognizer() API 31+
2. Intent-based: Trigger Samsung Notes transcription
3. Accessibility Service: Operate Galaxy AI UI programmatically (last resort)
4. Fallback STT: Google on-device recognition or local Whisper model

Phase 1 MUST include feasibility PoC for this.

### NotebookLM Integration

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| NotebookLM MCP Server | (confirmed) | AI meeting minutes | MCP server configured. source_add, note_create APIs | MEDIUM |
| OkHttp / Retrofit | latest stable | HTTP client | Communication with MCP server | HIGH |

No official REST API. Best approach: MCP Server relay (already configured).

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

Key dependencies (all versions verified 2026-03-24):
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
