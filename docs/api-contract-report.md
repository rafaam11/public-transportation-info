# 대구 버스 API 계약 검증 결과

## 실행 조건

- 검증 일시: 2026-07-16 21:42~21:48 KST, 버스 운행 시간대.
- 데이터: 공공데이터포털 승인 개발계정과 ignored `local.properties`의 Decoding 인증키.
- Base URL: `https://apis.data.go.kr/6270000/dbmsapi02`.
- 검증 노선: 814번, `routeId=3000814001`.
- 검증 정류소: 814번의 실제 위치 응답에 포함된 정류소 ID 하나를 사용했다. 이 문서에는 정류소 ID 값, 차량 식별값, 원문 JSON을 기록하지 않았다.
- 다섯 endpoint 모두 HTTP 200, `header.resultCode=0000`, `header.success=true`를 확인했다.

## Endpoint별 필수 요청 파라미터

| Endpoint | 필수 query parameter | 실응답 확인 |
| --- | --- | --- |
| `getBasic02` | `serviceKey` | 성공 |
| `getLink02` | `serviceKey`, `routeId` | `routeId=3000814001`로 성공 |
| `getBs02` | `serviceKey`, `routeId` | `routeId=3000814001`로 성공 |
| `getPos02` | `serviceKey`, `routeId` | `routeId=3000814001`로 성공 |
| `getRealtime02` | `serviceKey`, `bsId`, `routeNo` | 실제 정류소 ID와 `routeNo=814`로 성공 |

공공데이터포털의 개발계정 간이 표에는 네 상세 endpoint의 변수가 `stdt`로 표시되지만, 로그인된 Swagger와 실호출 결과에 따르면 이는 잘못된 정보다. `stdt` 호출은 HTTP 200 안에서 `resultCode=9003`을 반환했다.

## Endpoint별 응답 필드

- `getBasic02`
  - 공통: `header.resultCode`, `header.resultMsg`, `header.success`, `body.totalCount`.
  - `body.items.route[]`: `routeId`, `routeNo`, `routeNote`, `routeTCd`, `stBsId`, `edBsId`, `stNm`, `edNm`, `dirRouteNote`, `ndirRouteNote`, `dataconnareacd`.
  - `body.items.bs[]`: `bsId`, `bsNm`, `xPos`, `yPos`, `wincId`.
  - `body.items.node[]`: `nodeId`, `nodeNm`, `xPos`, `yPos`, `bsYn`.
  - `body.items.link[]`: `linkId`, `linkNm`, `stNode`, `edNode`, `gisDist`.
- `getLink02`
  - `body.items[]`: `linkId`, `stNode`, `edNode`, `gisDist`, `moveDir`, `linkSeq`.
  - 814번 검증 응답: 258개.
- `getBs02`
  - `body.items[]`: `bsId`, `bsNm`, `xPos`, `yPos`, `moveDir`, `seq`.
  - 814번 검증 응답: 154개.
- `getPos02`
  - `body.items[]`: `routeId`, `routeNo`, `moveDir`, `arTime`, `seq`, `bsId`, `xPos`, `yPos`, `busTCd2`, `busTCd3`, `vhcNo2`.
  - `vhcNo2`는 차량 식별 필드이므로 값은 저장·표시·로그하지 않고 마스킹한다.
  - 814번 검증 응답: 23개.
- `getRealtime02`
  - `body.items[]`: `routeNo`, `arrList`.
  - `body.items[].arrList[]`: `routeId`, `routeNo`, `moveDir`, `bsGap`, `bsNm`, `busTCd2`, `busTCd3`, `busAreaCd`, `arrState`, `prevBsGap`, `arrTime`.
  - Swagger 모델은 `vhcNo2`도 선언하지만 이번 실응답에는 없었다. 값이 존재할 경우 동일하게 마스킹한다.

## getPos02 위치 정밀도 판정

판정: `GPS_COORDINATES`.

- `xPos`와 `yPos`가 차량 레코드마다 실수형 숫자로 존재한다.
- 814번 23개 차량의 관측 범위는 `xPos=128.623136283333..128.8493`, `yPos=35.81502..35.9128`이었다.
- `bsId`, `seq`, `moveDir`도 함께 제공되어 좌표와 노선 진행 상태를 연결할 수 있다.
- 따라서 지도 마커는 `getPos02.xPos/yPos`를 확인 좌표로 사용한다. 링크·정류장 순서만으로 위치를 보간하는 대체 경로는 기본 동작에 필요하지 않다.
- API가 다음 좌표를 제공하기 전에는 임의로 미래 위치를 외삽하지 않는다.

## 오류 및 빈 응답 동작

- 잘못된 parameter 이름 `stdt`를 사용하면 HTTP 상태는 200이지만 JSON header는 `resultCode=9003`, `resultMsg=인증키오류 및 파라미터 오류`, `success=false`다.
- 클라이언트는 HTTP 상태만으로 성공을 판단하면 안 되며 `header.resultCode`와 `header.success`를 함께 확인해야 한다.
- 운행 시간대의 814번 검증에서는 `getPos02` 23개와 `getRealtime02` 1개 노선 항목이 반환되어 빈 응답 동작은 관측되지 않았다.
- 이후 단일 호출에서 빈 `items`가 반환되면 기존 정상 스냅샷을 유지하고 네트워크 실패로 오인하지 않으며, 데이터 신선도를 별도로 표시한다. 단일 빈 응답만으로 이 계약의 `GPS_COORDINATES` 판정을 바꾸지 않는다. 최초 계약 검증에서 운행 시간대 재시도 후에도 차량 항목이 없을 때만 runbook 기준의 `INSUFFICIENT_DATA`로 판정한다.

## 후속 DTO 설계 입력값

- 공통 envelope: `header(resultCode, resultMsg, success)`와 `body(totalCount, items)`.
- 노선: `routeId`, `routeNo`, 방향·기종점·노선 설명 필드.
- 정류소: `bsId`, `bsNm`, `xPos`, `yPos`, `moveDir`, `seq`.
- 차량 위치: `routeId`, `routeNo`, `bsId`, `seq`, `moveDir`, `xPos`, `yPos`, `arTime`, 버스 유형 코드. `vhcNo2`는 민감 필드로 별도 취급한다.
- 도착정보: 요청은 `bsId + routeNo`; 응답은 `arrState`, `arrTime`, `bsGap`, `prevBsGap`, `moveDir`, 노선·버스 유형 필드.
- 노선 형상: `linkId`, `stNode`, `edNode`, `gisDist`, `moveDir`, `linkSeq`; 기초 데이터의 node/link 좌표와 결합한다.
- 실제 JSON에서 `items`는 복수일 때 배열로 관측됐다. 후속 DTO fixture는 배열 응답과 빈 응답을 모두 포함해야 한다.
