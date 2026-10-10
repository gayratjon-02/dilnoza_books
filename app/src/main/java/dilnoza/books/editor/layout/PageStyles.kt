package dilnoza.books.editor.layout

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dilnoza.books.core.ui.theme.BookSerif
import dilnoza.books.domain.model.TextAlignment

/**
 * Text styles and block geometry of page content. Sizes are points rendered as sp at a density where
 * 1sp == 1pt, so the editor (scaled to the screen) and the exporters measure exactly the same lines.
 * The editor must render with these styles, never with theme typography.
 */
object PageStyles {
    const val LIST_INDENT_PT = 18f
    const val LIST_LABEL_WIDTH_PT = 18f
    const val QUOTE_INDENT_PT = 14f
    const val QUOTE_BAR_WIDTH_PT = 2f
    const val TABLE_CELL_PADDING_PT = 4f
    const val TABLE_BORDER_PT = 0.5f
    const val DIVIDER_HEIGHT_PT = 14f
    const val CAPTION_GAP_PT = 4f
    const val BLOCK_GAP_PT = 8f
    const val LIST_ITEM_GAP_PT = 2f

    private val trimNone = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    private fun base(sizePt: Float, align: TextAlignment) = TextStyle(
        fontFamily = BookSerif,
        fontSize = sizePt.sp,
        lineHeight = (sizePt * PageTypography.LINE_HEIGHT).sp,
        lineHeightStyle = trimNone,
        textAlign = align.toTextAlign(),
    )

    fun body(align: TextAlignment = TextAlignment.START) = base(PageTypography.BODY_SIZE_PT, align)

    fun heading(level: Int, align: TextAlignment = TextAlignment.START) =
        base(PageTypography.headingSizePt(level), align).copy(fontWeight = FontWeight.Bold)

    fun quote(align: TextAlignment = TextAlignment.START) = body(align).copy(fontStyle = FontStyle.Italic)

    fun caption() = base(PageTypography.CAPTION_SIZE_PT, TextAlignment.CENTER).copy(fontStyle = FontStyle.Italic)

    fun tableCell(header: Boolean) = body().copy(fontWeight = if (header) FontWeight.Bold else null)

    fun pageNumber() = base(PageTypography.PAGE_NUMBER_SIZE_PT, TextAlignment.CENTER)

    private fun TextAlignment.toTextAlign() = when (this) {
        TextAlignment.START -> TextAlign.Start
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.END -> TextAlign.End
        TextAlignment.JUSTIFY -> TextAlign.Justify
    }
}
