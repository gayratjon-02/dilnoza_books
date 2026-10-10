package dilnoza.books.editor.autosave

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSaverTest {

    // backgroundScope work is driven by advanceTimeBy/runCurrent; advanceUntilIdle does not wait for it.
    private fun TestScope.saver(onWrite: suspend () -> Unit) =
        AutoSaver(scope = backgroundScope, debounceMillis = 500, write = onWrite)

    @Test
    fun `burst of edits produces a single write after the debounce`() = runTest {
        var writes = 0
        val saver = saver { writes++ }

        repeat(10) {
            saver.markDirty()
            advanceTimeBy(100)
        }
        assertEquals(0, writes)

        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, writes)
        assertEquals(SaveStatus.Saved, saver.status.value)
    }

    @Test
    fun `flush writes immediately without waiting for the debounce`() = runTest {
        var writes = 0
        val saver = saver { writes++ }

        saver.markDirty()
        saver.flush()

        assertEquals(1, writes)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("pending debounce must not write again", 1, writes)
    }

    @Test
    fun `flush without changes does not write`() = runTest {
        var writes = 0
        val saver = saver { writes++ }

        saver.flush()

        assertEquals(0, writes)
    }

    @Test
    fun `failed write keeps changes dirty and is retried by the next flush`() = runTest {
        var attempts = 0
        val saver = saver {
            attempts++
            if (attempts == 1) error("disk full")
        }

        saver.markDirty()
        saver.flush()
        assertTrue(saver.status.value is SaveStatus.Error)

        saver.flush()
        assertEquals(2, attempts)
        assertEquals(SaveStatus.Saved, saver.status.value)
    }

    @Test
    fun `edit made during a write schedules another write`() = runTest {
        var writes = 0
        lateinit var saver: AutoSaver
        saver = saver {
            writes++
            if (writes == 1) saver.markDirty()
        }

        saver.markDirty()
        saver.flush()
        assertEquals(SaveStatus.Dirty, saver.status.value)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, writes)
        assertEquals(SaveStatus.Saved, saver.status.value)
    }
}
