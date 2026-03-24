#!/usr/bin/env python3
"""
Plaud Cloud API 폴백 테스트 스크립트

Plaud 클라우드 API(api.plaud.ai)를 사용하여 녹음 목록 조회 및 오디오 다운로드를
테스트한다. SDK appKey 획득이 불가능한 경우의 폴백 경로 검증용.

사용법: JWT_TOKEN=xxx python cloud-api-test.py
       JWT_TOKEN=xxx python cloud-api-test.py --download <recording_id>

JWT 토큰 추출 방법:
  1. 브라우저에서 https://www.plaud.ai 로그인
  2. 개발자 도구 (F12) > Application > Local Storage
  3. plaud.ai 도메인에서 JWT 토큰 값 복사
  4. 환경변수로 전달: export JWT_TOKEN="eyJhbG..."

참고: 이 API는 비공식 역공학 결과이며, Plaud 측에서 언제든 변경/차단할 수 있다.
"""

import os
import sys
import json
import argparse
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

# API 기본 설정
BASE_URL = "https://api.plaud.ai"
# EU 리전: https://api-euc1.plaud.ai (필요 시 변경)

def get_token():
    """환경변수에서 JWT 토큰을 읽는다."""
    token = os.environ.get("JWT_TOKEN")
    if not token:
        print("[오류] JWT_TOKEN 환경변수가 설정되지 않았습니다.")
        print("사용법: JWT_TOKEN=xxx python cloud-api-test.py")
        print()
        print("JWT 토큰 추출 방법:")
        print("  1. 브라우저에서 https://www.plaud.ai 로그인")
        print("  2. 개발자 도구 (F12) > Application > Local Storage")
        print("  3. JWT 토큰 값 복사")
        sys.exit(1)
    return token

def make_request(endpoint, token, method="GET"):
    """API 요청을 수행한다."""
    url = f"{BASE_URL}{endpoint}"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "User-Agent": "PlaudCloudAPITest/1.0",
    }

    print(f"[요청] {method} {url}")

    try:
        req = Request(url, headers=headers, method=method)
        with urlopen(req, timeout=30) as response:
            status = response.status
            data = response.read().decode("utf-8")
            print(f"[응답] HTTP {status}")
            return json.loads(data) if data else None
    except HTTPError as e:
        print(f"[오류] HTTP {e.code}: {e.reason}")
        if e.code == 401:
            print("  -> JWT 토큰이 만료되었거나 유효하지 않습니다.")
            print("  -> 브라우저에서 다시 로그인하여 새 토큰을 추출하세요.")
        elif e.code == 403:
            print("  -> 접근이 거부되었습니다. 계정 권한을 확인하세요.")
        try:
            error_body = e.read().decode("utf-8")
            print(f"  -> 응답: {error_body[:500]}")
        except Exception:
            pass
        return None
    except URLError as e:
        print(f"[오류] 연결 실패: {e.reason}")
        print("  -> 네트워크 연결을 확인하세요.")
        return None
    except json.JSONDecodeError:
        print("[오류] 응답 JSON 파싱 실패")
        return None

def test_connection(token):
    """API 연결 테스트: 기본 엔드포인트 접근 확인."""
    print("=" * 60)
    print("1. API 연결 테스트")
    print("=" * 60)

    # 사용자 정보 또는 기본 엔드포인트 테스트
    result = make_request("/v1/user/info", token)
    if result:
        print(f"  [성공] API 연결 확인. 응답: {json.dumps(result, indent=2, ensure_ascii=False)[:500]}")
        return True
    else:
        # 다른 엔드포인트 시도
        print("  [재시도] /v1/user/info 실패, 다른 엔드포인트 시도...")
        result = make_request("/api/v1/me", token)
        if result:
            print(f"  [성공] API 연결 확인.")
            return True
        print("  [실패] API 연결 불가. 토큰 또는 엔드포인트를 확인하세요.")
        return False

def test_list_recordings(token):
    """녹음 목록 조회 테스트."""
    print()
    print("=" * 60)
    print("2. 녹음 목록 조회 테스트")
    print("=" * 60)

    # 가능한 엔드포인트들 시도
    endpoints = [
        "/v1/recordings",
        "/v1/record/list",
        "/api/v1/recordings",
        "/v1/notes",
    ]

    for endpoint in endpoints:
        result = make_request(endpoint, token)
        if result:
            print(f"  [성공] 엔드포인트: {endpoint}")
            # 녹음 목록 출력
            recordings = result.get("data", result.get("recordings", result.get("items", [])))
            if isinstance(recordings, list):
                print(f"  녹음 개수: {len(recordings)}")
                for i, rec in enumerate(recordings[:5]):  # 최대 5개만 출력
                    rec_id = rec.get("id", rec.get("recordId", "N/A"))
                    title = rec.get("title", rec.get("name", "N/A"))
                    created = rec.get("createdAt", rec.get("created_at", "N/A"))
                    duration = rec.get("duration", "N/A")
                    print(f"    [{i+1}] ID: {rec_id}, 제목: {title}, 생성일: {created}, 길이: {duration}")
                return recordings
            else:
                print(f"  [정보] 응답 형식: {type(recordings)}")
                return result

    print("  [실패] 녹음 목록 조회 엔드포인트를 찾지 못했습니다.")
    return None

def test_download_url(token, recording_id):
    """오디오 다운로드 URL 획득 테스트."""
    print()
    print("=" * 60)
    print(f"3. 오디오 다운로드 URL 획득 테스트 (ID: {recording_id})")
    print("=" * 60)

    # 가능한 다운로드 엔드포인트들
    endpoints = [
        f"/v1/recordings/{recording_id}/download",
        f"/v1/record/{recording_id}/file",
        f"/api/v1/recordings/{recording_id}/download",
        f"/v1/recordings/{recording_id}/export",
    ]

    for endpoint in endpoints:
        result = make_request(endpoint, token)
        if result:
            print(f"  [성공] 엔드포인트: {endpoint}")
            download_url = result.get("url", result.get("downloadUrl", result.get("data", {}).get("url", "N/A")))
            print(f"  다운로드 URL: {str(download_url)[:200]}...")
            if "s3" in str(download_url).lower() or "amazonaws" in str(download_url).lower():
                print("  [확인] S3 presigned URL 형식 확인됨")
            return download_url

    print("  [실패] 다운로드 URL 획득 엔드포인트를 찾지 못했습니다.")
    return None

def main():
    parser = argparse.ArgumentParser(description="Plaud Cloud API 폴백 테스트")
    parser.add_argument("--download", type=str, help="다운로드 테스트할 녹음 ID")
    parser.add_argument("--region", type=str, default="global",
                       choices=["global", "eu"],
                       help="API 리전 (기본: global)")
    args = parser.parse_args()

    global BASE_URL
    if args.region == "eu":
        BASE_URL = "https://api-euc1.plaud.ai"
        print(f"[설정] EU 리전 사용: {BASE_URL}")

    token = get_token()
    print(f"[설정] JWT 토큰: {token[:20]}...{token[-10:]}")
    print(f"[설정] API 서버: {BASE_URL}")
    print()

    # 1. 연결 테스트
    connected = test_connection(token)

    if not connected:
        print()
        print("=" * 60)
        print("테스트 결과: FAIL")
        print("=" * 60)
        print("API 연결에 실패했습니다. 다음을 확인하세요:")
        print("  1. JWT 토큰이 유효한지 (브라우저에서 재추출)")
        print("  2. 네트워크 연결 상태")
        print("  3. API 서버 리전 설정 (--region eu 시도)")
        sys.exit(1)

    # 2. 녹음 목록 조회
    recordings = test_list_recordings(token)

    # 3. 다운로드 URL 획득 (ID 지정 시)
    if args.download:
        test_download_url(token, args.download)
    elif recordings and isinstance(recordings, list) and len(recordings) > 0:
        # 첫 번째 녹음으로 자동 테스트
        first_id = recordings[0].get("id", recordings[0].get("recordId"))
        if first_id:
            test_download_url(token, first_id)

    # 결과 요약
    print()
    print("=" * 60)
    print("테스트 결과 요약")
    print("=" * 60)
    print(f"  API 연결: {'성공' if connected else '실패'}")
    print(f"  녹음 목록: {'조회됨' if recordings else '실패/미확인'}")
    print()
    print("참고: 이 API는 비공식이며 언제든 변경될 수 있습니다.")
    print("SDK appKey 획득이 가능하다면 SDK 경로를 우선 사용하세요.")

if __name__ == "__main__":
    main()
