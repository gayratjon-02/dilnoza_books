package dilnoza.books.ui.editor

import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.POSITION_STEP
import dilnoza.books.domain.model.RichText
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockPositionsTest {

    private fun paragraph(id: String) = EditorBlock(id, BlockKind.PARAGRAPH, RichText(id))

    private fun pending(id: String) = EditorBlock(id, BlockKind.IMAGE, image = ImageState.Pending)

    private fun image(id: String) = EditorBlock(id, BlockKind.IMAGE, image = ImageState("$id.jpg", 4, 3))

    private fun positions(blocks: List<EditorBlock>, ids: Set<String>) =
        savedBlocks(blocks, ids, "c1").associate { it.id to it.position }

    @Test
    fun `a picture still importing does not shift the blocks after it`() {
        val blocks = listOf(paragraph("a"), pending("p"), paragraph("b"), paragraph("c"))

        assertEquals(mapOf("b" to 2 * POSITION_STEP), positions(blocks, setOf("b")))
        assertEquals(
            mapOf("a" to POSITION_STEP, "b" to 2 * POSITION_STEP, "c" to 3 * POSITION_STEP),
            positions(blocks, blocks.map { it.id }.toSet()),
        )
    }

    @Test
    fun `an imported picture takes its place`() {
        val blocks = listOf(paragraph("a"), image("p"), paragraph("b"))

        assertEquals(
            mapOf("a" to POSITION_STEP, "p" to 2 * POSITION_STEP, "b" to 3 * POSITION_STEP),
            positions(blocks, blocks.map { it.id }.toSet()),
        )
    }

    private fun stored(vararg blocks: Pair<String, Int>) =
        blocks.map { (id, position) -> Block(id, "c1", position, BlockContent.Paragraph(RichText(id))) }

    @Test
    fun `a picture saved on its own goes right after its stored neighbour`() {
        val stored = stored("a" to 1024, "b" to 2048, "c" to 3072)

        assertEquals(1536, positionAmong(stored, before = listOf("a"), after = listOf("b", "c")))
    }

    @Test
    fun `neighbours deleted since are skipped`() {
        val stored = stored("a" to 1024, "c" to 2048)

        assertEquals(1536, positionAmong(stored, before = listOf("b", "a"), after = listOf("c")))
        assertEquals(0, positionAmong(stored, before = listOf("x"), after = listOf("y", "a")))
    }

    @Test
    fun `with no stored neighbour the picture goes last`() {
        assertEquals(3072, positionAmong(stored("a" to 1024, "b" to 2048), before = listOf("x"), after = emptyList()))
        assertEquals(POSITION_STEP, positionAmong(emptyList(), before = emptyList(), after = emptyList()))
    }
}
