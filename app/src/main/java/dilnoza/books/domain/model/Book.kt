package dilnoza.books.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverPath: String?,
    val coverColor: Int,
    val pageSize: PageSize,
    val marginMm: Int,
    val fontFamily: String,
    val headerText: String?,
    val footerText: String?,
    val showPageNumbers: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class PageSize { A4, A5, LETTER }

data class BookSummary(
    val book: Book,
    val chapterCount: Int,
)

data class CreatedBook(
    val bookId: String,
    val firstChapterId: String,
)
