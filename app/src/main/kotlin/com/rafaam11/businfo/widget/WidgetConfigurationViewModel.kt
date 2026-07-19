package com.rafaam11.businfo.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WidgetConfigurationUiState(
    val bindingFavoriteId: FavoriteStopId? = null,
    val completedFavoriteId: FavoriteStopId? = null,
    val errorMessage: String? = null,
) {
    val isBinding: Boolean get() = bindingFavoriteId != null
}

class WidgetConfigurationViewModel(
    private val bindFavorite: suspend (FavoriteStop) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WidgetConfigurationUiState())
    val uiState: StateFlow<WidgetConfigurationUiState> = _uiState.asStateFlow()

    fun choose(favorite: FavoriteStop) {
        if (_uiState.value.isBinding) return
        _uiState.value = WidgetConfigurationUiState(bindingFavoriteId = favorite.id)
        viewModelScope.launch(dispatcher) {
            runCatching { bindFavorite(favorite) }
                .onSuccess {
                    _uiState.value = WidgetConfigurationUiState(completedFavoriteId = favorite.id)
                }
                .onFailure {
                    _uiState.value = WidgetConfigurationUiState(
                        errorMessage = "위젯을 연결하지 못했습니다. 다시 시도해 주세요",
                    )
                }
        }
    }

    fun consumeCompletion() {
        _uiState.value = _uiState.value.copy(completedFavoriteId = null)
    }
}
