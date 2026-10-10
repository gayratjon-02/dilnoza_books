package dilnoza.books.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dilnoza.books.R
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.editor.layout.PageStyles
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ImageBlockView(
    block: EditorBlock,
    file: File?,
    selected: Boolean,
    onSelect: () -> Unit,
    onResize: (Int) -> Unit,
    onCaptionChange: (TextFieldValue) -> Unit,
    onCaptionFocused: () -> Unit,
) {
    val image = block.image ?: return
    val horizontal = when (image.align) {
        TextAlignment.START -> Alignment.Start
        TextAlignment.END -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val fullWidthPx = constraints.maxWidth.toFloat()
        // Whole points, as the paginator measures the picture and its caption.
        val imageWidth = with(LocalDensity.current) { PageStyles.imageWidthPt(constraints.maxWidth, image.widthPercent).toDp() }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = horizontal) {
            Box(
                modifier = Modifier
                    .width(imageWidth)
                    .aspectRatio(image.aspectRatio),
            ) {
                val shape = RoundedCornerShape(6.dp)
                if (image.fileName == null || file == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    AsyncImage(
                        model = file,
                        contentDescription = block.value.text.ifBlank { null },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSelect,
                            ),
                    )
                }
                if (selected && image.fileName != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(2.dp, MaterialTheme.colorScheme.primary, shape),
                    )
                    ResizeHandle(
                        align = image.align,
                        widthPercent = image.widthPercent,
                        fullWidthPx = fullWidthPx,
                        onResize = onResize,
                        modifier = Modifier.align(
                            if (image.align == TextAlignment.END) Alignment.BottomStart else Alignment.BottomEnd,
                        ),
                    )
                }
            }
            if (selected || block.value.text.isNotEmpty()) {
                CaptionField(
                    block = block,
                    onValueChange = onCaptionChange,
                    onFocused = onCaptionFocused,
                    modifier = Modifier.width(imageWidth),
                )
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    align: TextAlignment,
    widthPercent: Int,
    fullWidthPx: Float,
    onResize: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPercent by rememberUpdatedState(widthPercent)
    val currentOnResize by rememberUpdatedState(onResize)
    val density = LocalDensity.current
    val handleOffset = with(density) { 12.dp.roundToPx() }
    // A centred image grows on both sides, so the finger moves half as far as the width changes.
    val factor = when (align) {
        TextAlignment.START -> 1f
        TextAlignment.END -> -1f
        else -> 2f
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = if (align == TextAlignment.END) -handleOffset else handleOffset,
                    y = handleOffset,
                )
            }
            .size(32.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .pointerInput(fullWidthPx, factor) {
                var widthPx = 0f
                detectDragGestures(
                    onDragStart = { widthPx = fullWidthPx * currentPercent / 100f },
                ) { change, drag ->
                    change.consume()
                    widthPx += drag.x * factor
                    currentOnResize((widthPx / fullWidthPx * 100f).roundToInt())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.OpenInFull,
            contentDescription = stringResource(R.string.editor_image_resize),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (align == TextAlignment.END) 90f else 0f),
        )
    }
}

@Composable
private fun CaptionField(
    block: EditorBlock,
    onValueChange: (TextFieldValue) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same caption style and gap the paginator measures, so the picture keeps its place on the page.
    val style = PageStyles.caption().copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    BasicTextField(
        value = block.value,
        onValueChange = onValueChange,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier
            .padding(top = PageStyles.CAPTION_GAP_PT.dp)
            .focusRequester(block.focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (block.value.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_image_caption_hint),
                        style = style.copy(color = style.color.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                inner()
            }
        },
    )
}
