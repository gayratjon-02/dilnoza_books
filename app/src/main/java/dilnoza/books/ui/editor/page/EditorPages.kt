package dilnoza.books.ui.editor.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.editor.layout.FlowItem
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PagePlan
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.editor.layout.Paginator
import dilnoza.books.ui.editor.BlockKind
import dilnoza.books.ui.editor.EditorBlock
import dilnoza.books.ui.editor.PageGap

/**
 * Pagination of the open chapter: where every block sits on the stacked pages. Compared by value, so
 * an edit that moves no line or page leaves readers alone instead of recomposing the whole editor.
 */
@Immutable
data class EditorPages(
    val plan: PagePlan,
    /** Page gaps inside the text blocks that continue on the next page, as character offsets. */
    private val gaps: Map<String, List<PageGap>>,
) {
    fun gapsOf(blockId: String): List<PageGap> = gaps[blockId].orEmpty()

    companion object {
        const val GAP_PT = 18f

        fun of(plan: PagePlan, items: List<FlowItem>): EditorPages {
            val gaps = HashMap<String, List<PageGap>>()
            for (item in items) {
                if (item !is FlowItem.Text) continue
                val inner = plan.blocks[item.blockId]?.innerGaps.orEmpty()
                if (inner.isEmpty()) continue
                gaps[item.blockId] = inner.mapNotNull { gap -> item.lineStarts.getOrNull(gap.beforeLine)?.let { PageGap(it, gap.heightPt) } }
            }
            return EditorPages(plan, gaps)
        }
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
    cache: BlockLayoutCache,
    captionShownFor: () -> String? = { null },
): EditorPages {
    val paginator = remember(spec) { Paginator(spec.contentHeightPt) }
    val pages by remember(cache, spec, blocks) {
        derivedStateOf(structuralEqualityPolicy()) {
            val titleText = title()
            val titleItem = cache.item(spec, PageMeasurer.TITLE_ID, titleText) { measureTitle(titleText) }
            val captionShown = captionShownFor()
            val items = listOf(titleItem) + blocks.map { block ->
                val content = block.layoutContent()
                val showsCaption = block.id == captionShown && content is BlockContent.Image
                val key: Any = if (showsCaption) ShownCaption(content) else content
                cache.item(spec, block.id, key) { measure(block.id, content, showsCaption) }
            }
            cache.retain(spec, items.mapTo(HashSet()) { it.blockId })
            val pagination = paginator.paginate(items)
            EditorPages.of(PagePlan.build(spec, EditorPages.GAP_PT, items.map { it.blockId }, pagination), items)
        }
    }
    return pages
}

private data class ShownCaption(val content: BlockContent)

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
