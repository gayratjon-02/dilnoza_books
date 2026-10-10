package dilnoza.books.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dilnoza.books.data.files.ImageStore
import dilnoza.books.domain.model.Book
import dilnoza.books.domain.model.BookSummary
import dilnoza.books.domain.repository.BookRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class LibraryUiState(
    val loading: Boolean = true,
    val books: List<BookSummary> = emptyList(),
)

sealed interface LibraryAction {
    data class CreateBook(val title: String, val author: String, val firstChapterTitle: String) : LibraryAction
    data class UpdateBook(val id: String, val title: String, val author: String) : LibraryAction
    data class DeleteBook(val id: String) : LibraryAction
}

sealed interface LibraryEvent {
    data class OpenNewBook(val bookId: String, val chapterId: String) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val books: BookRepository,
    private val imageStore: ImageStore,
) : ViewModel() {

    fun coverFile(book: Book): File? = book.coverPath?.let { imageStore.coverFile(book.id, it) }

    val state: StateFlow<LibraryUiState> = books.observeBooks()
        .map { LibraryUiState(loading = false, books = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    fun onAction(action: LibraryAction) {
        viewModelScope.launch {
            when (action) {
                is LibraryAction.CreateBook -> {
                    val created = books.createBook(action.title, action.author, action.firstChapterTitle)
                    _events.send(LibraryEvent.OpenNewBook(created.bookId, created.firstChapterId))
                }
                is LibraryAction.UpdateBook -> books.updateBookInfo(action.id, action.title, action.author)
                is LibraryAction.DeleteBook -> books.deleteBook(action.id)
            }
        }
    }
}
