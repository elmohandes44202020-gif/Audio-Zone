package com.audio.editor.audio.helper

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Storage Access Framework (SAF) Multi-File Picker Helper.
 *
 * Robustly parses single (Intent.data) and batch (Intent.clipData) audio file selections.
 * Safely requests persistable read permissions without loading file bytes into RAM.
 */
object AudioPickerHelper {

    fun extractUrisFromIntent(context: Context, intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()

        val uris = mutableListOf<Uri>()

        // 1. Check clipData for multiple selected files
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.uri?.let { uri ->
                    takePersistablePermission(context, uri)
                    uris.add(uri)
                }
            }
        }

        // 2. Check data for single selected file if clipData was empty
        if (uris.isEmpty()) {
            intent.data?.let { uri ->
                takePersistablePermission(context, uri)
                uris.add(uri)
            }
        }

        return uris.distinct()
    }

    private fun takePersistablePermission(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Non-fatal if URI does not support persistable flag
        }
    }
}
