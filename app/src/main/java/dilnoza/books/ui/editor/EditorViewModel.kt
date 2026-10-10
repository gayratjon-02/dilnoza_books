package dilnoza.books.ui.editor

import android.os.ParcelFileDescriptor
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
import dilnoza.books.data.files.ImageStore
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.POSITION_STEP
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.domain.repository.BlockRepository
import dilnoza.books.domain.repository.ChapterRepository
import dilnoza.books.editor.autosave.AutoSaver
import dilnoza.books.editor.autosave.SaveStatus
import dilnoza.books.editor.richtext.RichTextEditor
import dilnoza.books.editor.richtext.split
import dilnoza.books.editor.richtext.subText
import dilnoza.books.ui.navigation.EditorRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class FocusRequest(val blockId: String, val token: Long = System.nanoTime())

enum class EditorMessage { IMAGE_FAILED }

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chapterRepository: ChapterRepository,
    private val blockRepository: BlockRepository,
    private val imageStore: ImageStore,
    @ApplicationScope private val appScope: CoroutineScope,
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
    var message by mutableStateOf<EditorMessage?>(null)
        private set
    private var bookId: String? = null
    private var focusedBlockId by mutableStateOf<String?>(null)

    val wordCount by derivedStateOf {
        blocks.sumOf { block ->
            countWords(block.value.text) + (block.table?.grid?.rows?.sumOf { row -> row.sumOf { countWords(it.text) } } ?: 0)
        }
    }
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
        bookId = chapter.bookId
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
        block.setValue(value)
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
        val block = focusedBlock?.takeIf { it.kind.isText } ?: return
        block.kind = if (block.kind == BlockKind.HEADING) BlockKind.PARAGRAPH else BlockKind.HEADING
        if (block.kind == BlockKind.HEADING && block.value.text.contains('\n')) {
            // Same length, so the spans still line up.
            block.setRich(block.rich.copy(text = block.rich.text.replace('\n', ' ')), block.value.selection)
        }
        markDirty(block.id)
    }

    fun deleteFocusedBlock() {
        val block = focusedBlock ?: return
        val index = blocks.indexOf(block)
        if (blocks.size == 1) {
            block.setRich(RichText.Empty)
            block.kind = BlockKind.PARAGRAPH
            block.image = null
            block.table = null
            markDirty(block.id)
            return
        }
        blocks.removeAt(index)
        synchronized(lock) {
            dirtyIds.remove(block.id)
            deletedIds.add(block.id)
        }
        val neighbour = blocks[(index - 1).coerceAtLeast(0)]
        neighbour.setSelection(TextRange(neighbour.value.text.length))
        focusRequest = FocusRequest(neighbour.id)
        markStructureDirty()
    }

    fun focusLastBlock() {
        val last = blocks.lastOrNull()
        if (last == null || !last.kind.isText) {
            // Nothing to type into at the end (e.g. a picture): continue in a new paragraph.
            val block = newBlock(BlockKind.PARAGRAPH)
            blocks.add(block)
            focusedBlockId = block.id
            focusRequest = FocusRequest(block.id)
            markStructureDirty()
            return
        }
        last.setSelection(TextRange(last.value.text.length))
        focusRequest = FocusRequest(last.id)
    }

    fun imageFile(fileName: String): File? = bookId?.let { imageStore.imageFile(it, fileName) }

    /** Index right after the focused block, or the end of the chapter when nothing is focused. */
    fun insertionIndex(): Int =
        blocks.indexOfFirst { it.id == focusedBlockId }.takeIf { it >= 0 }?.plus(1) ?: blocks.size

    /**
     * Inserts placeholders immediately and imports the pictures in the background.
     * Descriptors are opened by the caller while it still holds read access to the content.
     */
    fun insertImages(sources: List<ParcelFileDescriptor>, index: Int = insertionIndex()) {
        val book = bookId
        if (book == null || sources.isEmpty()) {
            sources.forEach { runCatching { it.close() } }
            return
        }
        val placeholders = sources.map {
            EditorBlock(UUID.randomUUID().toString(), BlockKind.IMAGE, image = ImageState.Pending)
        }
        blocks.addAll(index.coerceIn(0, blocks.size), placeholders)
        focusedBlockId = placeholders.last().id

        // Application scope: the import must finish and be saved even if the user leaves the screen.
        placeholders.zip(sources).forEach { (block, source) ->
            appScope.launch {
                val stored = runCatching { imageStore.import(book, source) }
                withContext(Dispatchers.Main.immediate) {
                    stored.onSuccess { image ->
                        block.image = ImageState(image.fileName, image.width, image.height)
                        markStructureDirty()
                    }.onFailure {
                        blocks.remove(block)
                        message = EditorMessage.IMAGE_FAILED
                    }
                }
            }
        }
    }

    fun selectBlock(id: String) {
        focusedBlockId = id
    }

    fun setImageWidth(block: EditorBlock, percent: Int) {
        val image = block.image ?: return
        val clamped = percent.coerceIn(BlockContent.Image.MIN_WIDTH_PERCENT, BlockContent.Image.MAX_WIDTH_PERCENT)
        if (clamped == image.widthPercent) return
        block.image = image.copy(widthPercent = clamped)
        markDirty(block.id)
    }

    fun setImageAlign(block: EditorBlock, align: TextAlignment) {
        val image = block.image ?: return
        block.image = image.copy(align = align)
        markDirty(block.id)
    }

    fun moveFocusedBlock(offset: Int) {
        val index = blocks.indexOfFirst { it.id == focusedBlockId }
        val target = index + offset
        if (index < 0 || target !in blocks.indices) return
        blocks.add(target, blocks.removeAt(index))
        markStructureDirty()
    }

    fun onMessageShown() {
        message = null
    }

    fun flush() {
        autoSaver.flushInBackground()
    }

    override fun onCleared() {
        autoSaver.flushInBackground()
    }

    private fun splitHeading(block: EditorBlock, value: TextFieldValue) {
        val breakAt = value.text.indexOf('\n')
        val edited = RichTextEditor.applyTextChange(block.rich, value.text, cursor = value.selection.end)
        val (heading, tail) = edited.split(breakAt)
        val rest = tail.subText(1, tail.text.length)
        block.setRich(heading)
        val paragraph = newBlock(BlockKind.PARAGRAPH, rest.copy(text = rest.text.replace('\n', ' ')))
        blocks.add(blocks.indexOf(block) + 1, paragraph)
        focusRequest = FocusRequest(paragraph.id)
        markStructureDirty()
    }

    private fun newBlock(kind: BlockKind, rich: RichText = RichText.Empty) =
        EditorBlock(UUID.randomUUID().toString(), kind, rich, TextRange(0))

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
                if (block.id in ids && !block.isPendingImage) {
                    block.toBlock(chapterId, (index + 1) * POSITION_STEP)
                } else {
                    null
                }
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
