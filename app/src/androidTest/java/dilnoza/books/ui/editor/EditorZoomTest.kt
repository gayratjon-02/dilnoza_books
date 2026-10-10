package dilnoza.books.ui.editor

import android.graphics.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import dilnoza.books.MainActivity
import dilnoza.books.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class EditorZoomTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var robot: EditorRobot

    @Before
    fun openEditor() {
        robot = EditorRobot(rule)
        robot.createBookAndOpenEditor()
    }

    @After
    fun cleanUp() {
        robot.deleteBook()
    }

    @Test
    fun pinchOutZoomsInAndPinchInZoomsOut() {
        assertEquals(100, robot.zoomPercent())

        pageListObject().pinchOpen(PINCH_PERCENT, PINCH_SPEED)
        rule.waitForIdle()
        val zoomedIn = robot.zoomPercent()
        assertTrue("pinch-out should zoom in, zoom is $zoomedIn%", zoomedIn > 100)

        pageListObject().pinchClose(PINCH_PERCENT, PINCH_SPEED)
        rule.waitForIdle()
        val zoomedOut = robot.zoomPercent()
        assertTrue("pinch-in should zoom out from $zoomedIn%, zoom is $zoomedOut%", zoomedOut < zoomedIn)
    }

    @Test
    fun pinchKeepsTheTextUnderTheFingersInPlace() {
        robot.tapField(robot.blockFields().first())
        robot.pasteText(List(300) { "soʻz${it % 10}" }.joinToString(" "))
        robot.hideKeyboard()
        rule.waitForIdle()
        val field = robot.blockField("soʻz0")
        val list = robot.pageListOnScreen()
        // A spot in the lower right part of the page, where zooming from the corner would lose it.
        val focus = androidx.compose.ui.geometry.Offset(list.left + list.width() * 0.7f, list.top + list.height() * 0.6f)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        field.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val coordinates = field.layoutInfo.coordinates
        val local = coordinates.localPositionOf(coordinates.findRootCoordinates(), focus - robot.rootOnScreen())
        val offset = layouts.single().getOffsetForPosition(local)
        val before = robot.textPointOnScreen(field, offset)

        pinchAround(focus)
        val zoom = robot.zoomPercent()
        assertTrue("pinch-out should zoom in, zoom is $zoom%", zoom > 130)

        val after = robot.textPointOnScreen(robot.blockField("soʻz0"), offset)
        val moved = (after - before).getDistance()
        robot.screenshot("pinch_focus")
        assertTrue("text under the fingers moved ${moved.roundToInt()} px ($before -> $after) at $zoom%", moved < list.width() * 0.12f)
    }

    /** Two fingers moving apart symmetrically around [center]. */
    private fun pinchAround(center: androidx.compose.ui.geometry.Offset) {
        val start = 60f
        val end = 260f
        val steps = 30
        val downTime = android.os.SystemClock.uptimeMillis()
        fun pointers(d: Float) = arrayOf(
            android.view.MotionEvent.PointerCoords().apply { x = center.x - d; y = center.y - d; pressure = 1f; size = 1f },
            android.view.MotionEvent.PointerCoords().apply { x = center.x + d; y = center.y + d; pressure = 1f; size = 1f },
        )
        val properties = Array(2) { id -> android.view.MotionEvent.PointerProperties().apply { this.id = id; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER } }
        fun send(action: Int, d: Float, count: Int = 2) {
            val event = android.view.MotionEvent.obtain(
                downTime, android.os.SystemClock.uptimeMillis(), action, count, properties.copyOf(count).requireNoNulls(),
                pointers(d).copyOf(count).requireNoNulls(), 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            robot.instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(android.view.MotionEvent.ACTION_DOWN, start, count = 1)
        send(android.view.MotionEvent.ACTION_POINTER_DOWN or (1 shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT), start)
        for (step in 1..steps) {
            send(android.view.MotionEvent.ACTION_MOVE, start + (end - start) * step / steps)
            android.os.SystemClock.sleep(16)
        }
        send(android.view.MotionEvent.ACTION_POINTER_UP or (1 shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT), end)
        send(android.view.MotionEvent.ACTION_UP, end, count = 1)
        rule.waitForIdle()
    }

    @Test
    fun zoomControlStaysClearOfTheTextBeingTypedAndTheLastPage() {
        robot.tapField(robot.blockFields().first())
        robot.waitFor { robot.keyboardShown() }
        rule.waitForIdle()
        assertFalse("the zoom control must not cover the text while the keyboard is up", robot.zoomControlShown())

        robot.hideKeyboard()
        robot.waitFor { robot.zoomControlShown() }
        // Scrolled to the end, the add-page button sits above the control, not under it.
        rule.onNode(hasScrollToKeyAction()).performScrollToKey("add-page")
        rule.waitForIdle()
        val addPage = robot.nodes(hasText(robot.str(R.string.editor_add_page)), unmerged = true).first().boundsInRoot
        val control = robot.nodes(EditorRobot.ZOOM_LABEL).single().boundsInRoot
        robot.screenshot("zoom_control_end")
        assertTrue("add-page button $addPage must end above the zoom control $control", addPage.bottom <= control.top - 12f)
    }

    @Test
    fun oneFingerSwipeScrollsPagesWithoutZooming() {
        // A few empty pages so the chapter is taller than the screen.
        val addPage = robot.str(R.string.editor_add_page)
        repeat(2) {
            rule.onNode(hasScrollToKeyAction()).performScrollToKey("add-page")
            robot.waitFor { robot.nodes(hasText(addPage), unmerged = true).isNotEmpty() }
            robot.click(robot.nodes(hasText(addPage), unmerged = true).first())
        }
        robot.hideKeyboard()
        rule.waitForIdle()
        // Back to the top of the chapter.
        repeat(5) { swipe(up = false) }
        val before = robot.verticalScroll()

        swipe(up = true)

        val after = robot.verticalScroll()
        assertTrue("one-finger swipe should scroll the pages: $before -> $after", after > before + MIN_SCROLL_PX)
        assertEquals(100, robot.zoomPercent())
    }

    @Test
    fun buttonsReachMaximumAndMinimumZoom() {
        val seen = mutableListOf(robot.zoomPercent())
        repeat(MAX_CLICKS) {
            val zoomIn = robot.zoomInButton()
            if (!zoomIn.isEnabled()) return@repeat
            robot.click(zoomIn)
            seen += robot.zoomPercent()
        }
        assertEquals(listOf(100, 125, 150, 175, 200, 250), seen)
        assertFalse("zoom in must be disabled at 250%", robot.zoomInButton().isEnabled())
        assertTrue(robot.zoomOutButton().isEnabled())

        repeat(MAX_CLICKS) {
            val zoomOut = robot.zoomOutButton()
            if (!zoomOut.isEnabled()) return@repeat
            robot.click(zoomOut)
        }
        assertEquals(100, robot.zoomPercent())
        assertFalse("zoom out must be disabled at 100%", robot.zoomOutButton().isEnabled())
        assertTrue(robot.zoomInButton().isEnabled())
    }

    @Test
    fun landscapeZoomsOutUntilAWholePageIsInView() {
        robot.hideKeyboard()
        robot.device.setOrientationLandscape()
        try {
            robot.waitFor { robot.nodes(hasContentDescription(robot.str(R.string.find_open))).isNotEmpty() }
            rule.waitForIdle()
            assertEquals(100, robot.zoomPercent())
            assertTrue("in landscape a page does not fit at 100%, so zoom out must be enabled", robot.zoomOutButton().isEnabled())
            repeat(MAX_CLICKS) {
                val zoomOut = robot.zoomOutButton()
                if (!zoomOut.isEnabled()) return@repeat
                robot.click(zoomOut)
            }
            val zoom = robot.zoomPercent()
            assertTrue("zoom $zoom% should be below 100%", zoom < 100)
            val list = robot.pageList().boundsInRoot
            robot.screenshot("zoom_landscape_fit")
            assertTrue(
                "a whole page (${robot.pageHeightOnScreen()} px) should fit the list (${list.height} px) at $zoom%",
                robot.pageHeightOnScreen() <= list.height + 2f,
            )
            val screenWidth = rule.activity.window.decorView.width
            assertEquals("the narrower page should be centred", screenWidth / 2f, list.center.x, screenWidth * 0.02f)

            robot.click(robot.zoomInButton())
            assertEquals(100, robot.zoomPercent())
        } finally {
            robot.device.setOrientationNatural()
            robot.device.unfreezeRotation()
            rule.waitForIdle()
        }
    }

    @Test
    fun maximumZoomScrollsSidewaysAndKeepsTheCaretVisible() {
        robot.tapField(robot.blockFields().first())
        robot.type(FIRST_LINE)
        repeat(MAX_CLICKS) {
            val zoomIn = robot.zoomInButton()
            if (!zoomIn.isEnabled()) return@repeat
            robot.click(zoomIn)
        }
        assertEquals(250, robot.zoomPercent())

        // The page is now wider than the screen and scrolls sideways.
        assertTrue("page should be wider than the screen", robot.horizontalScrollMax() > 0f)
        val list = robot.pageListOnScreen()
        val y = list.top + list.height() / 3
        robot.device.swipe(list.right - EDGE_PX, y, list.left + EDGE_PX, y, SWIPE_STEPS)
        rule.waitForIdle()
        assertTrue("swipe should scroll the page sideways", robot.horizontalScroll() > 0f)
        repeat(2) { robot.device.swipe(list.left + EDGE_PX, y, list.right - EDGE_PX, y, SWIPE_STEPS) }
        rule.waitForIdle()
        assertEquals(0f, robot.horizontalScroll())

        // Put the caret near the right edge of the screen, in the middle of the long first line,
        // and type until the caret has to go past that edge.
        robot.tapField(robot.blockField(FIRST_LINE), xFraction = 0.9f)
        val caretBefore = robot.caretOnScreen()
        val textBefore = robot.fieldText(robot.focusedField())
        val selection = robot.focusedField().config[SemanticsProperties.TextSelectionRange]
        val typed = " bir ikki uch tort besh"
        robot.type(typed)
        rule.waitForIdle()

        val text = robot.fieldText(robot.focusedField())
        val at = (selection.end - 1).coerceIn(0, textBefore.length)
        assertEquals("typed text should go in at the caret", textBefore.substring(0, at) + typed + textBefore.substring(at), text)
        val caret = robot.caretOnScreen()
        val visible = robot.pageListOnScreen()
        robot.screenshot("zoom_250_caret")
        assertTrue(
            "caret moved from x=${caretBefore.x.roundToInt()} to (${caret.x.roundToInt()}, ${caret.y.roundToInt()}), " +
                "outside the visible pages $visible (sideways scroll ${robot.horizontalScroll()})",
            visible.contains(caret.x.roundToInt(), caret.y.roundToInt()),
        )
    }

    private fun swipe(up: Boolean) {
        val list = robot.pageListOnScreen()
        val x = list.centerX()
        val top = list.top + list.height() / 4
        val bottom = list.bottom - list.height() / 4
        if (up) robot.device.swipe(x, bottom, x, top, SWIPE_STEPS) else robot.device.swipe(x, top, x, bottom, SWIPE_STEPS)
        rule.waitForIdle()
    }

    /** The page list as UiAutomator sees it, for real multi-touch gestures. */
    private fun pageListObject(): UiObject2 {
        val bounds = robot.pageListOnScreen()
        return robot.device.findObjects(By.scrollable(true))
            .filter { Rect.intersects(it.visibleBounds, bounds) }
            .maxBy { it.visibleBounds.width() * it.visibleBounds.height() }
    }

    private companion object {
        const val PINCH_PERCENT = 0.6f
        const val PINCH_SPEED = 1_500
        const val MAX_CLICKS = 10
        const val SWIPE_STEPS = 30
        const val EDGE_PX = 80
        const val MIN_SCROLL_PX = 50f
        const val FIRST_LINE = "Boshlanish matni bir qatorda turadi"
    }
}
