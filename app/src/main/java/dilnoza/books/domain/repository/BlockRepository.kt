package dilnoza.books.domain.repository

import dilnoza.books.domain.model.Block

interface BlockRepository {
    suspend fun getBlocks(chapterId: String): List<Block>

    /** Applies one editor save atomically: title change, upserted blocks and removed blocks. */
    suspend fun saveChapter(
        chapterId: String,
        title: String?,
        upserts: List<Block>,
        deletedIds: Collection<String>,
    )
}
