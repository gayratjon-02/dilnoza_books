package dilnoza.books.editor.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoManagerTest {

    private var now = 0L
    private fun manager(maxSteps: Int = 100, window: Long = 1000) =
        UndoManager<String>(maxSteps = maxSteps, coalesceWindowMillis = window, clock = { now })

    /** Simulates an editor: records the old state, then applies the change. */
    private class Doc(val history: UndoManager<String>, var state: String = "") {
        fun edit(next: String, key: Any? = null) {
            history.record(state, key)
            state = next
        }

        fun undo(): Boolean = history.undo(state)?.also { state = it } != null
        fun redo(): Boolean = history.redo(state)?.also { state = it } != null
    }

    @Test
    fun `empty history has nothing to undo or redo`() {
        val history = manager()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo("x"))
        assertNull(history.redo("x"))
        assertEquals(0L, history.version)
    }

    @Test
    fun `undo restores previous state and redo reapplies it`() {
        val doc = Doc(manager())
        doc.edit("a")
        doc.edit("ab")

        assertTrue(doc.undo())
        assertEquals("a", doc.state)
        assertTrue(doc.history.canRedo)

        assertTrue(doc.undo())
        assertEquals("", doc.state)
        assertFalse(doc.history.canUndo)
        assertFalse(doc.undo())

        assertTrue(doc.redo())
        assertEquals("a", doc.state)
        assertTrue(doc.redo())
        assertEquals("ab", doc.state)
        assertFalse(doc.redo())
    }

    @Test
    fun `same key within window coalesces into one step`() {
        val doc = Doc(manager())
        "hello".forEachIndexed { i, _ ->
            now += 200
            doc.edit("hello".substring(0, i + 1), key = "typing")
        }
        assertEquals(1, doc.history.undoSize)
        doc.undo()
        assertEquals("", doc.state)
    }

    @Test
    fun `window slides with each record`() {
        val doc = Doc(manager(window = 1000))
        repeat(10) {
            now += 900
            doc.edit("x".repeat(it + 1), key = "typing")
        }
        assertEquals(1, doc.history.undoSize)
    }

    @Test
    fun `record exactly at window edge still coalesces, one past does not`() {
        val doc = Doc(manager(window = 1000))
        doc.edit("a", "k")
        now += 1000
        doc.edit("ab", "k")
        assertEquals(1, doc.history.undoSize)
        now += 1001
        doc.edit("abc", "k")
        assertEquals(2, doc.history.undoSize)
    }

    @Test
    fun `null key never coalesces`() {
        val doc = Doc(manager())
        doc.edit("a")
        doc.edit("ab")
        doc.edit("abc")
        assertEquals(3, doc.history.undoSize)
    }

    @Test
    fun `different keys start new steps`() {
        val doc = Doc(manager())
        doc.edit("hello", "word1")
        doc.edit("hello ", "word2")
        doc.edit("hello w", "word2")
        assertEquals(2, doc.history.undoSize)
        doc.undo()
        assertEquals("hello", doc.state)
        doc.undo()
        assertEquals("", doc.state)
    }

    @Test
    fun `null key between equal keys breaks the burst`() {
        val doc = Doc(manager())
        doc.edit("a", "k")
        doc.edit("a!", null)
        doc.edit("a!b", "k")
        assertEquals(3, doc.history.undoSize)
    }

    @Test
    fun `keys are compared by equality`() {
        val doc = Doc(manager())
        doc.edit("a", listOf("block-1", 0))
        doc.edit("ab", listOf("block-1", 0))
        assertEquals(1, doc.history.undoSize)
    }

    @Test
    fun `clock going backwards does not coalesce`() {
        val doc = Doc(manager())
        now = 5000
        doc.edit("a", "k")
        now = 4000
        doc.edit("ab", "k")
        assertEquals(2, doc.history.undoSize)
    }

    @Test
    fun `new record clears redo`() {
        val doc = Doc(manager())
        doc.edit("a")
        doc.edit("ab")
        doc.undo()
        assertTrue(doc.history.canRedo)
        doc.edit("ac")
        assertFalse(doc.history.canRedo)
        doc.undo()
        assertEquals("a", doc.state)
    }

    @Test
    fun `undo breaks coalescing so the next edit is a separate step`() {
        val doc = Doc(manager())
        doc.edit("a", "k")
        doc.edit("ab", "k")
        doc.undo()
        assertEquals("", doc.state)
        doc.edit("x", "k")
        doc.edit("xy", "k")
        assertFalse(doc.history.canRedo)
        assertEquals(1, doc.history.undoSize)
        doc.undo()
        assertEquals("", doc.state)
    }

    @Test
    fun `redo breaks coalescing`() {
        val doc = Doc(manager())
        doc.edit("a", "k")
        now += 5000
        doc.edit("ab", "k")
        doc.undo()
        doc.redo()
        assertEquals("ab", doc.state)
        doc.edit("abc", "k")
        assertEquals(3, doc.history.undoSize)
    }

    @Test
    fun `breakCoalescing starts a new step for the same key`() {
        val doc = Doc(manager())
        doc.edit("a", "k")
        doc.history.breakCoalescing()
        doc.edit("ab", "k")
        assertEquals(2, doc.history.undoSize)
    }

    @Test
    fun `history is bounded and drops the oldest steps`() {
        val doc = Doc(manager(maxSteps = 3))
        listOf("1", "2", "3", "4", "5").forEach { doc.edit(it) }
        assertEquals(3, doc.history.undoSize)
        while (doc.undo()) Unit
        assertEquals("2", doc.state)
    }

    @Test
    fun `redo respects the bound`() {
        val doc = Doc(manager(maxSteps = 2))
        doc.edit("1")
        doc.edit("2")
        doc.undo()
        doc.undo()
        assertEquals("", doc.state)
        doc.redo()
        doc.redo()
        assertEquals("2", doc.state)
        assertEquals(2, doc.history.undoSize)
    }

    @Test
    fun `single step history`() {
        val doc = Doc(manager(maxSteps = 1))
        doc.edit("a")
        doc.edit("b")
        assertTrue(doc.undo())
        assertEquals("a", doc.state)
        assertFalse(doc.undo())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero max steps is rejected`() {
        manager(maxSteps = 0)
    }

    @Test
    fun `clear empties both stacks`() {
        val doc = Doc(manager())
        doc.edit("a")
        doc.edit("b")
        doc.undo()
        doc.history.clear()
        assertFalse(doc.history.canUndo)
        assertFalse(doc.history.canRedo)
    }

    @Test
    fun `listener and version fire on every history change`() {
        val history = manager()
        var calls = 0
        history.onChanged = { calls++ }

        history.record("", "k")
        history.record("a", "k")
        assertEquals("coalesced record does not change history", 1, calls)

        history.undo("ab")
        history.redo("")
        history.clear()
        assertEquals(4, calls)
        assertEquals(4L, history.version)
    }

    @Test
    fun `failed undo and redo do not notify`() {
        val history = manager()
        var calls = 0
        history.onChanged = { calls++ }
        history.undo("x")
        history.redo("x")
        assertEquals(0, calls)
        assertEquals(0L, history.version)
    }
}
