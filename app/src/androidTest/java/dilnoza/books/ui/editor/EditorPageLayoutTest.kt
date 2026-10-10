package dilnoza.books.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dilnoza.books.MainActivity
import dilnoza.books.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Blocks drawn in the real editor stay inside the page slots the paginator planned for them. */
@RunWith(AndroidJUnit4::class)
class EditorPageLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var robot: EditorRobot

    @Before
    fun openEditor() {
        robot = EditorRobot(rule)
        robot.createBookAndOpenEditor()
    }

    @After
    fun cleanUp() {
        robot.deleteBook()
    }

    @Test
    fun tableTallerThanAPageIsShrunkOntoOnePage() {
        // The chip may sit past the end of the sideways scrolling toolbar.
        rule.onNode(hasText(robot.str(R.string.editor_table))).performScrollTo()
        robot.click(robot.nodes(hasText(robot.str(R.string.editor_table))).first())
        val more = robot.str(R.string.table_more_rows)
        robot.waitFor { robot.nodes(hasContentDescription(more)).isNotEmpty() }
        // The dialog is a window of its own, so its buttons are clicked through semantics.
        repeat(MAX_ROWS) {
            if (robot.nodes(hasContentDescription(more)).single().isEnabled()) {
                rule.onNode(hasContentDescription(more)).performClick()
                rule.waitForIdle()
            }
        }
        rule.onNode(hasText(robot.str(R.string.table_insert))).performClick()
        val below = robot.str(R.string.table_row_below)
        robot.waitFor { robot.nodes(hasText(below)).isNotEmpty() }
        // Past the dialog's limit of 20 rows the table no longer fits on one A5 page.
        repeat(EXTRA_ROWS) { robot.click(robot.nodes(hasText(below)).single()) }
        robot.hideKeyboard()
        val rows = MAX_ROWS + EXTRA_ROWS
        robot.waitFor { cellsOfColumn(1).size == rows }

        val cells = cellsOfColumn(1)
        robot.screenshot("tall_table")
        cells.zipWithNext { above, below ->
            assertTrue("row ${above.first} ($above) must be above row ${below.first} ($below)",
                below.second.top >= above.second.bottom - 1f)
        }
        val heights = cells.map { it.second.height }
        assertTrue("rows must keep their height: $heights", heights.all { it > heights.max() * 0.9f })
        val tableTop = cells.first().second.top
        val tableBottom = cells.last().second.bottom
        val pageHeight = robot.pageHeightOnScreen()
        assertTrue("table ($tableTop..$tableBottom) must fit a page ($pageHeight px)", tableBottom - tableTop < pageHeight * 0.85f)
    }

    @Test
    fun wideListNumbersStayOnOneLineNextToTheirItem() {
        robot.tapField(robot.blockFields().first())
        robot.type("1. Bir")
        for (item in 2..ITEMS) {
            robot.pressEnter()
            robot.type("Band")
        }
        robot.hideKeyboard()
        robot.waitFor { robot.nodes(hasText("$ITEMS.")).isNotEmpty() }
        robot.screenshot("list_labels")

        for (number in listOf(9, 10, 11, 12)) {
            val label = robot.nodes(hasText("$number.")).single()
            val layouts = mutableListOf<TextLayoutResult>()
            label.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            assertEquals("label $number. must be one line", 1, layout.lineCount)
            assertEquals("label $number. must be shown whole", "$number.".length, layout.getLineEnd(0, visibleEnd = true))
            // Its text starts after the label: the label ends before the item's text field.
            val fields = robot.blockFields()
            val field = fields[number - 1].boundsInRoot
            val labelBounds = label.boundsInRoot
            assertTrue("label $number. ($labelBounds) must sit on its item's first line ($field)",
                labelBounds.top >= field.top - 1f && labelBounds.bottom <= field.bottom + 1f)
            assertTrue("label $number. ($labelBounds) must end before its text ($field)", labelBounds.right <= field.left + 1f)
        }
    }

    @Test
    fun slashMenuOpensAtTheSlashOnTheNextPage() {
        robot.tapField(robot.blockFields().first())
        // One paragraph that runs on past the end of the first page, pasted: typing it takes too long.
        robot.pasteText(List(LONG_PARAGRAPH_WORDS) { WORDS[it % WORDS.size] }.joinToString(" "))
        robot.waitFor { drawnText().contains(GAP_CHAR) }
        robot.hideKeyboard()

        // The first line on the next page starts right after the page gap's character.
        val lineStart = drawnText().indexOf(GAP_CHAR) + 1
        scrollIntoView { robot.textPointOnScreen(paragraph(), lineStart) }
        // The caret goes exactly to the start of that line (an offset in the drawn text).
        val field = paragraph()
        rule.runOnUiThread { field.config[SemanticsActions.SetSelection].action?.invoke(lineStart, lineStart, false) }
        rule.waitForIdle()
        robot.type("/")
        // "/" never starts a line after a space (UAX #14), so the word before it moves down with it.
        robot.waitFor { drawnText().contains('/') }

        val menuLabel = robot.str(R.string.slash_menu)
        val menu = robot.device.wait(Until.findObject(By.desc(menuLabel)), MENU_TIMEOUT_MS)
        assertNotNull("the command menu should open", menu)
        robot.screenshot("slash_next_page")
        val slash = robot.textPointOnScreen(paragraph(), drawnText().indexOf('/'))
        val bounds = menu.visibleBounds
        val lineHeight = robot.pageHeightOnScreen() / A5_HEIGHT_PT * LINE_PT
        val distance = if (bounds.top >= slash.y) bounds.top - slash.y else slash.y - bounds.bottom
        assertTrue(
            "menu $bounds must open next to the slash at $slash, not ${distance}px away",
            distance <= lineHeight * 1.5f,
        )
        assertTrue("menu $bounds must start near the slash x=${slash.x}", abs(bounds.left - slash.x) <= lineHeight * 2)
    }

    private fun paragraph() = robot.blockField("kitob yozish sahifa")

    /** The text the paragraph's field draws: its text plus one character per page gap. */
    private fun drawnText(): String {
        val layouts = mutableListOf<TextLayoutResult>()
        paragraph().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.firstOrNull()?.layoutInput?.text?.text.orEmpty()
    }

    /** Swipes the pages until [point] is in the middle part of the visible list. */
    private fun scrollIntoView(point: () -> Offset) {
        repeat(MAX_SWIPES) {
            val list = robot.pageListOnScreen()
            val y = point().y
            val x = list.centerX()
            when {
                y > list.bottom - list.height() / 3 -> robot.device.swipe(x, list.bottom - list.height() / 4, x, list.top + list.height() / 4, 40)
                y < list.top + list.height() / 4 -> robot.device.swipe(x, list.top + list.height() / 3, x, list.bottom - list.height() / 3, 40)
                else -> return
            }
            rule.waitForIdle()
        }
    }

    /** Cells of [column] (1-based), as row number to bounds in the root, ordered by row. */
    private fun cellsOfColumn(column: Int) = robot.nodes(CELL, unmerged = true).mapNotNull { node ->
        val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ?: return@mapNotNull null
        val (row, col) = CELL_LABEL.find(label)?.destructured ?: return@mapNotNull null
        if (col.toInt() != column) return@mapNotNull null
        // Unclipped: rows of a tall table reach below the visible part of the list.
        val coordinates = node.layoutInfo.coordinates
        val size = coordinates.size
        val topLeft = coordinates.localToRoot(Offset.Zero)
        val bottomRight = coordinates.localToRoot(Offset(size.width.toFloat(), size.height.toFloat()))
        row.toInt() to Rect(topLeft, bottomRight)
    }.sortedBy { it.first }

    private companion object {
        const val ITEMS = 12
        const val LONG_PARAGRAPH_WORDS = 420
        const val GAP_CHAR = '\u200B'
        const val MENU_TIMEOUT_MS = 5_000L
        const val MAX_SWIPES = 12
        const val A5_HEIGHT_PT = 210f / 25.4f * 72f
        const val LINE_PT = 11f * 1.45f
        val WORDS = "kitob yozish sahifa bob muallif hikoya qahramon shahar qishloq daryo bahor ustoz".split(" ")
        const val MAX_ROWS = 20
        const val EXTRA_ROWS = 2
        // "%1$d-qator, %2$d-ustun" and its English and Russian forms: the two numbers in order.
        val CELL_LABEL = Regex("""(\d+)\D+(\d+)""")
        val CELL = SemanticsMatcher("table cell") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { CELL_LABEL.containsMatchIn(it) } == true &&
                node.config.contains(SemanticsProperties.EditableText)
        }
    }
}
