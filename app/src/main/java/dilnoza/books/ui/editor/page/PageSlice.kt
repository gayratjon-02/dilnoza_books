package dilnoza.books.ui.editor.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PagePlan
import dilnoza.books.editor.layout.PageStyles
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class PageColors(val paper: Color, val edge: Color, val number: Color)

/**
 * One piece of the stacked pages, from document y [startPt] to [endPt] (points). Draws the paper of
 * every page that overlaps it, the page edges and the page numbers that fall inside it, and places
 * [content] at [contentTopPt] inside the page's text column.
 */
@Composable
fun PageSlice(
    plan: PagePlan,
    startPt: Float,
    endPt: Float,
    contentTopPt: Float,
    contentHeightPt: Float?,
    firstPageNumber: Int,
    colors: PageColors,
    numberMeasurer: TextMeasurer,
    modifier: Modifier = Modifier,
    contentScale: Float = 1f,
    content: @Composable () -> Unit = {},
) {
    val spec = plan.spec
    val numberStyle = remember(colors.number) { PageStyles.pageNumber().copy(color = colors.number) }
    Box(
        modifier = modifier
            .width(spec.widthPt.dp)
            .height((endPt - startPt).coerceAtLeast(0f).dp)
            .drawBehind { drawPages(plan, startPt, endPt, firstPageNumber, colors, numberMeasurer, numberStyle) },
    ) {
        Box(
            modifier = Modifier
                .padding(start = spec.marginPt.dp, top = contentTopPt.dp)
                .width(spec.columnWidthPt.dp)
                .then(if (contentHeightPt != null) Modifier.requiredHeight(contentHeightPt.dp) else Modifier),
        ) {
            if (contentScale < 1f) ScaledDown(contentScale, content) else content()
        }
    }
}

/**
 * Lays [content] out at the full column width and its natural height, as the paginator measured it,
 * and draws it shrunk by [scale] from the top centre, so it fills exactly the slot planned for it.
 */
@Composable
private fun ScaledDown(scale: Float, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val natural = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(natural) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            ceil((placeables.maxOfOrNull { it.height } ?: 0) * scale).toInt()
        }
        layout(width, height) {
            placeables.forEach { placeable ->
                placeable.placeWithLayer((width - placeable.width) / 2, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
            }
        }
    }
}

private fun DrawScope.drawPages(
    plan: PagePlan,
    startPt: Float,
    endPt: Float,
    firstPageNumber: Int,
    colors: PageColors,
    numberMeasurer: TextMeasurer,
    numberStyle: TextStyle,
) {
    val spec = plan.spec
    val edge = 0.75f
    val firstPage = (startPt / plan.pageStridePt).toInt().coerceAtLeast(0)
    for (page in firstPage until plan.pageCount) {
        val top = plan.pageTop(page)
        val bottom = plan.pageBottom(page)
        if (top >= endPt) break
        val from = max(top, startPt)
        val to = min(bottom, endPt)
        if (to <= from) continue
        val y0 = from - startPt
        val y1 = to - startPt
        // Overlap the neighbouring slices a little: scaled slice edges fall between screen pixels and
        // would otherwise show as hairlines across the page.
        val paperTop = if (from > top) y0 - SEAM_OVERLAP_PT else y0
        val paperBottom = if (to < bottom) y1 + SEAM_OVERLAP_PT else y1
        drawRect(colors.paper, Offset(0f, paperTop), Size(size.width, paperBottom - paperTop))
        drawRect(colors.edge, Offset(0f, paperTop), Size(edge, paperBottom - paperTop))
        drawRect(colors.edge, Offset(size.width - edge, paperTop), Size(edge, paperBottom - paperTop))
        if (top >= startPt) drawRect(colors.edge, Offset(0f, top - startPt), Size(size.width, edge))
        if (bottom <= endPt) drawRect(colors.edge, Offset(0f, bottom - startPt - edge), Size(size.width, edge))

        val numberCenter = bottom - spec.marginPt / 2
        if (numberCenter in startPt..endPt) {
            // In points like the page, whatever density the measurer was made with: 9 pt, never 9 sp.
            val layout = numberMeasurer.measure(
                text = (firstPageNumber + page).toString(),
                style = numberStyle,
                constraints = Constraints(maxWidth = size.width.toInt()),
                density = PageMeasurer.PointDensity,
            )
            drawText(
                layout,
                topLeft = Offset(
                    (size.width - layout.size.width) / 2f,
                    numberCenter - startPt - layout.size.height / 2f,
                ),
            )
        }
    }
}

private const val SEAM_OVERLAP_PT = 1f
