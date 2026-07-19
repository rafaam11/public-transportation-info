# 대구 버스 0.7.1

## 개선 사항

- Naver 장소 검색 Worker 주소를 로컬 빌드와 서명 릴리스 빌드에 연결했다.
- 상단 햄버거 메뉴, 검색창, `내 주변` 버튼이 상태 표시줄과 화면 측면 시스템 영역을 침범하지 않도록 조정했다.
- `내 주변`을 누르면 위치 확인 진행 상태를 즉시 표시한다.
- 네트워크 위치를 먼저 확인한 뒤 GPS로 폴백하며, 공급자별 5초 제한으로 무한 대기를 막았다.
- 위치 권한 거부와 위치 확인 실패를 구분해 실제 원인에 맞는 안내를 표시한다.

## 기존 데이터와 호환성

0.7.0에서 저장한 공공데이터 API 키, 즐겨찾기 정류장, 고정 노선, 도착 캐시와 위젯 연결을 그대로 유지한다.

## 운영 설정

- Cloudflare Worker에는 `NAVER_SEARCH_CLIENT_ID`, `NAVER_SEARCH_CLIENT_SECRET`이 secret으로 등록되어 있어야 한다.
- GitHub Actions의 `PLACE_SEARCH_BASE_URL` repository variable은 배포된 Worker 루트 URL을 가리켜야 한다.
- Naver client secret과 Android 지도 Key ID는 APK 또는 Git 저장소에 직접 넣지 않는다.
