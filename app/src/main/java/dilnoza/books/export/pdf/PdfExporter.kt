package dilnoza.books.export.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import dilnoza.books.core.ui.theme.PrintColors
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.Book
import dilnoza.books.domain.model.Chapter
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.editor.commands.ListItem
import dilnoza.books.editor.commands.ListKind
import dilnoza.books.editor.commands.ListNumbering
import dilnoza.books.editor.layout.Fragment
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.editor.layout.PageStyles
import dilnoza.books.editor.layout.Paginator
import dilnoza.books.ui.editor.toAnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** A chapter with its blocks, in book order. */
data class ExportChapter(val chapter: Chapter, val blocks: List<Block>)

/**
 * Writes a book as PDF with the editor's own measurer and paginator, so every line, page break and
 * page number falls exactly where the writer saw it. The page canvas is in points, like the layout.
 */
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun export(
        book: Book,
        chapters: List<ExportChapter>,
        coverFile: File?,
        imageFile: (String) -> File,
        out: OutputStream,
        progress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.Default) {
        val spec = PageSpec.of(book.pageSize, book.marginMm)
        val textMeasurer = TextMeasurer(createFontFamilyResolver(context), PageMeasurer.PointDensity, LayoutDirection.Ltr)
        val measurer = PageMeasurer(textMeasurer, spec) { it.toAnnotatedString() }
        val pdf = PdfDocument()
        try {
            val painter = PagePainter(spec, textMeasurer, imageFile)
            var pageNumber = 0
            fun newPage(draw: DrawScope.() -> Unit) {
                val info = PdfDocument.PageInfo.Builder(spec.widthPt.roundToInt(), spec.heightPt.roundToInt(), pdf.pages.size + 1).create()
                val page = pdf.startPage(info)
                CanvasDrawScope().draw(PageMeasurer.PointDensity, LayoutDirection.Ltr, Canvas(page.canvas), Size(spec.widthPt, spec.heightPt), draw)
                pdf.finishPage(page)
            }

            // The front pages are not numbered, so chapter pages carry the numbers shown in the editor.
            if (coverFile != null && coverFile.exists()) newPage { painter.drawCover(this, coverFile) }
            newPage { painter.drawTitlePage(this, book) }

            chapters.forEachIndexed { index, (chapter, blocks) ->
                coroutineContext.ensureActive()
                val items = listOf(measurer.measureTitle(chapter.title)) + measurer.measure(blocks)
                val pagination = Paginator(spec.contentHeightPt).paginate(items)
                val labels = listLabels(blocks)
                val byPage = pagination.fragments.groupBy { it.pageIndex }
                val contents = blocks.associate { it.id to it.content }
                for (page in 0 until pagination.pageCount) {
                    pageNumber++
                    newPage {
                        for (fragment in byPage[page].orEmpty()) {
                            if (fragment.blockId == PageMeasurer.TITLE_ID) {
                                painter.drawText(this, fragment, RichText(chapter.title), PageStyles.title(), 0f)
                            } else {
                                contents[fragment.blockId]?.let { painter.drawBlock(this, fragment, it, labels[fragment.blockId]) }
                            }
                        }
                        painter.drawPageNumber(this, pageNumber)
                    }
                }
                progress((index + 1f) / chapters.size)
            }
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private fun listLabels(blocks: List<Block>): Map<String, String> {
        val items = blocks.map { block ->
            val content = block.content
            if (content is BlockContent.ListItem) {
                ListItem(if (content.ordered) ListKind.ORDERED else ListKind.BULLET, content.indent)
            } else {
                ListItem.NONE
            }
        }
        return blocks.zip(ListNumbering.labels(items)).mapNotNull { (block, label) -> label?.let { block.id to it } }.toMap()
    }
}

/** Draws page content the way the editor's block views draw it, at the plan's positions. */
private class PagePainter(
    private val spec: PageSpec,
    private val textMeasurer: TextMeasurer,
    private val imageFile: (String) -> File,
) {
    private val column = spec.columnWidthPt
    private val left = spec.marginPt

    fun drawBlock(scope: DrawScope, fragment: Fragment, content: BlockContent, listLabel: String?) {
        when (content) {
            is BlockContent.Paragraph, is BlockContent.Heading -> drawText(scope, fragment, content.richText(), style(content), 0f)
            is BlockContent.ListItem -> {
                val inset = PageMeasurer.listInset(content.indent)
                drawText(scope, fragment, content.text, style(content), inset)
                if (listLabel != null && fragment.firstLine == 0) drawListLabel(scope, fragment, content, listLabel)
            }
            is BlockContent.Quote -> {
                scope.drawRect(PrintColors.Accent, Offset(left, top(fragment)), Size(PageStyles.QUOTE_BAR_WIDTH_PT, fragment.heightPt))
                drawText(scope, fragment, content.text, style(content), PageStyles.QUOTE_INDENT_PT)
            }
            is BlockContent.Image -> scaled(scope, fragment) { drawImage(this, content, top(fragment)) }
            is BlockContent.Table -> scaled(scope, fragment) { drawTable(this, content, top(fragment)) }
            BlockContent.Divider -> {
                val y = top(fragment) + fragment.heightPt / 2
                scope.drawLine(PrintColors.Rule, Offset(left + DIVIDER_INSET_PT, y), Offset(left + column - DIVIDER_INSET_PT, y), RULE_WIDTH_PT)
            }
            BlockContent.PageBreak -> Unit
        }
    }

    /** Lines [Fragment.firstLine, Fragment.endLine) of the text, laid out at the editor's width. */
    fun drawText(scope: DrawScope, fragment: Fragment, rich: RichText, style: TextStyle, insetPt: Float) {
        // An empty paragraph only takes its line on the page; there is nothing to draw.
        if (fragment.endLine <= fragment.firstLine || rich.text.isEmpty()) return
        val layout = layout(rich.toAnnotatedString(), style, column - insetPt.toInt())
        val x = left + insetPt
        val y = top(fragment)
        val lineTop = layout.getLineTop(fragment.firstLine)
        // Generous sideways: italics and accents reach past the column a little.
        scope.clipRect(x - CLIP_SLACK_PT, y, x + layout.size.width + CLIP_SLACK_PT, y + fragment.heightPt) {
            drawText(layout, topLeft = Offset(x, y - lineTop))
        }
    }

    private fun drawListLabel(scope: DrawScope, fragment: Fragment, item: BlockContent.ListItem, label: String) {
        val layout = textMeasurer.measure(AnnotatedString(label), style(item).copy(textAlign = TextAlign.End), softWrap = false, maxLines = 1)
        val right = left + item.indent * PageStyles.LIST_INDENT_PT + PageStyles.LIST_LABEL_WIDTH_PT - LIST_LABEL_GAP_PT
        scope.drawText(layout, topLeft = Offset(right - layout.size.width, top(fragment)))
    }

    private fun drawImage(scope: DrawScope, image: BlockContent.Image, y: Float) {
        val width = PageStyles.imageWidthPt(column, image.widthPercent)
        val height = PageStyles.imageHeightPt(width, image.width, image.height)
        val x = left + when (image.align) {
            TextAlignment.START -> 0
            TextAlignment.END -> column - width
            else -> (column - width) / 2
        }
        decode(imageFile(image.fileName), width)?.let { bitmap ->
            scope.drawContext.canvas.nativeCanvas.drawBitmap(bitmap, null, RectF(x, y, x + width, y + height), null)
            bitmap.recycle()
        }
        if (image.caption.isNotEmpty()) {
            val caption = layout(AnnotatedString(image.caption), PageStyles.caption().copy(color = PrintColors.Muted), width)
            scope.drawText(caption, topLeft = Offset(x, y + height + PageStyles.CAPTION_GAP_PT))
        }
    }

    private fun drawTable(scope: DrawScope, table: BlockContent.Table, y: Float) {
        val columns = max(1, table.rows.maxOfOrNull { it.size } ?: 1)
        val columnWidth = PageStyles.tableColumnWidthPt(column, columns)
        val textWidth = PageStyles.tableCellTextWidthPt(columnWidth)
        val padding = PageStyles.TABLE_CELL_PADDING_PT
        var rowTop = y
        table.rows.forEachIndexed { index, row ->
            val header = table.headerRow && index == 0
            val style = PageStyles.tableCell(header)
            val cells = row.map { layout(it.toAnnotatedString(), style, textWidth) }
            val rowHeight = (cells.maxOfOrNull { it.size.height } ?: 0) + 2 * padding
            if (header) scope.drawRect(PrintColors.HeaderFill, Offset(left, rowTop), Size(columnWidth * columns.toFloat(), rowHeight))
            cells.forEachIndexed { c, cell ->
                val cellLeft = left + c * columnWidth
                scope.drawText(cell, topLeft = Offset(cellLeft + padding, rowTop + padding))
                scope.drawRect(PrintColors.Grid, Offset(cellLeft, rowTop), Size(columnWidth.toFloat(), rowHeight), style = stroke)
            }
            rowTop += rowHeight
            if (header) scope.drawLine(PrintColors.Rule, Offset(left, rowTop), Offset(left + columnWidth * columns, rowTop), HEADER_RULE_PT)
        }
    }

    fun drawPageNumber(scope: DrawScope, number: Int) {
        val layout = textMeasurer.measure(AnnotatedString(number.toString()), PageStyles.pageNumber().copy(color = PrintColors.Muted))
        val center = spec.heightPt - spec.marginPt / 2
        scope.drawText(layout, topLeft = Offset((spec.widthPt - layout.size.width) / 2, center - layout.size.height / 2f))
    }

    fun drawTitlePage(scope: DrawScope, book: Book) {
        // A fixed width: with only a maximum the layout shrinks to the text and centring does nothing.
        val width = Constraints.fixedWidth(column)
        val title = textMeasurer.measure(AnnotatedString(book.title), PageStyles.title().copy(textAlign = TextAlign.Center), constraints = width)
        val author = textMeasurer.measure(
            AnnotatedString(book.author),
            PageStyles.heading(3).copy(textAlign = TextAlign.Center, color = PrintColors.Muted),
            constraints = width,
        )
        val y = spec.heightPt / 3
        scope.drawText(title, topLeft = Offset(left, y))
        if (book.author.isNotBlank()) scope.drawText(author, topLeft = Offset(left, y + title.size.height + AUTHOR_GAP_PT))
    }

    /** The cover fills the page, cropped to its proportions. */
    fun drawCover(scope: DrawScope, file: File) {
        val bitmap = decode(file, spec.widthPt.roundToInt()) ?: return
        val pageRatio = spec.widthPt / spec.heightPt
        val crop = if (bitmap.width.toFloat() / bitmap.height > pageRatio) {
            val w = (bitmap.height * pageRatio).roundToInt()
            Rect((bitmap.width - w) / 2, 0, (bitmap.width + w) / 2, bitmap.height)
        } else {
            val h = (bitmap.width / pageRatio).roundToInt()
            Rect(0, (bitmap.height - h) / 2, bitmap.width, (bitmap.height + h) / 2)
        }
        scope.drawContext.canvas.nativeCanvas.drawBitmap(bitmap, crop, RectF(0f, 0f, spec.widthPt, spec.heightPt), null)
        bitmap.recycle()
    }

    private fun scaled(scope: DrawScope, fragment: Fragment, draw: DrawScope.() -> Unit) {
        if (fragment.scale >= 1f) {
            scope.draw()
        } else {
            // As the editor shows a shrunk box: laid out at full size, scaled from its top centre.
            scope.scale(fragment.scale, Offset(left + column / 2f, top(fragment))) { draw() }
        }
    }

    private fun top(fragment: Fragment) = spec.marginPt + fragment.topPt

    private fun layout(text: AnnotatedString, style: TextStyle, widthPt: Int): TextLayoutResult =
        textMeasurer.measure(text, style, constraints = Constraints(maxWidth = max(1, widthPt)))

    private fun style(content: BlockContent): TextStyle = checkNotNull(PageStyles.forContent(content))

    private fun BlockContent.richText(): RichText = when (this) {
        is BlockContent.Paragraph -> text
        is BlockContent.Heading -> text
        else -> RichText("")
    }

    /** Decodes at about [PIXELS_PER_POINT] pixels per point of [widthPt]: sharp in print, small in memory. */
    private fun decode(file: File, widthPt: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPt * PIXELS_PER_POINT) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private val stroke = Stroke(width = PageStyles.TABLE_BORDER_PT)

    private companion object {
        const val RULE_WIDTH_PT = 0.75f
        const val HEADER_RULE_PT = 1.5f
        const val DIVIDER_INSET_PT = 48f
        const val LIST_LABEL_GAP_PT = 5f
        const val CLIP_SLACK_PT = 4f
        const val AUTHOR_GAP_PT = 12f
        const val PIXELS_PER_POINT = 3
    }
}
