package dilnoza.books.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dilnoza.books.MainActivity
import dilnoza.books.testsupport.IMAGE_SOURCE_EXTRA_MODE
import dilnoza.books.testsupport.IMAGE_SOURCE_LABEL
import dilnoza.books.testsupport.IMAGE_SOURCE_MODE_CLIP
import dilnoza.books.testsupport.IMAGE_SOURCE_MODE_DRAG
import dilnoza.books.testsupport.ImageSourceActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pictures arriving from another app: pasted from the clipboard, or dragged in from a window
 * next to the editor. The picture comes from the test APK, which runs as a separate app.
 */
@RunWith(AndroidJUnit4::class)
class EditorImageInputTest {

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
    fun ctrlVPastesClipboardImageIntoFocusedText() {
        robot.tapField(robot.blockFields().first())
        robot.type("Alfa")
        copyPictureToClipboard()

        robot.tapField(robot.blockField("Alfa"), xFraction = 0.5f)
        robot.device.pressKeyCode(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
        rule.waitForIdle()

        assertImageSavedAfter("Alfa", "paste_ctrl_v")
    }

    /** Control for the image paste: the same Ctrl+V with text on the clipboard. */
    @Test
    fun ctrlVPastesClipboardText() {
        robot.tapField(robot.blockFields().first())
        robot.type("Alfa")
        robot.instrumentation.runOnMainSync {
            rule.activity.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("text", " Beta"))
        }

        robot.device.pressKeyCode(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
        rule.waitForIdle()

        val stored = robot.waitForStored { blocks -> blocks.any { it.json.contains("Alfa Beta") } }
        assertTrue("pasted text should be saved: $stored", stored.any { it.json.contains("Alfa Beta") })
    }

    @Test
    fun textToolbarPastePastesClipboardImage() {
        robot.tapField(robot.blockFields().first())
        robot.type("Alfa")
        copyPictureToClipboard()

        // A long press on the word selects it and brings up the floating text toolbar.
        val word = robot.textPointOnScreen(robot.blockField("Alfa"), 2)
        robot.longPress(word)
        SystemClock.sleep(TOOLBAR_SETTLE_MS)
        robot.screenshot("paste_long_press")
        val copyLabel = rule.activity.getString(android.R.string.copy)
        val copy = robot.device.wait(Until.findObject(By.text(copyLabel)), TOOLBAR_TIMEOUT_MS)
        assertNotNull("the text toolbar should open", copy)
        val pasteLabel = rule.activity.getString(android.R.string.paste)
        var paste = robot.device.findObject(By.text(pasteLabel))
        if (paste == null) {
            // Not among the main actions: look in the toolbar's overflow menu (its last button).
            val row = copy.visibleBounds
            robot.device.findObjects(By.clickable(true))
                .filter { it.visibleBounds.centerY() in row.top..row.bottom }
                .maxByOrNull { it.visibleBounds.right }
                ?.click()
            paste = robot.device.wait(Until.findObject(By.text(pasteLabel)), TOOLBAR_TIMEOUT_MS)
        }
        robot.screenshot("paste_toolbar")
        assertNotNull("text toolbar should offer \"$pasteLabel\" for an image on the clipboard", paste)
        paste.click()
        rule.waitForIdle()

        // The selected word may be replaced, so only the picture itself is checked here.
        assertImageSavedAfter(null, "paste_toolbar_done")
    }

    @Test
    fun imageDraggedFromAnotherAppIsInsertedAtTheDropPoint() {
        robot.tapField(robot.blockFields().first())
        robot.type("Alfa")
        robot.pressEnter()
        robot.type("Beta")
        robot.pressEnter()
        robot.type("Gamma")
        robot.waitForStored { blocks -> blocks.count { it.type == "paragraph" } >= 3 }
        robot.hideKeyboard()

        // The source opens side by side with the editor (split screen), as another app would.
        val intent = Intent()
            .setClassName(robot.instrumentation.context.packageName, ImageSourceActivity::class.java.name)
            .putExtra(IMAGE_SOURCE_EXTRA_MODE, IMAGE_SOURCE_MODE_DRAG)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        rule.activity.startActivity(intent)
        val source = robot.device.wait(Until.findObject(By.desc(IMAGE_SOURCE_LABEL)), SOURCE_TIMEOUT_MS)
        assertNotNull("the drag source window should be shown", source)
        robot.device.waitForIdle()
        SystemClock.sleep(SPLIT_SETTLE_MS)
        rule.waitForIdle()
        robot.screenshot("drag_split")

        // Drop on the lower half of "Beta": the picture belongs right after it.
        val beta = robot.blockField("Beta").boundsInRoot
        val target = robot.rootOnScreen() + Offset(beta.left + beta.width * 0.3f, beta.top + beta.height * 0.75f)
        val sourceBounds = robot.device.findObject(By.desc(IMAGE_SOURCE_LABEL)).visibleBounds
        val start = Offset(sourceBounds.exactCenterX(), sourceBounds.exactCenterY())
        assertTrue("drop point $target must be outside the source window", !sourceBounds.contains(target.x.toInt(), target.y.toInt()))

        robot.touchDrag(start, target) { robot.screenshot("drag_hovering") }
        rule.waitForIdle()
        robot.screenshot("drag_drop_done")

        val stored = robot.waitForStored { blocks -> blocks.any { it.type == "image" } }
        val order = stored.map { block -> if (block.type == "image") "image" else PARAGRAPH_TEXT.find(block.json)?.groupValues?.get(1) }
        assertEquals("image should be saved right after the block it was dropped on", listOf("Alfa", "Beta", "image", "Gamma"), order)
        assertNotNull("the picture file should be stored", robot.imageFile(stored.single { it.type == "image" }))
    }

    private fun copyPictureToClipboard() {
        startImageSource(IMAGE_SOURCE_MODE_CLIP)
        // The source activity finishes right after copying; wait for the editor to be back in front.
        robot.device.wait(Until.gone(By.pkg(robot.instrumentation.context.packageName)), SOURCE_TIMEOUT_MS)
        rule.waitForIdle()
        // Precondition: the editor's app can see and open the copied picture.
        var clip: ClipData? = null
        robot.instrumentation.runOnMainSync {
            clip = rule.activity.getSystemService(ClipboardManager::class.java).primaryClip
        }
        val uri = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        assertNotNull("clipboard should hold the picture, was $clip", uri)
        rule.activity.contentResolver.openFileDescriptor(uri!!, "r")!!.close()
    }

    private fun startImageSource(mode: String, extras: String = "") {
        val component = "${robot.instrumentation.context.packageName}/${ImageSourceActivity::class.java.name}"
        robot.device.executeShellCommand("am start -W -n $component --es $IMAGE_SOURCE_EXTRA_MODE $mode $extras")
    }

    private fun assertImageSavedAfter(text: String?, screenshot: String) {
        val stored = robot.waitForStored { blocks -> blocks.any { it.type == "image" } }
        robot.screenshot(screenshot)
        val types = stored.map { it.type }
        assertTrue("an image block should be saved, stored blocks: $types", "image" in types)
        if (text != null) {
            val textIndex = stored.indexOfFirst { it.json.contains("\"text\":\"$text\"") }
            assertEquals("image should follow \"$text\": $types", textIndex + 1, types.indexOf("image"))
        }
        assertNotNull("the picture file should be stored", robot.imageFile(stored.single { it.type == "image" }))
    }

    private companion object {
        const val SOURCE_TIMEOUT_MS = 5_000L
        const val TOOLBAR_TIMEOUT_MS = 3_000L
        const val SPLIT_SETTLE_MS = 1_500L
        const val TOOLBAR_SETTLE_MS = 500L
        val PARAGRAPH_TEXT = Regex(""""text":\{"text":"([^"]*)"""")
    }
}
