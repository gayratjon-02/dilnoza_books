package dilnoza.books.ui.editor.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.editor.layout.FlowItem
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PagePlan
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.editor.layout.Paginator
import dilnoza.books.ui.editor.BlockKind
import dilnoza.books.ui.editor.EditorBlock
import dilnoza.books.ui.editor.PageGap
import dilnoza.books.ui.editor.toAnnotatedString

/** Pagination of the open chapter: where every block sits on the stacked pages. */
@Stable
class EditorPages(
    val plan: PagePlan,
    private val items: Map<String, FlowItem>,
) {
    /** Page gaps inside a text block that continues on the next page, as character offsets. */
    fun gapsOf(blockId: String): List<PageGap> {
        val block = plan.blocks[blockId] ?: return emptyList()
        if (block.innerGaps.isEmpty()) return emptyList()
        val starts = (items[blockId] as? FlowItem.Text)?.lineStarts ?: return emptyList()
        return block.innerGaps.mapNotNull { gap -> starts.getOrNull(gap.beforeLine)?.let { PageGap(it, gap.heightPt) } }
    }

    companion object {
        const val GAP_PT = 18f
    }
}

/**
 * Re-paginates the chapter whenever its title, blocks or page size change. Only blocks whose content
 * changed are measured again, so typing stays cheap even in long chapters. [captionShownFor] names the
 * image whose caption field is shown although it may be empty (the selected one), so its line is planned.
 */
@Composable
fun rememberEditorPages(
    title: () -> String,
    blocks: List<EditorBlock>,
    spec: PageSpec,
    captionShownFor: () -> String? = { null },
): EditorPages {
    val fontResolver = LocalFontFamilyResolver.current
    val measurer = remember(spec, fontResolver) {
        val textMeasurer = TextMeasurer(fontResolver, PageMeasurer.PointDensity, LayoutDirection.Ltr, cacheSize = 32)
        PageMeasurer(textMeasurer, spec) { it.toAnnotatedString() }
    }
    val cache = remember(measurer) { HashMap<String, Pair<Any, FlowItem>>() }
    val paginator = remember(spec) { Paginator(spec.contentHeightPt) }
    val pages by remember(measurer, blocks) {
        derivedStateOf {
            val titleText = title()
            val titleItem = cache.cached(PageMeasurer.TITLE_ID, titleText) { measurer.measureTitle(titleText) }
            val captionShown = captionShownFor()
            val items = listOf(titleItem) + blocks.map { block ->
                val content = block.layoutContent()
                val showsCaption = block.id == captionShown && content is BlockContent.Image
                val key: Any = if (showsCaption) ShownCaption(content) else content
                cache.cached(block.id, key) { measurer.measure(block.id, content, showsCaption) }
            }
            cache.keys.retainAll(items.mapTo(HashSet()) { it.blockId })
            val pagination = paginator.paginate(items)
            EditorPages(
                plan = PagePlan.build(spec, EditorPages.GAP_PT, items.map { it.blockId }, pagination),
                items = items.associateBy { it.blockId },
            )
        }
    }
    return pages
}

private data class ShownCaption(val content: BlockContent)

private inline fun HashMap<String, Pair<Any, FlowItem>>.cached(id: String, key: Any, measure: () -> FlowItem): FlowItem {
    get(id)?.let { (cachedKey, item) -> if (cachedKey == key) return item }
    return measure().also { put(id, key to it) }
}

/** Layout-relevant content of a block, including images that are still being imported. */
fun EditorBlock.layoutContent(): BlockContent {
    val image = image
    return if (kind == BlockKind.IMAGE && image != null && image.fileName == null) {
        BlockContent.Image(fileName = "", width = image.width, height = image.height, caption = rich.text,
            widthPercent = image.widthPercent, align = image.align)
    } else {
        toBlock(chapterId = "", position = 0).content
    }
}
