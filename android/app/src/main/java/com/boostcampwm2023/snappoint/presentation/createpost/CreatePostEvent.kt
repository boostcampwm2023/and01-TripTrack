package com.boostcampwm2023.snappoint.presentation.createpost

sealed class CreatePostEvent {
    data class ShowMessage(val resId: Int): CreatePostEvent()
}