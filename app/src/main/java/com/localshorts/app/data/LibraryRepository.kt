package com.localshorts.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

/**
 * Scans user-picked folders through the Storage Access Framework. Works on folders that hold a
 * .nomedia file, because it reads the folder tree directly instead of going through MediaStore.
 */
class LibraryRepository(private val context: Context) {
    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Folder uris the app has lost access to (moved, deleted, or permission revoked). */
    private val _unavailable = MutableStateFlow<Set<String>>(emptySet())
    val unavailable: StateFlow<Set<String>> = _unavailable.asStateFlow()

    private val mutex = Mutex()

    fun takePermission(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        false
    }

    fun releasePermission(uri: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }

    fun folderName(tree: Uri): String {
        val doc = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrDefault("")
        val name = doc.substringAfterLast('/').substringAfterLast(':')
        return name.ifBlank { "Folder" }
    }

    suspend fun refresh(folders: List<Folder>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            _scanning.value = true
            val granted = context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
            val out = ArrayList<VideoItem>()
            val bad = HashSet<String>()
            for (f in folders) {
                if (f.uri !in granted) {
                    bad += f.uri
                    continue
                }
                try {
                    val tree = Uri.parse(f.uri)
                    val ok = scan(tree, DocumentsContract.getTreeDocumentId(tree), f.uri, out, 0)
                    if (!ok) bad += f.uri
                } catch (e: Exception) {
                    bad += f.uri
                }
            }
            _videos.value = out.distinctBy { it.id }
            _unavailable.value = bad
            _scanning.value = false
        }
    }

    /** Returns false when the folder itself could not be listed. */
    private fun scan(tree: Uri, docId: String, folder: String, out: MutableList<VideoItem>, depth: Int): Boolean {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cursor = try {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )
        } catch (e: Exception) {
            null
        } ?: return false
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                val modified = if (c.isNull(3)) 0L else c.getLong(3)
                val size = if (c.isNull(4)) 0L else c.getLong(4)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth < 4) scan(tree, id, folder, out, depth + 1)
                } else if (isVideo(name, mime)) {
                    out += VideoItem(
                        id = stableId(id, size),
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                        name = name.substringBeforeLast('.', name),
                        modified = modified,
                        size = size,
                        folder = folder,
                    )
                }
            }
        }
        return true
    }

    private fun isVideo(name: String, mime: String?): Boolean {
        if (mime != null && mime.startsWith("video/")) return true
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in setOf("mp4", "mkv", "mov", "webm", "m4v", "3gp", "ts")
    }

    private fun stableId(docId: String, size: Long): String {
        val d = MessageDigest.getInstance("SHA-1").digest("$docId|$size".toByteArray())
        return d.take(10).joinToString("") { "%02x".format(it) }
    }
}
