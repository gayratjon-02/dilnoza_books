package dilnoza.books.ui.book

import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dilnoza.books.core.di.ApplicationScope
import dilnoza.books.data.files.ImageStore
import dilnoza.books.domain.model.Book
import dilnoza.books.domain.model.Chapter
import dilnoza.books.domain.model.PageSize
import dilnoza.books.domain.repository.BookRepository
import dilnoza.books.domain.repository.ChapterRepository
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.ui.editor.page.BookPageCounter
import dilnoza.books.ui.navigation.BookRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BookUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    /** Pages each chapter occupies in the whole book, once counted. */
    val pageRanges: Map<String, IntRange> = emptyMap(),
    val coverFile: File? = null,
    val coverImporting: Boolean = false,
)

sealed interface BookAction {
    data class AddChapter(val title: String) : BookAction
    data class RenameChapter(val id: String, val title: String) : BookAction
    data class DeleteChapter(val id: String) : BookAction
    data class MoveChapter(val id: String, val offset: Int) : BookAction
    data class UpdateBook(val title: String, val author: String) : BookAction
    data class SetPageSize(val pageSize: PageSize) : BookAction
    /** The descriptor is opened by the screen while it still holds the picker's URI grant. */
    data class SetCover(val source: ParcelFileDescriptor) : BookAction
    data object RemoveCover : BookAction
}

sealed interface BookEvent {
    data class OpenChapter(val chapterId: String) : BookEvent
    data object CoverFailed : BookEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
    private val chapters: ChapterRepository,
    private val pageCounter: BookPageCounter,
    private val imageStore: ImageStore,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val bookId = savedStateHandle.toRoute<BookRoute>().bookId

    private val coverImporting = MutableStateFlow(false)

    val state: StateFlow<BookUiState> = combine(
        books.observeBook(bookId),
        chapters.observeChapters(bookId),
        coverImporting,
    ) { book, chapters, importing ->
        BookUiState(
            loading = false,
            book = book,
            chapters = chapters,
            coverFile = book?.coverPath?.let { imageStore.coverFile(bookId, it) },
            coverImporting = importing,
        )
    }
        .transformLatest { state ->
            emit(state)
            val book = state.book ?: return@transformLatest
            // Counting lays every chapter out, so the list shows first and the page ranges follow.
            val counts = pageCounter.pageCounts(state.chapters, PageSpec.of(book.pageSize, book.marginMm))
            var next = 1
            val ranges = state.chapters.zip(counts).associate { (chapter, pages) ->
                (chapter.id to (next until next + pages)).also { next += pages }
            }
            emit(state.copy(pageRanges = ranges))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookUiState())

    private val _events = Channel<BookEvent>(Channel.BUFFERED)
    val events: Flow<BookEvent> = _events.receiveAsFlow()

    fun onAction(action: BookAction) {
        viewModelScope.launch {
            when (action) {
                is BookAction.AddChapter -> {
                    val id = chapters.addChapter(bookId, action.title)
                    _events.send(BookEvent.OpenChapter(id))
                }
                is BookAction.RenameChapter -> chapters.renameChapter(action.id, action.title)
                is BookAction.DeleteChapter -> chapters.deleteChapter(action.id)
                is BookAction.MoveChapter -> chapters.moveChapter(action.id, action.offset)
                is BookAction.UpdateBook -> books.updateBookInfo(bookId, action.title, action.author)
                is BookAction.SetPageSize -> books.updatePageSize(bookId, action.pageSize)
                is BookAction.SetCover -> setCover(action.source)
                BookAction.RemoveCover -> books.updateCover(bookId, null)
            }
        }
    }

    private fun setCover(source: ParcelFileDescriptor) {
        coverImporting.value = true
        // Application scope: a picture chosen just before leaving the screen still becomes the cover.
        appScope.launch {
            val stored = runCatching { imageStore.importCover(bookId, source) }.getOrNull()
            if (stored != null) books.updateCover(bookId, stored)
            coverImporting.value = false
            if (stored == null) _events.send(BookEvent.CoverFailed)
        }
    }
}
