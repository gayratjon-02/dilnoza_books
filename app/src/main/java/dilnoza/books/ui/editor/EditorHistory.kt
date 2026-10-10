package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.TextAlignment

/** Everything about a block that undo can bring back; all of it is immutable. */
data class BlockSnapshot(
    val id: String,
    val kind: BlockKind,
    val rich: RichText,
    val align: TextAlignment,
    val headingLevel: Int,
    val indent: Int,
    val image: ImageState?,
    val table: TableState?,
)

/** The chapter's content at one moment: its title and blocks in order. */
data class ChapterSnapshot(val title: String, val blocks: List<BlockSnapshot>)

/** Where the caret was, so undo can put it back: a block, its selection and, in a table, the cell. */
data class Caret(val blockId: String, val selection: TextRange, val cell: CellKey? = null)

/** One undo step: the content to restore and where the caret goes. */
data class HistoryEntry(val content: ChapterSnapshot, val caret: Caret?)

fun EditorBlock.snapshot() = BlockSnapshot(id, kind, rich, align, headingLevel, indent, image, table)

/** What [restoreBlocks] changed, so the caller can save exactly that. */
data class RestoreResult(val changedIds: Set<String>, val addedIds: Set<String>, val removedIds: Set<String>, val reordered: Boolean)

/**
 * Makes [blocks] match [target], reusing the blocks that are still there so their fields, focus and
 * scroll position survive. [resolveImage] gives the picture for a block that was still being imported
 * when the snapshot was taken: the stored one, the placeholder while importing, or null when it failed.
 */
fun restoreBlocks(
    blocks: MutableList<EditorBlock>,
    target: List<BlockSnapshot>,
    resolveImage: (String) -> ImageState? = { null },
): RestoreResult {
    val current = blocks.associateBy { it.id }
    val changed = mutableSetOf<String>()
    val added = mutableSetOf<String>()
    val restored = target.mapNotNull { wanted ->
        val snapshot = if (wanted.image != null && wanted.image.fileName == null) {
            val image = current[wanted.id]?.image?.takeIf { it.fileName != null }
                ?: resolveImage(wanted.id)
                ?: return@mapNotNull null
            wanted.copy(image = image)
        } else {
            wanted
        }
        val block = current[snapshot.id]
        if (block == null) {
            added += snapshot.id
            EditorBlock(
                id = snapshot.id,
                kind = snapshot.kind,
                rich = snapshot.rich,
                align = snapshot.align,
                headingLevel = snapshot.headingLevel,
                indent = snapshot.indent,
                image = snapshot.image,
                table = snapshot.table,
            )
        } else {
            if (block.restore(snapshot)) changed += block.id
            block
        }
    }
    val restoredIds = restored.mapTo(mutableSetOf()) { it.id }
    val removed = current.keys - restoredIds
    val reordered = restored.map { it.id } != blocks.map { it.id }
    if (reordered) {
        blocks.clear()
        blocks.addAll(restored)
    }
    return RestoreResult(changed, added, removed, reordered)
}

/** Sets this block's content to [snapshot]; returns whether anything changed. */
private fun EditorBlock.restore(snapshot: BlockSnapshot): Boolean {
    if (snapshot() == snapshot) return false
    kind = snapshot.kind
    align = snapshot.align
    headingLevel = snapshot.headingLevel
    indent = snapshot.indent
    image = snapshot.image
    table = snapshot.table
    if (rich != snapshot.rich) {
        val length = snapshot.rich.text.length
        setRich(snapshot.rich, TextRange(value.selection.start.coerceAtMost(length), value.selection.end.coerceAtMost(length)))
    }
    return true
}
