package dilnoza.books.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dilnoza.books.R
import dilnoza.books.editor.autosave.SaveStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Leaving the app must never lose text, even if the process is killed afterwards.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    if (viewModel.missing) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val request = viewModel.focusRequest
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        val index = viewModel.blocks.indexOfFirst { it.id == request.blockId }
        if (index >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.key == request.blockId }
            // +1 skips the chapter title item.
            if (!visible) listState.scrollToItem(index + 1)
            withFrameNanos { }
            runCatching { viewModel.blocks[index].focusRequester.requestFocus() }
        }
        viewModel.onFocusRequestHandled()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = { SaveIndicator(saveStatus, Modifier.padding(end = 16.dp)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (viewModel.loaded) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    item(key = "title") {
                        TitleField(value = viewModel.title, onValueChange = viewModel::onTitleChange)
                    }
                    items(viewModel.blocks, key = { it.id }) { block ->
                        BlockField(
                            block = block,
                            onValueChange = { viewModel.onBlockChange(block, it) },
                            onFocused = { viewModel.onBlockFocused(block.id) },
                        )
                    }
                    item(key = "tail") {
                        // Tapping the empty space below the text continues writing at the end.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = viewModel::focusLastBlock,
                                ),
                        )
                    }
                }
                HorizontalDivider()
                EditorToolbar(
                    wordCount = viewModel.wordCount,
                    focusedKind = viewModel.focusedBlock?.kind,
                    onAddParagraph = { viewModel.addBlock(BlockKind.PARAGRAPH) },
                    onAddHeading = { viewModel.addBlock(BlockKind.HEADING) },
                    onToggleKind = viewModel::toggleFocusedKind,
                    onDelete = viewModel::deleteFocusedBlock,
                )
            }
        }
    }
}

@Composable
private fun TitleField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    val style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) Placeholder(stringResource(R.string.editor_title_hint), style)
                inner()
            }
        },
    )
}

@Composable
private fun BlockField(
    block: EditorBlock,
    onValueChange: (TextFieldValue) -> Unit,
    onFocused: () -> Unit,
) {
    val heading = block.kind == BlockKind.HEADING
    val style = if (heading) headingStyle() else paragraphStyle()
    BasicTextField(
        value = block.value,
        onValueChange = onValueChange,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (heading) 16.dp else 4.dp, bottom = if (heading) 8.dp else 4.dp)
            .focusRequester(block.focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() },
        decorationBox = { inner ->
            Box {
                if (block.value.text.isEmpty()) {
                    Placeholder(
                        text = stringResource(if (heading) R.string.editor_heading_hint else R.string.editor_body_hint),
                        style = style,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun Placeholder(text: String, style: TextStyle) {
    Text(text = text, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
}

@Composable
private fun headingStyle(): TextStyle = MaterialTheme.typography.headlineSmall.copy(
    color = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun paragraphStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontFamily = FontFamily.Serif,
    fontSize = 18.sp,
    lineHeight = 28.sp,
    color = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun EditorToolbar(
    wordCount: Int,
    focusedKind: BlockKind?,
    onAddParagraph: () -> Unit,
    onAddHeading: () -> Unit,
    onToggleKind: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleKind, enabled = focusedKind != null) {
                    Icon(
                        Icons.Outlined.TextFields,
                        contentDescription = stringResource(
                            if (focusedKind == BlockKind.HEADING) R.string.editor_make_paragraph else R.string.editor_make_heading,
                        ),
                    )
                }
                IconButton(onClick = onDelete, enabled = focusedKind != null) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.editor_delete_block))
                }
                AssistChip(
                    onClick = onAddParagraph,
                    label = { Text(stringResource(R.string.editor_add_paragraph)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null) },
                )
                AssistChip(
                    onClick = onAddHeading,
                    label = { Text(stringResource(R.string.editor_add_heading)) },
                    leadingIcon = { Icon(Icons.Outlined.Title, contentDescription = null) },
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = pluralStringResource(R.plurals.editor_word_count, wordCount, wordCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun SaveIndicator(status: SaveStatus, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        when (status) {
            SaveStatus.Saved -> Icon(Icons.Outlined.Check, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            SaveStatus.Dirty, SaveStatus.Saving -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            is SaveStatus.Error -> Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(
                when (status) {
                    SaveStatus.Saved -> R.string.save_status_saved
                    SaveStatus.Dirty, SaveStatus.Saving -> R.string.save_status_saving
                    is SaveStatus.Error -> R.string.save_status_error
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (status is SaveStatus.Error) MaterialTheme.colorScheme.error else color,
        )
    }
}
