package dilnoza.books.testsupport

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/**
 * Serves one PNG picture to the editor under test. It runs in the test APK's own process, where
 * the Kotlin runtime of the app is not available, so it sticks to plain Android and Java calls.
 */
class TestImageProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = TEST_IMAGE_MIME_TYPE

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        val file = File(context.cacheDir, TEST_IMAGE_FILE_NAME)
        if (!file.exists()) writePicture(file)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun writePicture(file: File) {
        val bitmap = Bitmap.createBitmap(TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30, 120, 200))
        val out = FileOutputStream(file)
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            out.close()
        }
    }
}

// Top-level constants: a companion object would pull in Kotlin runtime classes.
const val TEST_IMAGE_MIME_TYPE = "image/png"
const val TEST_IMAGE_FILE_NAME = "test_picture.png"
const val TEST_IMAGE_WIDTH = 640
const val TEST_IMAGE_HEIGHT = 480
