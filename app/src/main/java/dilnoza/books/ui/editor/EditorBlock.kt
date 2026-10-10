package dilnoza.books.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.editor.richtext.RichTextEditor
import dilnoza.books.editor.richtext.normalized

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
 * [rich] holds the styled text and [value] the same text plus the field's selection; [setValue]
 * and [setRich] keep the two in step. For image blocks the text is the caption.
 */
@Stable
class EditorBlock(
    val id: String,
    kind: BlockKind,
    rich: RichText = RichText.Empty,
    selection: TextRange = TextRange(rich.text.length),
    val align: TextAlignment = TextAlignment.START,
    image: ImageState? = null,
) {
    var kind by mutableStateOf(kind)
    var rich by mutableStateOf(rich.normalized())
        private set
    var value by mutableStateOf(TextFieldValue(rich.text, selection))
        private set
    var image by mutableStateOf(image)
    val focusRequester = FocusRequester()

    val isPendingImage: Boolean get() = kind == BlockKind.IMAGE && image?.fileName == null

    /** Takes an edit from the text field; styles follow the text, new text gets [typingStyle] when set. */
    fun setValue(newValue: TextFieldValue, typingStyle: SpanStyleData? = null) {
        if (newValue.text != value.text) {
            rich = RichTextEditor.applyTextChange(rich, newValue.text, typingStyle, newValue.selection.end)
        }
        value = newValue
    }

    fun setRich(newRich: RichText, selection: TextRange = TextRange(newRich.text.length)) {
        rich = newRich.normalized()
        value = TextFieldValue(newRich.text, selection)
    }

    fun setSelection(selection: TextRange) {
        value = value.copy(selection = selection)
    }

    fun toBlock(chapterId: String, position: Int) = Block(
        id = id,
        chapterId = chapterId,
        position = position,
        content = when (kind) {
            BlockKind.HEADING -> BlockContent.Heading(level = 1, text = rich)
            BlockKind.PARAGRAPH -> BlockContent.Paragraph(text = rich, align = align)
            BlockKind.IMAGE -> {
                val image = checkNotNull(image)
                BlockContent.Image(
                    fileName = checkNotNull(image.fileName) { "Pending images are not saved" },
                    width = image.width,
                    height = image.height,
                    caption = rich.text,
                    widthPercent = image.widthPercent,
                    align = image.align,
                )
            }
        },
    )

    companion object {
        fun from(block: Block): EditorBlock = when (val content = block.content) {
            is BlockContent.Heading -> EditorBlock(block.id, BlockKind.HEADING, content.text)
            is BlockContent.Paragraph -> EditorBlock(block.id, BlockKind.PARAGRAPH, content.text, align = content.align)
            is BlockContent.Image -> EditorBlock(
                id = block.id,
                kind = BlockKind.IMAGE,
                rich = RichText(content.caption),
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
