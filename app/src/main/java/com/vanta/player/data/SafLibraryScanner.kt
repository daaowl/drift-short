package com.vanta.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.vanta.player.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class FolderScanResult(
    val videos: List<VideoItem>,
    val folderAccessible: Boolean,
    val errorMessage: String? = null
)

private data class CachedVideoProbe(
    val lastModifiedMs: Long,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

class SafLibraryScanner(private val context: Context) {

    private val probeCache = ConcurrentHashMap<String, CachedVideoProbe>()
    private val metadataSemaphore = Semaphore(4)

    companion object {
        // Section 1: Scan up to 12 levels deep and recognise MP4, MKV, WebM, MOV, M4V, and 3GP
        private const val MAX_SCAN_DEPTH = 12
        private val SUPPORTED_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp")
    }

    fun isFolderPermissionValid(treeUri: Uri): Boolean {
        return runCatching {
            val persisted = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission
            }
            if (!persisted) return false
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            doc != null && doc.exists() && doc.canRead()
        }.getOrDefault(false)
    }

    fun resolveFolderDisplayName(treeUri: Uri): String {
        return runCatching {
            DocumentFile.fromTreeUri(context, treeUri)?.name
                ?: treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?: "Videos"
        }.getOrDefault("Videos")
    }

    fun resolveFolderDisplayPath(treeUri: Uri): String {
        return runCatching {
            val decoded = URLDecoder.decode(treeUri.toString(), "UTF-8")
            decoded.substringAfterLast("/tree/", decoded)
                .replace("primary:", "Internal storage/")
        }.getOrDefault(resolveFolderDisplayName(treeUri))
    }

    suspend fun scanFolder(treeUri: Uri): FolderScanResult = withContext(Dispatchers.IO) {
        if (!isFolderPermissionValid(treeUri)) {
            return@withContext FolderScanResult(
                videos = emptyList(),
                folderAccessible = false,
                errorMessage = "Folder permission was revoked or the folder was moved/deleted."
            )
        }

        val discovered = mutableListOf<RawDocumentEntry>()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext FolderScanResult(
                videos = emptyList(),
                folderAccessible = false,
                errorMessage = "Unable to read folder structure."
            )

        runCatching {
            traverseTree(treeUri, rootDocId, discovered, depth = 0)
        }.onFailure { e ->
            return@withContext FolderScanResult(
                videos = emptyList(),
                folderAccessible = false,
                errorMessage = e.localizedMessage ?: "Error scanning folder."
            )
        }

        val videos = discovered.mapNotNull { raw ->
            currentCoroutineContext().ensureActive()
            buildVideoItem(treeUri.toString(), raw)
        }

        FolderScanResult(
            videos = videos,
            folderAccessible = true,
            errorMessage = null
        )
    }

    private data class RawDocumentEntry(
        val documentId: String,
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val lastModifiedMs: Long
    )

    private suspend fun traverseTree(
        treeUri: Uri,
        parentDocumentId: String,
        outList: MutableList<RawDocumentEntry>,
        depth: Int
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        currentCoroutineContext().ensureActive()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val subDirectories = mutableListOf<String>()

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                val lastMod = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    subDirectories.add(docId)
                } else if (isSupportedVideoFile(name, mime)) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    outList.add(
                        RawDocumentEntry(
                            documentId = docId,
                            uri = fileUri,
                            displayName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            lastModifiedMs = lastMod
                        )
                    )
                }
            }
        }

        for (subDirId in subDirectories) {
            traverseTree(treeUri, subDirId, outList, depth + 1)
        }
    }

    private fun isSupportedVideoFile(fileName: String, mimeType: String): Boolean {
        if (fileName.isBlank() || fileName.startsWith(".")) return false
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in SUPPORTED_EXTENSIONS) return true
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        return false
    }

    private suspend fun buildVideoItem(
        folderUriString: String,
        raw: RawDocumentEntry
    ): VideoItem? {
        val stableId = "${raw.documentId}_${raw.sizeBytes}"
        val cached = probeCache[stableId]
        val probe = if (cached != null &&
            cached.lastModifiedMs == raw.lastModifiedMs &&
            cached.sizeBytes == raw.sizeBytes
        ) {
            cached
        } else {
            metadataSemaphore.withPermit {
                probeVideoMetadata(raw)
            }.also { probed ->
                if (probed != null) {
                    probeCache[stableId] = probed
                }
            }
        } ?: return null

        return VideoItem(
            stableId = stableId,
            uriString = raw.uri.toString(),
            fileName = raw.displayName,
            folderUriString = folderUriString,
            durationMs = probe.durationMs,
            width = probe.width,
            height = probe.height,
            sizeBytes = raw.sizeBytes,
            lastModifiedMs = raw.lastModifiedMs
        )
    }

    private fun probeVideoMetadata(raw: RawDocumentEntry): CachedVideoProbe? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, raw.uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val rawWidth = widthStr?.toIntOrNull() ?: 1080
            val rawHeight = heightStr?.toIntOrNull() ?: 1920
            val rotation = rotationStr?.toIntOrNull() ?: 0

            val (finalWidth, finalHeight) = if (rotation == 90 || rotation == 270) {
                rawHeight to rawWidth
            } else {
                rawWidth to rawHeight
            }

            CachedVideoProbe(
                lastModifiedMs = raw.lastModifiedMs,
                sizeBytes = raw.sizeBytes,
                durationMs = durationMs.coerceAtLeast(0L),
                width = finalWidth.coerceAtLeast(1),
                height = finalHeight.coerceAtLeast(1)
            )
        } catch (_: Exception) {
            CachedVideoProbe(
                lastModifiedMs = raw.lastModifiedMs,
                sizeBytes = raw.sizeBytes,
                durationMs = 0L,
                width = 1080,
                height = 1920
            )
        } finally {
            runCatching { retriever.release() }
        }
    }
}
