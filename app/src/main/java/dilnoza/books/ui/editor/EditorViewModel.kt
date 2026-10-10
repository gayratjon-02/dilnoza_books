package dilnoza.books.ui.editor

import android.os.ParcelFileDescriptor
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.ui.editor.page.BlockLayoutCache
import dilnoza.books.ui.editor.page.layoutContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dilnoza.books.core.di.ApplicationScope
import dilnoza.books.data.files.ImageStore
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.PageSize
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.domain.repository.BlockRepository
import dilnoza.books.domain.repository.BookRepository
import dilnoza.books.domain.repository.ChapterRepository
import dilnoza.books.editor.autosave.AutoSaver
import dilnoza.books.editor.autosave.SaveStatus
import dilnoza.books.editor.commands.ListItem
import dilnoza.books.editor.commands.ListKind
import dilnoza.books.editor.commands.ListNumbering
import dilnoza.books.editor.commands.SlashCommands
import dilnoza.books.editor.commands.SlashQuery
import dilnoza.books.editor.history.UndoManager
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.editor.search.SearchMatch
import dilnoza.books.editor.search.TextSearch
import dilnoza.books.editor.richtext.plus
import dilnoza.books.editor.richtext.subText
import dilnoza.books.ui.editor.page.BookPageCounter
import dilnoza.books.ui.navigation.EditorRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Moves the caret to a block, or to [cell] of a table block (its first cell when null). */
data class FocusRequest(val blockId: String, val cell: CellKey? = null, val token: Long = System.nanoTime())

enum class EditorMessage { IMAGE_FAILED }

/** What the screen must do after a "/" command: nothing, or ask for pictures or a table size. */
enum class SlashOutcome { DONE, PICK_IMAGES, PICK_TABLE_SIZE }

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chapterRepository: ChapterRepository,
    private val blockRepository: BlockRepository,
    private val bookRepository: BookRepository,
    private val pageCounter: BookPageCounter,
    private val imageStore: ImageStore,
    @ApplicationScope private val appScope: CoroutineScope,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val chapterId = savedStateHandle.toRoute<EditorRoute>().chapterId

    var loaded by mutableStateOf(false)
        private set
    var missing by mutableStateOf(false)
        private set
    var title by mutableStateOf(TextFieldValue())
        private set
    var pageSpec by mutableStateOf(PageSpec.of(PageSize.A5, DEFAULT_MARGIN_MM))
        private set

    /** Measured blocks for the pagination; kept here so rotation does not measure the chapter again. */
    val layoutCache: BlockLayoutCache = createFontFamilyResolver(context).let { fonts ->
        BlockLayoutCache { spec ->
            val textMeasurer = TextMeasurer(fonts, PageMeasurer.PointDensity, LayoutDirection.Ltr, cacheSize = 32)
            PageMeasurer(textMeasurer, spec) { it.toAnnotatedString() }
        }
    }

    /** Page magnification over "page fits the screen width"; line breaks never change with it. */
    var zoom by mutableFloatStateOf(1f)
        private set

    /** Number printed on this chapter's first page; pages are numbered across the whole book. */
    var firstPageNumber by mutableIntStateOf(1)
        private set
    val blocks = mutableStateListOf<EditorBlock>()
    var focusRequest by mutableStateOf<FocusRequest?>(null)
        private set
    var message by mutableStateOf<EditorMessage?>(null)
        private set
    /** Set when the keyboard focus must leave the text, e.g. to select a picture; cleared by the screen. */
    var clearFocusRequest by mutableStateOf<Long?>(null)
        private set
    private var bookId: String? = null
    private var focusedBlockId by mutableStateOf<String?>(null)
    /** The table cell holding the caret; only meaningful while its table is the focused block. */
    var focusedCell by mutableStateOf<CellKey?>(null)
        private set
    // Where the writer typed "/" to open the command menu; the menu stays while that query is active.
    private var slashAnchor by mutableStateOf<Pair<String, Int>?>(null)
    // A "/table" command waiting for its size: the block and the command text the table replaces.
    private var pendingTableCommand: Triple<String, Int, Int>? = null
    // Style chosen with a collapsed caret, given to the next typed characters until the caret moves.
    private var typingStyle by mutableStateOf<SpanStyleData?>(null)

    private val history = UndoManager<HistoryEntry>(coalesceWindowMillis = TYPING_COALESCE_MILLIS)
    private var historyVersion by mutableLongStateOf(0L)
    // Typing after a space starts a new undo step, so undo takes back one word at a time.
    private var lastTypedWhitespace = false
    // Pictures still being imported, and those already stored, so undo can bring a placeholder back right.
    private val importingImages = mutableSetOf<String>()
    private val importedImages = mutableMapOf<String, ImageState>()
    // Set on the main thread once the screen is gone; imports finishing later must not save the stale blocks.
    private var cleared = false
    var searchOpen by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var replacement by mutableStateOf("")
        private set
    private var matchIndex by mutableIntStateOf(0)
    /** Matches of the search in the text blocks, in reading order; highlighted only while searching. */
    val searchMatches by derivedStateOf {
        val query = searchQuery
        if (!searchOpen || query.isEmpty()) {
            emptyList()
        } else {
            TextSearch.findAll(blocks.filter { it.kind.isText }.map { it.id to it.value.text }, query)
        }
    }
    val matchesByBlock by derivedStateOf {
        searchMatches.groupBy({ it.unitId }, { it.start until it.end })
    }
    val currentMatch: SearchMatch? get() = searchMatches.getOrNull(currentMatchIndex())
    val canUndo: Boolean get() = historyVersion.let { history.canUndo }
    val canRedo: Boolean get() = historyVersion.let { history.canRedo }

    val wordCount by derivedStateOf {
        blocks.sumOf { block ->
            countWords(block.value.text) + (block.table?.grid?.rows?.sumOf { row -> row.sumOf { countWords(it.text) } } ?: 0)
        }
    }
    /** Bullet glyph or number shown before each list item, by block id. */
    val listLabels by derivedStateOf {
        val items = blocks.map { block ->
            when (block.kind) {
                BlockKind.BULLET -> ListItem(ListKind.BULLET, block.indent)
                BlockKind.NUMBERED -> ListItem(ListKind.ORDERED, block.indent)
                else -> ListItem.NONE
            }
        }
        blocks.zip(ListNumbering.labels(items)) { block, label -> block.id to label }.toMap()
    }
    val focusedBlock: EditorBlock? get() = blocks.firstOrNull { it.id == focusedBlockId }
    /** The open "/" command query in the focused block, or null when the menu is closed. */
    val slashQuery: SlashQuery?
        get() {
            val (blockId, slashIndex) = slashAnchor ?: return null
            val block = focusedTextBlock()?.takeIf { it.id == blockId } ?: return null
            val selection = block.value.selection
            if (!selection.collapsed) return null
            return SlashCommands.query(block.value.text, selection.end)?.takeIf { it.slashIndex == slashIndex }
        }
    /** Format bar state, or null when no text block is focused. */
    val formatState: FormatState? get() = focusedTextBlock()?.formatState(typingStyle)

    // Guarded by [lock]: what changed since the last successful save.
    private val lock = Any()
    private val dirtyIds = mutableSetOf<String>()
    private val deletedIds = mutableSetOf<String>()
    private var titleDirty = false

    private val editing = BlockEditing(blocks, ::newBlock)

    private val autoSaver = AutoSaver(scope = appScope, write = ::persist)
    val saveStatus: StateFlow<SaveStatus> = autoSaver.status

    init {
        history.onChanged = { historyVersion = history.version }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val chapter = chapterRepository.getChapter(chapterId)
        if (chapter == null) {
            missing = true
            return
        }
        bookId = chapter.bookId
        val firstBook = bookRepository.observeBook(chapter.bookId).filterNotNull().first()
        viewModelScope.launch {
            bookRepository.observeBook(chapter.bookId).filterNotNull().collect { book ->
                val spec = PageSpec.of(book.pageSize, book.marginMm)
                if (loaded && spec != pageSpec) primeLayout(spec)
                pageSpec = spec
                // Pages are numbered across the book: this chapter starts after all earlier ones.
                val earlier = chapterRepository.observeChapters(chapter.bookId).first().takeWhile { it.id != chapterId }
                firstPageNumber = 1 + pageCounter.pageCounts(earlier, spec).sum()
            }
        }
        title = TextFieldValue(chapter.title)
        blocks.addAll(blockRepository.getBlocks(chapterId).map(EditorBlock::from))
        if (blocks.isEmpty()) {
            val first = newBlock(BlockKind.PARAGRAPH)
            blocks.add(first)
            markDirty(first.id)
            focusRequest = FocusRequest(first.id)
        }
        val spec = PageSpec.of(firstBook.pageSize, firstBook.marginMm)
        primeLayout(spec)
        pageSpec = spec
        loaded = true
    }

    /** Measures the whole chapter for [spec] off the main thread, before the screen paginates it. */
    private suspend fun primeLayout(spec: PageSpec) {
        val titleText = title.text
        val contents = blocks.map { it.id to it.layoutContent() }
        withContext(Dispatchers.Default) { layoutCache.prime(spec, titleText, contents) }
    }

    fun onTitleChange(value: TextFieldValue) {
        if (value.text != title.text) history.record(historyEntry(), TITLE_TYPING)
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
        val old = block.value.text
        val textChanged = value.text != old
        if (!textChanged) {
            if (value.selection != block.value.selection) {
                typingStyle = null
                history.breakCoalescing()
            }
            block.setValue(value, typingStyle)
        } else if (!block.kind.isText) {
            history.record(historyEntry(), typingKey(block.id, old, value))
            block.setValue(value, typingStyle)
            markDirty(block.id)
        } else {
            val before = historyEntry()
            val kind = block.kind
            val key = typingKey(block.id, old, value)
            val edit = editing.type(block, value, typingStyle)
            apply(edit)
            // Enter, pasted lines and markdown shortcuts are steps of their own.
            history.record(before, key.takeIf { edit is BlockEdit.Content && block.kind == kind })
        }
        updateSlashAnchor(block, typedSlash = textChanged && value.text.length > old.length)
    }

    /** Runs a "/" menu command; the outcome says whether the screen must ask for pictures or a table size. */
    fun runSlashCommand(command: SlashCommand): SlashOutcome {
        val block = focusedTextBlock() ?: return SlashOutcome.DONE
        val query = slashQuery ?: return SlashOutcome.DONE
        slashAnchor = null
        val end = query.slashIndex + 1 + query.filter.length
        if (command.kind != BlockKind.TABLE) history.record(historyEntry())
        when (command.kind) {
            BlockKind.IMAGE -> {
                val text = block.rich
                block.setRich(text.subText(0, query.slashIndex) + text.subText(end, text.text.length), TextRange(query.slashIndex))
                markDirty(block.id)
                return SlashOutcome.PICK_IMAGES
            }
            // The command text stays until a size is picked, so cancelling leaves the text as typed.
            BlockKind.TABLE -> {
                pendingTableCommand = Triple(block.id, query.slashIndex, end)
                return SlashOutcome.PICK_TABLE_SIZE
            }
            else -> apply(editing.insert(block, query.slashIndex, end, command.kind, command.headingLevel))
        }
        return SlashOutcome.DONE
    }

    /**
     * Inserts a table in place of a pending "/table" command, or else where pictures would go, with a
     * paragraph after it to continue writing; the caret goes to its first cell.
     */
    fun insertTable(rows: Int, columns: Int, headerRow: Boolean) {
        history.record(historyEntry())
        val table = TableState.create(rows, columns, headerRow)
        val pending = pendingTableCommand
        pendingTableCommand = null
        val commandBlock = pending?.let { (id, _, end) ->
            blocks.firstOrNull { it.id == id }?.takeIf { it.kind.isText && end <= it.rich.text.length }
        }
        if (pending != null && commandBlock != null) {
            apply(editing.insert(commandBlock, pending.second, pending.third, BlockKind.TABLE, table = table))
            return
        }
        val block = EditorBlock(UUID.randomUUID().toString(), BlockKind.TABLE, table = table)
        blocks.add(insertionIndex(), block)
        editing.textBlockAfter(block)
        apply(BlockEdit.Structure(focusId = block.id))
    }

    fun cancelTableSize() {
        pendingTableCommand = null
    }

    fun dismissSlashMenu() {
        slashAnchor = null
    }

    private fun updateSlashAnchor(block: EditorBlock, typedSlash: Boolean) {
        val selection = block.value.selection
        val query = if (block.kind.isText && selection.collapsed) SlashCommands.query(block.value.text, selection.end) else null
        slashAnchor = when {
            query == null -> null
            typedSlash && query.filter.isEmpty() -> block.id to query.slashIndex
            slashAnchor == (block.id to query.slashIndex) -> slashAnchor
            else -> null
        }
    }

    fun onBackspaceAtStart(block: EditorBlock) = undoable { editing.backspaceAtStart(block)?.let(::apply) }

    fun onDeleteAtEnd(block: EditorBlock) = undoable { editing.deleteAtEnd(block)?.let(::apply) }

    fun onClearFocusHandled() {
        clearFocusRequest = null
    }

    fun onBlockFocused(id: String) {
        if (id != focusedBlockId) {
            typingStyle = null
            history.breakCoalescing()
        }
        focusedBlockId = id
        focusedCell = null
    }

    fun onCellFocused(block: EditorBlock, cell: CellKey) {
        if (cell != focusedCell) history.breakCoalescing()
        typingStyle = null
        slashAnchor = null
        focusedBlockId = block.id
        focusedCell = cell
    }

    fun onCellChange(block: EditorBlock, cell: CellKey, text: RichText) {
        val table = block.table ?: return
        val updated = table.updateCell(cell, text)
        if (updated == table) return
        history.record(historyEntry(), CellTyping(block.id, cell))
        block.table = updated
        markDirty(block.id)
    }

    /**
     * Enter or Tab in a cell: the caret moves to the next cell ([forward]) or the previous one. Past the
     * last cell, Tab adds a row and Enter ([enter]) continues writing below the table.
     */
    fun onCellNext(block: EditorBlock, cell: CellKey, forward: Boolean, enter: Boolean) {
        val table = block.table ?: return
        val next = table.neighbour(cell, forward)
        when {
            next != null -> focusRequest = FocusRequest(block.id, next)
            !forward -> Unit
            enter -> leaveTable(block)
            else -> {
                history.record(historyEntry())
                val (updated, first) = table.appendRow()
                block.table = updated
                markDirty(block.id)
                focusRequest = FocusRequest(block.id, first)
            }
        }
    }

    /** Changes the focused table around the focused cell; the caret follows to the cell [action] picks. */
    fun onTableAction(action: TableAction) {
        val block = focusedBlock?.takeIf { it.kind == BlockKind.TABLE } ?: return
        val table = block.table ?: return
        val cell = focusedCell?.takeIf(table::contains) ?: return
        val (updated, caret) = table.apply(action, cell)
        if (updated == table) return
        history.record(historyEntry())
        block.table = updated
        markDirty(block.id)
        focusedCell = caret
        focusRequest = FocusRequest(block.id, caret)
    }

    private fun leaveTable(block: EditorBlock) {
        val index = blocks.indexOf(block)
        val next = blocks.getOrNull(index + 1)
        if (next != null && next.kind.isText) {
            next.setSelection(TextRange(0))
            focusRequest = FocusRequest(next.id)
            return
        }
        val paragraph = newBlock(BlockKind.PARAGRAPH)
        blocks.add(index + 1, paragraph)
        focusRequest = FocusRequest(paragraph.id)
        markStructureDirty()
    }

    fun format(change: FormatChange) {
        val block = focusedTextBlock() ?: return
        val before = block.rich
        val entry = historyEntry()
        typingStyle = block.applyFormat(change, typingStyle)
        if (block.rich != before) {
            history.record(entry)
            markDirty(block.id)
        }
    }

    fun setFocusedAlign(align: TextAlignment) {
        val block = focusedTextBlock() ?: return
        if (block.align == align) return
        history.record(historyEntry())
        block.align = align
        markDirty(block.id)
    }

    /** Turns the focused text block into [kind]; [headingLevel] applies to headings. */
    fun setFocusedKind(kind: BlockKind, headingLevel: Int = BlockContent.Heading.MIN_LEVEL) {
        val block = focusedTextBlock() ?: return
        if (!kind.isText || block.kind == kind && (kind != BlockKind.HEADING || block.headingLevel == headingLevel)) return
        history.record(historyEntry())
        editing.convert(block, kind, headingLevel)
        markDirty(block.id)
    }

    /** Moves the focused list item one level in (+1) or out (-1). */
    fun indentFocused(delta: Int) {
        val block = focusedTextBlock()?.takeIf { it.kind == BlockKind.BULLET || it.kind == BlockKind.NUMBERED } ?: return
        val indent = (block.indent + delta).coerceIn(0, BlockContent.ListItem.MAX_INDENT)
        if (indent == block.indent) return
        history.record(historyEntry())
        block.indent = indent
        markDirty(block.id)
    }

    fun onFocusRequestHandled() {
        focusRequest = null
    }

    fun addBlock(kind: BlockKind) {
        history.record(historyEntry())
        val index = blocks.indexOfFirst { it.id == focusedBlockId }.takeIf { it >= 0 }?.plus(1) ?: blocks.size
        val block = newBlock(kind)
        blocks.add(index, block)
        focusRequest = FocusRequest(block.id)
        markStructureDirty()
    }

    fun deleteFocusedBlock() {
        val block = focusedBlock ?: return
        val index = blocks.indexOf(block)
        history.record(historyEntry())
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

    /**
     * Smallest zoom: the whole page in view when 100% (page fits the width) shows less, as in landscape
     * or on a tablet; never below [ABSOLUTE_MIN_ZOOM] and never above 100%.
     */
    var minZoom by mutableFloatStateOf(1f)
        private set

    val canZoomIn: Boolean get() = zoom < MAX_ZOOM - ZOOM_EPSILON
    val canZoomOut: Boolean get() = zoom > minZoom + ZOOM_EPSILON

    /** The screen reports the zoom at which a whole page fits; the current zoom is left as it is. */
    fun setPageFitZoom(fit: Float) {
        minZoom = fit.coerceIn(ABSOLUTE_MIN_ZOOM, 1f)
    }

    fun zoomIn() {
        zoom = ZOOM_STEPS.firstOrNull { it > zoom + ZOOM_EPSILON } ?: MAX_ZOOM
    }

    fun zoomOut() {
        zoom = (ZOOM_STEPS.lastOrNull { it < zoom - ZOOM_EPSILON } ?: minZoom).coerceAtLeast(minZoom)
    }

    /** Pinch: continuous zoom by [factor], kept within the supported range. */
    fun zoomBy(factor: Float) {
        val low = minOf(minZoom, zoom)
        zoom = (zoom * factor).coerceIn(low, MAX_ZOOM)
    }

    /** Starts a new page after the last block and puts the caret on it. */
    fun addPage() {
        history.record(historyEntry())
        val pageBreak = newBlock(BlockKind.PAGE_BREAK)
        val paragraph = newBlock(BlockKind.PARAGRAPH)
        blocks.add(pageBreak)
        blocks.add(paragraph)
        focusedBlockId = paragraph.id
        focusRequest = FocusRequest(paragraph.id)
        markStructureDirty()
    }

    fun imageFile(fileName: String): File? = bookId?.let { imageStore.imageFile(it, fileName) }

    /** Where new pictures go: after the focused block (at it, on an empty line), or at the end. */
    fun insertionIndex(): Int {
        val index = blocks.indexOfFirst { it.id == focusedBlockId }
        if (index < 0) return blocks.size
        val focused = blocks[index]
        // On an empty line the picture goes above it, and writing continues right below the picture.
        return if (focused.kind.isText && focused.value.text.isEmpty()) index else index + 1
    }

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
        history.record(historyEntry())
        placeholders.mapTo(importingImages) { it.id }
        blocks.addAll(index.coerceIn(0, blocks.size), placeholders)
        focusedBlockId = placeholders.last().id

        // Application scope: the import must finish and be saved even if the user leaves the screen.
        placeholders.zip(sources).forEach { (block, source) ->
            appScope.launch {
                val stored = runCatching { imageStore.import(book, source) }
                val orphan = withContext(Dispatchers.Main.immediate) {
                    importingImages.remove(block.id)
                    // Undo may have replaced the placeholder with another instance of the same block.
                    val current = blocks.firstOrNull { it.id == block.id }
                    val state = stored.getOrNull()?.let { ImageState(it.fileName, it.width, it.height) }
                    when {
                        state == null -> {
                            current?.let(blocks::remove)
                            message = EditorMessage.IMAGE_FAILED
                            null
                        }
                        // Another editor may have opened the chapter since: this one's other blocks are stale.
                        cleared -> current?.let {
                            it.image = state
                            orphanImage(it)
                        }
                        else -> {
                            importedImages[block.id] = state
                            current?.image = state
                            markStructureDirty()
                            null
                        }
                    }
                }
                orphan?.let { saveOrphanImage(it) }
            }
        }
    }

    /** [block], which now holds its imported picture, with the saved blocks around it, nearest first. */
    private fun orphanImage(block: EditorBlock): OrphanImage {
        val saved = blocks.filter { !it.isPendingImage }.map { it.id }
        val index = saved.indexOf(block.id)
        return OrphanImage(
            block = block.toBlock(chapterId, position = 0),
            before = saved.subList(0, index).asReversed(),
            after = saved.subList(index + 1, saved.size),
        )
    }

    /** Stores only the picture, between its neighbours as they are stored now, leaving every other block as it is. */
    private suspend fun saveOrphanImage(orphan: OrphanImage) {
        runCatching {
            val stored = blockRepository.getBlocks(chapterId)
            val position = positionAmong(stored, orphan.before, orphan.after)
            blockRepository.saveChapter(chapterId, null, listOf(orphan.block.copy(position = position)), emptyList())
        }
    }

    fun selectBlock(id: String) {
        typingStyle = null
        focusedBlockId = id
        focusedCell = null
    }

    fun setImageWidth(block: EditorBlock, percent: Int) {
        val image = block.image ?: return
        val clamped = percent.coerceIn(BlockContent.Image.MIN_WIDTH_PERCENT, BlockContent.Image.MAX_WIDTH_PERCENT)
        if (clamped == image.widthPercent) return
        history.record(historyEntry(), ImageResize(block.id))
        block.image = image.copy(widthPercent = clamped)
        markDirty(block.id)
    }

    fun setImageAlign(block: EditorBlock, align: TextAlignment) {
        val image = block.image ?: return
        if (image.align == align) return
        history.record(historyEntry())
        block.image = image.copy(align = align)
        markDirty(block.id)
    }

    fun moveFocusedBlock(offset: Int) {
        val index = blocks.indexOfFirst { it.id == focusedBlockId }
        val target = index + offset
        if (index < 0 || target !in blocks.indices) return
        history.record(historyEntry())
        val block = blocks.removeAt(index)
        blocks.add(target, block)
        markStructureDirty()
        // The caret stays in the moved block, and the block is scrolled into view if it left it.
        when {
            block.kind.isText -> focusRequest = FocusRequest(block.id)
            block.kind == BlockKind.TABLE && focusedCell != null -> focusRequest = FocusRequest(block.id, focusedCell)
        }
    }

    /** Whether the focused block can move by [offset] places. */
    fun canMoveFocused(offset: Int): Boolean {
        val index = blocks.indexOfFirst { it.id == focusedBlockId }
        return index >= 0 && index + offset in blocks.indices
    }

    fun undo() {
        history.undo(historyEntry())?.let(::restore)
    }

    fun redo() {
        history.redo(historyEntry())?.let(::restore)
    }

    private fun historyEntry(): HistoryEntry {
        val block = focusedBlock
        return HistoryEntry(
            content = ChapterSnapshot(title.text, blocks.map { it.snapshot() }),
            caret = block?.let { Caret(it.id, it.value.selection, focusedCell.takeIf { _ -> it.kind == BlockKind.TABLE }) },
        )
    }

    /** Records an undo step for [action] if it changed the chapter. */
    private inline fun undoable(action: () -> Unit) {
        val before = historyEntry()
        action()
        if (historyEntry().content != before.content) history.record(before)
    }

    /** Typing in one block is one step per word; deleting is a step of its own. */
    private fun typingKey(blockId: String, old: String, value: TextFieldValue): Any {
        val inserted = value.text.length > old.length
        val typed = if (inserted) value.text.getOrNull(value.selection.end - 1) else null
        if (typed != null && !typed.isWhitespace() && lastTypedWhitespace) history.breakCoalescing()
        lastTypedWhitespace = typed?.isWhitespace() == true
        return BlockTyping(blockId, inserted)
    }

    private fun restore(entry: HistoryEntry) {
        typingStyle = null
        slashAnchor = null
        pendingTableCommand = null
        lastTypedWhitespace = false
        val content = entry.content
        if (content.title != title.text) {
            title = TextFieldValue(content.title, TextRange(content.title.length))
            synchronized(lock) { titleDirty = true }
            autoSaver.markDirty()
        }
        val result = restoreBlocks(blocks, content.blocks) { id ->
            importedImages[id] ?: ImageState.Pending.takeIf { id in importingImages }
        }
        synchronized(lock) {
            dirtyIds.removeAll(result.removedIds)
            deletedIds.addAll(result.removedIds)
            deletedIds.removeAll(result.addedIds)
            dirtyIds.addAll(result.changedIds)
        }
        if (result.reordered) markStructureDirty() else autoSaver.markDirty()
        // Only the title changed: the caret stays where it is.
        if (!result.reordered && result.changedIds.isEmpty()) return

        val caret = entry.caret?.takeIf { caret -> blocks.any { it.id == caret.blockId } }
        val focusId = caret?.blockId
            ?: (result.changedIds + result.addedIds).let { ids -> blocks.firstOrNull { it.id in ids }?.id }
            ?: return
        val block = blocks.first { it.id == focusId }
        when {
            block.kind.isText -> {
                val length = block.value.text.length
                val selection = caret?.selection ?: TextRange(length)
                block.setSelection(TextRange(selection.start.coerceAtMost(length), selection.end.coerceAtMost(length)))
                focusedBlockId = block.id
                focusRequest = FocusRequest(block.id)
            }
            block.kind == BlockKind.TABLE -> {
                focusedBlockId = block.id
                focusRequest = FocusRequest(block.id, caret?.cell)
            }
            else -> {
                selectBlock(block.id)
                clearFocusRequest = System.nanoTime()
            }
        }
    }

    fun openSearch() {
        searchOpen = true
        val block = focusedTextBlock() ?: return
        val selection = block.value.selection
        val selected = block.value.text.substring(selection.min, selection.max)
        if (selected.isNotBlank() && '\n' !in selected) onSearchQueryChange(selected)
    }

    fun closeSearch() {
        searchOpen = false
    }

    /** Changes the search; the current match becomes the first one at or after the caret. */
    fun onSearchQueryChange(query: String) {
        searchQuery = query
        val block = focusedTextBlock()
        val order = blocks.indexOf(block)
        val caret = block?.value?.selection?.min ?: 0
        matchIndex = firstMatchFrom(order, caret)
    }

    fun onReplacementChange(text: String) {
        replacement = text
    }

    fun nextMatch() = stepMatch(1)

    fun previousMatch() = stepMatch(-1)

    private fun stepMatch(delta: Int) {
        val count = searchMatches.size
        if (count == 0) return
        matchIndex = (currentMatchIndex() + delta + count) % count
    }

    /** Replaces the current match and moves on to the next one. */
    fun replaceCurrent() {
        val match = currentMatch ?: return
        val block = blocks.firstOrNull { it.id == match.unitId } ?: return
        history.record(historyEntry())
        val replaced = block.rich.replaceMatches(listOf(match), replacement, block.value.selection)
        block.setRich(replaced.rich, replaced.selection)
        markDirty(block.id)
        matchIndex = firstMatchFrom(blocks.indexOf(block), match.start + replacement.length)
    }

    /** Replaces every match in one undo step. */
    fun replaceAll() {
        val byBlock = searchMatches.groupBy { it.unitId }
        if (byBlock.isEmpty()) return
        history.record(historyEntry())
        for ((id, matches) in byBlock) {
            val block = blocks.firstOrNull { it.id == id } ?: continue
            val replaced = block.rich.replaceMatches(matches, replacement, block.value.selection)
            block.setRich(replaced.rich, replaced.selection)
            markDirty(block.id)
        }
        matchIndex = 0
    }

    private fun currentMatchIndex(): Int = matchIndex.coerceIn(0, (searchMatches.size - 1).coerceAtLeast(0))

    /** Index of the first match at or after [offset] in the block at [order], wrapping to the start. */
    private fun firstMatchFrom(order: Int, offset: Int): Int {
        val orderOf = blocks.withIndex().associate { (index, block) -> block.id to index }
        val index = searchMatches.indexOfFirst {
            val matchOrder = orderOf[it.unitId] ?: -1
            matchOrder > order || matchOrder == order && it.start >= offset
        }
        return index.coerceAtLeast(0)
    }

    fun onMessageShown() {
        message = null
    }

    fun flush() {
        autoSaver.flushInBackground()
    }

    override fun onCleared() {
        cleared = true
        autoSaver.flushInBackground()
    }

    private fun apply(edit: BlockEdit) {
        when (edit) {
            is BlockEdit.Content -> markDirty(edit.blockId)
            is BlockEdit.Structure -> {
                typingStyle = null
                synchronized(lock) {
                    dirtyIds.removeAll(edit.removedIds.toSet())
                    deletedIds.addAll(edit.removedIds)
                }
                edit.focusId?.let {
                    focusedBlockId = it
                    focusRequest = FocusRequest(it)
                }
                edit.selectId?.let {
                    selectBlock(it)
                    clearFocusRequest = System.nanoTime()
                }
                markStructureDirty()
            }
        }
    }

    private fun focusedTextBlock(): EditorBlock? = focusedBlock?.takeIf { it.kind.isText }

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
            savedBlocks(blocks, ids, chapterId) to title.text
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

    private data class BlockTyping(val blockId: String, val inserting: Boolean)

    private data class CellTyping(val blockId: String, val cell: CellKey)

    private data class ImageResize(val blockId: String)

    private class OrphanImage(val block: Block, val before: List<String>, val after: List<String>)

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val TITLE_TYPING = "title"
        const val DEFAULT_MARGIN_MM = 20
        const val ABSOLUTE_MIN_ZOOM = 0.1f
        const val MAX_ZOOM = 2.5f
        const val ZOOM_EPSILON = 0.01f
        val ZOOM_STEPS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f)
        const val TYPING_COALESCE_MILLIS = 2000L

        fun countWords(text: String): Int = text.split(WHITESPACE).count { it.isNotEmpty() }
    }
}
