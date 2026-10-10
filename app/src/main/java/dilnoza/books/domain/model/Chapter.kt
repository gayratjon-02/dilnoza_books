package dilnoza.books.domain.model

data class Chapter(
    val id: String,
    val bookId: String,
    val position: Int,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)
