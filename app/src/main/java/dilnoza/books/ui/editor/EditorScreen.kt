package dilnoza.books.ui.editor

import android.content.ClipDescription
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dilnoza.books.R
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.editor.autosave.SaveStatus
import dilnoza.books.editor.commands.SlashCommands
import dilnoza.books.editor.commands.SlashQuery
import dilnoza.books.editor.richtext.StyleAttribute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val resolver = LocalContext.current.contentResolver
    val activity = LocalActivity.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var listTopInRoot by remember { mutableFloatStateOf(0f) }
    var listBottomInRoot by remember { mutableFloatStateOf(0f) }
    var dropHovering by remember { mutableStateOf(false) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_IMAGES),
    ) { uris -> viewModel.insertImages(resolver.openImageSources(uris)) }

    val dropTarget = remember(viewModel, resolver, activity) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragEvent = event.toAndroidDragEvent()
                // Grants read access to content dragged in from other apps for as long as we hold it.
                val permissions = activity?.requestDragAndDropPermissions(dragEvent)
                val sources = resolver.openImageSources(dragEvent.clipData.imageUris(resolver))
                permissions?.release()
                dropHovering = false
                if (sources.isEmpty()) return false
                val index = dropIndex(listState, viewModel, dragEvent.y - listTopInRoot)
                viewModel.insertImages(sources, index)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) {
                dropHovering = true
            }

            override fun onExited(event: DragAndDropEvent) {
                dropHovering = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                dropHovering = false
            }
        }
    }

    val imageFailed = stringResource(R.string.editor_image_failed)
    LaunchedEffect(viewModel.message) {
        if (viewModel.message == EditorMessage.IMAGE_FAILED) {
            snackbarHostState.showSnackbar(imageFailed)
            viewModel.onMessageShown()
        }
    }

    // Leaving the app must never lose text, even if the process is killed afterwards.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    if (viewModel.missing) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    LaunchedEffect(viewModel.clearFocusRequest) {
        if (viewModel.clearFocusRequest == null) return@LaunchedEffect
        focusManager.clearFocus()
        viewModel.onClearFocusHandled()
    }

    val request = viewModel.focusRequest
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        val target = viewModel.blocks.firstOrNull { it.id == request.blockId }
        // A block added in this frame has no field yet; focus moves as soon as it has one, so that
        // keys typed right after Enter land in the new block. A block just below the visible part is
        // scrolled up a little at a time rather than jumped to the top.
        if (target != null) {
            for (attempt in 0 until MAX_FOCUS_FRAMES) {
                val table = target.table
                val requester = if (target.kind == BlockKind.TABLE && table != null) {
                    target.cellFocusRequester(request.cell?.takeIf(table::contains) ?: table.firstCell)
                } else {
                    target.focusRequester
                }
                if (requester.tryFocus()) break
                withFrameNanos { }
                // +1 skips the chapter title item.
                val item = viewModel.blocks.indexOf(target) + 1
                val info = listState.layoutInfo
                if (item == 0 || info.visibleItemsInfo.any { it.index == item }) continue
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (item > last && item - last <= 2) {
                    listState.scrollBy((info.viewportEndOffset - info.viewportStartOffset) / 3f)
                } else {
                    listState.scrollToItem(item)
                }
            }
        }
        viewModel.onFocusRequestHandled()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            listTopInRoot = it.positionInRoot().y
                            listBottomInRoot = listTopInRoot + it.size.height
                        }
                        .dragAndDropTarget(
                            shouldStartDragAndDrop = { event ->
                                event.mimeTypes().any { ClipDescription.compareMimeTypes(it, "image/*") }
                            },
                            target = dropTarget,
                        )
                        .then(
                            if (dropHovering) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            },
                        ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    item(key = "title") {
                        TitleField(value = viewModel.title, onValueChange = viewModel::onTitleChange)
                    }
                    items(viewModel.blocks, key = { it.id }) { block ->
                        val selectBlock = {
                            focusManager.clearFocus()
                            viewModel.selectBlock(block.id)
                        }
                        val selected = viewModel.focusedBlock?.id == block.id
                        when (block.kind) {
                            BlockKind.IMAGE -> ImageBlockView(
                                block = block,
                                file = block.image?.fileName?.let(viewModel::imageFile),
                                selected = selected,
                                onSelect = selectBlock,
                                onResize = { viewModel.setImageWidth(block, it) },
                                onCaptionChange = { viewModel.onBlockChange(block, it) },
                                onCaptionFocused = { viewModel.onBlockFocused(block.id) },
                            )
                            BlockKind.DIVIDER -> DividerBlockView(selected, selectBlock)
                            BlockKind.PAGE_BREAK -> PageBreakBlockView(selected, selectBlock)
                            BlockKind.TABLE -> block.table?.let { table ->
                                TableBlockView(
                                    block = block,
                                    table = table,
                                    selected = selected && viewModel.focusedCell == null,
                                    focusedCell = viewModel.focusedCell.takeIf { selected },
                                    onCellFocused = { viewModel.onCellFocused(block, it) },
                                    onCellChange = { cell, text -> viewModel.onCellChange(block, cell, text) },
                                    onCellNext = { cell, forward, enter -> viewModel.onCellNext(block, cell, forward, enter) },
                                )
                            }
                            else -> BlockField(
                                block = block,
                                listLabel = viewModel.listLabels[block.id],
                                onValueChange = { viewModel.onBlockChange(block, it) },
                                onBackspaceAtStart = { viewModel.onBackspaceAtStart(block) },
                                onDeleteAtEnd = { viewModel.onDeleteAtEnd(block) },
                                onFocused = { viewModel.onBlockFocused(block.id) },
                                onFormat = viewModel::format,
                                onIndent = viewModel::indentFocused,
                                slashQuery = viewModel.slashQuery?.takeIf { selected },
                                visibleTop = listTopInRoot,
                                visibleBottom = listBottomInRoot,
                                onSlashCommand = { command ->
                                    if (viewModel.runSlashCommand(command)) {
                                        pickImages.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    }
                                },
                                onSlashDismiss = viewModel::dismissSlashMenu,
                                onImagesPasted = { uris ->
                                    viewModel.insertImages(resolver.openImageSources(uris))
                                },
                            )
                        }
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
                val focused = viewModel.focusedBlock
                if (focused != null && focused.kind == BlockKind.IMAGE && !focused.isPendingImage) {
                    ImageToolbar(
                        image = checkNotNull(focused.image),
                        onAlign = { viewModel.setImageAlign(focused, it) },
                        onWidth = { viewModel.setImageWidth(focused, it) },
                        onMove = viewModel::moveFocusedBlock,
                        onDelete = viewModel::deleteFocusedBlock,
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.selectBlock("")
                        },
                    )
                } else {
                    val formatState = viewModel.formatState
                    val table = focused?.table
                    val cell = viewModel.focusedCell
                    if (focused != null && focused.kind == BlockKind.TABLE && table != null && cell != null && table.contains(cell)) {
                        TableBar(
                            table = table,
                            onAction = viewModel::onTableAction,
                            onDeleteTable = viewModel::deleteFocusedBlock,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    } else if (focused != null && formatState != null) {
                        FormatBar(
                            state = formatState,
                            kind = focused.kind,
                            headingLevel = focused.headingLevel,
                            indent = focused.indent,
                            align = focused.align,
                            onFormat = viewModel::format,
                            onAlign = viewModel::setFocusedAlign,
                            onKind = viewModel::setFocusedKind,
                            onIndent = viewModel::indentFocused,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    EditorToolbar(
                        wordCount = viewModel.wordCount,
                        focusedKind = focused?.kind,
                        onAddParagraph = { viewModel.addBlock(BlockKind.PARAGRAPH) },
                        onAddHeading = { viewModel.addBlock(BlockKind.HEADING) },
                        onAddImage = {
                            pickImages.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onDelete = viewModel::deleteFocusedBlock,
                    )
                }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockField(
    block: EditorBlock,
    listLabel: String?,
    onValueChange: (TextFieldValue) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onDeleteAtEnd: () -> Unit,
    onFocused: () -> Unit,
    onFormat: (FormatChange) -> Unit,
    onIndent: (Int) -> Unit,
    slashQuery: SlashQuery?,
    visibleTop: Float,
    visibleBottom: Float,
    onSlashCommand: (SlashCommand) -> Unit,
    onSlashDismiss: () -> Unit,
    onImagesPasted: (List<Uri>) -> Unit,
) {
    val kind = block.kind
    val heading = kind == BlockKind.HEADING
    val rich = block.rich
    val baseStyle = blockStyle(kind, block.headingLevel)
    val largestSpanSp = rich.spans.maxOfOrNull { it.style.fontSizeSp ?: 0 } ?: 0
    val style = baseStyle.copy(
        textAlign = block.align.toTextAlign(),
        // The block's fixed line height would clip enlarged text, so grow it to fit the largest run.
        lineHeight = if (baseStyle.lineHeight.isSp && largestSpanSp * LINE_HEIGHT_FACTOR > baseStyle.lineHeight.value) {
            (largestSpanSp * LINE_HEIGHT_FACTOR).sp
        } else {
            baseStyle.lineHeight
        },
    )
    val darkTheme = isDarkPage()
    val transformation = remember(rich, darkTheme) { RichTextTransformation(rich, darkTheme, hiddenPrefix = 1) }
    val fieldValue = remember(block.value) { block.value.toFieldValue() }
    val labels = SlashCommand.entries.associateWith { it.label() }
    val commands = remember(slashQuery?.filter, labels) {
        slashQuery?.let { query -> SlashCommands.filter(SlashCommand.entries, query.filter) { listOf(labels.getValue(it)) + it.keywords } }
            .orEmpty()
    }
    var highlighted by remember(commands) { mutableIntStateOf(0) }
    val menuOpen = commands.isNotEmpty()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var fieldTop by remember { mutableFloatStateOf(0f) }
    val quoteColor = MaterialTheme.colorScheme.primary
    val hint = when (kind) {
        BlockKind.HEADING -> R.string.editor_heading_hint
        BlockKind.BULLET, BlockKind.NUMBERED -> R.string.editor_list_hint
        BlockKind.QUOTE -> R.string.editor_quote_hint
        else -> R.string.editor_body_hint
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (heading) 16.dp else 4.dp, bottom = if (heading) 8.dp else 4.dp)
            .then(
                if (kind == BlockKind.QUOTE) {
                    Modifier
                        .drawBehind {
                            val width = 3.dp.toPx()
                            val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
                            drawRect(quoteColor, topLeft = Offset(x, 0f), size = Size(width, size.height))
                        }
                        .padding(start = 16.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        if (listLabel != null) {
            Text(
                text = listLabel,
                style = style.copy(textAlign = TextAlign.End),
                modifier = Modifier
                    .padding(start = LIST_INDENT * block.indent, end = 8.dp)
                    .width(LIST_MARKER_WIDTH),
            )
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { field ->
                when (val edit = fieldEdit(block.value, field)) {
                    is FieldEdit.Change -> if (
                        menuOpen &&
                        BlockEditing.insertedLineBreaks(block.value.text, edit.value.text, edit.value.selection.end).isNotEmpty()
                    ) {
                        // Enter on the soft keyboard picks the highlighted command.
                        onSlashCommand(commands[highlighted])
                    } else {
                        onValueChange(edit.value)
                    }
                    FieldEdit.BackspaceAtStart -> onBackspaceAtStart()
                }
            },
            textStyle = style,
            onTextLayout = { layout = it },
            visualTransformation = transformation,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .focusRequester(block.focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .onPreviewKeyEvent { event ->
                    if (menuOpen) {
                        val handled = event.key in MENU_KEYS
                        if (handled && event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionDown -> highlighted = (highlighted + 1) % commands.size
                                Key.DirectionUp -> highlighted = (highlighted - 1 + commands.size) % commands.size
                                Key.Escape -> onSlashDismiss()
                                else -> onSlashCommand(commands[highlighted])
                            }
                        }
                        if (handled) return@onPreviewKeyEvent true
                    }
                    val value = block.value
                    if (event.key == Key.Delete && value.selection == TextRange(value.text.length) && event.isPlain()) {
                        if (event.type == KeyEventType.KeyDown) onDeleteAtEnd()
                        return@onPreviewKeyEvent true
                    }
                    if (event.key == Key.Tab && (kind == BlockKind.BULLET || kind == BlockKind.NUMBERED) &&
                        !event.isCtrlPressed && !event.isAltPressed
                    ) {
                        if (event.type == KeyEventType.KeyDown) onIndent(if (event.isShiftPressed) -1 else 1)
                        return@onPreviewKeyEvent true
                    }
                    val change = event.formatShortcut() ?: return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) onFormat(change)
                    true
                }
                // Images pasted from the clipboard or inserted by the keyboard (e.g. a fresh screenshot).
                .contentReceiver { content ->
                    if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
                    val uris = mutableListOf<Uri>()
                    val remaining = content.consume { item -> item.uri?.let(uris::add) != null }
                    if (uris.isNotEmpty()) onImagesPasted(uris)
                    remaining
                },
            decorationBox = { inner ->
                // The inner field only fills the width (and so honours text alignment) under a min width.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { fieldTop = it.positionInRoot().y },
                    propagateMinConstraints = true,
                ) {
                    if (block.value.text.isEmpty()) Placeholder(stringResource(hint), style)
                    inner()
                    val textLayout = layout
                    if (menuOpen && slashQuery != null && textLayout != null) {
                        val density = LocalDensity.current
                        val offset = slashQuery.slashIndex.coerceIn(0, textLayout.layoutInput.text.length)
                        val gap = with(density) { MENU_GAP.toPx() }
                        val caret = textLayout.getCursorRect(offset).let { it.copy(top = it.top - gap, bottom = it.bottom + gap) }
                        val spaceBelow = visibleBottom - (fieldTop + caret.bottom)
                        val spaceAbove = fieldTop + caret.top - visibleTop
                        val wanted = with(density) { minOf(MENU_MAX_HEIGHT, MENU_ITEM_HEIGHT * commands.size + 12.dp).toPx() }
                        val below = spaceBelow >= wanted || spaceBelow >= spaceAbove
                        SlashMenuPopup(
                            commands = commands,
                            labels = labels,
                            highlighted = highlighted.coerceIn(commands.indices),
                            caret = caret,
                            below = below,
                            maxHeight = with(density) { (if (below) spaceBelow else spaceAbove).toDp() }
                                .coerceIn(MENU_ITEM_HEIGHT * 2, MENU_MAX_HEIGHT),
                            onSelect = onSlashCommand,
                            onDismiss = onSlashDismiss,
                        )
                    }
                }
            },
        )
    }
}

private fun FocusRequester.tryFocus(): Boolean = runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

private fun KeyEvent.isPlain(): Boolean = !isCtrlPressed && !isAltPressed && !isShiftPressed

/** Word-processor shortcuts on a hardware keyboard: Ctrl+B, I, U, Ctrl+Shift+X and Ctrl+Backslash. */
private fun KeyEvent.formatShortcut(): FormatChange? {
    if (!isCtrlPressed || isAltPressed) return null
    return when {
        key == Key.B && !isShiftPressed -> FormatChange.Toggle(StyleAttribute.BOLD)
        key == Key.I && !isShiftPressed -> FormatChange.Toggle(StyleAttribute.ITALIC)
        key == Key.U && !isShiftPressed -> FormatChange.Toggle(StyleAttribute.UNDERLINE)
        key == Key.X && isShiftPressed -> FormatChange.Toggle(StyleAttribute.STRIKETHROUGH)
        key == Key.Backslash && !isShiftPressed -> FormatChange.Clear
        else -> null
    }
}

@Composable
private fun Placeholder(text: String, style: TextStyle) {
    Text(text = text, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
}

@Composable
private fun blockStyle(kind: BlockKind, headingLevel: Int): TextStyle = when (kind) {
    BlockKind.HEADING -> when (headingLevel) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }.copy(color = MaterialTheme.colorScheme.onSurface)
    BlockKind.QUOTE -> paragraphStyle().copy(
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    else -> paragraphStyle()
}

private fun TextAlignment.toTextAlign(): TextAlign = when (this) {
    TextAlignment.START -> TextAlign.Start
    TextAlignment.CENTER -> TextAlign.Center
    TextAlignment.END -> TextAlign.End
    TextAlignment.JUSTIFY -> TextAlign.Justify
}

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
    onAddImage: () -> Unit,
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
                AssistChip(
                    onClick = onAddImage,
                    label = { Text(stringResource(R.string.editor_add_image)) },
                    leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
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

@Composable
private fun ImageToolbar(
    image: ImageState,
    onAlign: (TextAlignment) -> Unit,
    onWidth: (Int) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Outlined.Done, contentDescription = stringResource(R.string.editor_image_done))
            }
            AlignButton(Icons.AutoMirrored.Outlined.FormatAlignLeft, R.string.editor_align_start, image.align == TextAlignment.START) {
                onAlign(TextAlignment.START)
            }
            AlignButton(Icons.Outlined.FormatAlignCenter, R.string.editor_align_center, image.align == TextAlignment.CENTER) {
                onAlign(TextAlignment.CENTER)
            }
            AlignButton(Icons.AutoMirrored.Outlined.FormatAlignRight, R.string.editor_align_end, image.align == TextAlignment.END) {
                onAlign(TextAlignment.END)
            }
            WIDTH_PRESETS.forEach { percent ->
                FilterChip(
                    selected = image.widthPercent == percent,
                    onClick = { onWidth(percent) },
                    label = { Text(stringResource(R.string.editor_image_width, percent)) },
                )
            }
            IconButton(onClick = { onMove(-1) }) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.action_move_up))
            }
            IconButton(onClick = { onMove(1) }) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.action_move_down))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.editor_delete_block))
            }
        }
    }
}

@Composable
private fun AlignButton(
    icon: ImageVector,
    label: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconToggleButton(checked = selected, onCheckedChange = { onClick() }) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

/** Maps a drop point (relative to the list's top) to the block index the images should be inserted at. */
private fun dropIndex(listState: LazyListState, viewModel: EditorViewModel, y: Float): Int {
    val hit = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.offset <= y }
        ?: return 0
    return when (hit.key) {
        "title" -> 0
        "tail" -> viewModel.blocks.size
        else -> {
            val index = viewModel.blocks.indexOfFirst { it.id == hit.key }
            if (index < 0) viewModel.blocks.size else index + if (y > hit.offset + hit.size / 2) 1 else 0
        }
    }
}

private const val MAX_PICKED_IMAGES = 10
private const val MAX_FOCUS_FRAMES = 6
private val MENU_GAP = 4.dp
private val MENU_ITEM_HEIGHT = 48.dp
private val MENU_KEYS = setOf(Key.DirectionDown, Key.DirectionUp, Key.Enter, Key.NumPadEnter, Key.Escape)
private val LIST_INDENT = 24.dp
private const val LINE_HEIGHT_FACTOR = 1.4f
private val LIST_MARKER_WIDTH = 28.dp
private val WIDTH_PRESETS = listOf(25, 50, 75, 100)
