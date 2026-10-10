package dilnoza.books.ui.editor

import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dilnoza.books.MainActivity
import dilnoza.books.R
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

fun SemanticsNode.isEnabled(): Boolean = !config.contains(SemanticsProperties.Disabled)

typealias MainRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/**
 * Drives the real app: a throw-away book is created through the library and deleted again, the
 * editor is read through its semantics and touched through system-level input (UiAutomator).
 */
class EditorRobot(private val rule: MainRule) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    val bookTitle = "UI test ${System.currentTimeMillis()}"
    private var bookCreated = false

    fun str(@StringRes id: Int, vararg args: Any): String = rule.activity.getString(id, *args)

    fun nodes(matcher: SemanticsMatcher, unmerged: Boolean = false): List<SemanticsNode> =
        rule.onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes(atLeastOneRootRequired = false)

    fun waitFor(timeoutMs: Long = DEFAULT_TIMEOUT_MS, condition: () -> Boolean) {
        try {
            rule.waitUntil(timeoutMs, condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out; screen was:\n" + rule.onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE), e)
        }
    }

    fun createBookAndOpenEditor() {
        val newBook = str(R.string.library_new_book)
        // The floating button's label is only in the unmerged tree.
        waitFor { nodes(hasText(newBook), unmerged = true).isNotEmpty() }
        rule.onAllNodes(hasText(newBook), useUnmergedTree = true).onFirst().performClick()
        waitFor { nodes(hasSetTextAction()).isNotEmpty() }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(bookTitle)
        rule.onNode(hasText(str(R.string.action_create))).performClick()
        bookCreated = true
        waitFor { nodes(hasContentDescription(str(R.string.find_open))).isNotEmpty() }
        rule.waitForIdle()
    }

    /** Goes back to the library and deletes the book this robot created. */
    fun deleteBook() {
        if (!bookCreated) return
        val library = str(R.string.library_title)
        repeat(MAX_BACK_PRESSES) {
            if (nodes(hasText(library)).isNotEmpty()) return@repeat
            device.pressBack()
            rule.waitForIdle()
        }
        waitFor { nodes(hasText(bookTitle, substring = true)).isNotEmpty() }
        val card = nodes(hasText(bookTitle, substring = true)).first().boundsInRoot
        val more = nodes(hasContentDescription(str(R.string.action_more)))
            .minBy { abs(it.boundsInRoot.center.y - card.center.y) }
        rule.onNode(withId(more.id)).performClick()
        val delete = str(R.string.action_delete)
        waitFor { nodes(hasText(delete)).isNotEmpty() }
        rule.onAllNodes(hasText(delete)).onFirst().performClick()
        waitFor { nodes(hasText(str(R.string.book_delete_title))).isNotEmpty() }
        rule.onAllNodes(hasText(delete)).onLast().performClick()
        waitFor { nodes(hasText(bookTitle, substring = true)).isEmpty() }
    }

    // --- Zoom -------------------------------------------------------------------------------

    // The zoom control is hidden while the keyboard is up.
    private fun showZoomControl() {
        hideKeyboard()
        waitFor { nodes(ZOOM_LABEL).isNotEmpty() }
    }

    fun zoomControlShown(): Boolean = nodes(ZOOM_LABEL).isNotEmpty()

    fun zoomPercent(): Int {
        showZoomControl()
        val label = nodes(ZOOM_LABEL).single().config[SemanticsProperties.Text].first().text
        return label.removeSuffix("%").toInt()
    }

    fun zoomInButton(): SemanticsNode {
        showZoomControl()
        return nodes(hasContentDescription(str(R.string.editor_zoom_in))).single()
    }

    fun zoomOutButton(): SemanticsNode {
        showZoomControl()
        return nodes(hasContentDescription(str(R.string.editor_zoom_out))).single()
    }

    /**
     * Taps [node] with a real touch. Compose test clicks are avoided in the editor: with the test
     * frame clock, a background save can end up running the ripple animation off the main thread.
     */
    fun click(node: SemanticsNode) {
        val center = node.boundsInRoot.center + rootOnScreen()
        device.click(center.x.roundToInt(), center.y.roundToInt())
        rule.waitForIdle()
    }

    // --- Page list --------------------------------------------------------------------------

    /** The vertical page list (LazyColumn); its bounds are clipped to what is on screen. */
    fun pageList(): SemanticsNode = nodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .maxBy { it.boundsInRoot.width * it.boundsInRoot.height }

    fun verticalScroll(): Float = pageList().config[SemanticsProperties.VerticalScrollAxisRange].value()

    /** The sideways scroller that holds the zoomed pages. */
    fun pageScroller(): SemanticsNode {
        val center = pageList().boundsInRoot.center
        return nodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .first { it.boundsInRoot.contains(center) }
    }

    fun horizontalScroll(): Float = pageScroller().config[SemanticsProperties.HorizontalScrollAxisRange].value()

    fun horizontalScrollMax(): Float = pageScroller().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue()

    /**
     * Height of one page on screen: a new book has A5 pages, which fill the list's width less the
     * desk padding at 100% zoom.
     */
    fun pageHeightOnScreen(): Float {
        val a5Width = 148f / 25.4f * 72f
        val a5Height = 210f / 25.4f * 72f
        // Zoomed in, the list is cut to the screen width; zoomed out, it is the narrower page itself.
        val zoom = zoomPercent() / 100f
        val scale = pageList().boundsInRoot.width / (a5Width + 2 * DESK_PADDING_PT) * maxOf(zoom, 1f)
        return a5Height * scale
    }

    /** On-screen rectangle (pixels) of the visible part of the page list. */
    fun pageListOnScreen(): Rect = pageList().boundsInRoot.let { bounds ->
        val origin = rootOnScreen()
        Rect(
            (bounds.left + origin.x).roundToInt(),
            (bounds.top + origin.y).roundToInt(),
            (bounds.right + origin.x).roundToInt(),
            (bounds.bottom + origin.y).roundToInt(),
        )
    }

    fun rootOnScreen(): Offset {
        val location = IntArray(2)
        instrumentation.runOnMainSync {
            rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0).getLocationOnScreen(location)
        }
        return Offset(location[0].toFloat(), location[1].toFloat())
    }

    // --- Text blocks ------------------------------------------------------------------------

    /** Text fields of the chapter's blocks, in order (the chapter title field is excluded). */
    fun blockFields(): List<SemanticsNode> = nodes(hasSetTextAction()).drop(1)

    fun blockField(text: String): SemanticsNode =
        nodes(hasSetTextAction() and hasText(text, substring = true)).single()

    fun focusedField(): SemanticsNode = nodes(hasSetTextAction() and isFocused()).single()

    fun fieldText(node: SemanticsNode): String = node.config[SemanticsProperties.EditableText].text

    /** Taps a field at a point that is on screen (its centre may be off screen when zoomed). */
    fun tapField(node: SemanticsNode, xFraction: Float = 0.1f, yFraction: Float = 0.5f) {
        val origin = rootOnScreen()
        val visible = node.boundsInRoot
        device.click(
            (origin.x + visible.left + visible.width * xFraction).roundToInt(),
            (origin.y + visible.top + visible.height * yFraction).roundToInt(),
        )
        rule.waitForIdle()
        // Keys sent while the keyboard is still connecting to the field can be dropped.
        waitFor { keyboardShown() }
        rule.waitForIdle()
    }

    fun type(text: String) {
        device.executeShellCommand("input text ${text.replace(" ", "%s")}")
        rule.waitForIdle()
    }

    /** Puts [text] on the clipboard and pastes it at the caret with Ctrl+V. */
    fun pasteText(text: String) {
        instrumentation.runOnMainSync {
            rule.activity.getSystemService(android.content.ClipboardManager::class.java)
                .setPrimaryClip(android.content.ClipData.newPlainText("text", text))
        }
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_V, android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON)
        rule.waitForIdle()
    }

    /** Splits the block at the caret and waits until the caret has moved to the new, empty block. */
    fun pressEnter() {
        val before = focusedField().id
        device.pressEnter()
        waitFor { nodes(hasSetTextAction() and isFocused()).singleOrNull()?.let { it.id != before && fieldText(it).isEmpty() } == true }
    }

    fun keyboardShown(): Boolean {
        var shown = false
        instrumentation.runOnMainSync {
            shown = rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        }
        return shown
    }

    fun hideKeyboard() {
        if (keyboardShown()) {
            device.pressBack()
            rule.waitForIdle()
        }
    }

    /** Caret of the focused field, in screen pixels, mapped through the page zoom. */
    fun caretOnScreen(): Offset {
        val node = focusedField()
        val selection = node.config[SemanticsProperties.TextSelectionRange]
        // The field keeps one hidden character in front of the text (see BlockFieldValue).
        return textPointOnScreen(node, selection.end - 1)
    }

    /** Where the caret would be drawn before character [offset] of the shown text, in screen pixels. */
    fun textPointOnScreen(node: SemanticsNode, offset: Int): Offset {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()
        val caret = layout.getCursorRect(offset.coerceIn(0, layout.layoutInput.text.length))
        return node.layoutInfo.coordinates.localToRoot(caret.center) + rootOnScreen()
    }

    // --- Gestures ---------------------------------------------------------------------------

    fun longPress(at: Offset, holdMs: Long = 1_200) {
        val downTime = SystemClock.uptimeMillis()
        inject(downTime, MotionEvent.ACTION_DOWN, at)
        SystemClock.sleep(holdMs)
        // Compose's long-press timeout runs on the test frame clock, which only moves when told to.
        rule.mainClock.advanceTimeBy(holdMs)
        inject(downTime, MotionEvent.ACTION_UP, at)
        rule.waitForIdle()
    }

    /** A single finger pressed at [from], held, moved to [to] and released there (a drag-and-drop). */
    fun touchDrag(
        from: Offset,
        to: Offset,
        holdMs: Long = 400,
        moveMs: Long = 1_000,
        settleMs: Long = 600,
        beforeRelease: () -> Unit = {},
    ) {
        val downTime = SystemClock.uptimeMillis()
        inject(downTime, MotionEvent.ACTION_DOWN, from)
        SystemClock.sleep(holdMs)
        val steps = 40
        for (step in 1..steps) {
            val point = from + (to - from) * (step / steps.toFloat())
            inject(downTime, MotionEvent.ACTION_MOVE, point)
            SystemClock.sleep(moveMs / steps)
        }
        SystemClock.sleep(settleMs)
        inject(downTime, MotionEvent.ACTION_MOVE, to)
        beforeRelease()
        inject(downTime, MotionEvent.ACTION_UP, to)
    }

    private fun inject(downTime: Long, action: Int, at: Offset) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at.x, at.y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        check(instrumentation.uiAutomation.injectInputEvent(event, true)) { "Could not inject $event" }
        event.recycle()
    }

    // --- Storage ----------------------------------------------------------------------------

    data class StoredBlock(val type: String, val json: String)

    /** Blocks of this robot's book as saved in the database, in chapter order. */
    fun storedBlocks(): List<StoredBlock> {
        val path = rule.activity.getDatabasePath(DATABASE_NAME).path
        return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                """
                SELECT b.type, b.content_json FROM blocks b
                JOIN chapters c ON c.id = b.chapter_id
                JOIN books k ON k.id = c.book_id
                WHERE k.title = ?
                ORDER BY c.position, b.position
                """.trimIndent(),
                arrayOf(bookTitle),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(StoredBlock(cursor.getString(0), cursor.getString(1)))
                }
            }
        }
    }

    fun waitForStored(timeoutMs: Long = SAVE_TIMEOUT_MS, condition: (List<StoredBlock>) -> Boolean): List<StoredBlock> {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var last = storedBlocks()
        while (!condition(last) && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(POLL_MS)
            last = storedBlocks()
        }
        return last
    }

    /** The stored picture file of an image block. */
    fun imageFile(block: StoredBlock): File? {
        val name = IMAGE_FILE.find(block.json)?.groupValues?.get(1) ?: return null
        return File(rule.activity.filesDir, "books").walkTopDown().firstOrNull { it.name == name }
    }

    fun screenshot(name: String) {
        val dir = rule.activity.getExternalFilesDir(null) ?: return
        device.takeScreenshot(File(dir, "$name.png"))
    }

    companion object {
        const val DESK_PADDING_PT = 10f
        const val DATABASE_NAME = "dilnoza_books.db"
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val SAVE_TIMEOUT_MS = 15_000L
        const val POLL_MS = 250L
        const val MAX_BACK_PRESSES = 5
        private val PERCENT = Regex("""\d+%""")
        private val IMAGE_FILE = Regex(""""file"\s*:\s*"([^"]+)"""")
        val ZOOM_LABEL = SemanticsMatcher("zoom label") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { PERCENT.matches(it.text) } == true
        }

        fun withId(id: Int) = SemanticsMatcher("id $id") { it.id == id }
    }
}
