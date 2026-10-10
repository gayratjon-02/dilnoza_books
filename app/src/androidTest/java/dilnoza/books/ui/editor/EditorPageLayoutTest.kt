package dilnoza.books.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dilnoza.books.MainActivity
import dilnoza.books.R
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
