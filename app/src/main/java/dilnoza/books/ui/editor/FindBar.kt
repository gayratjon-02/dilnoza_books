package dilnoza.books.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dilnoza.books.R

/** Find and replace across the chapter's text: a query row with match navigation and a replace row. */
@Composable
fun FindBar(
    query: String,
    replacement: String,
    current: Int,
    total: Int,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val queryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { queryFocus.requestFocus() } }
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChanged(it.hasFocus) }
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FindField(
                    value = query,
                    onValueChange = onQueryChange,
                    hint = stringResource(R.string.find_hint),
                    imeAction = ImeAction.Search,
                    onImeAction = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(queryFocus),
                )
                if (query.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.find_counter, current, total),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (total == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                IconButton(onClick = onPrevious, enabled = total > 0) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.find_previous))
                }
                IconButton(onClick = onNext, enabled = total > 0) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.find_next))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.find_close))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FindField(
                    value = replacement,
                    onValueChange = onReplacementChange,
                    hint = stringResource(R.string.find_replace_hint),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReplace, enabled = total > 0) {
                    Text(stringResource(R.string.find_replace))
                }
                val replaceAll = stringResource(R.string.find_replace_all)
                TextButton(
                    onClick = onReplaceAll,
                    enabled = total > 0,
                    modifier = Modifier.semantics { contentDescription = replaceAll },
                ) {
                    Text(stringResource(R.string.find_replace_all_short))
                }
            }
        }
    }
}

@Composable
private fun FindField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    onImeAction: (() -> Unit)? = null,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "")) },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = onImeAction?.let { action -> KeyboardActions(onAny = { action() }) } ?: KeyboardActions.Default,
        modifier = modifier,
        decorationBox = { inner ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(hint, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                    inner()
                }
            }
        },
    )
}
