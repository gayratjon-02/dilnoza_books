package dilnoza.books.ui.editor

import android.content.ClipDescription
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dilnoza.books.R
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.editor.autosave.SaveStatus
import dilnoza.books.editor.commands.SlashCommands
import dilnoza.books.editor.commands.SlashQuery
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PageStyles
import dilnoza.books.editor.richtext.StyleAttribute
import dilnoza.books.editor.search.SearchMatch
import dilnoza.books.ui.editor.page.EditorPages
import dilnoza.books.ui.editor.page.LocalPageScale
import dilnoza.books.ui.editor.page.LocalScreenDensity
import dilnoza.books.ui.editor.page.NormalSize
import dilnoza.books.ui.editor.page.PageColors
import dilnoza.books.ui.editor.page.PageSlice
import dilnoza.books.ui.editor.page.PointSpace
import dilnoza.books.ui.editor.page.layoutContent
import dilnoza.books.ui.editor.page.rememberEditorPages
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    // The block list is laid out in page points (see PointSpace); scroll maths uses the same units.
    val density = PageMeasurer.PointDensity
    var pageScale by remember { mutableFloatStateOf(1f) }
    var listTopInRoot by remember { mutableFloatStateOf(0f) }
    var listLeftInRoot by remember { mutableFloatStateOf(0f) }
    var listRightInRoot by remember { mutableFloatStateOf(0f) }
    val screenDensity = LocalDensity.current
    val pageScroll = rememberScrollState()
    var viewportWidth by remember { mutableIntStateOf(0) }
    // Screen pixels per page point at 100%.
    var fitWidthScale by remember { mutableFloatStateOf(0f) }
    // Sideways scroll position wanted after a pinch, applied once the wider pages are laid out.
    var pendingPageScroll by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pageScroll) {
        snapshotFlow { pageScroll.maxValue }.collect {
            pendingPageScroll?.let { target ->
                pendingPageScroll = null
                pageScroll.scrollTo(target)
            }
        }
    }

    val scope = rememberCoroutineScope()

    /** Scrolls the zoomed pages sideways so the caret between [left] and [right] (root x) is on screen. */
    fun revealSideways(left: Float, right: Float) {
        val margin = with(screenDensity) { CARET_SIDE_MARGIN.toPx() }
        val delta = when {
            right > listRightInRoot - margin -> right - (listRightInRoot - margin)
            left < listLeftInRoot + margin -> left - (listLeftInRoot + margin)
            else -> return
        }
        scope.launch { pageScroll.scrollBy(delta) }
    }

    /** Zooms by [factor] keeping the page point under the fingers' [focus] (in list coordinates) where it is. */
    fun pinch(factor: Float, focus: Offset) {
        val oldZoom = viewModel.zoom
        viewModel.zoomBy(factor)
        val ratio = viewModel.zoom / oldZoom
        if (ratio == 1f || fitWidthScale <= 0f) return
        // From the zoom itself: several pinch steps can arrive before the pages are drawn again.
        val oldScale = fitWidthScale * oldZoom
        val newScale = fitWidthScale * viewModel.zoom
        // Vertically the list is laid out in points: move it by the distance the focus drifts in points.
        listState.dispatchRawDelta(focus.y / oldScale - focus.y / newScale)
        // Sideways the pages are viewportWidth * zoom wide, centred while narrower than the screen.
        val oldLeft = (viewportWidth * (1f - oldZoom) / 2f).coerceAtLeast(0f)
        val newLeft = (viewportWidth * (1f - viewModel.zoom) / 2f).coerceAtLeast(0f)
        val x = ((pendingPageScroll ?: pageScroll.value) + focus.x - oldLeft) * ratio + newLeft - focus.x
        pendingPageScroll = x.roundToInt().coerceAtLeast(0)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var listBottomInRoot by remember { mutableFloatStateOf(0f) }
    var dropHovering by remember { mutableStateOf(false) }
    var pickTableSize by rememberSaveable { mutableStateOf(false) }
    var findFocused by remember { mutableStateOf(false) }

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
                val index = dropIndex(listState, viewModel, (dragEvent.y - listTopInRoot) / pageScale)
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

    BackHandler(enabled = viewModel.searchOpen) { viewModel.closeSearch() }

    // The latest pagination, for scrolling to search matches from outside the page list.
    val latestPages = remember { mutableStateOf<EditorPages?>(null) }
    val match = viewModel.currentMatch
    LaunchedEffect(match) {
        if (match != null) scrollToMatch(listState, viewModel, latestPages.value, match, density)
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
            // A block that kept its focus but moved (or came back with undo) may sit outside the view.
            withFrameNanos { }
            val item = viewModel.blocks.indexOf(target) + 1
            val info = listState.layoutInfo
            val shown = info.visibleItemsInfo.firstOrNull { it.index == item }
            val minVisible = with(density) { MIN_VISIBLE_BLOCK.toPx() }
            when {
                item == 0 -> Unit
                shown == null -> listState.scrollToItem(item)
                shown.offset + minVisible > info.viewportEndOffset ->
                    listState.animateScrollBy((shown.offset + minOf(shown.size, info.viewportEndOffset / 2) - info.viewportEndOffset).toFloat())
                shown.offset + shown.size < info.viewportStartOffset + minVisible -> listState.animateScrollToItem(item)
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
                actions = {
                    SaveIndicator(saveStatus, Modifier.padding(end = 4.dp))
                    IconButton(onClick = viewModel::openSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.find_open))
                    }
                    IconButton(onClick = viewModel::undo, enabled = viewModel.canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.editor_undo))
                    }
                    IconButton(onClick = viewModel::redo, enabled = viewModel.canRedo) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.editor_redo))
                    }
                },
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
                if (viewModel.searchOpen) {
                    FindBar(
                        query = viewModel.searchQuery,
                        replacement = viewModel.replacement,
                        current = viewModel.currentMatch?.let { viewModel.searchMatches.indexOf(it) + 1 } ?: 0,
                        total = viewModel.searchMatches.size,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onReplacementChange = viewModel::onReplacementChange,
                        onPrevious = viewModel::previousMatch,
                        onNext = viewModel::nextMatch,
                        onReplace = viewModel::replaceCurrent,
                        onReplaceAll = viewModel::replaceAll,
                        onClose = viewModel::closeSearch,
                        onFocusChanged = { findFocused = it },
                    )
                    HorizontalDivider()
                }
                val pages = rememberEditorPages(
                    title = { viewModel.title.text },
                    blocks = viewModel.blocks,
                    spec = viewModel.pageSpec,
                    cache = viewModel.layoutCache,
                    // ImageBlockView shows the caption field of the selected image even when it is empty.
                    captionShownFor = { viewModel.focusedBlock?.takeIf { it.kind == BlockKind.IMAGE }?.id },
                )
                val plan = pages.plan
                SideEffect { latestPages.value = pages }
                val numberMeasurer = rememberTextMeasurer()
                val pageColors = PageColors(
                    paper = MaterialTheme.colorScheme.surfaceContainerLowest,
                    edge = MaterialTheme.colorScheme.outlineVariant,
                    number = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                @Composable
                fun PlannedSlice(id: String, content: @Composable () -> Unit) {
                    val placed = plan.blocks[id] ?: return
                    PageSlice(
                        plan = plan,
                        startPt = placed.startPt,
                        endPt = placed.endPt,
                        contentTopPt = placed.leadPt,
                        contentHeightPt = placed.heightPt,
                        firstPageNumber = viewModel.firstPageNumber,
                        colors = pageColors,
                        numberMeasurer = numberMeasurer,
                        contentScale = placed.scale,
                        content = content,
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .onGloballyPositioned {
                            listTopInRoot = it.positionInRoot().y
                            listBottomInRoot = listTopInRoot + it.size.height
                            listLeftInRoot = it.positionInRoot().x
                            listRightInRoot = listLeftInRoot + it.size.width
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
                        )
                        .pinchToZoom(::pinch),
                ) {
                    // At 100% the page fills the screen width; everything inside is laid out in page points,
                    // so zooming only scales the drawing and never moves a line or page break.
                    val deskWidthPt = viewModel.pageSpec.widthPt + 2 * DESK_PADDING_PT
                    val scale = constraints.maxWidth / deskWidthPt * viewModel.zoom
                    SideEffect {
                        pageScale = scale
                        viewportWidth = constraints.maxWidth
                        fitWidthScale = constraints.maxWidth / deskWidthPt
                    }
                    // Zooming out stops where a whole page is in view. The keyboard does not count: the
                    // smallest zoom must not jump while typing.
                    val keyboardPx = (WindowInsets.ime.getBottom(screenDensity) - WindowInsets.navigationBars.getBottom(screenDensity))
                        .coerceAtLeast(0)
                    val deskHeightPt = viewModel.pageSpec.heightPt + 2 * DESK_PADDING_PT
                    val fitZoom = (constraints.maxHeight + keyboardPx) / (deskHeightPt * constraints.maxWidth / deskWidthPt)
                    SideEffect { viewModel.setPageFitZoom(fitZoom) }
                    val deskWidth = with(screenDensity) { (deskWidthPt * scale).toDp() }
                    PointSpace(
                        scale = scale,
                        modifier = Modifier
                            // Narrower than the screen when zoomed out: the pages stay in the middle.
                            .align(Alignment.TopCenter)
                            .fillMaxHeight()
                            .horizontalScroll(pageScroll, enabled = viewModel.zoom > 1f)
                            .width(deskWidth),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // Room below the last page so it and the add-page button scroll clear of the zoom control.
                            contentPadding = PaddingValues(
                                start = DESK_PADDING_PT.dp,
                                top = DESK_PADDING_PT.dp,
                                end = DESK_PADDING_PT.dp,
                                bottom = DESK_PADDING_PT.dp + with(screenDensity) { (ZOOM_CONTROL_ROOM.toPx() / scale).dp },
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            item(key = "title") {
                                PlannedSlice(PageMeasurer.TITLE_ID) {
                                    TitleField(value = viewModel.title, onValueChange = viewModel::onTitleChange)
                                }
                            }
                            items(viewModel.blocks, key = { it.id }) { block ->
                                val selectBlock = {
                                    focusManager.clearFocus()
                                    viewModel.selectBlock(block.id)
                                }
                                val selected = viewModel.focusedBlock?.id == block.id
                                PlannedSlice(block.id) {
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
                                        searchMarks = viewModel.matchesByBlock[block.id].orEmpty(),
                                        currentMark = viewModel.currentMatch?.takeIf { it.unitId == block.id }?.let { it.start until it.end },
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
                                            when (viewModel.runSlashCommand(command)) {
                                                SlashOutcome.PICK_IMAGES -> pickImages.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                                )
                                                SlashOutcome.PICK_TABLE_SIZE -> pickTableSize = true
                                                SlashOutcome.DONE -> Unit
                                            }
                                        },
                                        onSlashDismiss = viewModel::dismissSlashMenu,
                                        onImagesPasted = { uris ->
                                            viewModel.insertImages(resolver.openImageSources(uris))
                                        },
                                        pageGaps = pages.gapsOf(block.id),
                                        onCaretMoved = ::revealSideways,
                                    )
                                }
                                }
                            }
                            item(key = "tail") {
                                val end = plan.blocks.values.lastOrNull()?.endPt ?: 0f
                                // Tapping the empty rest of the last page continues writing at the end.
                                PageSlice(
                                    plan = plan,
                                    startPt = end,
                                    endPt = end + plan.tailPt,
                                    contentTopPt = 0f,
                                    contentHeightPt = null,
                                    firstPageNumber = viewModel.firstPageNumber,
                                    colors = pageColors,
                                    numberMeasurer = numberMeasurer,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = viewModel::focusLastBlock,
                                    ),
                                )
                            }
                            item(key = "add-page") {
                                NormalSize {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                        FilledTonalButton(onClick = viewModel::addPage) {
                                            Icon(Icons.Filled.Add, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.editor_add_page))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Hidden while the keyboard is up: the line being typed sits at the bottom of the view.
                    if (keyboardPx == 0) ZoomControl(
                        zoom = viewModel.zoom,
                        canZoomIn = viewModel.canZoomIn,
                        canZoomOut = viewModel.canZoomOut,
                        onZoomIn = viewModel::zoomIn,
                        onZoomOut = viewModel::zoomOut,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    )
                }
                HorizontalDivider()
                val focused = viewModel.focusedBlock
                if (findFocused && viewModel.searchOpen) {
                    // Typing a search needs the room; the block tools come back with the caret.
                } else if (focused != null && focused.kind == BlockKind.IMAGE && !focused.isPendingImage) {
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
                        onAddTable = { pickTableSize = true },
                        onDelete = viewModel::deleteFocusedBlock,
                        canMoveUp = viewModel.canMoveFocused(-1),
                        canMoveDown = viewModel.canMoveFocused(1),
                        onMove = viewModel::moveFocusedBlock,
                    )
                }
            }
        }
    }

    if (pickTableSize) {
        TableSizeDialog(
            onConfirm = { rows, columns, headerRow ->
                pickTableSize = false
                viewModel.insertTable(rows, columns, headerRow)
            },
            onDismiss = {
                pickTableSize = false
                viewModel.cancelTableSize()
            },
        )
    }
}

@Composable
private fun TitleField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    val style = PageStyles.title().copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
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
    searchMarks: List<IntRange>,
    currentMark: IntRange?,
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
    pageGaps: List<PageGap>,
    onCaretMoved: (left: Float, right: Float) -> Unit,
) {
    val kind = block.kind
    val heading = kind == BlockKind.HEADING
    val rich = block.rich
    // Same style the paginator measured with, so lines and pages match what is drawn.
    val style = (PageStyles.forContent(block.layoutContent()) ?: PageStyles.body()).copy(
        color = if (kind == BlockKind.QUOTE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
    val darkTheme = isDarkPage()
    val colors = MaterialTheme.colorScheme
    val marks = remember(searchMarks, currentMark, colors) {
        searchMarks.map { range ->
            val style = if (range == currentMark) {
                SpanStyle(background = colors.tertiary, color = colors.onTertiary)
            } else {
                SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer)
            }
            AnnotatedString.Range(style, range.first, range.last + 1)
        }
    }
    val transformation = remember(rich, darkTheme, marks, pageGaps) {
        RichTextTransformation(rich, darkTheme, hiddenPrefix = 1, marks = marks, gaps = pageGaps)
    }
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
    var fieldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focused by remember { mutableStateOf(false) }
    // The field keeps its caret line in view vertically only; sideways (zoomed in) the screen follows it.
    LaunchedEffect(focused, block.value.selection, layout) {
        val textLayout = layout ?: return@LaunchedEffect
        val coordinates = fieldCoordinates?.takeIf { focused && it.isAttached } ?: return@LaunchedEffect
        val offset = drawnOffset(block.value.selection.end, pageGaps, block.value.text.length)
            .coerceIn(0, textLayout.layoutInput.text.length)
        val caret = textLayout.getCursorRect(offset)
        onCaretMoved(coordinates.localToRoot(caret.topLeft).x, coordinates.localToRoot(caret.bottomRight).x)
    }
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
            .then(
                if (kind == BlockKind.QUOTE) {
                    Modifier
                        .drawBehind {
                            val width = PageStyles.QUOTE_BAR_WIDTH_PT.dp.toPx()
                            val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
                            drawRect(quoteColor, topLeft = Offset(x, 0f), size = Size(width, size.height))
                        }
                        .padding(start = PageStyles.QUOTE_INDENT_PT.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        if (listLabel != null) {
            // Always one line, ending at the gap before the text: a wide label ("10.", "viii.") reaches
            // into the margin as in print instead of wrapping and being cut to the planned line height.
            Text(
                text = listLabel,
                style = style.copy(textAlign = TextAlign.End),
                softWrap = false,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = (PageStyles.LIST_INDENT_PT * block.indent).dp)
                    .width(PageStyles.LIST_LABEL_WIDTH_PT.dp)
                    .padding(end = LIST_LABEL_GAP_PT.dp)
                    .wrapContentWidth(Alignment.End, unbounded = true),
            )
        }
        val clipboard = LocalClipboard.current
        val resolver = LocalContext.current.contentResolver
        val imageClipboard = remember(clipboard, resolver) { ImagePasteClipboard(clipboard, resolver) }
        CompositionLocalProvider(LocalClipboard provides imageClipboard) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { pasted ->
                val withoutImage = pasted.withoutImagePasteMarker()
                if (withoutImage != null) {
                    // Pictures pasted from the clipboard (toolbar or Ctrl+V): they go in as image blocks.
                    val uris = clipboard.nativeClipboard.primaryClip?.imageUris(resolver).orEmpty()
                    if (withoutImage.text != fieldValue.text) {
                        (fieldEdit(block.value, withoutImage) as? FieldEdit.Change)?.let { onValueChange(it.value) }
                    }
                    if (uris.isNotEmpty()) onImagesPasted(uris)
                    return@BasicTextField
                }
                when (val edit = fieldEdit(block.value, pasted)) {
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
            onTextLayout = {
                layout = it
                block.textLayout = it
            },
            visualTransformation = transformation,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .focusRequester(block.focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
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
                        .onGloballyPositioned {
                            fieldTop = it.positionInRoot().y
                            fieldCoordinates = it
                        },
                    propagateMinConstraints = true,
                ) {
                    if (block.value.text.isEmpty()) Placeholder(stringResource(hint), style)
                    inner()
                    val textLayout = layout
                    if (menuOpen && slashQuery != null && textLayout != null) {
                        // The field is drawn scaled inside the page; the popup lives in screen pixels.
                        val density = LocalScreenDensity.current ?: LocalDensity.current
                        val scale = LocalPageScale.current
                        // The layout is of the drawn text, which has a character for every page gap.
                        val offset = drawnOffset(slashQuery.slashIndex, pageGaps, block.value.text.length)
                            .coerceIn(0, textLayout.layoutInput.text.length)
                        val gap = with(density) { MENU_GAP.toPx() }
                        val caret = textLayout.getCursorRect(offset).let {
                            Rect(it.left * scale, it.top * scale - gap, it.right * scale, it.bottom * scale + gap)
                        }
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
private fun EditorToolbar(
    wordCount: Int,
    focusedKind: BlockKind?,
    onAddParagraph: () -> Unit,
    onAddHeading: () -> Unit,
    onAddImage: () -> Unit,
    onAddTable: () -> Unit,
    onDelete: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
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
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.editor_move_block_up))
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.editor_move_block_down))
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
                AssistChip(
                    onClick = onAddTable,
                    label = { Text(stringResource(R.string.editor_table)) },
                    leadingIcon = { Icon(Icons.Outlined.TableChart, contentDescription = null) },
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
                Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.editor_move_block_up))
            }
            IconButton(onClick = { onMove(1) }) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.editor_move_block_down))
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

/** Scrolls so the search [match] sits in the upper part of the view, unless it is already well in view. */
private suspend fun scrollToMatch(
    listState: LazyListState,
    viewModel: EditorViewModel,
    pages: EditorPages?,
    match: SearchMatch,
    density: Density,
) {
    val index = viewModel.blocks.indexOfFirst { it.id == match.unitId }
    if (index < 0) return
    // +1 skips the chapter title item.
    val item = index + 1
    val block = viewModel.blocks[index]
    val layout = block.textLayout
    // The field's layout is of the drawn text (with page gap characters), placed below the block's lead.
    val start = drawnOffset(match.start, pages?.gapsOf(block.id).orEmpty(), block.value.text.length)
    val box = layout?.takeIf { start < it.layoutInput.text.length }?.getBoundingBox(start)
    val lead = pages?.plan?.blocks?.get(block.id)?.leadPt ?: 0f
    val top = (lead + (box?.top ?: 0f)).toInt()
    val bottom = (lead + (box?.bottom ?: 0f)).toInt()
    val info = listState.layoutInfo
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    val margin = with(density) { MIN_VISIBLE_BLOCK.roundToPx() }
    val shown = info.visibleItemsInfo.firstOrNull { it.index == item }
    if (shown == null) {
        listState.scrollToItem(item)
        listState.animateScrollBy((top - viewport / 3).toFloat())
        return
    }
    val matchTop = shown.offset + top
    val matchBottom = shown.offset + bottom
    if (matchTop < info.viewportStartOffset + margin || matchBottom > info.viewportEndOffset - margin) {
        listState.animateScrollBy((matchTop - info.viewportStartOffset - viewport / 3).toFloat())
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
private val MIN_VISIBLE_BLOCK = 48.dp
private val CARET_SIDE_MARGIN = 24.dp
// The zoom control's height plus its margins.
private val ZOOM_CONTROL_ROOM = 72.dp
private val MENU_GAP = 4.dp
private val MENU_ITEM_HEIGHT = 48.dp
private val MENU_KEYS = setOf(Key.DirectionDown, Key.DirectionUp, Key.Enter, Key.NumPadEnter, Key.Escape)
private const val LIST_LABEL_GAP_PT = 5f
private const val DESK_PADDING_PT = 10f
private val WIDTH_PRESETS = listOf(25, 50, 75, 100)

@Composable
private fun ZoomControl(
    zoom: Float,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onZoomOut, enabled = canZoomOut) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.editor_zoom_out))
            }
            Text(
                text = stringResource(R.string.editor_zoom_percent, (zoom * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(onClick = onZoomIn, enabled = canZoomIn) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.editor_zoom_in))
            }
        }
    }
}

/**
 * Two-finger pinch changes the zoom; one finger is left alone so the pages still scroll and the
 * text can be tapped and selected.
 */
private fun Modifier.pinchToZoom(onZoom: (factor: Float, focus: Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val factor = event.calculateZoom()
                if (factor != 1f) {
                    onZoom(factor, event.calculateCentroid(useCurrent = true))
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
