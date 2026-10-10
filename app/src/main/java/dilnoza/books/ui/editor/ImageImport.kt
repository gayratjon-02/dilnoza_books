package dilnoza.books.ui.editor

import android.content.ClipData
import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * Opens read descriptors right away, while the caller still holds the temporary URI grant
 * (drag-and-drop permissions, keyboard content, picker results). Copying happens later off the main thread.
 */
fun ContentResolver.openImageSources(uris: List<Uri>): List<ParcelFileDescriptor> =
    uris.mapNotNull { uri -> runCatching { openFileDescriptor(uri, "r") }.getOrNull() }

fun ClipData.imageUris(resolver: ContentResolver): List<Uri> = (0 until itemCount)
    .mapNotNull { getItemAt(it).uri }
    .filter { uri -> resolver.getType(uri)?.startsWith("image/") != false }
