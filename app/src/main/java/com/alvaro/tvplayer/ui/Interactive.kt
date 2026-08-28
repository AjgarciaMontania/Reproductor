package com.alvaro.tvplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Elemento pulsable que funciona con CUALQUIER entrada:
 *
 *  - Toque en pantalla (celular / tablet)
 *  - Cursor tipo mouse (mandos "air-mouse" de muchos TV Box)
 *  - D-pad del control remoto (flechas + OK)
 *
 * Los componentes de androidx.tv.material3 solo reaccionan al foco y al D-pad,
 * asi que no servian fuera del televisor. Modifier.combinedClickable de Compose
 * foundation cubre los tres casos: gestiona el toque y, cuando el elemento tiene
 * el foco, tambien responde a las teclas OK / Enter / DPAD_CENTER.
 *
 * El parametro `focused` que recibe el contenido permite resaltar visualmente
 * el elemento seleccionado, imprescindible para navegar con el control remoto.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCorner12,
    containerColor: Color = Surface1,
    focusedContainerColor: Color = Surface2,
    focusedScale: Float = 1f,
    enabled: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // "Activo" = con foco (control remoto) o pulsado (dedo / cursor)
    val active = focused || pressed

    val scale by animateFloatAsState(
        targetValue = if (active) focusedScale else 1f,
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (active) focusedContainerColor else containerColor, shape)
    ) {
        content(active)
    }
}
