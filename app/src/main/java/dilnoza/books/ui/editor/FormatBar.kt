package dilnoza.books.ui.editor

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignJustify
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dilnoza.books.R
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.editor.richtext.StyleAttribute

private enum class FormatPanel { NONE, TEXT_STYLE, COLOR, HIGHLIGHT, SIZE, ALIGN }

/**
 * Character and block formatting for the focused text block. Choices with several options open an
 * inline panel in place of the main row, so the keyboard stays up and the bar never changes height.
 */
@Composable
fun FormatBar(
    state: FormatState,
    kind: BlockKind,
    headingLevel: Int,
    indent: Int,
    align: TextAlignment,
    onFormat: (FormatChange) -> Unit,
    onAlign: (TextAlignment) -> Unit,
    onKind: (kind: BlockKind, headingLevel: Int) -> Unit,
    onIndent: (delta: Int) -> Unit,
) {
    var panel by rememberSaveable { mutableStateOf(FormatPanel.NONE) }
    val close = { panel = FormatPanel.NONE }
    AnimatedContent(
        targetState = panel,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "formatPanel",
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT),
    ) { shown ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shown != FormatPanel.NONE) {
                IconButton(onClick = close) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.format_close_panel))
                }
                VerticalDivider(Modifier.height(24.dp))
            }
            when (shown) {
                FormatPanel.NONE -> MainRow(state, kind, headingLevel, indent, align, onFormat, onIndent, open = { panel = it })
                FormatPanel.TEXT_STYLE -> TextStylePanel(kind, headingLevel) { newKind, level ->
                    onKind(newKind, level)
                    close()
                }
                FormatPanel.COLOR -> ColorPanel(
                    colors = TextPalette.text,
                    selected = state.color,
                    resetLabel = R.string.format_color_default,
                ) {
                    onFormat(FormatChange.TextColor(it))
                    close()
                }
                FormatPanel.HIGHLIGHT -> ColorPanel(
                    colors = TextPalette.highlight,
                    selected = state.background,
                    resetLabel = R.string.format_highlight_none,
                ) {
                    onFormat(FormatChange.Highlight(it))
                    close()
                }
                FormatPanel.SIZE -> SizePanel(state.fontSizeSp) {
                    onFormat(FormatChange.FontSize(it))
                    close()
                }
                FormatPanel.ALIGN -> AlignPanel(align) {
                    onAlign(it)
                    close()
                }
            }
        }
    }
}

@Composable
private fun RowScope.MainRow(
    state: FormatState,
    kind: BlockKind,
    headingLevel: Int,
    indent: Int,
    align: TextAlignment,
    onFormat: (FormatChange) -> Unit,
    onIndent: (Int) -> Unit,
    open: (FormatPanel) -> Unit,
) {
    val textStyleLabel = stringResource(R.string.format_text_style)
    TextButton(
        onClick = { open(FormatPanel.TEXT_STYLE) },
        modifier = Modifier.semantics { contentDescription = textStyleLabel },
    ) {
        Text(
            text = when (kind) {
                BlockKind.HEADING -> stringResource(R.string.format_heading_short, headingLevel)
                BlockKind.BULLET -> stringResource(R.string.block_bulleted_list)
                BlockKind.NUMBERED -> stringResource(R.string.block_numbered_list)
                BlockKind.QUOTE -> stringResource(R.string.editor_quote_hint)
                else -> stringResource(R.string.format_paragraph)
            },
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
    }
    if (kind == BlockKind.BULLET || kind == BlockKind.NUMBERED) {
        IconButton(onClick = { onIndent(-1) }, enabled = indent > 0) {
            Icon(Icons.AutoMirrored.Outlined.FormatIndentDecrease, contentDescription = stringResource(R.string.format_outdent))
        }
        IconButton(onClick = { onIndent(1) }, enabled = indent < BlockContent.ListItem.MAX_INDENT) {
            Icon(Icons.AutoMirrored.Outlined.FormatIndentIncrease, contentDescription = stringResource(R.string.format_indent))
        }
    }
    VerticalDivider(Modifier.height(24.dp))
    StyleToggle(Icons.Outlined.FormatBold, R.string.format_bold, state.bold) {
        onFormat(FormatChange.Toggle(StyleAttribute.BOLD))
    }
    StyleToggle(Icons.Outlined.FormatItalic, R.string.format_italic, state.italic) {
        onFormat(FormatChange.Toggle(StyleAttribute.ITALIC))
    }
    StyleToggle(Icons.Outlined.FormatUnderlined, R.string.format_underline, state.underline) {
        onFormat(FormatChange.Toggle(StyleAttribute.UNDERLINE))
    }
    StyleToggle(Icons.Outlined.FormatStrikethrough, R.string.format_strikethrough, state.strikethrough) {
        onFormat(FormatChange.Toggle(StyleAttribute.STRIKETHROUGH))
    }
    VerticalDivider(Modifier.height(24.dp))
    ColorButton(Icons.Outlined.FormatColorText, R.string.format_text_color, state.color) { open(FormatPanel.COLOR) }
    ColorButton(Icons.Outlined.BorderColor, R.string.format_highlight, state.background) { open(FormatPanel.HIGHLIGHT) }
    StyleToggle(Icons.Outlined.FormatSize, R.string.format_font_size, state.fontSizeSp != null) {
        open(FormatPanel.SIZE)
    }
    IconButton(onClick = { open(FormatPanel.ALIGN) }) {
        Icon(align.icon(), contentDescription = stringResource(R.string.format_alignment))
    }
    VerticalDivider(Modifier.height(24.dp))
    IconButton(onClick = { onFormat(FormatChange.Clear) }) {
        Icon(Icons.Outlined.FormatClear, contentDescription = stringResource(R.string.format_clear))
    }
}

@Composable
private fun StyleToggle(icon: ImageVector, @StringRes label: Int, checked: Boolean, onClick: () -> Unit) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

/** Icon with a bar underneath showing the colour currently in effect. */
@Composable
private fun ColorButton(icon: ImageVector, @StringRes label: Int, argb: Int?, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = stringResource(label), modifier = Modifier.offset(y = (-2).dp))
            Box(
                Modifier
                    .offset(y = 11.dp)
                    .width(18.dp)
                    .height(3.dp)
                    .background(
                        argb?.let { resolvedColor(it) } ?: MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

@Composable
private fun TextStylePanel(kind: BlockKind, headingLevel: Int, onSelect: (BlockKind, Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
        FilterChip(
            selected = kind == BlockKind.PARAGRAPH,
            onClick = { onSelect(BlockKind.PARAGRAPH, headingLevel) },
            label = { Text(stringResource(R.string.format_paragraph)) },
        )
        for (level in BlockContent.Heading.MIN_LEVEL..BlockContent.Heading.MAX_LEVEL) {
            FilterChip(
                selected = kind == BlockKind.HEADING && headingLevel == level,
                onClick = { onSelect(BlockKind.HEADING, level) },
                label = { Text(stringResource(R.string.format_heading, level)) },
            )
        }
        for ((option, label) in LIST_STYLES) {
            FilterChip(
                selected = kind == option,
                onClick = { onSelect(option, headingLevel) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

private val LIST_STYLES = listOf(
    BlockKind.BULLET to R.string.block_bulleted_list,
    BlockKind.NUMBERED to R.string.block_numbered_list,
    BlockKind.QUOTE to R.string.editor_quote_hint,
)

@Composable
private fun ColorPanel(colors: List<PaletteColor>, selected: Int?, @StringRes resetLabel: Int, onSelect: (Int?) -> Unit) {
    Swatch(color = null, label = stringResource(resetLabel), selected = selected == null) { onSelect(null) }
    colors.forEach { color ->
        Swatch(color = resolvedColor(color.light), label = stringResource(color.label), selected = selected == color.light) {
            onSelect(color.light)
        }
    }
}

/** A 48dp touch target around a colour circle; [color] null is the "no colour" choice. */
@Composable
private fun Swatch(color: Color?, label: String, selected: Boolean, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp)
                    } else {
                        Modifier
                    },
                )
                .background(color ?: MaterialTheme.colorScheme.surface, CircleShape)
                .border(1.dp, outline.copy(alpha = 0.4f), CircleShape),
        ) {
            when {
                color == null -> Icon(
                    Icons.Outlined.FormatColorReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                selected -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SizePanel(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
        TextSize.entries.forEach { size ->
            FilterChip(
                selected = selected == size.sp,
                onClick = { onSelect(size.sp) },
                label = { Text(stringResource(size.label)) },
            )
        }
    }
}

@Composable
private fun AlignPanel(selected: TextAlignment, onSelect: (TextAlignment) -> Unit) {
    TextAlignment.entries.forEach { align ->
        StyleToggle(align.icon(), align.label(), selected == align) { onSelect(align) }
    }
}

/** Whether the page is drawn dark, whatever theme setting made it so. */
@Composable
fun isDarkPage(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
private fun resolvedColor(argb: Int): Color = Color(TextPalette.resolve(argb, isDarkPage()))

private fun TextAlignment.icon(): ImageVector = when (this) {
    TextAlignment.START -> Icons.AutoMirrored.Outlined.FormatAlignLeft
    TextAlignment.CENTER -> Icons.Outlined.FormatAlignCenter
    TextAlignment.END -> Icons.AutoMirrored.Outlined.FormatAlignRight
    TextAlignment.JUSTIFY -> Icons.Outlined.FormatAlignJustify
}

@StringRes
private fun TextAlignment.label(): Int = when (this) {
    TextAlignment.START -> R.string.editor_align_start
    TextAlignment.CENTER -> R.string.editor_align_center
    TextAlignment.END -> R.string.editor_align_end
    TextAlignment.JUSTIFY -> R.string.format_align_justify
}

private val BAR_HEIGHT = 56.dp
