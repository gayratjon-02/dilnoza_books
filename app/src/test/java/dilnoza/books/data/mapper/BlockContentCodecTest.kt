package dilnoza.books.data.mapper

import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.TextAlignment
import org.junit.Assert.assertEquals
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
}
