package dilnoza.books.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dilnoza.books.R

/** Dialog with one required and an optional second text field. */
@Composable
fun TextFieldsDialog(
    title: String,
    confirmLabel: String,
    primaryLabel: String,
    initialPrimary: String,
    onConfirm: (primary: String, secondary: String) -> Unit,
    onDismiss: () -> Unit,
    secondaryLabel: String? = null,
    initialSecondary: String = "",
) {
    var primary by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialPrimary, TextRange(0, initialPrimary.length)))
    }
    var secondary by rememberSaveable { mutableStateOf(initialSecondary) }
    val focusRequester = remember { FocusRequester() }
    val canConfirm = primary.text.isNotBlank()
    val confirm = { if (canConfirm) onConfirm(primary.text.trim(), secondary.trim()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = primary,
                    onValueChange = { primary = it },
                    label = { Text(primaryLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (secondaryLabel == null) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { confirm() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (secondaryLabel != null) {
                    OutlinedTextField(
                        value = secondary,
                        onValueChange = { secondary = it },
                        label = { Text(secondaryLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { confirm() },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = canConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
