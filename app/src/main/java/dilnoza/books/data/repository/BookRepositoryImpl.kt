package dilnoza.books.data.repository

import androidx.room.withTransaction
import dilnoza.books.core.time.Clock
import dilnoza.books.data.db.AppDatabase
import dilnoza.books.data.db.entity.BookEntity
import dilnoza.books.data.db.entity.ChapterEntity
import dilnoza.books.data.files.ImageStore
import dilnoza.books.data.mapper.toDomain
import dilnoza.books.domain.model.Book
import dilnoza.books.domain.model.BookSummary
import dilnoza.books.domain.model.CreatedBook
import dilnoza.books.domain.model.POSITION_STEP
import dilnoza.books.domain.model.PageSize
import dilnoza.books.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val imageStore: ImageStore,
) : BookRepository {

    private val bookDao = db.bookDao()
    private val chapterDao = db.chapterDao()

    override fun observeBooks(): Flow<List<BookSummary>> =
        bookDao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeBook(id: String): Flow<Book?> = bookDao.observe(id).map { it?.toDomain() }

    override suspend fun createBook(title: String, author: String, firstChapterTitle: String): CreatedBook {
        val now = clock.now()
        val bookId = UUID.randomUUID().toString()
        val chapterId = UUID.randomUUID().toString()
        db.withTransaction {
            bookDao.insert(
                BookEntity(
                    id = bookId,
                    title = title.trim(),
                    author = author.trim(),
                    description = "",
                    coverPath = null,
                    coverColor = coverColorFor(bookId),
                    pageSize = PageSize.A5.name,
                    marginMm = DEFAULT_MARGIN_MM,
                    fontFamily = DEFAULT_FONT,
                    headerText = null,
                    footerText = null,
                    showPageNumbers = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            chapterDao.insert(
                ChapterEntity(
                    id = chapterId,
                    bookId = bookId,
                    position = POSITION_STEP,
                    title = firstChapterTitle,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return CreatedBook(bookId = bookId, firstChapterId = chapterId)
    }

    override suspend fun updateBookInfo(id: String, title: String, author: String) {
        bookDao.updateInfo(id, title.trim(), author.trim(), clock.now())
    }

    override suspend fun updatePageSize(id: String, pageSize: PageSize) {
        bookDao.updatePageSize(id, pageSize.name, clock.now())
    }

    override suspend fun updateCover(id: String, coverFileName: String?) {
        val old = bookDao.coverPath(id)
        bookDao.updateCover(id, coverFileName, clock.now())
        if (old != null && old != coverFileName) imageStore.deleteCover(id, old)
    }

    override suspend fun deleteBook(id: String) {
        bookDao.delete(id)
        imageStore.deleteBook(id)
    }

    private fun coverColorFor(id: String): Int = COVER_COLORS[Math.floorMod(id.hashCode(), COVER_COLORS.size)]

    private companion object {
        const val DEFAULT_MARGIN_MM = 20
        const val DEFAULT_FONT = "serif"

        val COVER_COLORS = intArrayOf(
            0xFF8A4B2E.toInt(),
            0xFF4F6538.toInt(),
            0xFF35586E.toInt(),
            0xFF6E3550.toInt(),
            0xFF7A6A2E.toInt(),
            0xFF4A4A6E.toInt(),
        )
    }
}
