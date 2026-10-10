package dilnoza.books.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dilnoza.books.data.db.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY position")
    fun observeByBook(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY position")
    suspend fun getByBook(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun get(id: String): ChapterEntity?

    @Query("SELECT MAX(position) FROM chapters WHERE book_id = :bookId")
    suspend fun maxPosition(bookId: String): Int?

    @Insert
    suspend fun insert(chapter: ChapterEntity)

    @Query("UPDATE chapters SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE chapters SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("UPDATE chapters SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun delete(id: String)
}
