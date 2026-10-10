package dilnoza.books.editor.history

/**
 * Snapshot-based undo/redo history for an immutable editor state [T].
 *
 * Call [record] with the state *before* a change is applied. Consecutive records sharing the same
 * non-null coalesce key within [coalesceWindowMillis] of the previous record collapse into one undo
 * step, so a burst of typing is undone at once; the window slides with every record.
 *
 * Not thread-safe: use it from a single (main) thread.
 */
class UndoManager<T>(
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
    private val coalesceWindowMillis: Long = DEFAULT_COALESCE_WINDOW_MILLIS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    init {
        require(maxSteps >= 1) { "maxSteps must be at least 1, was $maxSteps" }
        require(coalesceWindowMillis >= 0) { "coalesceWindowMillis must not be negative" }
    }

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    private var lastKey: Any? = null
    private var lastRecordAt = 0L

    /** Incremented on every change to [canUndo]/[canRedo] or the stacks; cheap to observe from UI. */
    var version: Long = 0L
        private set

    /** Invoked after every mutation of the history (record, undo, redo, clear). */
    var onChanged: (() -> Unit)? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val undoSize: Int get() = undoStack.size
    val redoSize: Int get() = redoStack.size

    fun record(before: T, coalesceKey: Any? = null) {
        val now = clock()
        val coalesce = coalesceKey != null &&
            coalesceKey == lastKey &&
            undoStack.isNotEmpty() &&
            now - lastRecordAt in 0..coalesceWindowMillis
        lastKey = coalesceKey
        lastRecordAt = now
        if (coalesce) {
            // The step already holds the state from before the burst began; a redo branch, if any,
            // was dropped by the first record of the burst.
            return
        }
        undoStack.addLast(before)
        while (undoStack.size > maxSteps) undoStack.removeFirst()
        redoStack.clear()
        changed()
    }

    /** Returns the state to restore, or null when there is nothing to undo. [current] becomes redoable. */
    fun undo(current: T): T? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        breakCoalescing()
        changed()
        return previous
    }

    /** Returns the state to restore, or null when there is nothing to redo. [current] becomes undoable. */
    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        while (undoStack.size > maxSteps) undoStack.removeFirst()
        breakCoalescing()
        changed()
        return next
    }

    /** Ends the current coalescing burst, e.g. when the caret moves or focus changes. */
    fun breakCoalescing() {
        lastKey = null
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        breakCoalescing()
        changed()
    }

    private fun changed() {
        version++
        onChanged?.invoke()
    }

    companion object {
        const val DEFAULT_MAX_STEPS = 100
        const val DEFAULT_COALESCE_WINDOW_MILLIS = 1000L
    }
}
