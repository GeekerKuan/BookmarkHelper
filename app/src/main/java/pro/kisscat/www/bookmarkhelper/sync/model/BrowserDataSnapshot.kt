package pro.kisscat.www.bookmarkhelper.sync.model

import pro.kisscat.www.bookmarkhelper.entry.app.Bookmark
import java.net.URI
import java.security.MessageDigest

/**
 * Browser-neutral records used between readers, previews, queues and writers.
 * Browser-specific fields stay at the adapter boundary instead of leaking into task logic.
 */
enum class BrowserId(val packageName: String) {
    VIA("mark.via"),
    EDGE("com.microsoft.emmx"),
}

data class CanonicalBookmark(
    val title: String,
    val url: String,
    val folderPath: String = "",
    val createdAtEpochMillis: Long? = null,
)

data class CanonicalHistoryVisit(
    val title: String,
    val url: String,
    val visitedAtEpochMillis: Long,
    val transition: String? = null,
)

data class CanonicalOpenTab(
    val title: String,
    val url: String,
    val openedAtEpochMillis: Long,
    val lastActiveAtEpochMillis: Long = openedAtEpochMillis,
    val pinned: Boolean = false,
)

data class BrowserDataSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val source: BrowserId,
    val exportedAtEpochMillis: Long = System.currentTimeMillis(),
    val bookmarks: List<CanonicalBookmark> = emptyList(),
    val history: List<CanonicalHistoryVisit> = emptyList(),
    val openTabs: List<CanonicalOpenTab> = emptyList(),
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported browser data schema: $schemaVersion" }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

object BrowserDataMapper {
    fun fromLegacyBookmarks(source: BrowserId, records: List<Bookmark>): BrowserDataSnapshot =
        BrowserDataSnapshot(
            source = source,
            bookmarks = records.mapNotNull { bookmark ->
                val url = bookmark.url?.trim().orEmpty()
                if (url.isEmpty()) null else CanonicalBookmark(
                    title = bookmark.title.orEmpty(),
                    url = url,
                    folderPath = bookmark.folder.orEmpty(),
                )
            },
        )

    fun toLegacyBookmarks(snapshot: BrowserDataSnapshot): List<Bookmark> =
        snapshot.bookmarks.map { record ->
            Bookmark().apply {
                title = record.title
                url = record.url
                folder = record.folderPath
            }
        }
}

/** Stable cross-browser identities used by the indexed store and every adapter. */
object BrowserRecordIdentity {
    fun bookmark(url: String, title: String, folderPath: String): String = hash(
        "b|${normalizeUrl(url)}|${title.trim()}|${folderPath.trim()}"
    )

    fun history(url: String, visitedAtEpochMillis: Long): String =
        hash("h|${normalizeUrl(url)}|$visitedAtEpochMillis")

    // Re-reading a browser session must not duplicate the same open page merely
    // because Chromium rewrote its session file at another time.
    fun openTab(url: String, @Suppress("UNUSED_PARAMETER") openedAtEpochMillis: Long): String =
        hash("t|${normalizeUrl(url)}")

    fun normalizeUrl(value: String): String = runCatching {
        val uri = URI(value.trim())
        URI(
            uri.scheme?.lowercase(), uri.userInfo, uri.host?.lowercase(), uri.port,
            uri.path?.ifEmpty { "/" } ?: "/", uri.query, uri.fragment,
        ).toASCIIString()
    }.getOrDefault(value.trim())

    private fun hash(identity: String): String = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
