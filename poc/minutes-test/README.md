# 회의록 생성 PoC 테스트

## 목적

전사(transcription) 텍스트를 입력으로 AI가 구조화된 회의록을 자동 생성할 수 있는지 검증한다.

## 검증 대상

| 경로 | 설명 | 파일 |
|------|------|------|
| Gemini API 직접 호출 | google-genai SDK로 전사 텍스트 -> 회의록 | `gemini-test.py`, `gemini-api-test.md` |
| NotebookLM MCP 서버 | MCP source_add + note_create로 회의록 | `notebooklm-mcp-test.md` |

## 파일 구조

```
poc/minutes-test/
  README.md                  # 이 파일
  sample-transcript.txt      # 테스트용 한국어 회의 전사 샘플
  gemini-test.py             # Gemini API 테스트 스크립트
  gemini-api-test.md         # Gemini API 검증 결과 문서
  notebooklm-mcp-test.md     # NotebookLM MCP 검증 결과 문서
```

## 실행 방법

### Gemini API 테스트

```bash
pip install google-genai
GEMINI_API_KEY=your_key python gemini-test.py
```

### NotebookLM MCP 테스트

MCP 서버가 설정된 환경에서 `notebooklm-mcp-test.md`의 시나리오를 따라 실행한다.
