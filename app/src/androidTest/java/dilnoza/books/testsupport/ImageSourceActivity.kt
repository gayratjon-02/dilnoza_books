package dilnoza.books.testsupport

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Another app's picture, offered to the editor either on the clipboard ([IMAGE_SOURCE_MODE_CLIP]) or as a global
 * drag from a small window ([IMAGE_SOURCE_MODE_DRAG]) opened next to the editor.
 * Like [TestImageProvider] it runs in the test APK's own process and avoids the Kotlin runtime.
 */
class ImageSourceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val picture = Uri.parse("content://" + packageName + ".images/" + TEST_IMAGE_FILE_NAME)
        val clip = ClipData(
            ClipDescription(IMAGE_SOURCE_LABEL, arrayOf(TEST_IMAGE_MIME_TYPE)),
            ClipData.Item(picture),
        )
        if (IMAGE_SOURCE_MODE_CLIP.equals(intent.getStringExtra(IMAGE_SOURCE_EXTRA_MODE))) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            Log.i(LOG_TAG, "copied " + picture)
            finish()
            return
        }

        val source = View(this)
        source.setBackgroundColor(Color.MAGENTA)
        source.contentDescription = IMAGE_SOURCE_LABEL
        source.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val started = view.startDragAndDrop(
                    clip,
                    View.DragShadowBuilder(view),
                    null,
                    View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ,
                )
                Log.i(LOG_TAG, "drag started: " + started)
            }
            true
        }
        // Accepting the drag's start is what delivers its end, when the window can go away.
        source.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DRAG_ENDED) {
                Log.i(LOG_TAG, "drag ended, dropped: " + event.result)
                finish()
            }
            true
        }
        setContentView(source)
        setFinishOnTouchOutside(false)
        window.setLayout(IMAGE_SOURCE_SIZE_PX, IMAGE_SOURCE_SIZE_PX)
        window.setGravity(Gravity.TOP or Gravity.END)
        // Touches outside the small window reach the editor behind it.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
}

const val IMAGE_SOURCE_EXTRA_MODE = "mode"
const val IMAGE_SOURCE_MODE_CLIP = "clip"
const val IMAGE_SOURCE_MODE_DRAG = "drag"
const val IMAGE_SOURCE_LABEL = "test picture"
const val IMAGE_SOURCE_SIZE_PX = 240
const val LOG_TAG = "ImageSource"
