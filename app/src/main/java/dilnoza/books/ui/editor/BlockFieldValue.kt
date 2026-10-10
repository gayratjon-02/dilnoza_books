package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Soft keyboards often send nothing for Backspace when there is no text before the caret, so a
 * block field never starts empty: it holds a hidden line break in front of the block's text. Deleting
 * it is how a backspace at the start of a block arrives, from any keyboard. A line break (rather than
 * a zero-width character) keeps keyboards capitalising the first word, as at the start of a line.
 */
const val FIELD_SENTINEL = '\n'

/** The value given to the text field: [value] behind the hidden sentinel. */
fun TextFieldValue.toFieldValue(): TextFieldValue = TextFieldValue(
    text = FIELD_SENTINEL + text,
    selection = selection.shifted(1),
    composition = composition?.shifted(1),
)

sealed interface FieldEdit {
    data class Change(val value: TextFieldValue) : FieldEdit
    data object BackspaceAtStart : FieldEdit
}

/** Translates the field's new value back to the block's text, given the block's [current] value. */
fun fieldEdit(current: TextFieldValue, field: TextFieldValue): FieldEdit {
    if (field.text.startsWith(FIELD_SENTINEL)) {
        return FieldEdit.Change(
            TextFieldValue(
                text = field.text.substring(1),
                selection = field.selection.shifted(-1),
                composition = field.composition?.shifted(-1),
            ),
        )
    }
    if (field.text == current.text) return FieldEdit.BackspaceAtStart
    // The sentinel went with a larger replacement; what is left is all the writer's text.
    return FieldEdit.Change(field)
}

private fun TextRange.shifted(by: Int) = TextRange((start + by).coerceAtLeast(0), (end + by).coerceAtLeast(0))
