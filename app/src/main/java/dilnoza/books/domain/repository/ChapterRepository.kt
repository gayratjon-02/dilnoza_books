package dilnoza.books.domain.repository

import dilnoza.books.domain.model.Chapter
import kotlinx.coroutines.flow.Flow

interface ChapterRepository {
    fun observeChapters(bookId: String): Flow<List<Chapter>>
    suspend fun getChapter(id: String): Chapter?
    suspend fun addChapter(bookId: String, title: String): String
    suspend fun renameChapter(id: String, title: String)
    suspend fun deleteChapter(id: String)

    /** Swaps the chapter with its neighbour; [offset] is -1 (up) or +1 (down). */
    suspend fun moveChapter(id: String, offset: Int)
}
