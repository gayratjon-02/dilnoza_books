package dilnoza.books.editor.autosave

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SaveStatus {
    data object Saved : SaveStatus
    data object Dirty : SaveStatus
    data object Saving : SaveStatus
    data class Error(val message: String?) : SaveStatus
}

/**
 * Debounced writer: every [markDirty] restarts the timer, [flush] writes immediately.
 *
 * [scope] must outlive the screen (application scope) so a pending save still completes after the
 * ViewModel is cleared. Writes run under [NonCancellable] and are serialized, so two saves never overlap.
 * [write] is expected to collect and persist whatever is dirty at the moment it runs.
 */
class AutoSaver(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val write: suspend () -> Unit,
) {
    private val _status = MutableStateFlow<SaveStatus>(SaveStatus.Saved)
    val status: StateFlow<SaveStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private var pending: Job? = null

    @Volatile
    private var dirty = false

    fun markDirty() {
        dirty = true
        _status.value = SaveStatus.Dirty
        pending?.cancel()
        pending = scope.launch {
            delay(debounceMillis)
            flush()
        }
    }

    suspend fun flush() = withContext(NonCancellable) {
        mutex.withLock {
            if (!dirty) return@withLock
            dirty = false
            _status.value = SaveStatus.Saving
            runCatching { write() }
                .onSuccess { if (!dirty) _status.value = SaveStatus.Saved }
                .onFailure { error ->
                    dirty = true
                    _status.value = SaveStatus.Error(error.message)
                }
        }
    }

    fun flushInBackground() {
        pending?.cancel()
        scope.launch { flush() }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 500L
    }
}
