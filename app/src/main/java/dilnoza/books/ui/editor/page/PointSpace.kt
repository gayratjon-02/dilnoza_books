package dilnoza.books.ui.editor.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
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
    val screenDensity = LocalDensity.current
    Layout(
        modifier = modifier,
        content = {
            CompositionLocalProvider(
                LocalDensity provides PageMeasurer.PointDensity,
                LocalScreenDensity provides screenDensity,
                LocalPageScale provides scale,
                content = content,
            )
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

/** The real screen density, for UI inside [PointSpace] that must keep its normal size (popups, menus). */
val LocalScreenDensity = staticCompositionLocalOf<Density?> { null }

/** Screen pixels per page point inside [PointSpace]; 1 outside it. */
val LocalPageScale = staticCompositionLocalOf { 1f }

/** Restores normal screen sizes for [content] placed inside [PointSpace], e.g. a popup's body. */
@Composable
fun ScreenDensity(content: @Composable () -> Unit) {
    val density = LocalScreenDensity.current ?: LocalDensity.current
    CompositionLocalProvider(LocalDensity provides density, LocalPageScale provides 1f, content = content)
}

/** Normal-sized UI (buttons, labels) placed inside the scaled page list. */
@Composable
fun NormalSize(content: @Composable () -> Unit) {
    val screen = LocalScreenDensity.current ?: LocalDensity.current
    val scale = LocalPageScale.current
    val viewConfiguration = LocalViewConfiguration.current
    // Pointer positions here are in page points too, so the touch slop is scaled to stay the same on screen.
    val scaledConfiguration = remember(viewConfiguration, scale) {
        if (scale == 1f) viewConfiguration else ScaledViewConfiguration(viewConfiguration, scale)
    }
    CompositionLocalProvider(
        LocalDensity provides Density(screen.density / scale, screen.fontScale),
        LocalViewConfiguration provides scaledConfiguration,
        content = content,
    )
}

private class ScaledViewConfiguration(private val base: ViewConfiguration, private val scale: Float) : ViewConfiguration by base {
    override val touchSlop: Float get() = base.touchSlop / scale
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
