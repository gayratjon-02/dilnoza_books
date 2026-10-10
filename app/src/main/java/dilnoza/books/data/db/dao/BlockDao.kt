package dilnoza.books.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dilnoza.books.data.db.entity.BlockEntity

@Dao
interface BlockDao {

    @Query("SELECT * FROM blocks WHERE chapter_id = :chapterId ORDER BY position")
    suspend fun getByChapter(chapterId: String): List<BlockEntity>

    @Query(
        """
        SELECT b.content_json FROM blocks b
        INNER JOIN chapters c ON c.id = b.chapter_id
        WHERE c.book_id = :bookId AND b.type = 'image'
        """,
    )
    suspend fun imageContents(bookId: String): List<String>

    @Upsert
    suspend fun upsert(blocks: List<BlockEntity>)

    @Query("DELETE FROM blocks WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}
