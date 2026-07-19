# 대구 버스

대구광역시 버스 공공데이터와 Accubus 초정밀 위치를 정류장 중심으로 보여주는 Android 앱이다. Google Play가 아닌 [GitHub Releases](https://github.com/rafaam11/public-transportation-info/releases)에서 APK를 배포한다.

## 0.7.1 주요 기능

0.7.1은 장소 검색 Worker를 서명 빌드에 연결하고, 시스템 바와 겹치던 상단 조작부 및 끝나지 않던 `내 주변` 위치 조회를 보완한 안정화 릴리스다.

- 단일 홈 상단의 `햄버거 | 버스·정류장·장소 검색 | 내 주변`
- 최대 20개 즐겨찾기 정류장과 선택적 고정 노선
- 정류장 전체 노선의 도착정보를 한 번에 조회하고 마지막 성공 시각과 함께 캐시
- 검색 결과를 버스·정류장·장소로 묶어 한 화면에 표시
- 사용자가 `내 주변`을 누를 때만 위치 권한 요청. 500m 안에서 최대 10개를 찾고 5개 미만이면 1km로 확장
- 장소 또는 현재 위치를 첫 카메라로 하는 주변 지도와 정류장 마커
- 정류장 상세 지도의 첫 프레임을 선택 정류장 좌표로 생성해 서울시청 기본 위치 노출 방지
- 선택 정류장에 아직 도착하지 않은 모든 노선·방향의 초정밀 차량을 동시에 표시
- 강조 노선 3초, 나머지 8초, 차량 명단 30초 갱신. 상세 좌표 요청 전체 동시성 4개
- GPS 위치는 15초까지 정상, 15~30초 반투명, 30초 초과 숨김. 예측·보간 없음
- 위젯 하나를 즐겨찾기 정류장 하나에 연결. 작은 크기는 2개, 넓은 크기는 4개 노선 표시
- 위젯은 설정 직후 즉시 갱신하고 고유 1회성 부트스트랩을 실행하며, 이후 네트워크는 수동 새로고침과 앱 동기화에만 사용
- GitHub Releases 공지 및 앱 업데이트 확인

## 설치와 초기 설정

1. Releases에서 최신 `daegu-bus-x.y.z.apk`를 내려받아 설치한다.
2. 공공데이터포털의 `대구광역시_대구버스정보시스템` 활용 신청 후 받은 일반 인증키의 **Decoding 값**을 앱에 입력한다.
3. `내 주변`을 사용할 때만 Android 위치 권한을 허용한다. 거부해도 검색·즐겨찾기·정류장 지도는 계속 동작한다.

0.6.x에서 처음 0.7.x로 갱신하면 기존 출근/퇴근 슬롯과 해당 도착 캐시·위젯 연결은 정리된다. 공공데이터 API 키와 재사용 가능한 노선·경로·차량 캐시는 유지된다. 0.7.0에서 0.7.1로 갱신할 때는 즐겨찾기와 위젯 연결을 그대로 유지한다.

## 로컬 개발

요구사항은 JDK 17, Android SDK, Node.js 22 이상이다.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat assembleDebug lintDebug
```

프로젝트 루트의 Git 제외 파일 `local.properties`에서 로컬 값을 설정한다.

```properties
sdk.dir=C\:\\Android\\Sdk
NAVER_MAP_NCP_KEY_ID=
DAEGU_BUS_SERVICE_KEY=
PLACE_SEARCH_BASE_URL=https://your-worker.example.com/
```

- `NAVER_MAP_NCP_KEY_ID`: NAVER Cloud Maps Dynamic Map의 Android Key ID
- `DAEGU_BUS_SERVICE_KEY`: API probe에서만 사용하는 공공데이터 Decoding 키
- `PLACE_SEARCH_BASE_URL`: 배포된 장소 검색 Worker의 루트 URL. 없으면 버스·정류장 검색은 동작하고 장소 섹션만 비어 있다.

비밀값, 키스토어, `.dev.vars`, APK는 커밋하지 않는다.

## 장소 검색 Worker

`place-search-worker/`는 Naver Local Search 클라이언트 ID와 secret을 Android 앱 대신 보관한다. `GET /v1/places?q=`만 제공하며 대구 결과 최대 5개, 10분 캐시, 클라이언트별 분당 30회 제한을 적용한다.

```powershell
cd place-search-worker
npm install
npm run types
npm test
npm run typecheck
npm run check:types
npm run deploy:dry

npx wrangler secret put NAVER_SEARCH_CLIENT_ID
npx wrangler secret put NAVER_SEARCH_CLIENT_SECRET
npx wrangler deploy
```

마지막 세 명령은 실제 Cloudflare 계정과 운영 비밀키가 준비된 안전한 대화형 환경 또는 CI에서만 실행한다.

## 데이터와 안전 경계

| 저장소 | 내용 |
|---|---|
| `SharedPreferences: credentials` | 공공데이터 API 키. Android 백업 제외 |
| `SharedPreferences: api-call-diagnostics` | 서울 시간 기준 일별 API 호출 수 |
| Room `bus-info.db` v4 | 정류장 카탈로그, 즐겨찾기 정류장, 고정 노선, 도착 캐시, 위젯 바인딩, 노선·경로 캐시 |
| Worker secrets | Naver Local Search 클라이언트 ID와 secret |

- 위젯과 앱 업데이트에는 주기 백그라운드 네트워크가 없다.
- 위치 권한은 앱 시작 시 요청하지 않고 `내 주변` 동작에서만 요청한다.
- Accubus 내부 차량 식별자와 증분 커서는 메모리에만 유지한다.
- 연결형 Android 테스트는 설치된 앱 데이터를 바꿀 수 있으므로 명시적 승인 후 실행한다.

## 구조

- Compose Material 3: 단일 홈, 그룹 검색, 주변 지도, 정류장 상세
- Room v4: 정류장 중심 로컬 상태와 위젯 바인딩
- OkHttp: 대구 공공데이터, Accubus, GitHub Releases, 장소 검색 Worker
- NAVER Maps SDK: 첫 프레임 카메라 옵션과 실시간 마커
- Jetpack Glance + WorkManager: 반응형 정류장 위젯과 설정 직후 1회성 부트스트랩
- Cloudflare Workers + Vitest: Naver 장소 검색 비밀 프록시

상세 설계와 구현 체크리스트는 `docs/superpowers/specs/2026-07-18-stop-centered-daegu-bus-redesign.md`와 `docs/superpowers/plans/2026-07-18-stop-centered-daegu-bus-redesign.md`에 있다.
