package com.rafaam11.businfo.ui.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapAuthMonitor {
    private val _errorCode = MutableStateFlow<String?>(null)
    val errorCode: StateFlow<String?> = _errorCode.asStateFlow()

    fun report(code: String) {
        _errorCode.value = code
    }

    fun clear() {
        _errorCode.value = null
    }
}
