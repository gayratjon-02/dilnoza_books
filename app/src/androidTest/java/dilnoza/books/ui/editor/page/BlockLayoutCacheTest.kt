package dilnoza.books.ui.editor.page

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.PageSize
import dilnoza.books.domain.model.RichText
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.ui.editor.toAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class BlockLayoutCacheTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var measurersMade = 0
    private val cache = BlockLayoutCache { spec ->
        measurersMade++
        PageMeasurer(TextMeasurer(createFontFamilyResolver(context), PageMeasurer.PointDensity, LayoutDirection.Ltr), spec) {
            it.toAnnotatedString()
        }
    }
    private val a5 = PageSpec.of(PageSize.A5, 20)
    private val blocks = List(300) { "b$it" to (BlockContent.Paragraph(RichText("Paragraf $it ".repeat(20))) as BlockContent) }

    @Test
    fun primedOffTheMainThreadTheEditorMeasuresNothingAgain() {
        thread { cache.prime(a5, "1-bob", blocks) }.join()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val title = cache.item(a5, PageMeasurer.TITLE_ID, "1-bob") { error("title measured again") }
            assertEquals(PageMeasurer.TITLE_ID, title.blockId)
            for ((id, content) in blocks) cache.item(a5, id, content) { error("$id measured again") }
        }
        assertEquals(1, measurersMade)
    }

    @Test
    fun changedBlocksAndPageSizesAreMeasuredAgain() {
        cache.prime(a5, "1-bob", blocks)
        val (id, content) = blocks.first()
        val edited = BlockContent.Paragraph(RichText("Boshqa matn"))
        var measured = 0
        cache.item(a5, id, edited) { measured++; measure(id, edited) }
        assertEquals(1, measured)

        val a4 = PageSpec.of(PageSize.A4, 20)
        val wide = cache.item(a4, id, content) { measured++; measure(id, content) }
        assertEquals(2, measured)
        assertNotEquals(cache.item(a5, id, content) { measure(id, content) }, wide)
    }
}
