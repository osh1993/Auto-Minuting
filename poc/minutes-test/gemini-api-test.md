# Gemini API 회의록 생성 검증

## API 설정

### API 키 발급 방법

1. [Google AI Studio](https://aistudio.google.com/apikey) 접속
2. Google 계정으로 로그인
3. "Create API key" 클릭
4. 프로젝트 선택 또는 새 프로젝트 생성
5. 발급된 API 키를 환경변수에 설정: `GEMINI_API_KEY=your_key`

### Firebase AI Logic SDK vs google-genai 비교

| 항목 | google-genai (Python) | Firebase AI Logic SDK (Android) |
|------|----------------------|-------------------------------|
| 용도 | 서버/로컬 테스트 | Android 앱 프로덕션 |
| 언어 | Python | Kotlin |
| 인증 | API 키 직접 사용 | Firebase 프로젝트 경유 |
| 모바일 독립 실행 | 불가 (Python 런타임 필요) | 가능 (네이티브 앱) |
| 무료 티어 | Google AI Studio 무료 | Firebase Blaze 무료 할당 |
| 프로덕션 경로 | PoC/테스트 전용 | 최종 앱에 사용 |

**PoC 전략:** google-genai로 프롬프트/모델 검증 -> 프로덕션에서 Firebase AI Logic SDK로 전환

## 프롬프트 설계

### 사용한 프롬프트 구조

```
역할 지정: "당신은 전문 회의록 작성자입니다"
출력 형식 지시:
  1. 회의 개요 (날짜, 참석자, 시간)
  2. 주요 안건 및 논의 내용
  3. 결정 사항 (번호 매김)
  4. 액션 아이템 (테이블: 담당자 | 할 일 | 기한)
작성 지침:
  - 핵심 내용만 간결하게 요약
  - 실제 이름 사용 (화자 A 대신)
  - 결정/액션 빠짐없이 포함
  - 한국어 작성
입력: 전사 텍스트 전문
```

### 프롬프트 설계 원칙

1. **구조화된 출력 형식 명시** - Markdown 형식으로 섹션별 지시
2. **역할 부여** - 전문 회의록 작성자로 역할 지정
3. **구체적 지침** - 이름 사용, 언어, 포함 항목 명시
4. **테이블 형식 액션 아이템** - 담당자/할 일/기한을 구조화

## 테스트 결과

**상태:** 테스트 대기 (API 키 필요)

테스트 실행 방법:
```bash
pip install google-genai
GEMINI_API_KEY=your_key python gemini-test.py
```

실행 후 `output-minutes.md`에 생성된 회의록이 저장된다.

### 예상 평가 기준

| 기준 | 설명 | 목표 |
|------|------|------|
| 구조 준수 | 지시한 4개 섹션이 모두 포함되는가 | 100% |
| 정확성 | 전사 내용이 정확하게 반영되는가 | 90%+ |
| 완전성 | 결정 사항/액션 아이템 누락 없는가 | 100% |
| 한국어 품질 | 자연스러운 한국어 표현인가 | 양호 |
| 응답 속도 | API 응답 시간 | 5초 이내 |

## Android 통합 방법

### Firebase AI Logic SDK 사용 시 Kotlin 코드 예시

```kotlin
// build.gradle.kts
// implementation("com.google.firebase:firebase-ai")

import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeModel

// Firebase AI Logic SDK를 통한 Gemini API 호출
class MinutesGenerator {

    private val model = FirebaseAI.instance.generativeModel("gemini-2.5-flash")

    /**
     * 전사 텍스트에서 구조화된 회의록을 생성한다.
     * @param transcript 회의 전사 텍스트
     * @return 구조화된 회의록 (Markdown 형식)
     */
    suspend fun generateMinutes(transcript: String): String {
        val prompt = buildMinutesPrompt(transcript)
        val response = model.generateContent(prompt)
        return response.text ?: throw IllegalStateException("회의록 생성 실패: 빈 응답")
    }

    private fun buildMinutesPrompt(transcript: String): String {
        return """
            당신은 전문 회의록 작성자입니다.
            아래 회의 전사 텍스트를 읽고 구조화된 회의록을 작성해주세요.

            ## 출력 형식
            1. 회의 개요 (날짜, 참석자, 시간)
            2. 주요 안건 및 논의 내용
            3. 결정 사항
            4. 액션 아이템 (담당자, 할 일, 기한)

            ## 전사 텍스트
            $transcript
        """.trimIndent()
    }
}
```

### 통합 시 고려사항

1. **Firebase 프로젝트 설정 필요** - `google-services.json` 추가
2. **Coroutine 기반** - `suspend fun`으로 비동기 호출
3. **에러 처리** - 네트워크 오류, 할당량 초과, 빈 응답 처리
4. **프롬프트 관리** - 프롬프트를 앱 리소스 또는 Remote Config에서 관리

## 비용

### 무료 티어 한도 (Google AI Studio - 2026년 3월 기준)

| 항목 | 무료 한도 |
|------|----------|
| Gemini 2.5 Flash | RPM 10 / TPM 250K / RPD 500 |
| 입력 토큰 | 무료 |
| 출력 토큰 | 무료 |

### 예상 사용량

| 항목 | 추정 |
|------|------|
| 평균 회의 전사 텍스트 | 3,000~10,000 토큰 |
| 회의록 출력 | 500~2,000 토큰 |
| 일일 사용 | 1~5회 |
| 월간 사용 | 20~100회 |

**결론:** 무료 티어 내에서 충분히 운용 가능. 일일 500회 제한 대비 실사용 5회 미만.

### Firebase Blaze 플랜 (프로덕션)

Firebase AI Logic SDK 경유 시 Blaze 플랜 필요하지만, 무료 할당이 포함되어 있어 소량 사용 시 비용 없음.

## 평가: NEEDS_API_KEY

**판정 근거:**

| 기준 | 결과 |
|------|------|
| API 접근 가능 | 예 (API 키 발급 필요) |
| 모바일 독립 실행 | 가능 (Firebase AI Logic SDK) |
| 프롬프트 설계 | 완료 |
| 테스트 스크립트 | 완료 |
| 비용 | 무료 티어 내 운용 가능 |
| 실제 테스트 | 미완료 (API 키 필요) |

**NEEDS_API_KEY** - 테스트 인프라는 준비 완료. API 키 발급 후 실제 품질 검증 필요. 기술적으로는 VIABLE 경로로 판단됨 (공식 API, SDK 존재, 무료 티어, 모바일 독립 실행 가능).
