package dilnoza.books.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dilnoza.books.R

/** Row, column and header tools for the table cell holding the caret. Buttons never take focus, so the keyboard stays up. */
@Composable
fun TableBar(table: TableState, onAction: (TableAction) -> Unit, onDeleteTable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = table.headerRow,
            onClick = { onAction(TableAction.TOGGLE_HEADER) },
            label = { Text(stringResource(R.string.table_header_row)) },
            leadingIcon = { Icon(Icons.Outlined.TableRows, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        Spacer(Modifier.width(6.dp))
        VerticalDivider(Modifier.height(24.dp))
        BarAction(Icons.Outlined.ArrowUpward, R.string.table_row_above) { onAction(TableAction.ROW_ABOVE) }
        BarAction(Icons.Outlined.ArrowDownward, R.string.table_row_below) { onAction(TableAction.ROW_BELOW) }
        BarAction(Icons.Outlined.RemoveCircleOutline, R.string.table_delete_row, enabled = table.rowKeys.size > 1) {
            onAction(TableAction.DELETE_ROW)
        }
        VerticalDivider(Modifier.height(24.dp))
        BarAction(Icons.AutoMirrored.Outlined.ArrowBack, R.string.table_column_before) { onAction(TableAction.COLUMN_BEFORE) }
        BarAction(Icons.AutoMirrored.Outlined.ArrowForward, R.string.table_column_after) { onAction(TableAction.COLUMN_AFTER) }
        BarAction(Icons.Outlined.RemoveCircleOutline, R.string.table_delete_column, enabled = table.columnKeys.size > 1) {
            onAction(TableAction.DELETE_COLUMN)
        }
        VerticalDivider(Modifier.height(24.dp))
        TextButton(
            onClick = onDeleteTable,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.table_delete))
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, @StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(label))
    }
}
