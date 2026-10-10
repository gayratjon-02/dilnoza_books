package dilnoza.books.ui.editor.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.PageSize
import dilnoza.books.domain.model.RichText
import dilnoza.books.editor.layout.FlowItem
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PagePlan
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.editor.layout.PageStyles
import dilnoza.books.editor.layout.Pagination
import dilnoza.books.ui.editor.toAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * The paginator plans every block from [PageMeasurer]; the editor then draws it in a [PageSlice]
 * column. Both must break the same text into the same lines, or text overflows its planned slot.
 */
@RunWith(AndroidJUnit4::class)
class PageLayoutMatchTest {

    @get:Rule
    val rule = createComposeRule()

    private val paragraphs = List(PARAGRAPHS) { index -> sentence(Random(index)) }

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

    private class Case(val spec: PageSpec, val style: TextStyle, val insetPt: Int)

    private var case by mutableStateOf<Case?>(null)
    private val drawn = arrayOfNulls<TextLayoutResult>(PARAGRAPHS)
    private var contentSet = false

    private fun assertSameLines(spec: PageSpec, style: TextStyle, insetPt: Int, content: (String) -> BlockContent) {
        drawn.fill(null)
        if (!contentSet) {
            contentSet = true
            rule.setContent {
                val current = case ?: return@setContent
                PointSpace(scale = 1f) {
                    Column {
                        paragraphs.forEachIndexed { index, text ->
                            Slice(current.spec) {
                                BasicTextField(
                                    value = TextFieldValue(text),
                                    onValueChange = {},
                                    textStyle = current.style,
                                    onTextLayout = { drawn[index] = it },
                                    modifier = Modifier.fillMaxWidth().padding(start = current.insetPt.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.runOnIdle { case = Case(spec, style, insetPt) }
        rule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val measurer = PageMeasurer(
            TextMeasurer(createFontFamilyResolver(context), PageMeasurer.PointDensity, LayoutDirection.Ltr),
            spec,
        ) { it.toAnnotatedString() }
        rule.runOnIdle {
            val mismatches = paragraphs.indices.mapNotNull { index ->
                val planned = measurer.measure("b$index", content(paragraphs[index])) as FlowItem.Text
                val shown = checkNotNull(drawn[index]) { "paragraph $index was not laid out" }
                check(shown.layoutInput.style.fontSize == style.fontSize)
                val shownStarts = (0 until shown.lineCount).map(shown::getLineStart)
                if (shownStarts == planned.lineStarts) null else "#$index planned ${planned.lineStarts} drawn $shownStarts"
            }
            assertEquals("lines drawn differently from the plan (width ${spec.columnWidthPt})", emptyList<String>(), mismatches)
        }
    }

    @Composable
    private fun Slice(spec: PageSpec, content: @Composable () -> Unit) {
        val plan = PagePlan.build(spec, 18f, emptyList(), Pagination(1, emptyList()))
        Box {
            PageSlice(
                plan = plan,
                startPt = 0f,
                endPt = 0f,
                contentTopPt = 0f,
                contentHeightPt = null,
                firstPageNumber = 1,
                colors = PageColors(Color.White, Color.Gray, Color.Black),
                numberMeasurer = TextMeasurer(LocalFontFamilyResolver.current, PageMeasurer.PointDensity, LayoutDirection.Ltr),
                content = content,
            )
        }
    }

    private fun sentence(random: Random): String =
        List(random.nextInt(25, 70)) { WORDS[random.nextInt(WORDS.size)] }.joinToString(" ")

    private companion object {
        const val PARAGRAPHS = 120
        val SPECS = listOf(
            PageSpec.of(PageSize.A5, 20),
            PageSpec.of(PageSize.A5, 15),
            PageSpec.of(PageSize.A4, 20),
            PageSpec.of(PageSize.LETTER, 25),
        )
        val WORDS = (
            "kitob yozish oʻzbek tili qalam varaq sahifa bob muallif hikoya qahramon shahar qishloq daryo " +
                "tog' bahor kuz qish yoz ona ota bola maktab ustoz shogird do'st sevgi orzu umid yo'l safar " +
                "a i va bu u biz siz ular mening sening Toshkent Samarqand Buxoro Xiva Andijon Farg'ona"
            ).split(" ")
    }
}
