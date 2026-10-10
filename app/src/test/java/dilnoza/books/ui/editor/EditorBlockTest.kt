package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import dilnoza.books.domain.model.TextAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorBlockTest {

    private val styled = RichText(
        text = "Salom, dunyo",
        spans = listOf(
            StyleSpan(0, 5, SpanStyleData(bold = true)),
            StyleSpan(7, 12, SpanStyleData(italic = true, color = 0xFF112233.toInt())),
        ),
    )

    private fun roundTrip(content: BlockContent) {
        val block = Block(id = "b1", chapterId = "c1", position = 2048, content = content)

        assertEquals(block, EditorBlock.from(block).toBlock("c1", 2048))
    }

    @Test
    fun `paragraph keeps spans and alignment`() = roundTrip(BlockContent.Paragraph(styled, TextAlignment.JUSTIFY))

    @Test
    fun `heading keeps spans`() = roundTrip(BlockContent.Heading(1, styled))

    @Test
    fun `image keeps caption and layout`() = roundTrip(
        BlockContent.Image("a.jpg", 800, 600, caption = "Izoh", widthPercent = 50, align = TextAlignment.END),
    )

    @Test
    fun `typing carries styles along`() {
        val block = EditorBlock.from(Block("b1", "c1", 0, BlockContent.Paragraph(styled)))

        block.setValue(TextFieldValue("Salomlar, dunyo", TextRange(8)))

        assertEquals(
            listOf(
                StyleSpan(0, 8, SpanStyleData(bold = true)),
                StyleSpan(10, 15, SpanStyleData(italic = true, color = 0xFF112233.toInt())),
            ),
            block.rich.spans,
        )
        assertEquals(block.value.text, block.rich.text)
    }

    @Test
    fun `typing style applies to inserted text`() {
        val block = EditorBlock("b1", BlockKind.PARAGRAPH, RichText("ab"))

        block.setValue(TextFieldValue("abc", TextRange(3)), SpanStyleData(underline = true))

        assertEquals(listOf(StyleSpan(2, 3, SpanStyleData(underline = true))), block.rich.spans)
    }
}
