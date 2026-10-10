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

/**
 * A position for a block placed on its own among the [stored] blocks (in stored order): right after the
 * nearest of [before] (nearest first) that is still stored, else right before the nearest of [after].
 */
internal fun positionAmong(stored: List<Block>, before: List<String>, after: List<String>): Int {
    val indexOf = stored.withIndex().associate { (index, block) -> block.id to index }
    val slot = before.firstNotNullOfOrNull { indexOf[it] }?.plus(1)
        ?: after.firstNotNullOfOrNull { indexOf[it] }
        ?: stored.size
    val low = stored.getOrNull(slot - 1)?.position
    val high = stored.getOrNull(slot)?.position
    return when {
        low != null && high != null -> low + (high - low) / 2
        low != null -> low + POSITION_STEP
        high != null -> high - POSITION_STEP
        else -> POSITION_STEP
    }
}
