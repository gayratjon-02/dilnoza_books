package dilnoza.books.ui.editor.page

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import dilnoza.books.domain.model.Chapter
import dilnoza.books.domain.repository.BlockRepository
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.editor.layout.Paginator
import dilnoza.books.ui.editor.toAnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts the pages of chapters with the same measurer and paginator as the editor, so page numbers
 * run on across the whole book. Counts are cached per chapter until the chapter or the page size changes.
 */
@Singleton
class BookPageCounter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blocks: BlockRepository,
) {
    private data class Key(val chapterId: String, val updatedAt: Long, val spec: PageSpec)

    private val cache = HashMap<Key, Int>()
    private val mutex = Mutex()
    private var measurer: Pair<PageSpec, PageMeasurer>? = null

    /** Pages of each chapter, in the given order. */
    suspend fun pageCounts(chapters: List<Chapter>, spec: PageSpec): List<Int> = withContext(Dispatchers.Default) {
        mutex.withLock { chapters.map { count(it, spec) } }
    }

    private suspend fun count(chapter: Chapter, spec: PageSpec): Int {
        val key = Key(chapter.id, chapter.updatedAt, spec)
        cache[key]?.let { return it }
        val measurer = measurerFor(spec)
        val items = listOf(measurer.measureTitle(chapter.title)) + measurer.measure(blocks.getBlocks(chapter.id))
        val pages = Paginator(spec.contentHeightPt).paginate(items).pageCount
        cache.keys.removeAll { it.chapterId == chapter.id }
        cache[key] = pages
        return pages
    }

    private fun measurerFor(spec: PageSpec): PageMeasurer {
        measurer?.let { (cachedSpec, cached) -> if (cachedSpec == spec) return cached }
        val textMeasurer = TextMeasurer(createFontFamilyResolver(context), PageMeasurer.PointDensity, LayoutDirection.Ltr)
        return PageMeasurer(textMeasurer, spec) { it.toAnnotatedString() }.also { measurer = spec to it }
    }
}
