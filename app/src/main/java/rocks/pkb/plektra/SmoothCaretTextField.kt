package rocks.pkb.plektra

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
    val scrollState = rememberScrollState()
    var viewportHeight by remember { mutableStateOf(0) }
    val viewportMaxHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2
    }

    LaunchedEffect(value.selection, layoutResult) {
        val layout = layoutResult ?: return@LaunchedEffect
        val index = value.selection.end.coerceIn(0, value.text.length)
        cursorTarget = layout.getCursorRect(index)
    }
    LaunchedEffect(cursorTarget, viewportHeight, scrollState.maxValue, focused) {
        if (!focused || viewportHeight == 0 || cursorTarget == Rect.Zero) return@LaunchedEffect
        val visibleTop = scrollState.value
        val visibleBottom = visibleTop + viewportHeight
        val target = when {
            cursorTarget.top < visibleTop -> cursorTarget.top
            cursorTarget.bottom > visibleBottom -> cursorTarget.bottom - viewportHeight
            else -> return@LaunchedEffect
        }
        scrollState.scrollTo(target.toInt().coerceIn(0, scrollState.maxValue))
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
        // The field and caret share full-text coordinates; the outer viewport scrolls both.
        Box(
            Modifier.fillMaxWidth()
                .heightIn(max = viewportMaxHeight)
                .onSizeChanged { viewportHeight = it.height }
                .verticalScroll(scrollState)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .drawWithContent {
                        drawContent()
                        if (focused && value.selection.collapsed && cursorTarget != Rect.Zero) {
                            clipRect {
                                drawRoundRect(
                                    color = caretColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(caretX.value, caretTop.value),
                                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), caretBottom.value - caretTop.value),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                                )
                            }
                        }
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                keyboardOptions = keyboardOptions,
                minLines = 1,
                maxLines = Int.MAX_VALUE,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
                onTextLayout = { layoutResult = it }
            )
        }
    }
}
