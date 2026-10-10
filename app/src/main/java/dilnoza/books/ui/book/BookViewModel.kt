package dilnoza.books.ui.book

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dilnoza.books.domain.model.Book
import dilnoza.books.domain.model.Chapter
import dilnoza.books.domain.model.PageSize
import dilnoza.books.domain.repository.BookRepository
import dilnoza.books.domain.repository.ChapterRepository
import dilnoza.books.ui.navigation.BookRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
)

sealed interface BookAction {
    data class AddChapter(val title: String) : BookAction
    data class RenameChapter(val id: String, val title: String) : BookAction
    data class DeleteChapter(val id: String) : BookAction
    data class MoveChapter(val id: String, val offset: Int) : BookAction
    data class UpdateBook(val title: String, val author: String) : BookAction
    data class SetPageSize(val pageSize: PageSize) : BookAction
}

sealed interface BookEvent {
    data class OpenChapter(val chapterId: String) : BookEvent
}

@HiltViewModel
class BookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
    private val chapters: ChapterRepository,
) : ViewModel() {

    private val bookId = savedStateHandle.toRoute<BookRoute>().bookId

    val state: StateFlow<BookUiState> = combine(
        books.observeBook(bookId),
        chapters.observeChapters(bookId),
    ) { book, chapters -> BookUiState(loading = false, book = book, chapters = chapters) }
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
            }
        }
    }
}
