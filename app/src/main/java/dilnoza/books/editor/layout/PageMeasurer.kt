package dilnoza.books.editor.layout

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import kotlin.math.ceil
import kotlin.math.max

/**
 * Turns blocks into [FlowItem]s by measuring them with the bundled font in page points.
 * [textMeasurer] must be created with [PointDensity] so that 1sp == 1px == 1pt.
 * [styled] converts rich text to the same annotated string the editor draws (spans change widths).
 */
class PageMeasurer(
    private val textMeasurer: TextMeasurer,
    private val spec: PageSpec,
    private val styled: (RichText) -> AnnotatedString,
) {
    fun measure(blocks: List<Block>): List<FlowItem> = blocks.map { measure(it.id, it.content) }

    fun measureTitle(title: String): FlowItem.Text =
        text(TITLE_ID, RichText(title), PageStyles.title(), spec.contentWidthPt)
            .copy(spaceAfterPt = PageTypography.TITLE_SPACE_AFTER_PT, keepWithNext = true)

    fun measure(id: String, content: BlockContent): FlowItem = when (content) {
        is BlockContent.Paragraph -> text(id, content.text, textStyle(content), spec.contentWidthPt)
            .copy(spaceAfterPt = PageTypography.PARAGRAPH_SPACING_PT)

        is BlockContent.Heading -> text(id, content.text, textStyle(content), spec.contentWidthPt).copy(
            spaceBeforePt = PageTypography.HEADING_SPACE_BEFORE_PT,
            spaceAfterPt = PageTypography.HEADING_SPACE_AFTER_PT,
            keepWithNext = true,
        )

        is BlockContent.ListItem -> text(id, content.text, textStyle(content), spec.contentWidthPt - listInset(content.indent))
            .copy(spaceAfterPt = PageStyles.LIST_ITEM_GAP_PT)

        is BlockContent.Quote -> text(id, content.text, textStyle(content), spec.contentWidthPt - PageStyles.QUOTE_INDENT_PT)
            .copy(spaceBeforePt = PageTypography.PARAGRAPH_SPACING_PT, spaceAfterPt = PageTypography.PARAGRAPH_SPACING_PT)

        is BlockContent.Image -> FlowItem.Box(
            blockId = id,
            heightPt = imageHeight(content),
            spaceBeforePt = PageStyles.BLOCK_GAP_PT,
            spaceAfterPt = PageStyles.BLOCK_GAP_PT,
        )

        is BlockContent.Table -> FlowItem.Box(
            blockId = id,
            heightPt = tableHeight(content),
            spaceBeforePt = PageStyles.BLOCK_GAP_PT,
            spaceAfterPt = PageStyles.BLOCK_GAP_PT,
        )

        BlockContent.Divider -> FlowItem.Box(id, PageStyles.DIVIDER_HEIGHT_PT)
        BlockContent.PageBreak -> FlowItem.Break(id)
    }

    private fun textStyle(content: BlockContent): TextStyle = checkNotNull(PageStyles.forContent(content))

    /** Picture height at its width share of the content box, plus the caption below it. */
    fun imageHeight(image: BlockContent.Image): Float {
        val width = spec.contentWidthPt * image.widthPercent / 100f
        val pictureHeight = width * image.height / max(1, image.width)
        if (image.caption.isBlank()) return pictureHeight
        val caption = layout(AnnotatedString(image.caption), PageStyles.caption(), width)
        return pictureHeight + PageStyles.CAPTION_GAP_PT + caption.size.height
    }

    /** Columns share the content width equally; each row is as tall as its tallest cell. */
    fun tableHeight(table: BlockContent.Table): Float {
        val columns = max(1, table.rows.maxOfOrNull { it.size } ?: 1)
        val cellWidth = spec.contentWidthPt / columns - 2 * PageStyles.TABLE_CELL_PADDING_PT
        return table.rows.withIndex().sumOf { (index, row) ->
            val style = PageStyles.tableCell(header = table.headerRow && index == 0)
            val tallest = row.maxOfOrNull { layout(styled(it), style, cellWidth).size.height } ?: 0
            (tallest + 2 * PageStyles.TABLE_CELL_PADDING_PT).toDouble()
        }.toFloat()
    }

    private fun text(id: String, rich: RichText, style: TextStyle, widthPt: Float): FlowItem.Text {
        val result = layout(styled(rich), style, widthPt)
        val lines = (0 until result.lineCount).map { result.getLineBottom(it) - result.getLineTop(it) }
        return FlowItem.Text(id, lines, lineStarts = (0 until result.lineCount).map(result::getLineStart))
    }

    private fun layout(text: AnnotatedString, style: TextStyle, widthPt: Float): TextLayoutResult =
        textMeasurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = max(1, ceil(widthPt).toInt())),
        )

    companion object {
        const val TITLE_ID = "chapter-title"

        fun listInset(indent: Int): Float = indent * PageStyles.LIST_INDENT_PT + PageStyles.LIST_LABEL_WIDTH_PT

        /** 1dp == 1sp == 1px: measurements come out directly in points. */
        val PointDensity = Density(density = 1f, fontScale = 1f)
    }
}
