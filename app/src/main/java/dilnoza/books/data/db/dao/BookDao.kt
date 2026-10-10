package dilnoza.books.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dilnoza.books.data.db.entity.BookEntity
import dilnoza.books.data.db.entity.BookSummaryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query(
        """
        SELECT b.*, (SELECT COUNT(*) FROM chapters c WHERE c.book_id = b.id) AS chapter_count
        FROM books b
        ORDER BY b.updated_at DESC
        """,
    )
    fun observeSummaries(): Flow<List<BookSummaryRow>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Insert
    suspend fun insert(book: BookEntity)

    @Query("UPDATE books SET title = :title, author = :author, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateInfo(id: String, title: String, author: String, updatedAt: Long)

    @Query("UPDATE books SET page_size = :pageSize, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePageSize(id: String, pageSize: String, updatedAt: Long)

    @Query("SELECT cover_path FROM books WHERE id = :id")
    suspend fun coverPath(id: String): String?

    @Query("UPDATE books SET cover_path = :coverPath, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateCover(id: String, coverPath: String?, updatedAt: Long)

    @Query("UPDATE books SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}
