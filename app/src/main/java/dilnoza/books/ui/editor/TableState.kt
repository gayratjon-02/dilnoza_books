package dilnoza.books.ui.editor

import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.editor.table.TableGrid
import java.util.concurrent.atomic.AtomicLong

/** A cell named by its row and column keys, so it stays the same cell when rows or columns move around it. */
data class CellKey(val row: Long, val column: Long)

enum class TableAction { ROW_ABOVE, ROW_BELOW, COLUMN_BEFORE, COLUMN_AFTER, DELETE_ROW, DELETE_COLUMN, TOGGLE_HEADER }

/**
 * A table block while it is edited. Rows and columns carry keys that live only in memory: cell fields
 * and the focused cell follow them, so inserting a row above keeps the caret in the cell being typed in.
 */
data class TableState(
    val grid: TableGrid<RichText>,
    val headerRow: Boolean = false,
    val rowKeys: List<Long> = newKeys(grid.rowCount),
    val columnKeys: List<Long> = newKeys(grid.columnCount),
) {
    init {
        require(rowKeys.size == grid.rowCount && columnKeys.size == grid.columnCount) { "Keys must match the grid" }
    }

    val firstCell: CellKey get() = CellKey(rowKeys.first(), columnKeys.first())

    fun contains(cell: CellKey): Boolean = cell.row in rowKeys && cell.column in columnKeys

    /** The cell's text, or null if its row or column is gone. */
    operator fun get(cell: CellKey): RichText? {
        val row = rowKeys.indexOf(cell.row)
        val column = columnKeys.indexOf(cell.column)
        return if (row < 0 || column < 0) null else grid[row, column]
    }

    fun updateCell(cell: CellKey, text: RichText): TableState {
        val row = rowKeys.indexOf(cell.row)
        val column = columnKeys.indexOf(cell.column)
        if (row < 0 || column < 0) return this
        return copy(grid = grid.updateCell(row, column, text))
    }

    /** The next cell in reading order ([forward]) or the one before it; null past either end. */
    fun neighbour(cell: CellKey, forward: Boolean): CellKey? {
        val row = rowKeys.indexOf(cell.row)
        val column = columnKeys.indexOf(cell.column)
        if (row < 0 || column < 0) return null
        val index = row * columnKeys.size + column + if (forward) 1 else -1
        if (index !in 0 until rowKeys.size * columnKeys.size) return null
        return CellKey(rowKeys[index / columnKeys.size], columnKeys[index % columnKeys.size])
    }

    /**
     * Applies [action] around [cell]; returns the new table and the cell that should have the caret.
     * Deleting the only row or column changes nothing.
     */
    fun apply(action: TableAction, cell: CellKey): Pair<TableState, CellKey> {
        val row = rowKeys.indexOf(cell.row)
        val column = columnKeys.indexOf(cell.column)
        if (row < 0 || column < 0) return this to firstCell
        return when (action) {
            TableAction.ROW_ABOVE, TableAction.ROW_BELOW -> {
                val at = if (action == TableAction.ROW_ABOVE) row else row + 1
                val key = nextKey()
                copy(
                    grid = grid.insertRow(at) { RichText.Empty },
                    rowKeys = rowKeys.toMutableList().apply { add(at, key) },
                ) to CellKey(key, cell.column)
            }
            TableAction.COLUMN_BEFORE, TableAction.COLUMN_AFTER -> {
                val at = if (action == TableAction.COLUMN_BEFORE) column else column + 1
                val key = nextKey()
                copy(
                    grid = grid.insertColumn(at) { RichText.Empty },
                    columnKeys = columnKeys.toMutableList().apply { add(at, key) },
                ) to CellKey(cell.row, key)
            }
            TableAction.DELETE_ROW -> {
                if (rowKeys.size == 1) return this to cell
                val keys = rowKeys.toMutableList().apply { removeAt(row) }
                copy(grid = grid.removeRow(row), rowKeys = keys) to CellKey(keys[row.coerceAtMost(keys.lastIndex)], cell.column)
            }
            TableAction.DELETE_COLUMN -> {
                if (columnKeys.size == 1) return this to cell
                val keys = columnKeys.toMutableList().apply { removeAt(column) }
                copy(grid = grid.removeColumn(column), columnKeys = keys) to
                    CellKey(cell.row, keys[column.coerceAtMost(keys.lastIndex)])
            }
            TableAction.TOGGLE_HEADER -> copy(headerRow = !headerRow) to cell
        }
    }

    /** A row added at the end, for typing on past the last cell; returns its first cell. */
    fun appendRow(): Pair<TableState, CellKey> {
        val key = nextKey()
        return copy(grid = grid.insertRow(rowKeys.size) { RichText.Empty }, rowKeys = rowKeys + key) to
            CellKey(key, columnKeys.first())
    }

    companion object {
        const val DEFAULT_SIZE = 3
        const val MAX_ROWS = 20
        const val MAX_COLUMNS = 8

        private val keys = AtomicLong()

        private fun nextKey(): Long = keys.incrementAndGet()

        private fun newKeys(count: Int): List<Long> = List(count) { nextKey() }

        fun of(content: BlockContent.Table) = TableState(TableGrid(content.rows), content.headerRow)

        fun create(rows: Int, columns: Int, headerRow: Boolean) = TableState(
            grid = TableGrid.create(rows.coerceIn(1, MAX_ROWS), columns.coerceIn(1, MAX_COLUMNS)) { _, _ -> RichText.Empty },
            headerRow = headerRow,
        )
    }
}
