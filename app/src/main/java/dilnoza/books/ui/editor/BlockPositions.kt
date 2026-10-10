package dilnoza.books.ui.editor

import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.POSITION_STEP

/**
 * The blocks among [ids] as they are stored, positioned by their order among saved blocks only: a picture
 * still being imported is not stored, so it must not shift the positions of the blocks after it.
 */
internal fun savedBlocks(blocks: List<EditorBlock>, ids: Set<String>, chapterId: String): List<Block> {
    var order = 0
    return blocks.mapNotNull { block ->
        if (block.isPendingImage) return@mapNotNull null
        order++
        if (block.id in ids) block.toBlock(chapterId, order * POSITION_STEP) else null
    }
}
