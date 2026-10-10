package dilnoza.books.data.repository

import androidx.room.withTransaction
import dilnoza.books.core.time.Clock
import dilnoza.books.data.db.AppDatabase
import dilnoza.books.data.db.entity.ChapterEntity
import dilnoza.books.data.mapper.toDomain
import dilnoza.books.domain.model.Chapter
import dilnoza.books.domain.model.POSITION_STEP
import dilnoza.books.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class ChapterRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) : ChapterRepository {

    private val chapterDao = db.chapterDao()
    private val bookDao = db.bookDao()

    override fun observeChapters(bookId: String): Flow<List<Chapter>> =
        chapterDao.observeByBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun getChapter(id: String): Chapter? = chapterDao.get(id)?.toDomain()

    override suspend fun addChapter(bookId: String, title: String): String {
        val now = clock.now()
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            val position = (chapterDao.maxPosition(bookId) ?: 0) + POSITION_STEP
            chapterDao.insert(ChapterEntity(id, bookId, position, title, now, now))
            bookDao.touch(bookId, now)
        }
        return id
    }

    override suspend fun renameChapter(id: String, title: String) {
        val now = clock.now()
        db.withTransaction {
            val chapter = chapterDao.get(id) ?: return@withTransaction
            chapterDao.rename(id, title.trim(), now)
            bookDao.touch(chapter.bookId, now)
        }
    }

    override suspend fun deleteChapter(id: String) {
        db.withTransaction {
            val chapter = chapterDao.get(id) ?: return@withTransaction
            chapterDao.delete(id)
            bookDao.touch(chapter.bookId, clock.now())
        }
    }

    override suspend fun moveChapter(id: String, offset: Int) {
        db.withTransaction {
            val chapter = chapterDao.get(id) ?: return@withTransaction
            val siblings = chapterDao.getByBook(chapter.bookId)
            val index = siblings.indexOfFirst { it.id == id }
            val neighbour = siblings.getOrNull(index + offset) ?: return@withTransaction
            chapterDao.setPosition(chapter.id, neighbour.position)
            chapterDao.setPosition(neighbour.id, chapter.position)
            bookDao.touch(chapter.bookId, clock.now())
        }
    }
}
