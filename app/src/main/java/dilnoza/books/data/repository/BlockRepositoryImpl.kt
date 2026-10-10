package dilnoza.books.data.repository

import androidx.room.withTransaction
import dilnoza.books.core.time.Clock
import dilnoza.books.data.db.AppDatabase
import dilnoza.books.data.mapper.toDomain
import dilnoza.books.data.mapper.toEntity
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.repository.BlockRepository
import javax.inject.Inject

class BlockRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) : BlockRepository {

    private val blockDao = db.blockDao()
    private val chapterDao = db.chapterDao()
    private val bookDao = db.bookDao()

    override suspend fun getBlocks(chapterId: String): List<Block> =
        blockDao.getByChapter(chapterId).map { it.toDomain() }

    override suspend fun saveChapter(
        chapterId: String,
        title: String?,
        upserts: List<Block>,
        deletedIds: Collection<String>,
    ) {
        val now = clock.now()
        db.withTransaction {
            // The chapter may have been deleted from another screen while the editor was still open.
            val chapter = chapterDao.get(chapterId) ?: return@withTransaction
            if (title != null) chapterDao.rename(chapterId, title.trim(), now) else chapterDao.touch(chapterId, now)
            if (deletedIds.isNotEmpty()) blockDao.delete(deletedIds.toList())
            if (upserts.isNotEmpty()) blockDao.upsert(upserts.map { it.toEntity(now) })
            bookDao.touch(chapter.bookId, now)
        }
    }
}
