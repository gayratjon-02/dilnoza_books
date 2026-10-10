package dilnoza.books.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import dilnoza.books.editor.layout.PageStyles
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dilnoza.books.R
import dilnoza.books.domain.model.RichText
import dilnoza.books.editor.richtext.RichTextEditor

/**
 * A table whose cells are edited in place. Columns share the width of the page but never get narrower
 * than [MIN_COLUMN_WIDTH]; a wider table scrolls sideways. [selected] marks the whole table (picked with
 * Backspace from below), [focusedCell] the cell holding the caret.
 */
@Composable
fun TableBlockView(
    block: EditorBlock,
    table: TableState,
    selected: Boolean,
    focusedCell: CellKey?,
    onCellFocused: (CellKey) -> Unit,
    onCellChange: (CellKey, RichText) -> Unit,
    onCellNext: (cell: CellKey, forward: Boolean, enter: Boolean) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val headerLine = MaterialTheme.colorScheme.outline
    val headerBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val description = stringResource(R.string.editor_table)
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val density = LocalDensity.current
        // Columns share the page width equally, exactly as the paginator measures them.
        val columnWidth = with(density) { (constraints.maxWidth / table.columnKeys.size).toDp() }
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .semantics {
                    contentDescription = description
                    this.selected = selected
                }
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else lineColor,
                ),
        ) {
            table.rowKeys.forEachIndexed { rowIndex, rowKey ->
                key(rowKey) {
                    val header = table.headerRow && rowIndex == 0
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .then(
                                if (header) {
                                    Modifier
                                        .background(headerBackground)
                                        .drawWithContent {
                                            drawContent()
                                            val y = size.height - 1.dp.toPx()
                                            drawLine(headerLine, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        table.columnKeys.forEachIndexed { columnIndex, columnKey ->
                            key(columnKey) {
                                val cell = CellKey(rowKey, columnKey)
                                TableCell(
                                    rich = table.grid[rowIndex, columnIndex],
                                    header = header,
                                    focused = cell == focusedCell,
                                    width = columnWidth,
                                    label = stringResource(R.string.table_cell, rowIndex + 1, columnIndex + 1),
                                    focusRequester = remember(block, cell) { block.cellFocusRequester(cell) },
                                    onFocused = { onCellFocused(cell) },
                                    onChange = { onCellChange(cell, it) },
                                    onNext = { forward, enter -> onCellNext(cell, forward, enter) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableCell(
    rich: RichText,
    header: Boolean,
    focused: Boolean,
    width: Dp,
    label: String,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onChange: (RichText) -> Unit,
    onNext: (forward: Boolean, enter: Boolean) -> Unit,
) {
    // The field's own value keeps the selection and the keyboard's composing region between edits; it
    // gives way whenever the stored text changes from elsewhere.
    var value by remember { mutableStateOf(TextFieldValue(rich.text)) }
    val shown = if (value.text == rich.text) value else TextFieldValue(rich.text, TextRange(rich.text.length))
    val darkTheme = isDarkPage()
    val transformation = remember(rich, darkTheme) { RichTextTransformation(rich, darkTheme) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val bringIntoView = remember { BringIntoViewRequester() }
    var cellSize by remember { mutableStateOf(IntSize.Zero) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val margin = with(LocalDensity.current) { CELL_VIEW_MARGIN.toPx() }
    // The field only keeps its caret line in view; show the whole cell with some room around it once
    // the keyboard has finished resizing the page (the effect restarts on every frame it moves).
    LaunchedEffect(focused, imeBottom) {
        if (!focused) return@LaunchedEffect
        delay(SETTLE_MILLIS)
        bringIntoView.bringIntoView(Rect(0f, -margin, cellSize.width.toFloat(), cellSize.height + margin))
    }
    BasicTextField(
        value = shown,
        onValueChange = { new ->
            val breaks = BlockEditing.insertedLineBreaks(shown.text, new.text, new.selection.end)
            if (breaks.size == 1 && new.text.length == shown.text.length + 1) {
                // Enter moves on to the next cell rather than breaking the line.
                onNext(true, true)
                return@BasicTextField
            }
            // Pasted line breaks become spaces: a cell holds one paragraph.
            val cleaned = if ('\n' in new.text) new.copy(text = new.text.replace('\n', ' ')) else new
            value = cleaned
            if (cleaned.text != shown.text) {
                onChange(RichTextEditor.applyTextChange(rich, cleaned.text, cursor = cleaned.selection.end))
            }
        },
        textStyle = PageStyles.tableCell(header).copy(color = MaterialTheme.colorScheme.onSurface),
        visualTransformation = transformation,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext(true, true) }),
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .onSizeChanged { cellSize = it }
            .bringIntoViewRequester(bringIntoView)
            .border(0.5.dp, lineColor)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Tab || event.isCtrlPressed || event.isAltPressed) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) onNext(!event.isShiftPressed, false)
                true
            }
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                modifier = Modifier.padding(PageStyles.TABLE_CELL_PADDING_PT.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                inner()
            }
        },
    )
}

private val CELL_VIEW_MARGIN = 16.dp
private const val SETTLE_MILLIS = 60L
