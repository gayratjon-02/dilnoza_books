package dilnoza.books.ui.editor

import android.graphics.Rect
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
            robot.waitFor { robot.nodes(hasContentDescription(robot.str(R.string.editor_zoom_out))).isNotEmpty() }
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
