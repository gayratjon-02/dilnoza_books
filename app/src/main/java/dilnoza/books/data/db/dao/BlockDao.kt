package dilnoza.books.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dilnoza.books.data.db.entity.BlockEntity

@Dao
interface BlockDao {

    @Query("SELECT * FROM blocks WHERE chapter_id = :chapterId ORDER BY position")
    suspend fun getByChapter(chapterId: String): List<BlockEntity>

    @Upsert
    suspend fun upsert(blocks: List<BlockEntity>)

    @Query("DELETE FROM blocks WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}
