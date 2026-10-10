package dilnoza.books.ui.editor.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import dilnoza.books.editor.layout.PageMeasurer
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Lays [content] out in page points (1dp == 1sp == 1px == 1pt, font scale ignored) and draws it
 * scaled by [scale]. Line breaks are therefore decided in points, exactly as the paginator and the
 * PDF export decide them, whatever the screen size, density or system font size.
 */
@Composable
fun PointSpace(
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            CompositionLocalProvider(LocalDensity provides PageMeasurer.PointDensity, content = content)
        },
    ) { measurables, constraints ->
        val inner = constraints.unscaled(scale)
        val placeables = measurables.map { it.measure(inner) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(ceil(width * scale).toInt(), ceil(height * scale).toInt()) {
            placeables.forEach { placeable ->
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

private fun Constraints.unscaled(scale: Float): Constraints {
    fun Int.down(bounded: Boolean) = if (bounded) floor(this / scale).toInt() else Constraints.Infinity
    return Constraints(
        minWidth = floor(minWidth / scale).toInt(),
        maxWidth = maxWidth.down(hasBoundedWidth),
        minHeight = floor(minHeight / scale).toInt(),
        maxHeight = maxHeight.down(hasBoundedHeight),
    )
}
