# 대구 버스 정보

## 네이버 지도 로컬 설정

NAVER Cloud Maps 애플리케이션에서 Dynamic Map을 활성화하고 Android 패키지
`com.rafaam11.businfo`를 등록한다. 발급된 NCP Key ID는 Git에 넣지 않고 프로젝트 루트의
ignored `local.properties`에 다음 이름으로만 저장한다.

`NAVER_MAP_NCP_KEY_ID=<발급된 Key ID>`

값이 없으면 APK는 빌드되지만 실기기 지도 인증은 실패 상태로 표시된다.

## 실기기 APK 인수 테스트

테스트에는 공공데이터포털에서 발급받은 `대구광역시_대구버스정보시스템` 일반 인증키의 **Decoding 값**, NAVER Maps NCP Key ID, Android 실기기가 필요하다. 두 키를 명령줄, 스크린샷, 이슈, 로그 또는 Git에 남기지 않는다.

1. `NAVER_MAP_NCP_KEY_ID`가 설정된 상태로 debug APK를 빌드하고, USB 디버깅이 연결된 기기에 앱 데이터를 유지하는 `adb install -r`로 설치한다.

   ```powershell
   .\gradlew.bat :app:assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

2. 급행8-1 카드에서 실시간 지도를 열고 저장한 방향의 노선·정류소·차량만 나타나는지 확인한다. 차량은 급행 공식 색상 `#FF4917`의 옆모습 버스이고, 각 차량 안에 전체 노선번호가 표시되어야 한다.
3. 링크 기반 노선선이 도로망을 따라가고 불연속 구간을 임의의 직선으로 가로지르지 않는지 확인한다.
4. 차량 위치와 수신 시각이 약 8초 간격으로 갱신되는지 확인한다. 새 응답 사이에 마커가 추정 이동하지 않아야 한다.
5. 지도를 직접 이동한 뒤 일반 갱신에서 카메라가 초기화되지 않는지, `노선 전체 보기`를 누르면 전체 노선으로 다시 맞춰지는지 확인한다.
6. 네트워크를 끄고 15초 이후 지연 표시, 30초 이후 차량 마커 숨김을 확인한다. 마지막으로 확인된 위치를 현재 위치처럼 계속 보여서는 안 된다.
7. 앱을 백그라운드로 보낸 뒤 `getPos02` 호출이 멈추고, 다시 열면 즉시 갱신되는지 확인한다.
8. NAVER 지도 인증 오류가 대구 버스 API 키 오류와 구분되어 표시되는지 확인한다.
9. 런처에서 새 버스 아이콘을 확인한다. 출근·퇴근 위젯을 각각 추가한 뒤 저장된 도착 정보, 수동 새로고침, 네트워크 실패 시 마지막 값 유지, 카드 탭의 해당 실시간 지도 이동을 확인한다. 위젯은 자동 주기 요청을 하지 않는다. API 키 오류의 `API 키 변경`은 앱 내부의 비공개 진입점을 통해서만 열리며, 새 키 검증이 성공하기 전에는 기존 키를 삭제하거나 교체하지 않는다.

연결형 Android 테스트는 앱 데이터가 교체될 수 있으므로 별도 승인 없이 실행하지 않는다. 단위 테스트와 Android 테스트 컴파일은 다음 명령으로 실행할 수 있다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```
