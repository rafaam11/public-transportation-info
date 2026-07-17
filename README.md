# 대구 버스 정보

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/minSdk-26-3DDC84" alt="minSdk 26">
  <img src="https://img.shields.io/github/v/release/rafaam11/public-transportation-info?display_name=tag" alt="latest release">
</p>

**대구 버스**는 대구광역시 버스정보시스템 공공데이터와 대구시 초정밀 위치(Accubus)를 이용해 출퇴근 버스를 확인하는 개인용 Android 네이티브 앱이다. Google Play에는 배포하지 않고, GitHub Releases에서 APK를 내려받아 설치하는 사이드로드 방식으로 배포한다.

- **출퇴근 대시보드** — 출근/퇴근 카드에 저장한 노선·정류장의 가장 가까운 도착 차량과 데이터 신선도를 보여준다.
- **실시간 버스 지도** — 네이버 지도 위에 도로망 기반 노선 경로와 초정밀 실시간 차량 위치를 표시한다.
- **홈 화면 위젯** — 출근·퇴근 카드 중 하나를 선택해 홈 화면에서 바로 확인한다. 자동 주기 갱신은 하지 않고 수동 새로고침만 지원한다.
- **앱 내 업데이트 확인** — 앱을 열면 GitHub Releases의 최신 버전을 1회 확인하고, 새 버전이 있으면 앱 안에서 다운로드·설치할 수 있다.

## 설치

Google Play에는 배포하지 않으므로 GitHub Releases에서 APK를 직접 받아 설치한다.

1. [Releases](https://github.com/rafaam11/public-transportation-info/releases)에서 최신 `daegu-bus-x.y.z.apk`를 내려받는다.
2. 서명되지 않은(스토어 미등록) APK라 설치 시 "출처를 알 수 없는 앱" 경고가 뜰 수 있다 — 설정에서 해당 앱(브라우저 또는 파일 관리자)의 설치 허용을 켜거나, 설치 화면에서 뜨는 안내를 따라 허용한다.
3. 설치 후 앱을 열고 공공데이터포털에서 발급받은 대구광역시 버스정보시스템 API 키를 입력한다.

## 업데이트 확인 (앱 내)

앱은 백그라운드에서 주기적으로 갱신을 확인하지 않는다 — 위젯과 동일하게, 명시적인 사용자 행동에만 네트워크 호출이 결합된다.

- 앱을 열면 GitHub Releases의 최신 버전을 **1회** 조회한다.
- 대시보드 상단의 **업데이트 확인** 버튼으로 언제든 다시 확인할 수 있다(자동 확인과 같은 동작을 재사용한다).
- 새 버전이 있으면 배너에 **다운로드** 버튼이 뜨고, 다운로드가 끝나면 **설치** 버튼으로 시스템 설치 확인 화면을 연다.
- "출처를 알 수 없는 앱" 설치 권한이 꺼져 있으면 설정 화면으로 안내하며, 권한을 켠 뒤 설치 버튼을 다시 누르면 진행된다.
- 무음 자동 설치가 아니다 — 다운로드와 설치는 항상 버튼을 눌러야 진행된다.

## 사용법

**카드 설정** — 대시보드에서 빈 카드의 "버스 추가"를 누르고 노선번호 또는 기·종점으로 검색 → 방향 선택 → 승차 정류장 선택 순으로 저장한다. 카드를 눌러 실시간 지도로 이동하거나, "편집"으로 노선을 다시 설정하거나 삭제할 수 있다.

**실시간 지도** — 카드를 탭하면 저장한 노선·방향의 도로망 기반 경로와 실시간 차량 위치가 뜬다. 하단 시트에 전체 운행 대수와 초정밀 위치 대수가 구분되어 표시된다.

**홈 화면 위젯** — 출근 또는 퇴근 카드를 선택해 홈 화면에 추가한다. 위젯을 탭하면 새로고침되고, 다시 탭하면 앱의 해당 실시간 지도로 이동한다.

**API 키 변경** — 대시보드 상단의 "API 키 변경"에서 새 키를 입력해 검증한다. 새 키 검증이 성공하기 전에는 기존 키를 삭제하거나 교체하지 않는다.

## 아키텍처

### 네이버 지도 로컬 설정

NAVER Cloud Maps 애플리케이션에서 Dynamic Map을 활성화하고 Android 패키지
`com.rafaam11.businfo`를 등록한다. 발급된 NCP Key ID는 Git에 넣지 않고 프로젝트 루트의
ignored `local.properties`에 다음 이름으로만 저장한다.

`NAVER_MAP_NCP_KEY_ID=<발급된 Key ID>`

값이 없으면 APK는 빌드되지만 실기기 지도 인증은 실패 상태로 표시된다.

### 초정밀 위치 데이터

지도 차량 마커는 대구 버스정보시스템의 초정밀 화면이 사용하는 Accubus 상세 응답의
확인된 GPS 좌표만 표시한다. 공개 `getPos02` 응답은 전체 운행 대수와 최근 정류소 정보에만
사용하며, 그 좌표를 지도 마커로 대신 표시하지 않는다. GPS 수신 시각이 15초를 넘으면
마커를 반투명으로 표시하고 30초를 넘으면 숨긴다. 위치를 보간·예측하거나 도로 위로
강제 보정하지 않는다.

Accubus 경로는 대구시의 공개 웹 화면이 사용하는 1차 시스템이지만 공개 API 계약이나
가용성 보장이 없다. 연결 실패는 차량별로 격리하고 마지막 확인 위치도 30초 후 제거한다.
내부 차량 식별자와 증분 요청 커서는 메모리에만 두며 저장·로그·화면 표시에 사용하지 않는다.

현재 Accubus 서버는 leaf 발급자인 `GlobalSign GCC R3 DV TLS CA 2020` 대신 관련 없는
인증서를 TLS 체인에 함께 보내 Android가 경로를 완성하지 못한다. 앱은 leaf 인증서의 공식
AIA 주소에서 받은 해당 GlobalSign 중간 인증서만 `accubus.daegu.go.kr` 단일 도메인의 추가
trust anchor로 등록한다. 시스템 CA 신뢰는 유지하며 하위 도메인이나 다른 호스트에는 적용하지
않는다. 서버 체인이 정상화되면 이 보완 설정을 다시 검증하고 제거한다.

대구 실시간 신호등은 현재 이용 가능한 공식 API 범위에 포함되지 않으므로 표시하거나
추정하지 않는다. 향후 공식 전국 신호 API에 대구 코드 `2700000000`이 제공될 때 별도
데이터 소스로 추가한다.

## 안전장치

### TLS 인증서 이슈

위 "초정밀 위치 데이터" 항목에 설명한 대로, `accubus.daegu.go.kr` 단일 도메인에만 GlobalSign
중간 인증서를 추가 trust anchor로 등록한다. 시스템 CA 신뢰는 그대로 유지하며 다른 호스트에는
적용하지 않는다.

### 백그라운드 폴링 없음

위젯과 업데이트 확인 모두 자동 주기 네트워크 호출을 하지 않는다. 앱을 열거나 버튼을 누르는
등 명시적인 사용자 행동에만 네트워크 호출이 결합된다.

### 릴리스 서명 키스토어 고정

배포 서명 키스토어는 생성 이후 재생성하지 않는다. 서명이 바뀌면 기존 설치본이 인앱 업데이트로
새 버전을 설치할 수 없게 되어(서명 불일치) 사용자가 수동으로 재설치해야 한다.

## 개발

### 로컬 설정

`local.properties`(Git 추적 제외)에 다음 값을 저장한다.

| 키 | 설명 |
|---|---|
| `NAVER_MAP_NCP_KEY_ID` | NAVER Cloud Maps Dynamic Map Key ID |
| `DAEGU_BUS_SERVICE_KEY` | 공공데이터포털 발급 대구광역시 버스정보시스템 일반 인증키 (Decoding 값, `api-probe` 모듈 실행 시에만 필요) |
| `RELEASE_KEYSTORE_PATH` 등 | 로컬에서 서명된 release 빌드를 테스트하려는 경우에만 필요 — [릴리스 절차](#릴리스-절차-유지관리자용) 참고 |

### 빌드 · 테스트

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

### 실기기 APK 인수 테스트

테스트에는 공공데이터포털에서 발급받은 `대구광역시_대구버스정보시스템` 일반 인증키의 **Decoding 값**, NAVER Maps NCP Key ID, Android 실기기가 필요하다. 두 키를 명령줄, 스크린샷, 이슈, 로그 또는 Git에 남기지 않는다.

1. `NAVER_MAP_NCP_KEY_ID`가 설정된 상태로 debug APK를 빌드하고, USB 디버깅이 연결된 기기에 앱 데이터를 유지하는 `adb install -r`로 설치한다.

   ```powershell
   .\gradlew.bat :app:assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

2. 급행8-1 카드에서 실시간 지도를 열고 저장한 방향의 노선·정류소·차량만 나타나는지 확인한다. 차량은 급행 공식 색상 `#FF4917`의 옆모습 버스이고, 각 차량 안에 전체 노선번호가 표시되어야 한다.
3. 링크 기반 노선선이 도로망을 따라가고 불연속 구간을 임의의 직선으로 가로지르지 않는지 확인한다.
4. 시트에 `전체 운행 n대 · 초정밀 위치 m대`가 구분되어 보이고, 초정밀 차량 위치가 약 3초 간격으로 갱신되는지 확인한다. 새 응답 사이에 마커가 추정 이동하지 않아야 한다.
5. 지도를 직접 이동한 뒤 일반 갱신에서 카메라가 초기화되지 않는지, `노선 전체 보기`를 누르면 전체 노선으로 다시 맞춰지는지 확인한다.
6. 네트워크를 끄고 차량별 GPS 수신 시각 15초 이후 반투명 지연 표시, 30초 이후 차량 마커 숨김을 확인한다. 이때 공개 API의 정류소 좌표가 대체 마커로 나타나면 안 된다.
7. 앱을 백그라운드로 보낸 뒤 공개 요약과 초정밀 위치 호출이 모두 멈추고, 다시 열면 즉시 갱신되는지 확인한다.
8. NAVER 지도 인증 오류가 대구 버스 API 키 오류와 구분되어 표시되는지 확인한다.
9. 런처에서 새 버스 아이콘을 확인한다. 출근·퇴근 위젯을 각각 추가한 뒤 저장된 도착 정보, 수동 새로고침, 네트워크 실패 시 마지막 값 유지, 카드 탭의 해당 실시간 지도 이동을 확인한다. 위젯은 자동 주기 요청을 하지 않는다. API 키 오류의 `API 키 변경`은 앱 내부의 비공개 진입점을 통해서만 열리며, 새 키 검증이 성공하기 전에는 기존 키를 삭제하거나 교체하지 않는다.
10. 이전 버전이 설치된 상태에서 앱을 열어 업데이트 배너가 뜨는지, 다운로드·설치 버튼이 실제로 동작하는지 확인한다. 최신 버전에서는 배너가 뜨지 않아야 한다. "출처를 알 수 없는 앱" 권한이 꺼져 있으면 설정 화면으로 안내되는지도 확인한다.

연결형 Android 테스트는 앱 데이터가 교체될 수 있으므로 별도 승인 없이 실행하지 않는다. 단위 테스트와 Android 테스트 컴파일은 다음 명령으로 실행할 수 있다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

## 릴리스 절차 (유지관리자용)

1. `app/build.gradle.kts`의 `versionCode`와 `versionName`을 **둘 다** 올린다. `versionCode`를 빼먹으면 인앱 업데이트 설치가 `INSTALL_FAILED_VERSION_DOWNGRADE`로 실패한다.
2. 커밋 후 태그를 붙여 푸시한다.

   ```powershell
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```

3. GitHub Actions(`.github/workflows/release.yml`)가 태그와 `versionName`이 일치하는지 검증하고, 서명된 release APK를 빌드해 같은 태그의 **draft 릴리스**에 첨부한다.
4. GitHub에서 draft 릴리스 노트를 확인하고 **수동으로 publish**한다. publish 전까지는 `/releases/latest` API에 노출되지 않으므로 앱의 업데이트 확인에도 잡히지 않는다.

### 최초 1회 설정

1. 릴리스 서명 키스토어를 생성한다 (PKCS12는 store/key 암호가 같아야 한다).

   ```powershell
   keytool -genkeypair -v -keystore release-daegu-bus.jks -alias daegu-bus-release -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12
   ```

   **이 키스토어는 이후 절대 재생성하지 않는다** — 재생성하면 서명이 바뀌어 기존 설치본이 인앱 업데이트를 받을 수 없다. 안전한 곳에 별도로 백업해 둔다.

2. Base64로 인코딩한다.

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("release-daegu-bus.jks")) | Set-Content -NoNewline release-daegu-bus.b64
   ```

3. 저장소 GitHub Secrets에 다음 5개를 등록한다.

   | Secret 이름 | 값 |
   |---|---|
   | `RELEASE_KEYSTORE_BASE64` | 위에서 만든 `.b64` 파일 내용 |
   | `RELEASE_KEYSTORE_PASSWORD` | keytool에 입력한 store 암호 |
   | `RELEASE_KEY_ALIAS` | `daegu-bus-release` |
   | `RELEASE_KEY_PASSWORD` | keytool에 입력한 key 암호 |
   | `NAVER_MAP_NCP_KEY_ID` | `local.properties`의 값과 동일 |

   값은 로그·커밋·이슈에 남기지 않는다.
