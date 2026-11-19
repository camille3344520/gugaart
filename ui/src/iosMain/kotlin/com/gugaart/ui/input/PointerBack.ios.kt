package com.gugaart.ui.input

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun PointerEvent.isBackButtonRelease(): Boolean = type == PointerEventType.Release && button == PointerButton.Back
