package dilnoza.books.ui.editor.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.PageSize
import dilnoza.books.domain.model.RichText
import dilnoza.books.editor.layout.FlowItem
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PagePlan
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.editor.layout.PageStyles
import dilnoza.books.editor.layout.Pagination
import dilnoza.books.editor.layout.Paginator
import dilnoza.books.ui.editor.BlockKind
import dilnoza.books.ui.editor.EditorBlock
import dilnoza.books.ui.editor.TableBlockView
import dilnoza.books.ui.editor.TableState
import dilnoza.books.ui.editor.toAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * The paginator plans every block from [PageMeasurer]; the editor then draws it in a [PageSlice]
 * column. Both must break the same text into the same lines and give boxes the same height, or
 * content overflows its planned slot.
 */
@RunWith(AndroidJUnit4::class)
class PageLayoutMatchTest {

    @get:Rule
    val rule = createComposeRule()

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    @Before
    fun setUp() {
        rule.setContent { screen?.invoke() }
    }

    /** Shows [content] in page points, each item in its own page column like the editor's block items. */
    private fun show(content: @Composable () -> Unit) {
        rule.runOnIdle { screen = { PointSpace(scale = 1f) { Column(Modifier.wrapContentHeight(unbounded = true)) { content() } } } }
        rule.waitForIdle()
    }

    @Test
    fun paragraphsWrapLikeThePlanOnEveryPageSize() {
        for (spec in SPECS) {
            assertSameLines(spec, PageStyles.body(), insetPt = 0) { BlockContent.Paragraph(RichText(it)) }
        }
    }

    @Test
    fun quotesAndListItemsWrapLikeThePlan() {
        val spec = SPECS.first()
        assertSameLines(spec, PageStyles.quote(), insetPt = PageStyles.QUOTE_INDENT_PT.toInt()) {
            BlockContent.Quote(RichText(it))
        }
        assertSameLines(spec, PageStyles.body(), insetPt = PageMeasurer.listInset(0).toInt()) {
            BlockContent.ListItem(ordered = false, indent = 0, text = RichText(it))
        }
    }

    @Test
    fun tablesAreAsTallAsPlanned() {
        val tables = List(TABLES) { index ->
            val random = Random(1000 + index)
            val columns = random.nextInt(2, 8)
            val rows = List(random.nextInt(1, 5)) {
                List(columns) { RichText(words(random, 1, 14)) }
            }
            BlockContent.Table(rows = rows, headerRow = random.nextBoolean())
        }
        for (spec in SPECS) {
            val heights = IntArray(tables.size) { -1 }
            show {
                tables.forEachIndexed { index, table ->
                    // A slot taller than any of the tables, so each can take its natural height.
                    Slice(spec, heightPt = 3000f) {
                        Box(Modifier.onSizeChanged { heights[index] = it.height }) {
                            val state = TableState.of(table)
                            TableBlockView(
                                block = EditorBlock("t$index", BlockKind.TABLE, table = state),
                                table = state,
                                selected = false,
                                focusedCell = null,
                                onCellFocused = {},
                                onCellChange = { _, _ -> },
                                onCellNext = { _, _, _ -> },
                            )
                        }
                    }
                }
            }
            val measurer = measurer(spec)
            rule.runOnIdle {
                val mismatches = tables.indices.mapNotNull { index ->
                    val planned = measurer.tableHeight(tables[index]).toInt()
                    if (planned == heights[index]) null else "#$index planned $planned drawn ${heights[index]}"
                }
                assertEquals("tables drawn at another height than planned (width ${spec.columnWidthPt})", emptyList<String>(), mismatches)
            }
        }
    }

    @Test
    fun tableTallerThanAPageIsDrawnShrunkInsideItsSlot() {
        val spec = SPECS.first()
        val table = BlockContent.Table(rows = List(30) { row -> List(3) { RichText("Qator ${row + 1}") } }, headerRow = true)
        val measured = measurer(spec).tableHeight(table)
        val pagination = Paginator(spec.contentHeightPt).paginate(listOf(FlowItem.Box("t", measured)))
        val plan = PagePlan.build(spec, 18f, listOf("t"), pagination).blocks.getValue("t")
        assertTrue("the table should not fit on a page: $measured", plan.scale < 1f)
        var slot = Rect.Zero
        var drawn = Rect.Zero
        show {
            Box(Modifier.onGloballyPositioned { slot = it.boundsInRoot() }) {
                PageSlice(
                    plan = PagePlan.build(spec, 18f, listOf("t"), pagination),
                    startPt = 0f,
                    endPt = plan.endPt,
                    contentTopPt = plan.leadPt,
                    contentHeightPt = plan.heightPt,
                    firstPageNumber = 1,
                    colors = PageColors(Color.White, Color.Gray, Color.Black),
                    numberMeasurer = TextMeasurer(LocalFontFamilyResolver.current, PageMeasurer.PointDensity, LayoutDirection.Ltr),
                    contentScale = plan.scale,
                ) {
                    Box(Modifier.onGloballyPositioned { drawn = it.boundsInRoot() }) {
                        val state = TableState.of(table)
                        TableBlockView(
                            block = EditorBlock("t", BlockKind.TABLE, table = state),
                            table = state,
                            selected = false,
                            focusedCell = null,
                            onCellFocused = {},
                            onCellChange = { _, _ -> },
                            onCellNext = { _, _, _ -> },
                        )
                    }
                }
            }
        }
        rule.runOnIdle {
            val top = slot.top + plan.leadPt
            assertEquals("shrunk table top", top, drawn.top, 1f)
            assertEquals("shrunk table height", plan.heightPt, drawn.height, 1f)
            assertTrue("table must end inside its page: $drawn vs page bottom ${top + spec.contentHeightPt}",
                drawn.bottom <= top + spec.contentHeightPt + 1f)
            assertEquals("shrunk table width", spec.columnWidthPt * plan.scale, drawn.width, 1f)
        }
    }

    private fun assertSameLines(spec: PageSpec, style: TextStyle, insetPt: Int, content: (String) -> BlockContent) {
        val drawn = arrayOfNulls<TextLayoutResult>(PARAGRAPHS.size)
        show {
            PARAGRAPHS.forEachIndexed { index, text ->
                Slice(spec) {
                    BasicTextField(
                        value = TextFieldValue(text),
                        onValueChange = {},
                        textStyle = style,
                        onTextLayout = { drawn[index] = it },
                        modifier = Modifier.fillMaxWidth().padding(start = insetPt.dp),
                    )
                }
            }
        }
        val measurer = measurer(spec)
        rule.runOnIdle {
            val mismatches = PARAGRAPHS.indices.mapNotNull { index ->
                val planned = measurer.measure("b$index", content(PARAGRAPHS[index])) as FlowItem.Text
                val shown = checkNotNull(drawn[index]) { "paragraph $index was not laid out" }
                val shownStarts = (0 until shown.lineCount).map(shown::getLineStart)
                if (shownStarts == planned.lineStarts) null else "#$index planned ${planned.lineStarts} drawn $shownStarts"
            }
            assertEquals("lines drawn differently from the plan (width ${spec.columnWidthPt})", emptyList<String>(), mismatches)
        }
    }

    private fun measurer(spec: PageSpec): PageMeasurer {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val textMeasurer = TextMeasurer(createFontFamilyResolver(context), PageMeasurer.PointDensity, LayoutDirection.Ltr)
        return PageMeasurer(textMeasurer, spec) { it.toAnnotatedString() }
    }

    @Composable
    private fun Slice(spec: PageSpec, heightPt: Float = 0f, content: @Composable () -> Unit) {
        PageSlice(
            plan = PagePlan.build(spec, 18f, emptyList(), Pagination(1, emptyList())),
            startPt = 0f,
            endPt = heightPt,
            contentTopPt = 0f,
            contentHeightPt = null,
            firstPageNumber = 1,
            colors = PageColors(Color.White, Color.Gray, Color.Black),
            numberMeasurer = TextMeasurer(LocalFontFamilyResolver.current, PageMeasurer.PointDensity, LayoutDirection.Ltr),
            content = content,
        )
    }

    private companion object {
        const val TABLES = 40
        val SPECS = listOf(
            PageSpec.of(PageSize.A5, 20),
            PageSpec.of(PageSize.A5, 15),
            PageSpec.of(PageSize.A4, 20),
            PageSpec.of(PageSize.LETTER, 25),
        )
        val WORDS = (
            "kitob yozish oʻzbek tili qalam varaq sahifa bob muallif hikoya qahramon shahar qishloq daryo " +
                "togʻ bahor kuz qish yoz ona ota bola maktab ustoz shogird doʻst sevgi orzu umid yoʻl safar " +
                "a i va bu u biz siz ular mening sening Toshkent Samarqand Buxoro Xiva Andijon Fargʻona"
            ).split(" ")

        fun words(random: Random, min: Int, max: Int): String =
            List(random.nextInt(min, max)) { WORDS[random.nextInt(WORDS.size)] }.joinToString(" ")

        val PARAGRAPHS = List(120) { index -> words(Random(index), 25, 70) }
    }
}
