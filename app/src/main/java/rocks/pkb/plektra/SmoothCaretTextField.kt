package rocks.pkb.plektra

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SmoothCaretTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier
) {
    val caretX = remember { Animatable(0f) }
    val caretTop = remember { Animatable(0f) }
    val caretBottom = remember { Animatable(0f) }
    var cursorTarget by remember { mutableStateOf(Rect.Zero) }
    var focused by remember { mutableStateOf(false) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var caretInitialized by remember { mutableStateOf(false) }
    val caretColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(value.selection, layoutResult) {
        val layout = layoutResult ?: return@LaunchedEffect
        val index = value.selection.end.coerceIn(0, value.text.length)
        cursorTarget = layout.getCursorRect(index)
    }
    LaunchedEffect(cursorTarget) {
        if (cursorTarget == Rect.Zero) return@LaunchedEffect
        val motion = spring<Float>(dampingRatio = 0.78f, stiffness = 620f)
        if (!caretInitialized) {
            caretX.snapTo(cursorTarget.left)
            caretTop.snapTo(cursorTarget.top)
            caretBottom.snapTo(cursorTarget.bottom)
            caretInitialized = true
        } else {
            coroutineScope {
                launch { caretX.animateTo(cursorTarget.left, motion) }
                launch { caretTop.animateTo(cursorTarget.top, motion) }
                launch { caretBottom.animateTo(cursorTarget.bottom, motion) }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .drawWithContent {
                    drawContent()
                    if (focused && value.selection.collapsed && cursorTarget != Rect.Zero) {
                        drawRoundRect(
                            color = caretColor,
                            topLeft = androidx.compose.ui.geometry.Offset(caretX.value, caretTop.value),
                            size = androidx.compose.ui.geometry.Size(2.dp.toPx(), caretBottom.value - caretTop.value),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                        )
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            keyboardOptions = keyboardOptions,
            minLines = 1,
            maxLines = 2,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
            onTextLayout = { layoutResult = it }
        )
    }
}
