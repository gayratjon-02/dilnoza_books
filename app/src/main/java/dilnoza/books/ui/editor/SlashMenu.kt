package dilnoza.books.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertPageBreak
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dilnoza.books.R

/** A block type offered by the "/" menu. [keywords] are English and match in every language. */
enum class SlashCommand(
    val kind: BlockKind,
    val icon: ImageVector,
    val keywords: List<String>,
    val headingLevel: Int = 1,
) {
    TEXT(BlockKind.PARAGRAPH, Icons.AutoMirrored.Outlined.Notes, listOf("text", "paragraph", "plain")),
    HEADING_1(BlockKind.HEADING, Icons.Outlined.Title, listOf("heading", "h1", "title"), headingLevel = 1),
    HEADING_2(BlockKind.HEADING, Icons.Outlined.Title, listOf("heading", "h2", "subtitle"), headingLevel = 2),
    HEADING_3(BlockKind.HEADING, Icons.Outlined.Title, listOf("heading", "h3"), headingLevel = 3),
    BULLETED_LIST(BlockKind.BULLET, Icons.AutoMirrored.Outlined.FormatListBulleted, listOf("bulleted list", "list", "ul", "-")),
    NUMBERED_LIST(BlockKind.NUMBERED, Icons.Outlined.FormatListNumbered, listOf("numbered list", "list", "ol", "1.")),
    QUOTE(BlockKind.QUOTE, Icons.Outlined.FormatQuote, listOf("quote", "blockquote", ">")),
    DIVIDER(BlockKind.DIVIDER, Icons.Outlined.HorizontalRule, listOf("divider", "line", "separator", "hr", "---")),
    PAGE_BREAK(BlockKind.PAGE_BREAK, Icons.Outlined.InsertPageBreak, listOf("page break", "break", "new page")),
    IMAGE(BlockKind.IMAGE, Icons.Outlined.Image, listOf("image", "picture", "photo", "img")),
    TABLE(BlockKind.TABLE, Icons.Outlined.TableChart, listOf("table", "grid")),
}

@Composable
fun SlashCommand.label(): String = when (this) {
    SlashCommand.HEADING_1, SlashCommand.HEADING_2, SlashCommand.HEADING_3 ->
        stringResource(R.string.format_heading, headingLevel)
    else -> stringResource(simpleLabel())
}

@StringRes
private fun SlashCommand.simpleLabel(): Int = when (this) {
    SlashCommand.TEXT -> R.string.format_paragraph
    SlashCommand.BULLETED_LIST -> R.string.block_bulleted_list
    SlashCommand.NUMBERED_LIST -> R.string.block_numbered_list
    SlashCommand.QUOTE -> R.string.editor_quote_hint
    SlashCommand.DIVIDER -> R.string.editor_divider
    SlashCommand.PAGE_BREAK -> R.string.editor_page_break
    SlashCommand.IMAGE -> R.string.editor_add_image
    SlashCommand.TABLE -> R.string.editor_table
    SlashCommand.HEADING_1, SlashCommand.HEADING_2, SlashCommand.HEADING_3 -> R.string.format_heading
}

/**
 * The "/" menu, shown next to the caret: below the line when it fits in the visible part of the list,
 * otherwise above it. It never takes focus, so the keyboard stays up and typing keeps filtering.
 */
@Composable
fun SlashMenuPopup(
    commands: List<SlashCommand>,
    labels: Map<SlashCommand, String>,
    highlighted: Int,
    caret: Rect,
    below: Boolean,
    maxHeight: Dp,
    onSelect: (SlashCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(highlighted) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == highlighted && it.offset >= 0 } || highlighted == visible.lastOrNull()?.index) {
            listState.scrollToItem((highlighted - 1).coerceAtLeast(0))
        }
    }
    val menuLabel = stringResource(R.string.slash_menu)
    Popup(
        popupPositionProvider = CaretPositionProvider(caret, below),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(MENU_WIDTH)
                .heightIn(max = maxHeight)
                .semantics { contentDescription = menuLabel },
        ) {
            LazyColumn(state = listState, modifier = Modifier.padding(vertical = 6.dp)) {
                itemsIndexed(commands, key = { _, command -> command }) { index, command ->
                    SlashMenuItem(command, labels.getValue(command), index == highlighted) { onSelect(command) }
                }
            }
        }
    }
}

@Composable
private fun SlashMenuItem(command: SlashCommand, label: String, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .semantics { selected = highlighted }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
        ) {
            Icon(command.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Places the popup under (or over) the caret line [caret], given in the anchor's coordinates. */
private class CaretPositionProvider(private val caret: Rect, private val below: Boolean) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + caret.left.toInt()).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = if (below) {
            anchorBounds.top + caret.bottom.toInt()
        } else {
            anchorBounds.top + caret.top.toInt() - popupContentSize.height
        }
        return IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}

private val MENU_WIDTH = 280.dp
val MENU_MAX_HEIGHT = 312.dp
