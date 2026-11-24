package com.autosend.scheduler.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object MediaStorageHelper {
    private const val MEDIA_DIR = "scheduled_media"

    private fun getMediaDir(context: Context): File {
        val dir = File(context.filesDir, MEDIA_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Sao chép ảnh từ thư viện sang bộ nhớ riêng của ứng dụng
     * để đảm bảo đến giờ gửi ảnh không bị mất quyền truy cập URI.
     */
    fun copyUriToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val mimeType = context.contentResolver.getType(sourceUri)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            val destFile = File(getMediaDir(context), "img_${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun getShareableUris(context: Context, paths: List<String>): ArrayList<Uri> {
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList<Uri>()
        for (path in paths) {
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                uris.add(FileProvider.getUriForFile(context, authority, file))
            }
        }
        return uris
    }

    fun deleteFiles(paths: List<String>) {
        paths.forEach { path ->
            runCatching {
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
    }
}
