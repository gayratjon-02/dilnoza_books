package dilnoza.books.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dilnoza.books.R

/** A block without text of its own: tapping selects it so the toolbar can act on it. */
@Composable
private fun SelectableBlock(
    selected: Boolean,
    description: String,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(min = 48.dp)
            .clip(shape)
            .semantics { contentDescription = description }
            .selectable(selected = selected, onClick = onSelect)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun DividerBlockView(selected: Boolean, onSelect: () -> Unit) {
    SelectableBlock(selected, stringResource(R.string.editor_divider), onSelect) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 48.dp),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
fun PageBreakBlockView(selected: Boolean, onSelect: () -> Unit) {
    val color = MaterialTheme.colorScheme.outlineVariant
    SelectableBlock(selected, stringResource(R.string.editor_page_break), onSelect) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .drawBehind {
                    drawLine(
                        color = color,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = size.height,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    )
                },
        )
        Text(
            text = stringResource(R.string.editor_page_break),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp),
        )
    }
}

/** Read-only grid; cell editing arrives with the table tools. */
@Composable
fun TableBlockView(table: TableState, selected: Boolean, onSelect: () -> Unit) {
    val grid = table.grid
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    SelectableBlock(selected, stringResource(R.string.editor_table), onSelect) {
        Column(modifier = Modifier.fillMaxWidth().border(1.dp, lineColor)) {
            grid.rows.forEachIndexed { rowIndex, row ->
                val header = table.headerRow && rowIndex == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .then(if (header) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (header) FontWeight.SemiBold else null,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, lineColor)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
