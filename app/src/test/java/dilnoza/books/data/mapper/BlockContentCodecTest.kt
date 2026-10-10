package dilnoza.books.data.mapper

import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import dilnoza.books.domain.model.TextAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockContentCodecTest {

    @Test
    fun `paragraph survives round trip`() {
        val content = BlockContent.Paragraph(RichText("Salom, dunyo!\nIkkinchi qator «qo‘shtirnoq»"), TextAlignment.JUSTIFY)

        assertEquals(content, BlockContentCodec.decode(BlockContentCodec.encode(content)))
    }

    @Test
    fun `heading survives round trip`() {
        val content = BlockContent.Heading(level = 2, text = RichText("Birinchi bob"))

        assertEquals(content, BlockContentCodec.decode(BlockContentCodec.encode(content)))
    }

    @Test
    fun `type matches content`() {
        assertEquals("heading", BlockContentCodec.typeOf(BlockContent.Heading(1, RichText.Empty)))
        assertEquals("paragraph", BlockContentCodec.typeOf(BlockContent.Paragraph(RichText.Empty)))
    }

    @Test
    fun `unknown fields are ignored`() {
        val raw = """{"kind":"paragraph","v":3,"text":{"text":"abc","spans":[]},"align":"CENTER","future":true}"""

        assertEquals(BlockContent.Paragraph(RichText("abc"), TextAlignment.CENTER), BlockContentCodec.decode(raw))
    }

    @Test
    fun `invalid values fall back to safe defaults`() {
        val raw = """{"kind":"paragraph","text":{"text":"x"},"align":"DIAGONAL"}"""
        val heading = """{"kind":"heading","level":9,"text":{"text":"h"}}"""

        assertEquals(TextAlignment.START, (BlockContentCodec.decode(raw) as BlockContent.Paragraph).align)
        assertEquals(3, (BlockContentCodec.decode(heading) as BlockContent.Heading).level)
    }

    @Test
    fun `image survives round trip`() {
        val content = BlockContent.Image(
            fileName = "a1b2.jpg",
            width = 2048,
            height = 1536,
            caption = "Toshkent, 2026",
            widthPercent = 60,
            align = TextAlignment.END,
        )

        assertEquals("image", BlockContentCodec.typeOf(content))
        assertEquals(content, BlockContentCodec.decode(BlockContentCodec.encode(content)))
    }

    @Test
    fun `image width and size are clamped`() {
        val raw = """{"kind":"image","file":"x.jpg","width":0,"height":-5,"widthPercent":400}"""

        val image = BlockContentCodec.decode(raw) as BlockContent.Image

        assertEquals(100, image.widthPercent)
        assertEquals(1, image.width)
        assertEquals(1, image.height)
        assertEquals(TextAlignment.CENTER, image.align)
    }

    @Test
    fun `styled text survives round trip`() {
        val text = RichText(
            text = "Qalin, qiya va rangli 😀 matn",
            spans = listOf(
                StyleSpan(0, 5, SpanStyleData(bold = true)),
                StyleSpan(7, 11, SpanStyleData(italic = true, underline = true, strikethrough = true)),
                StyleSpan(15, 21, SpanStyleData(color = 0xFFAA0000.toInt(), background = 0x33FFFF00, fontSizeSp = 24)),
                StyleSpan(22, 24, SpanStyleData(fontFamily = "serif")),
            ),
        )
        val content = BlockContent.Paragraph(text, TextAlignment.CENTER)

        assertEquals(content, BlockContentCodec.decode(BlockContentCodec.encode(content)))
        assertEquals(text, (BlockContentCodec.decode(BlockContentCodec.encode(BlockContent.Heading(2, text))) as BlockContent.Heading).text)
    }

    @Test
    fun `unset style fields are not written`() {
        val json = BlockContentCodec.encode(
            BlockContent.Paragraph(RichText("abc", listOf(StyleSpan(0, 1, SpanStyleData(bold = true))))),
        )

        assertTrue(json, json.contains(""""spans":[{"s":0,"e":1,"b":true}]"""))
        assertFalse(json, json.contains("null"))
        assertTrue(json, json.contains(""""v":2"""))
    }

    @Test
    fun `version 1 text without spans still decodes`() {
        val paragraph = """{"kind":"paragraph","v":1,"text":{"text":"Eski matn"},"align":"END"}"""
        val heading = """{"kind":"heading","v":1,"level":2,"text":{"text":"Eski sarlavha"}}"""

        assertEquals(BlockContent.Paragraph(RichText("Eski matn"), TextAlignment.END), BlockContentCodec.decode(paragraph))
        assertEquals(BlockContent.Heading(2, RichText("Eski sarlavha")), BlockContentCodec.decode(heading))
    }

    @Test
    fun `invalid spans are normalized on decode`() {
        val raw = """{"kind":"paragraph","v":2,"text":{"text":"abcdef","spans":[""" +
            """{"s":4,"e":99,"b":true},{"s":-3,"e":2,"i":true},{"s":3,"e":3,"u":true},""" +
            """{"s":0,"e":1},{"s":2,"e":3,"sz":5000,"f":" "}]}}"""

        val text = (BlockContentCodec.decode(raw) as BlockContent.Paragraph).text

        assertEquals(
            listOf(
                StyleSpan(0, 2, SpanStyleData(italic = true)),
                StyleSpan(4, 6, SpanStyleData(bold = true)),
            ),
            text.spans,
        )
    }

    @Test
    fun `every block type survives round trip`() {
        val styled = RichText("Bir ikki", listOf(StyleSpan(4, 8, SpanStyleData(bold = true))))
        val contents = listOf(
            BlockContent.Heading(3, styled, TextAlignment.CENTER),
            BlockContent.Paragraph(styled, TextAlignment.END),
            BlockContent.ListItem(ordered = false, indent = 0, text = styled),
            BlockContent.ListItem(ordered = true, indent = 2, text = styled, align = TextAlignment.JUSTIFY),
            BlockContent.Quote(styled, TextAlignment.CENTER),
            BlockContent.Divider,
            BlockContent.PageBreak,
            BlockContent.Table(
                rows = listOf(listOf(RichText("Ism"), styled), listOf(RichText.Empty, RichText("Oʻzbek"))),
                headerRow = true,
            ),
            BlockContent.Image("a.jpg", 10, 20),
        )

        contents.forEach { assertEquals(it, BlockContentCodec.decode(BlockContentCodec.encode(it))) }
    }

    @Test
    fun `types are stable`() {
        assertEquals("list_item", BlockContentCodec.typeOf(BlockContent.ListItem(true, 0, RichText.Empty)))
        assertEquals("quote", BlockContentCodec.typeOf(BlockContent.Quote(RichText.Empty)))
        assertEquals("divider", BlockContentCodec.typeOf(BlockContent.Divider))
        assertEquals("page_break", BlockContentCodec.typeOf(BlockContent.PageBreak))
        assertEquals("table", BlockContentCodec.typeOf(BlockContent.Table(listOf(listOf(RichText.Empty)))))
    }

    @Test
    fun `list indent is clamped`() {
        val deep = """{"kind":"list_item","ordered":true,"indent":7,"text":{"text":"a"}}"""
        val negative = """{"kind":"list_item","indent":-1,"text":{"text":"b"},"align":"??"}"""

        assertEquals(BlockContent.ListItem(true, 2, RichText("a")), BlockContentCodec.decode(deep))
        assertEquals(BlockContent.ListItem(false, 0, RichText("b")), BlockContentCodec.decode(negative))
    }

    @Test
    fun `table is made rectangular`() {
        val ragged = """{"kind":"table","rows":[[{"text":"a"}],[{"text":"b"},{"text":"c"},{"text":"d"}],[]]}"""
        val empty = """{"kind":"table","rows":[[],[]],"headerRow":true}"""
        val missing = """{"kind":"table"}"""

        val table = BlockContentCodec.decode(ragged) as BlockContent.Table
        assertEquals(
            listOf(
                listOf(RichText("a"), RichText.Empty, RichText.Empty),
                listOf(RichText("b"), RichText("c"), RichText("d")),
                listOf(RichText.Empty, RichText.Empty, RichText.Empty),
            ),
            table.rows,
        )
        assertFalse(table.headerRow)
        assertEquals(BlockContent.Table(listOf(listOf(RichText.Empty)), headerRow = true), BlockContentCodec.decode(empty))
        assertEquals(BlockContent.Table(listOf(listOf(RichText.Empty))), BlockContentCodec.decode(missing))
    }

    @Test
    fun `version 1 heading gets start alignment`() {
        val heading = """{"kind":"heading","v":1,"level":0,"text":{"text":"h"}}"""

        assertEquals(BlockContent.Heading(1, RichText("h"), TextAlignment.START), BlockContentCodec.decode(heading))
    }
}
