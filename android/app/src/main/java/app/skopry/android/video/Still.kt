package app.skopry.android.video

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Stills of the stream's picture: the decoded frame as its surface holds it, copied with PixelCopy (the
 * surface's own pixels, so the rails and the touch layer drawn over it are not in the copy), then saved as
 * a PNG or shared through the system share sheet.
 */
object Still {
    /** Copies the surface into a bitmap of its size; `done` runs on the main thread with null if the copy failed. */
    fun capture(view: SurfaceView, done: (Bitmap?) -> Unit) {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) {
            done(null)
            return
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        PixelCopy.request(view, bitmap, { result -> done(if (result == PixelCopy.SUCCESS) bitmap else null) }, Handler(Looper.getMainLooper()))
    }

    /** Writes the PNG into Pictures/Skopry through MediaStore: no storage permission from API 29. */
    fun save(context: Context, bitmap: Bitmap, name: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Skopry")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
        }.getOrDefault(false)
        if (!written) {
            resolver.delete(uri, null, null)
            return false
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return true
    }

    /** The PNG in the cache, shared through a FileProvider, opened as the system share sheet. */
    fun share(context: Context, bitmap: Bitmap, name: String): Boolean = runCatching {
        val dir = File(context.cacheDir, "shots").apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.shots", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share picture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
