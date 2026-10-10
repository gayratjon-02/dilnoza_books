package dilnoza.books.ui.editor

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dilnoza.books.core.di.ApplicationScope
import dilnoza.books.domain.model.POSITION_STEP
import dilnoza.books.domain.repository.BlockRepository
import dilnoza.books.domain.repository.ChapterRepository
import dilnoza.books.editor.autosave.AutoSaver
import dilnoza.books.editor.autosave.SaveStatus
import dilnoza.books.ui.navigation.EditorRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class FocusRequest(val blockId: String, val token: Long = System.nanoTime())

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chapterRepository: ChapterRepository,
    private val blockRepository: BlockRepository,
    @ApplicationScope appScope: CoroutineScope,
) : ViewModel() {

    private val chapterId = savedStateHandle.toRoute<EditorRoute>().chapterId

    var loaded by mutableStateOf(false)
        private set
    var missing by mutableStateOf(false)
        private set
    var title by mutableStateOf(TextFieldValue())
        private set
    val blocks = mutableStateListOf<EditorBlock>()
    var focusRequest by mutableStateOf<FocusRequest?>(null)
        private set
    private var focusedBlockId by mutableStateOf<String?>(null)

    val wordCount by derivedStateOf { blocks.sumOf { countWords(it.value.text) } }
    val focusedBlock: EditorBlock? get() = blocks.firstOrNull { it.id == focusedBlockId }

    // Guarded by [lock]: what changed since the last successful save.
    private val lock = Any()
    private val dirtyIds = mutableSetOf<String>()
    private val deletedIds = mutableSetOf<String>()
    private var titleDirty = false

    private val autoSaver = AutoSaver(scope = appScope, write = ::persist)
    val saveStatus: StateFlow<SaveStatus> = autoSaver.status

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val chapter = chapterRepository.getChapter(chapterId)
        if (chapter == null) {
            missing = true
            return
        }
        title = TextFieldValue(chapter.title)
        blocks.addAll(blockRepository.getBlocks(chapterId).map(EditorBlock::from))
        if (blocks.isEmpty()) {
            val first = newBlock(BlockKind.PARAGRAPH)
            blocks.add(first)
            markDirty(first.id)
            focusRequest = FocusRequest(first.id)
        }
        loaded = true
    }

    fun onTitleChange(value: TextFieldValue) {
        if (value.text.contains('\n')) {
            // Enter in the title jumps to the text instead of inserting a line break.
            title = value.copy(text = value.text.replace("\n", ""), selection = TextRange(value.text.indexOf('\n')))
            blocks.firstOrNull()?.let { focusRequest = FocusRequest(it.id) }
        } else {
            title = value
        }
        synchronized(lock) { titleDirty = true }
        autoSaver.markDirty()
    }

    fun onBlockChange(block: EditorBlock, value: TextFieldValue) {
        val textChanged = value.text != block.value.text
        if (block.kind == BlockKind.HEADING && value.text.contains('\n')) {
            splitHeading(block, value)
            return
        }
        block.value = value
        if (textChanged) markDirty(block.id)
    }

    fun onBlockFocused(id: String) {
        focusedBlockId = id
    }

    fun onFocusRequestHandled() {
        focusRequest = null
    }

    fun addBlock(kind: BlockKind) {
        val index = blocks.indexOfFirst { it.id == focusedBlockId }.takeIf { it >= 0 }?.plus(1) ?: blocks.size
        val block = newBlock(kind)
        blocks.add(index, block)
        focusRequest = FocusRequest(block.id)
        markStructureDirty()
    }

    fun toggleFocusedKind() {
        val block = focusedBlock ?: return
        block.kind = if (block.kind == BlockKind.HEADING) BlockKind.PARAGRAPH else BlockKind.HEADING
        if (block.kind == BlockKind.HEADING && block.value.text.contains('\n')) {
            block.value = block.value.copy(text = block.value.text.replace('\n', ' '))
        }
        markDirty(block.id)
    }

    fun deleteFocusedBlock() {
        val block = focusedBlock ?: return
        val index = blocks.indexOf(block)
        if (blocks.size == 1) {
            block.value = TextFieldValue()
            block.kind = BlockKind.PARAGRAPH
            markDirty(block.id)
            return
        }
        blocks.removeAt(index)
        synchronized(lock) {
            dirtyIds.remove(block.id)
            deletedIds.add(block.id)
        }
        val neighbour = blocks[(index - 1).coerceAtLeast(0)]
        neighbour.value = neighbour.value.copy(selection = TextRange(neighbour.value.text.length))
        focusRequest = FocusRequest(neighbour.id)
        markStructureDirty()
    }

    fun focusLastBlock() {
        val last = blocks.lastOrNull() ?: return
        last.value = last.value.copy(selection = TextRange(last.value.text.length))
        focusRequest = FocusRequest(last.id)
    }

    fun flush() {
        autoSaver.flushInBackground()
    }

    override fun onCleared() {
        autoSaver.flushInBackground()
    }

    private fun splitHeading(block: EditorBlock, value: TextFieldValue) {
        val breakAt = value.text.indexOf('\n')
        val headingText = value.text.substring(0, breakAt)
        val rest = value.text.substring(breakAt + 1).replace('\n', ' ')
        block.value = TextFieldValue(headingText, TextRange(headingText.length))
        val paragraph = newBlock(BlockKind.PARAGRAPH, rest)
        blocks.add(blocks.indexOf(block) + 1, paragraph)
        focusRequest = FocusRequest(paragraph.id)
        markStructureDirty()
    }

    private fun newBlock(kind: BlockKind, text: String = "") =
        EditorBlock(UUID.randomUUID().toString(), kind, TextFieldValue(text, TextRange(0)))

    private fun markDirty(id: String) {
        synchronized(lock) { dirtyIds.add(id) }
        autoSaver.markDirty()
    }

    /** Order changed: every block's position must be rewritten. */
    private fun markStructureDirty() {
        synchronized(lock) { blocks.mapTo(dirtyIds) { it.id } }
        autoSaver.markDirty()
    }

    private suspend fun persist() {
        if (!loaded) return
        val (ids, deleted, saveTitle) = synchronized(lock) {
            val snapshot = Triple(dirtyIds.toSet(), deletedIds.toSet(), titleDirty)
            dirtyIds.clear()
            deletedIds.clear()
            titleDirty = false
            snapshot
        }
        // Snapshot state is mutated on the main thread; read it there for a consistent view.
        val (upserts, titleText) = withContext(Dispatchers.Main.immediate) {
            blocks.mapIndexedNotNull { index, block ->
                if (block.id in ids) block.toBlock(chapterId, (index + 1) * POSITION_STEP) else null
            } to title.text
        }
        try {
            blockRepository.saveChapter(
                chapterId = chapterId,
                title = titleText.takeIf { saveTitle && it.isNotBlank() },
                upserts = upserts,
                deletedIds = deleted,
            )
        } catch (error: Throwable) {
            synchronized(lock) {
                dirtyIds.addAll(ids)
                deletedIds.addAll(deleted)
                titleDirty = titleDirty || saveTitle
            }
            throw error
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")

        fun countWords(text: String): Int = text.split(WHITESPACE).count { it.isNotEmpty() }
    }
}
