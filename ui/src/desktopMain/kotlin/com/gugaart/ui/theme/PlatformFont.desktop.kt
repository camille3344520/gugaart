package com.gugaart.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

internal actual fun platformFont(identity: String, bytes: ByteArray, weight: FontWeight, style: FontStyle, variable: Boolean): Font? =
    if (variable) {
        androidx.compose.ui.text.platform.Font(identity, bytes, weight, style, FontVariation.Settings(FontVariation.weight(weight.weight)))
    } else {
        androidx.compose.ui.text.platform.Font(identity, bytes, weight, style)
    }
