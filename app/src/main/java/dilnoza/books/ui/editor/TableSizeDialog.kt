package dilnoza.books.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dilnoza.books.R

/** Picks the size of a new table, with a live preview of its grid. */
@Composable
fun TableSizeDialog(onConfirm: (rows: Int, columns: Int, headerRow: Boolean) -> Unit, onDismiss: () -> Unit) {
    var rows by rememberSaveable { mutableIntStateOf(TableState.DEFAULT_SIZE) }
    var columns by rememberSaveable { mutableIntStateOf(TableState.DEFAULT_SIZE) }
    var headerRow by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.table_insert_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TablePreview(rows, columns, headerRow)
                Stepper(R.string.table_rows, R.string.table_fewer_rows, R.string.table_more_rows, rows, 1..TableState.MAX_ROWS) {
                    rows = it
                }
                Stepper(
                    R.string.table_columns,
                    R.string.table_fewer_columns,
                    R.string.table_more_columns,
                    columns,
                    1..TableState.MAX_COLUMNS,
                ) { columns = it }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = headerRow, role = Role.Checkbox, onValueChange = { headerRow = it }),
                ) {
                    Checkbox(checked = headerRow, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.table_header_row),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(rows, columns, headerRow) }) { Text(stringResource(R.string.table_insert)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TablePreview(rows: Int, columns: Int, headerRow: Boolean) {
    val line = MaterialTheme.colorScheme.outline
    val header = MaterialTheme.colorScheme.secondaryContainer
    val description = stringResource(R.string.table_size, rows, columns)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = description },
    ) {
        val cell = minOf(size.width / columns, size.height / rows, 24.dp.toPx() * 1.6f)
        val width = cell * columns
        val height = cell * rows
        val left = (size.width - width) / 2
        val top = (size.height - height) / 2
        val stroke = 1.dp.toPx()
        if (headerRow) drawRect(header, Offset(left, top), Size(width, cell))
        for (r in 0..rows) drawLine(line, Offset(left, top + r * cell), Offset(left + width, top + r * cell), stroke)
        for (c in 0..columns) drawLine(line, Offset(left + c * cell, top), Offset(left + c * cell, top + height), stroke)
    }
}

@Composable
private fun Stepper(
    @StringRes label: Int,
    @StringRes decrease: Int,
    @StringRes increase: Int,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    val name = stringResource(label)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(decrease))
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(40.dp)
                .clearAndSetSemantics { stateDescription = "$name: $value" },
        )
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(increase))
        }
    }
}
