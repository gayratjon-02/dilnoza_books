package dilnoza.books.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.TextAlignment

enum class BlockKind { PARAGRAPH, HEADING, IMAGE }

/** Image part of an image block; [fileName] is null while the picture is still being imported. */
data class ImageState(
    val fileName: String?,
    val width: Int,
    val height: Int,
    val widthPercent: Int = BlockContent.Image.MAX_WIDTH_PERCENT,
    val align: TextAlignment = TextAlignment.CENTER,
) {
    val aspectRatio: Float get() = width.toFloat() / height

    companion object {
        val Pending = ImageState(fileName = null, width = 4, height = 3)
    }
}

/**
 * Mutable, in-memory block while a chapter is open; the editor's source of truth until it is saved.
 * For image blocks [value] holds the caption.
 */
@Stable
class EditorBlock(
    val id: String,
    kind: BlockKind,
    value: TextFieldValue,
    val align: TextAlignment = TextAlignment.START,
    image: ImageState? = null,
) {
    var kind by mutableStateOf(kind)
    var value by mutableStateOf(value)
    var image by mutableStateOf(image)
    val focusRequester = FocusRequester()

    val isPendingImage: Boolean get() = kind == BlockKind.IMAGE && image?.fileName == null

    fun toBlock(chapterId: String, position: Int) = Block(
        id = id,
        chapterId = chapterId,
        position = position,
        content = when (kind) {
            BlockKind.HEADING -> BlockContent.Heading(level = 1, text = RichText(value.text))
            BlockKind.PARAGRAPH -> BlockContent.Paragraph(text = RichText(value.text), align = align)
            BlockKind.IMAGE -> {
                val image = checkNotNull(image)
                BlockContent.Image(
                    fileName = checkNotNull(image.fileName) { "Pending images are not saved" },
                    width = image.width,
                    height = image.height,
                    caption = value.text,
                    widthPercent = image.widthPercent,
                    align = image.align,
                )
            }
        },
    )

    companion object {
        fun from(block: Block): EditorBlock = when (val content = block.content) {
            is BlockContent.Heading -> EditorBlock(block.id, BlockKind.HEADING, TextFieldValue(content.text.text))
            is BlockContent.Paragraph ->
                EditorBlock(block.id, BlockKind.PARAGRAPH, TextFieldValue(content.text.text), content.align)
            is BlockContent.Image -> EditorBlock(
                id = block.id,
                kind = BlockKind.IMAGE,
                value = TextFieldValue(content.caption),
                image = ImageState(
                    fileName = content.fileName,
                    width = content.width,
                    height = content.height,
                    widthPercent = content.widthPercent,
                    align = content.align,
                ),
            )
        }
    }
}
