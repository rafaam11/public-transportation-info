package com.rafaam11.businfo.ui

import com.rafaam11.businfo.domain.BusDataError

fun BusDataError.userMessage(): String = when (this) {
    BusDataError.InvalidCredential -> "API 키를 확인할 수 없습니다. 키를 다시 입력해 주세요."
    BusDataError.NetworkUnavailable -> "네트워크에 연결할 수 없습니다. 마지막 정상 데이터는 유지됩니다."
    BusDataError.ServiceUnavailable -> "버스 정보 서비스를 사용할 수 없습니다. 잠시 후 새로고침해 주세요."
    BusDataError.MalformedResponse -> "버스 정보를 읽을 수 없습니다. 잠시 후 새로고침해 주세요."
    BusDataError.RateLimited -> "요청 한도를 초과했습니다. 잠시 기다린 뒤 새로고침해 주세요."
}
