package dilnoza.books.data.files

import dilnoza.books.core.time.Clock
import dilnoza.books.data.db.AppDatabase
import dilnoza.books.data.mapper.BlockContentCodec
import dilnoza.books.domain.model.BlockContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Removes image files no block points to any more (deleted blocks, deleted books, interrupted imports).
 * Deleting a block keeps its file so it can be restored; recent files are therefore left alone.
 */
class OrphanImageCleaner @Inject constructor(
    private val db: AppDatabase,
    private val imageStore: ImageStore,
    private val clock: Clock,
) {
    suspend fun clean() = withContext(Dispatchers.IO) {
        val bookDirs = imageStore.booksRoot.listFiles { file -> file.isDirectory } ?: return@withContext
        val threshold = clock.now() - GRACE_PERIOD_MILLIS
        for (bookDir in bookDirs) {
            val bookId = bookDir.name
            if (!db.bookDao().exists(bookId)) {
                bookDir.deleteRecursively()
                continue
            }
            val referenced = db.blockDao().imageContents(bookId)
                .mapNotNull { raw -> runCatching { BlockContentCodec.decode(raw) }.getOrNull() as? BlockContent.Image }
                .mapTo(HashSet()) { it.fileName }
            imageStore.imagesDir(bookId).listFiles()
                ?.filter { it.name !in referenced && it.lastModified() < threshold }
                ?.forEach { it.delete() }
        }
    }

    private companion object {
        val GRACE_PERIOD_MILLIS = TimeUnit.HOURS.toMillis(1)
    }
}
