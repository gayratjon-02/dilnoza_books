package dilnoza.books.editor.table

/**
 * Immutable rectangular grid with at least one row and one column. Every operation returns a new grid.
 * Invalid indices throw [IndexOutOfBoundsException]; removing the last row or column is a no-op.
 */
data class TableGrid<T>(val rows: List<List<T>>) {

    init {
        require(rows.isNotEmpty()) { "Table needs at least one row" }
        val width = rows[0].size
        require(width > 0) { "Table needs at least one column" }
        require(rows.all { it.size == width }) { "Table rows must have equal length" }
    }

    val rowCount: Int get() = rows.size
    val columnCount: Int get() = rows[0].size

    operator fun get(row: Int, col: Int): T {
        checkCell(row, col)
        return rows[row][col]
    }

    /** Inserts a row so it ends up at index [at] (0..rowCount); [factory] receives the column index. */
    fun insertRow(at: Int, factory: (col: Int) -> T): TableGrid<T> {
        checkIndex(at, rowCount + 1, "row")
        val newRow = List(columnCount, factory)
        return TableGrid(rows.toMutableList().apply { add(at, newRow) })
    }

    fun removeRow(at: Int): TableGrid<T> {
        checkIndex(at, rowCount, "row")
        if (rowCount == 1) return this
        return TableGrid(rows.toMutableList().apply { removeAt(at) })
    }

    /** Inserts a column so it ends up at index [at] (0..columnCount); [factory] receives the row index. */
    fun insertColumn(at: Int, factory: (row: Int) -> T): TableGrid<T> {
        checkIndex(at, columnCount + 1, "column")
        return TableGrid(rows.mapIndexed { r, row -> row.toMutableList().apply { add(at, factory(r)) } })
    }

    fun removeColumn(at: Int): TableGrid<T> {
        checkIndex(at, columnCount, "column")
        if (columnCount == 1) return this
        return TableGrid(rows.map { row -> row.toMutableList().apply { removeAt(at) } })
    }

    /** Moves the row at [from] so that it ends up at index [to]; both must be existing row indices. */
    fun moveRow(from: Int, to: Int): TableGrid<T> {
        checkIndex(from, rowCount, "row")
        checkIndex(to, rowCount, "row")
        if (from == to) return this
        return TableGrid(rows.moved(from, to))
    }

    /** Moves the column at [from] so that it ends up at index [to]; both must be existing column indices. */
    fun moveColumn(from: Int, to: Int): TableGrid<T> {
        checkIndex(from, columnCount, "column")
        checkIndex(to, columnCount, "column")
        if (from == to) return this
        return TableGrid(rows.map { it.moved(from, to) })
    }

    fun updateCell(row: Int, col: Int, value: T): TableGrid<T> {
        checkCell(row, col)
        return TableGrid(
            rows.mapIndexed { r, cells -> if (r == row) cells.toMutableList().apply { set(col, value) } else cells },
        )
    }

    private fun checkCell(row: Int, col: Int) {
        checkIndex(row, rowCount, "row")
        checkIndex(col, columnCount, "column")
    }

    private fun checkIndex(index: Int, size: Int, what: String) {
        if (index !in 0 until size) throw IndexOutOfBoundsException("$what $index out of 0 until $size")
    }

    private fun <E> List<E>.moved(from: Int, to: Int): List<E> =
        toMutableList().apply { add(to, removeAt(from)) }

    companion object {
        fun <T> create(rows: Int, cols: Int, factory: (row: Int, col: Int) -> T): TableGrid<T> {
            require(rows >= 1 && cols >= 1) { "Table must be at least 1x1, was ${rows}x$cols" }
            return TableGrid(List(rows) { r -> List(cols) { c -> factory(r, c) } })
        }
    }
}
