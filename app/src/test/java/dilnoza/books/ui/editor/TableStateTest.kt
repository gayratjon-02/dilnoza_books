package dilnoza.books.ui.editor

import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TableStateTest {

    private val table = TableState.of(
        BlockContent.Table(
            rows = listOf(
                listOf(RichText("A1"), RichText("B1")),
                listOf(RichText("A2"), RichText("B2")),
            ),
            headerRow = true,
        ),
    )

    private fun cell(row: Int, column: Int) = CellKey(table.rowKeys[row], table.columnKeys[column])

    private fun TableState.texts() = grid.rows.map { row -> row.map { it.text } }

    @Test
    fun `keys are unique across tables`() {
        val other = TableState.create(2, 2, headerRow = false)

        assertTrue((table.rowKeys + table.columnKeys).none { it in other.rowKeys + other.columnKeys })
    }

    @Test
    fun `cells are found and updated by key`() {
        val updated = table.updateCell(cell(1, 0), RichText("x"))

        assertEquals(listOf(listOf("A1", "B1"), listOf("x", "B2")), updated.texts())
        assertEquals(RichText("x"), updated[cell(1, 0)])
    }

    @Test
    fun `a row above gets the caret and moves the old cells down without changing their keys`() {
        val focus = cell(1, 1)

        val (result, caret) = table.apply(TableAction.ROW_ABOVE, focus)

        assertEquals(listOf(listOf("A1", "B1"), listOf("", ""), listOf("A2", "B2")), result.texts())
        assertEquals(CellKey(result.rowKeys[1], focus.column), caret)
        assertEquals(RichText("B2"), result[focus])
    }

    @Test
    fun `a row below goes after the focused row`() {
        val (result, caret) = table.apply(TableAction.ROW_BELOW, cell(0, 0))

        assertEquals(listOf(listOf("A1", "B1"), listOf("", ""), listOf("A2", "B2")), result.texts())
        assertEquals(result.rowKeys[1], caret.row)
    }

    @Test
    fun `columns are added before and after`() {
        val (before, caretBefore) = table.apply(TableAction.COLUMN_BEFORE, cell(0, 0))
        val (after, caretAfter) = table.apply(TableAction.COLUMN_AFTER, cell(0, 1))

        assertEquals(listOf(listOf("", "A1", "B1"), listOf("", "A2", "B2")), before.texts())
        assertEquals(before.columnKeys[0], caretBefore.column)
        assertEquals(listOf(listOf("A1", "B1", ""), listOf("A2", "B2", "")), after.texts())
        assertEquals(after.columnKeys[2], caretAfter.column)
    }

    @Test
    fun `deleting a row moves the caret to the row that takes its place, or the one above at the end`() {
        val (middle, caret) = table.apply(TableAction.DELETE_ROW, cell(0, 1))
        val (last, lastCaret) = table.apply(TableAction.DELETE_ROW, cell(1, 0))

        assertEquals(listOf(listOf("A2", "B2")), middle.texts())
        assertEquals(cell(1, 1), caret)
        assertEquals(listOf(listOf("A1", "B1")), last.texts())
        assertEquals(cell(0, 0), lastCaret)
    }

    @Test
    fun `deleting a column keeps the table rectangular`() {
        val (result, caret) = table.apply(TableAction.DELETE_COLUMN, cell(1, 1))

        assertEquals(listOf(listOf("A1"), listOf("A2")), result.texts())
        assertEquals(cell(1, 0), caret)
    }

    @Test
    fun `the only row or column is never deleted`() {
        val single = TableState.create(1, 1, headerRow = false)

        assertSame(single, single.apply(TableAction.DELETE_ROW, single.firstCell).first)
        assertSame(single, single.apply(TableAction.DELETE_COLUMN, single.firstCell).first)
    }

    @Test
    fun `header row toggles`() {
        val (result, caret) = table.apply(TableAction.TOGGLE_HEADER, cell(1, 1))

        assertFalse(result.headerRow)
        assertEquals(cell(1, 1), caret)
    }

    @Test
    fun `neighbours run in reading order and stop at the ends`() {
        assertEquals(cell(0, 1), table.neighbour(cell(0, 0), forward = true))
        assertEquals(cell(1, 0), table.neighbour(cell(0, 1), forward = true))
        assertEquals(cell(0, 1), table.neighbour(cell(1, 0), forward = false))
        assertNull(table.neighbour(cell(1, 1), forward = true))
        assertNull(table.neighbour(cell(0, 0), forward = false))
    }

    @Test
    fun `an appended row starts at its first cell`() {
        val (result, caret) = table.appendRow()

        assertEquals(3, result.grid.rowCount)
        assertEquals(CellKey(result.rowKeys[2], result.columnKeys[0]), caret)
    }

    @Test
    fun `a stale cell is ignored`() {
        val gone = CellKey(-1, -1)

        assertNull(table[gone])
        assertSame(table, table.updateCell(gone, RichText("x")))
        assertFalse(table.contains(gone))
    }

    @Test
    fun `new tables are clamped to the allowed size`() {
        val big = TableState.create(100, 100, headerRow = true)

        assertEquals(TableState.MAX_ROWS to TableState.MAX_COLUMNS, big.grid.rowCount to big.grid.columnCount)
    }
}
