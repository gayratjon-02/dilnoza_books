package dilnoza.books.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.ParcelFileDescriptor
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredImage(val fileName: String, val width: Int, val height: Int)

/**
 * Owns the image files of every book: `filesDir/books/{bookId}/images/{uuid}.jpg|png`.
 * Imported images are copied, EXIF-rotated, downscaled to [ImageSizing.MAX_EDGE] and re-encoded,
 * so the book never depends on the original file and stays small.
 */
@Singleton
class ImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val booksRoot: File = File(context.filesDir, "books")

    // Full-size bitmaps are large; decoding one at a time keeps memory predictable.
    private val decodeMutex = Mutex()

    fun imagesDir(bookId: String): File = File(booksRoot, "$bookId/images")

    fun imageFile(bookId: String, fileName: String): File = File(imagesDir(bookId), fileName)

    /** Takes ownership of [source] and closes it. */
    suspend fun import(bookId: String, source: ParcelFileDescriptor): StoredImage = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("import", ".tmp", context.cacheDir)
        try {
            // Copy first: the source may be a pipe that cannot be read twice (bounds pass + decode pass).
            ParcelFileDescriptor.AutoCloseInputStream(source).use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            decodeMutex.withLock { encode(bookId, temp) }
        } finally {
            temp.delete()
        }
    }

    fun deleteBook(bookId: String) {
        File(booksRoot, bookId).deleteRecursively()
    }

    private fun encode(bookId: String, file: File): StoredImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = requireNotNull(BitmapFactory.decodeFile(file.path, options)) { "Unsupported image" }
        val oriented = decoded.applyExifOrientation(file)
        val (width, height) = ImageSizing.fit(oriented.width, oriented.height)
        val scaled = if (width == oriented.width && height == oriented.height) {
            oriented
        } else {
            oriented.scale(width, height).also { oriented.recycle() }
        }

        try {
            val transparent = scaled.hasAlpha() && scaled.hasTransparentPixels()
            val fileName = "${UUID.randomUUID()}.${if (transparent) "png" else "jpg"}"
            val dir = imagesDir(bookId).apply { mkdirs() }
            val partial = File(dir, "$fileName.part")
            partial.outputStream().buffered().use { out ->
                val format = if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                check(scaled.compress(format, JPEG_QUALITY, out)) { "Could not encode image" }
            }
            check(partial.renameTo(File(dir, fileName))) { "Could not store image" }
            return StoredImage(fileName, width, height)
        } finally {
            scaled.recycle()
        }
    }

    private fun Bitmap.applyExifOrientation(file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { postRotate(270f); postScale(-1f, 1f) }
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { if (it !== this) recycle() }
    }

    /** Screenshots often carry an alpha channel without using it; only real transparency keeps PNG. */
    private fun Bitmap.hasTransparentPixels(): Boolean {
        val row = IntArray(width)
        for (y in 0 until height step 4) {
            getPixels(row, 0, width, 0, y, width, 1)
            if (row.any { it ushr 24 != 0xFF }) return true
        }
        return false
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}
