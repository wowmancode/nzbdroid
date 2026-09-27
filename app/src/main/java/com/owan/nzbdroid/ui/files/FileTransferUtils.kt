package com.owan.nzbdroid.ui.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Scoped-storage-aware helpers for getting files between the app's sandbox and the
 * public Downloads folder / an arbitrary content:// Uri the user picked.
 */
object FileTransferUtils {

    /** Copies a picked content:// Uri into a real File in the app's cache dir, for jcifs to upload. */
    fun copyUriToCacheFile(context: Context, uri: Uri, displayName: String): File {
        val dest = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$displayName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Couldn't open picked file")
        return dest
    }

    /**
     * Moves a file that's already been downloaded into the app's cache dir into the
     * public Downloads collection, so the user can find it in their normal Downloads app/folder.
     */
    fun publishToDownloads(context: Context, cachedFile: File, displayName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Couldn't create Downloads entry")
            resolver.openOutputStream(itemUri)?.use { output ->
                cachedFile.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val dest = File(downloadsDir, displayName)
            cachedFile.copyTo(dest, overwrite = true)
        }
        cachedFile.delete()
    }
}
