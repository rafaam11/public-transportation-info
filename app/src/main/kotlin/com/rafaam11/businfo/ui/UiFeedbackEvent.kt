package com.rafaam11.businfo.ui

sealed interface UiFeedbackEvent {
    val id: Long
    val message: String

    data class Notice(
        override val id: Long,
        override val message: String,
    ) : UiFeedbackEvent

    data class FavoriteRemoved(
        override val id: Long,
        override val message: String,
        val stopName: String,
    ) : UiFeedbackEvent
}
