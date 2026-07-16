# 대구 버스 API 계약 확인 Runbook

1. 공공데이터포털에서 `대구광역시_대구버스정보시스템` 활용 신청이 `승인` 상태인지 확인한다.
2. 저장소 루트의 ignored `local.properties`에 `DAEGU_BUS_SERVICE_KEY=<일반 인증키 Decoding 값>`을 추가한다.
3. `./gradlew.bat :api-probe:run --args="getBasic02"`를 실행한다.
4. `.local/api-probe/reports/getBasic02.md`에서 실제 노선 ID와 정류장 ID 필드 이름을 찾고, raw 파일에서 현재 운행할 가능성이 높은 노선 ID 하나를 고른다.
5. 로그인된 공공데이터포털 Swagger에서 각 endpoint의 필수 파라미터 이름을 그대로 확인한다.
6. 아래 형식으로 나머지 endpoint를 실행한다. `name`은 Swagger의 실제 이름이고 `value`는 기초 응답에서 고른 ID다.

   `./gradlew.bat :api-probe:run --args="getPos02 --param name=value"`

   `./gradlew.bat :api-probe:run --args="getBs02 --param name=value"`

   `./gradlew.bat :api-probe:run --args="getLink02 --param name=value"`

   `./gradlew.bat :api-probe:run --args="getRealtime02 --param name=value"`

7. 모든 Markdown report에서 HTTP 2xx와 JSON field paths를 확인한다. 운행 종료로 빈 응답이면 운행 시간에 다시 실행한다.
8. `getPos02`에서 위도·경도 쌍이 실제 숫자로 존재하면 `GPS_COORDINATES`, 링크·정류장 순서만 존재하면 `ROUTE_SEGMENT_ONLY`, 차량 항목 자체가 없으면 `INSUFFICIENT_DATA`로 판정한다.
9. raw 파일은 커밋하지 않는다. sanitized report에 실제 비밀값이나 차량 번호가 없는지 `rg -n "DAEGU_BUS_SERVICE_KEY=|serviceKey=[A-Za-z0-9%+/]|vehicleNo.*[0-9]{4}|plate.*[0-9]{4}" .local/api-probe/reports`로 검사한다.
10. 다섯 report를 `docs/api-contract-report.md` 하나로 합치고 exact endpoint parameter names, response paths, sample coordinate ranges, 위치 정밀도 판정을 기록한다.

## 실기기 APK 인수 테스트

`app\build\outputs\apk\debug\app-debug.apk`를 실기기에 설치한 뒤 아래 순서로 확인한다. 인증키는 일반 인증키의 **Decoding 값**을 사용하며 명령줄, 스크린샷, 로그 또는 Git에 남기지 않는다.

1. 앱 데이터가 초기화된 상태에서 앱을 열고 `공공데이터 API 키 입력` 화면에 Decoding 키를 입력한 뒤 `키 저장하고 조회`를 누른다.
2. 814번 운행 시간에 `814번 실시간 차량` 목록, 운행 대수, `최신 · N초 전` 상태가 표시되는지 확인한다. 운행 종료 시간의 `현재 운행 차량 없음`은 정상적인 빈 결과이므로 목록 검증은 운행 시간에 다시 수행한다.
3. `새로고침`을 눌러 `조회 중` 표시 후 목록과 경과 시간이 갱신되는지 확인한다.
4. 정상 목록이 표시된 상태에서 Wi-Fi와 모바일 데이터를 모두 끄고 `새로고침`을 누른다. 기존 목록이 유지되고 `네트워크에 연결할 수 없습니다. 마지막 정상 데이터는 유지됩니다.` 배너가 나타나는지 확인한다.
5. 네트워크를 복구하고 `API 키 변경`을 누른 뒤 잘못된 키를 저장한다. `API 키를 확인할 수 없습니다. 키를 다시 입력해 주세요.` 오류를 확인한다.
6. 올바른 Decoding 키를 다시 저장해 814번 목록이 복구되는지 확인한다.

이 인수 테스트 범위에는 지도와 홈 화면 위젯이 포함되지 않는다.
