package dilnoza.books.editor.table

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class TableGridTest {

    /** 2x3 grid with cells "r,c". */
    private val grid = TableGrid.create(2, 3) { r, c -> "$r,$c" }

    @Test
    fun `create fills cells by position`() {
        assertEquals(2, grid.rowCount)
        assertEquals(3, grid.columnCount)
        assertEquals(listOf(listOf("0,0", "0,1", "0,2"), listOf("1,0", "1,1", "1,2")), grid.rows)
        assertEquals("1,2", grid[1, 2])
    }

    @Test
    fun `create rejects empty dimensions`() {
        assertThrows(IllegalArgumentException::class.java) { TableGrid.create(0, 1) { _, _ -> "" } }
        assertThrows(IllegalArgumentException::class.java) { TableGrid.create(1, 0) { _, _ -> "" } }
        assertThrows(IllegalArgumentException::class.java) { TableGrid.create(-1, -1) { _, _ -> "" } }
    }

    @Test
    fun `constructor rejects empty or ragged rows`() {
        assertThrows(IllegalArgumentException::class.java) { TableGrid(emptyList<List<String>>()) }
        assertThrows(IllegalArgumentException::class.java) { TableGrid(listOf(emptyList<String>())) }
        assertThrows(IllegalArgumentException::class.java) { TableGrid(listOf(listOf("a"), listOf("b", "c"))) }
    }

    @Test
    fun `single cell grid`() {
        val one = TableGrid.create(1, 1) { _, _ -> 7 }
        assertEquals(1, one.rowCount)
        assertEquals(1, one.columnCount)
        assertEquals(7, one[0, 0])
    }

    @Test
    fun `get validates indices`() {
        assertThrows(IndexOutOfBoundsException::class.java) { grid[2, 0] }
        assertThrows(IndexOutOfBoundsException::class.java) { grid[0, 3] }
        assertThrows(IndexOutOfBoundsException::class.java) { grid[-1, 0] }
    }

    @Test
    fun `insert row at start middle and end`() {
        val top = grid.insertRow(0) { c -> "n$c" }
        assertEquals(listOf("n0", "n1", "n2"), top.rows[0])
        assertEquals(3, top.rowCount)

        val middle = grid.insertRow(1) { "x" }
        assertEquals(listOf("0,0", "x", "1,0"), middle.rows.map { it[0] })

        val end = grid.insertRow(2) { "e" }
        assertEquals(listOf("e", "e", "e"), end.rows[2])
    }

    @Test
    fun `insert row validates index`() {
        assertThrows(IndexOutOfBoundsException::class.java) { grid.insertRow(3) { "" } }
        assertThrows(IndexOutOfBoundsException::class.java) { grid.insertRow(-1) { "" } }
    }

    @Test
    fun `remove row`() {
        val removed = grid.removeRow(0)
        assertEquals(listOf(listOf("1,0", "1,1", "1,2")), removed.rows)
    }

    @Test
    fun `remove last remaining row is a no op`() {
        val one = grid.removeRow(1)
        assertSame(one, one.removeRow(0))
        assertEquals(1, one.removeRow(0).rowCount)
    }

    @Test
    fun `remove row validates index even on a single row`() {
        assertThrows(IndexOutOfBoundsException::class.java) { grid.removeRow(2) }
        assertThrows(IndexOutOfBoundsException::class.java) { grid.removeRow(1).removeRow(1) }
    }

    @Test
    fun `insert column passes row index to factory`() {
        val inserted = grid.insertColumn(1) { r -> "c$r" }
        assertEquals(4, inserted.columnCount)
        assertEquals(listOf("0,0", "c0", "0,1", "0,2"), inserted.rows[0])
        assertEquals(listOf("1,0", "c1", "1,1", "1,2"), inserted.rows[1])

        assertEquals(listOf("z", "z"), grid.insertColumn(3) { "z" }.rows.map { it[3] })
        assertEquals(listOf("a", "a"), grid.insertColumn(0) { "a" }.rows.map { it[0] })
    }

    @Test
    fun `insert column validates index`() {
        assertThrows(IndexOutOfBoundsException::class.java) { grid.insertColumn(4) { "" } }
        assertThrows(IndexOutOfBoundsException::class.java) { grid.insertColumn(-1) { "" } }
    }

    @Test
    fun `remove column`() {
        val removed = grid.removeColumn(1)
        assertEquals(listOf(listOf("0,0", "0,2"), listOf("1,0", "1,2")), removed.rows)
    }

    @Test
    fun `remove last remaining column is a no op`() {
        val one = grid.removeColumn(0).removeColumn(0)
        assertEquals(1, one.columnCount)
        assertSame(one, one.removeColumn(0))
        assertThrows(IndexOutOfBoundsException::class.java) { one.removeColumn(1) }
        assertThrows(IndexOutOfBoundsException::class.java) { grid.removeColumn(3) }
    }

    @Test
    fun `move row forward and backward`() {
        val three = TableGrid.create(3, 1) { r, _ -> r }
        assertEquals(listOf(1, 2, 0), three.moveRow(0, 2).rows.map { it[0] })
        assertEquals(listOf(2, 0, 1), three.moveRow(2, 0).rows.map { it[0] })
        assertEquals(listOf(1, 0, 2), three.moveRow(0, 1).rows.map { it[0] })
        assertSame(three, three.moveRow(1, 1))
    }

    @Test
    fun `move row validates indices`() {
        assertThrows(IndexOutOfBoundsException::class.java) { grid.moveRow(0, 2) }
        assertThrows(IndexOutOfBoundsException::class.java) { grid.moveRow(-1, 0) }
        assertThrows(IndexOutOfBoundsException::class.java) { grid.moveRow(2, 2) }
    }

    @Test
    fun `move column forward and backward`() {
        assertEquals(listOf("0,1", "0,2", "0,0"), grid.moveColumn(0, 2).rows[0])
        assertEquals(listOf("1,2", "1,0", "1,1"), grid.moveColumn(2, 0).rows[1])
        assertSame(grid, grid.moveColumn(1, 1))
    }

    @Test
    fun `move column validates indices`() {
        assertThrows(IndexOutOfBoundsException::class.java) { grid.moveColumn(0, 3) }
        assertThrows(IndexOutOfBoundsException::class.java) { grid.moveColumn(3, 0) }
    }

    @Test
    fun `update cell only changes that cell`() {
        val updated = grid.updateCell(1, 2, "new")
        assertEquals("new", updated[1, 2])
        assertEquals(grid.rows[0], updated.rows[0])
        assertEquals(listOf("1,0", "1,1", "new"), updated.rows[1])
        assertEquals("1,2", grid[1, 2])
    }

    @Test
    fun `update cell validates indices`() {
        assertThrows(IndexOutOfBoundsException::class.java) { grid.updateCell(2, 0, "x") }
        assertThrows(IndexOutOfBoundsException::class.java) { grid.updateCell(0, -1, "x") }
    }

    @Test
    fun `operations never mutate the original grid`() {
        val before = grid.rows.map { it.toList() }
        grid.insertRow(0) { "x" }
        grid.removeRow(0)
        grid.insertColumn(0) { "x" }
        grid.removeColumn(0)
        grid.moveRow(0, 1)
        grid.moveColumn(0, 2)
        grid.updateCell(0, 0, "x")
        assertEquals(before, grid.rows)
    }

    @Test
    fun `structural equality`() {
        assertEquals(TableGrid.create(2, 3) { r, c -> "$r,$c" }, grid)
        assertEquals(grid, grid.moveRow(0, 1).moveRow(0, 1))
    }

    @Test
    fun `chained operations keep the grid rectangular`() {
        var g = TableGrid.create(1, 1) { _, _ -> 0 }
        g = g.insertColumn(1) { 1 }.insertRow(1) { 2 }.insertColumn(0) { 3 }.removeRow(0).moveColumn(0, 2)
        assertEquals(1, g.rowCount)
        assertEquals(3, g.columnCount)
        assertEquals(listOf(2, 2, 3), g.rows[0])
    }
}
