package dilnoza.books.domain.repository

import dilnoza.books.domain.model.Book
import dilnoza.books.domain.model.BookSummary
import dilnoza.books.domain.model.CreatedBook
import dilnoza.books.domain.model.PageSize
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBooks(): Flow<List<BookSummary>>
    fun observeBook(id: String): Flow<Book?>
    suspend fun createBook(title: String, author: String, firstChapterTitle: String): CreatedBook
    suspend fun updateBookInfo(id: String, title: String, author: String)
    suspend fun updatePageSize(id: String, pageSize: PageSize)
    suspend fun deleteBook(id: String)
}
