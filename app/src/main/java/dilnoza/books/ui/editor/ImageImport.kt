package dilnoza.books.ui.editor

import android.content.ClipData
import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Opens read descriptors right away, while the caller still holds the temporary URI grant
 * (drag-and-drop permissions, keyboard content, picker results). Copying happens later off the main thread.
 */
fun ContentResolver.openImageSources(uris: List<Uri>): List<ParcelFileDescriptor> =
    uris.mapNotNull { uri -> runCatching { openFileDescriptor(uri, "r") }.getOrNull() }

fun ClipData.imageUris(resolver: ContentResolver): List<Uri> = (0 until itemCount)
    .mapNotNull { getItemAt(it).uri }
    .filter { uri -> resolver.getType(uri)?.startsWith("image/") != false }

/**
 * Stands in for a picture on the clipboard. The text field only pastes text, so a clipboard holding
 * pictures and no text offers this character instead; the field then inserts the pictures for it.
 */
const val IMAGE_PASTE_MARKER = '￼'

/** Clipboard that lets the text field offer Paste (toolbar and Ctrl+V) for pictures, see [IMAGE_PASTE_MARKER]. */
class ImagePasteClipboard(private val base: Clipboard, private val resolver: ContentResolver) : Clipboard by base {
    override suspend fun getClipEntry(): ClipEntry? {
        val entry = base.getClipEntry() ?: return null
        val clip = entry.clipData
        val hasText = (0 until clip.itemCount).any { !clip.getItemAt(it).text.isNullOrEmpty() }
        if (hasText || clip.imageUris(resolver).isEmpty()) return entry
        return ClipEntry(ClipData.newPlainText("image", IMAGE_PASTE_MARKER.toString()))
    }
}

/**
 * The field's new value with a pasted [IMAGE_PASTE_MARKER] taken out and the caret where it was, or
 * null when nothing was pasted that way.
 */
fun TextFieldValue.withoutImagePasteMarker(): TextFieldValue? {
    val at = text.indexOf(IMAGE_PASTE_MARKER)
    if (at < 0) return null
    val cleaned = text.replace(IMAGE_PASTE_MARKER.toString(), "")
    return TextFieldValue(cleaned, TextRange(at.coerceAtMost(cleaned.length)))
}
