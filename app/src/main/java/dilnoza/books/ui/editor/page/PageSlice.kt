package dilnoza.books.ui.editor.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dilnoza.books.editor.layout.PagePlan
import dilnoza.books.editor.layout.PageStyles
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
    content: @Composable () -> Unit = {},
) {
    val spec = plan.spec
    Box(
        modifier = modifier
            .width(spec.widthPt.dp)
            .height((endPt - startPt).coerceAtLeast(0f).dp)
            .drawBehind { drawPages(plan, startPt, endPt, firstPageNumber, colors, numberMeasurer) },
    ) {
        Box(
            modifier = Modifier
                .padding(start = spec.marginPt.dp, top = contentTopPt.dp)
                .width(spec.contentWidthPt.dp)
                .then(if (contentHeightPt != null) Modifier.requiredHeight(contentHeightPt.dp) else Modifier),
        ) {
            content()
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
            val layout = numberMeasurer.measure(
                text = (firstPageNumber + page).toString(),
                style = PageStyles.pageNumber().copy(color = colors.number),
                constraints = Constraints(maxWidth = size.width.toInt()),
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
