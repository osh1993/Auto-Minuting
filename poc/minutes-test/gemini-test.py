# 사용법: GEMINI_API_KEY=xxx python gemini-test.py
# 의존성: pip install google-genai

"""
Gemini API를 사용하여 전사 텍스트에서 구조화된 회의록을 생성하는 테스트 스크립트.
google-genai 패키지를 사용하며, Gemini 2.5 Flash 모델로 테스트한다.
"""

import os
import sys
from pathlib import Path

# google-genai 패키지 임포트
try:
    from google import genai
except ImportError:
    print("오류: google-genai 패키지가 설치되지 않았습니다.")
    print("설치: pip install google-genai")
    sys.exit(1)

# 환경변수에서 API 키 로드
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    print("오류: GEMINI_API_KEY 환경변수가 설정되지 않았습니다.")
    print("사용법: GEMINI_API_KEY=your_key python gemini-test.py")
    sys.exit(1)

# 모델 설정
MODEL_NAME = "gemini-2.5-flash"

# 회의록 생성 프롬프트
MINUTES_PROMPT = """당신은 전문 회의록 작성자입니다. 아래 회의 전사 텍스트를 읽고, 다음 형식에 맞춰 구조화된 회의록을 작성해주세요.

## 출력 형식

### 1. 회의 개요
- 날짜:
- 참석자:
- 회의 시간:

### 2. 주요 안건 및 논의 내용
(안건별로 구분하여 핵심 논의 내용을 요약)

### 3. 결정 사항
(번호를 매겨 명확하게 나열)

### 4. 액션 아이템
| 담당자 | 할 일 | 기한 |
|--------|--------|------|
(테이블 형식으로 정리)

## 작성 지침
- 핵심 내용만 간결하게 요약한다
- 화자의 이름을 사용한다 (화자 A 등이 아닌 실제 이름)
- 결정 사항과 액션 아이템은 빠짐없이 포함한다
- 한국어로 작성한다

---

## 회의 전사 텍스트

{transcript}
"""


def load_transcript(file_path: str) -> str:
    """전사 텍스트 파일을 읽어온다."""
    path = Path(file_path)
    if not path.exists():
        print(f"오류: 전사 텍스트 파일을 찾을 수 없습니다: {file_path}")
        sys.exit(1)
    return path.read_text(encoding="utf-8")


def generate_minutes(transcript: str) -> str:
    """Gemini API를 호출하여 회의록을 생성한다."""
    # 클라이언트 초기화
    client = genai.Client(api_key=GEMINI_API_KEY)

    # 프롬프트에 전사 텍스트 삽입
    prompt = MINUTES_PROMPT.format(transcript=transcript)

    # Gemini API 호출
    print(f"모델: {MODEL_NAME}")
    print("회의록 생성 중...")

    response = client.models.generate_content(
        model=MODEL_NAME,
        contents=prompt,
    )

    return response.text


def save_output(content: str, output_path: str) -> None:
    """생성된 회의록을 파일로 저장한다."""
    path = Path(output_path)
    path.write_text(content, encoding="utf-8")
    print(f"회의록 저장 완료: {output_path}")


def main():
    """메인 실행 함수."""
    # 스크립트 위치 기준 경로 설정
    script_dir = Path(__file__).parent
    transcript_path = script_dir / "sample-transcript.txt"
    output_path = script_dir / "output-minutes.md"

    print("=" * 60)
    print("Gemini API 회의록 생성 테스트")
    print("=" * 60)

    # 1. 전사 텍스트 로드
    print(f"\n전사 텍스트 로드: {transcript_path}")
    transcript = load_transcript(str(transcript_path))
    print(f"전사 텍스트 길이: {len(transcript)} 글자")

    # 2. 회의록 생성
    print()
    minutes = generate_minutes(transcript)

    # 3. 결과 출력
    print("\n" + "=" * 60)
    print("생성된 회의록:")
    print("=" * 60)
    print(minutes)

    # 4. 파일로 저장
    print("\n" + "=" * 60)
    save_output(minutes, str(output_path))

    print("\n테스트 완료!")


if __name__ == "__main__":
    main()
