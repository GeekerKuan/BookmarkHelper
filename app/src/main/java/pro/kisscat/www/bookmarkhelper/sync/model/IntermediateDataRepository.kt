package pro.kisscat.www.bookmarkhelper.sync.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class IntermediateItemKind(val databaseValue: Int) {
    BOOKMARK(0), HISTORY(1), OPEN_TAB(2);

    companion object {
        fun fromDatabase(value: Int) = entries.first { it.databaseValue == value }
    }
}

data class IntermediateItemRef(val rowId: Long, val kind: IntermediateItemKind)

data class IntermediateDataState(
    val bookmarkCount: Int = 0,
    val historyCount: Int = 0,
    val openTabCount: Int = 0,
    val revision: Long = 0L,
) {
    val isEmpty: Boolean get() = bookmarkCount + historyCount + openTabCount == 0
}

data class ManagedDataRow(
    val ref: IntermediateItemRef,
    val title: String,
    val url: String,
    val detail: String,
    val timestampEpochMillis: Long?,
    val sources: Set<BrowserId>,
)

data class ManagedDataPage(
    val rows: List<ManagedDataRow>,
    val total: Int,
    val offset: Int,
    val hasMore: Boolean,
    val folders: List<BookmarkFolderEntry> = emptyList(),
)

data class BookmarkFolderEntry(val name: String, val path: String, val recordCount: Int)
data class BrowserSpaceCount(val browser: BrowserId, val count: Int)

data class BookmarkFolderContents(
    val path: String,
    val recordCount: Int,
    val folderCount: Int,
) {
    val isEmpty: Boolean get() = recordCount == 0 && folderCount <= 1
}

enum class BookmarkFolderRejection {
    ROOT_PROTECTED,
    UNSAFE_PATH,
    SOURCE_NOT_FOUND,
    DESTINATION_UNCHANGED,
    WOULD_CREATE_CYCLE,
}

sealed interface BookmarkFolderOperationResult {
    data class Completed(val affectedRecords: Int, val affectedFolders: Int) : BookmarkFolderOperationResult
    data class ConfirmationRequired(val contents: BookmarkFolderContents) : BookmarkFolderOperationResult
    data class Rejected(val reason: BookmarkFolderRejection) : BookmarkFolderOperationResult
}

sealed interface BookmarkFolderMovePlan {
    data class Move(val source: String, val destination: String) : BookmarkFolderMovePlan
    data class Rejected(val reason: BookmarkFolderRejection) : BookmarkFolderMovePlan
}

/** Pure path rules shared by repository operations and local unit tests. */
object BookmarkFolderPathRules {
    fun normalize(value: String): String = value.replace('\\', '/')
        .split('/')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("/")

    fun isSafe(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.split('/').none { it == "." || it == ".." || it.any(Char::isISOControl) }
    }

    fun move(sourcePath: String, destinationParentPath: String): BookmarkFolderMovePlan {
        val source = normalize(sourcePath)
        val destinationParent = normalize(destinationParentPath)
        if (source.isEmpty()) return BookmarkFolderMovePlan.Rejected(BookmarkFolderRejection.ROOT_PROTECTED)
        if (!isSafe(source) || !isSafe(destinationParent)) {
            return BookmarkFolderMovePlan.Rejected(BookmarkFolderRejection.UNSAFE_PATH)
        }
        val destination = listOf(destinationParent, source.substringAfterLast('/'))
            .filter(String::isNotEmpty)
            .joinToString("/")
        if (destination == source) {
            return BookmarkFolderMovePlan.Rejected(BookmarkFolderRejection.DESTINATION_UNCHANGED)
        }
        if (destination.startsWith("$source/")) {
            return BookmarkFolderMovePlan.Rejected(BookmarkFolderRejection.WOULD_CREATE_CYCLE)
        }
        return BookmarkFolderMovePlan.Move(source, destination)
    }

    fun relocate(path: String, source: String, destination: String): String {
        val normalized = normalize(path)
        require(normalized == source || normalized.startsWith("$source/"))
        val suffix = normalized.removePrefix(source).trimStart('/')
        return listOf(destination, suffix).filter(String::isNotEmpty).joinToString("/")
    }
}

/**
 * Indexed app-private staging database used by browser readers, editors and writers.
 * Preview saves are bulk transactions; searches are paged and use FTS4 where possible;
 * single-record edits never rewrite unrelated history rows.
 */
object IntermediateDataRepository {
    private const val PAGE_LIMIT_MAX = 500
    private const val MAX_DATASETS = 20
    private val mutableState = MutableStateFlow(IntermediateDataState())
    val state: StateFlow<IntermediateDataState> = mutableState.asStateFlow()

    private var database: StoreDatabase? = null
    private var applicationContext: Context? = null

    @Synchronized
    fun initialize(context: Context) {
        if (database != null) return
        val appContext = context.applicationContext
        applicationContext = appContext
        database = StoreDatabase(appContext).apply { setWriteAheadLoggingEnabled(true) }
        requireDatabase().writableDatabase
        migrateAlpha09Json(appContext)
        publishCounts()
    }

    /**
     * Runs file-level backup or validated replacement while SQLite owns no open handle.
     * The callback receives only the canonical database file; success and failure both
     * reopen the helper and republish observable state before this method returns.
     */
    @Synchronized
    fun <T> withClosedDatabaseFile(block: (File) -> T): T {
        val context = requireNotNull(applicationContext) { "Data repository is not initialized" }
        val helper = requireDatabase()
        val dbFile = File(context.noBackupFilesDir, "browser_data.sqlite")
        var closed = false
        try {
            helper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.columnCount >= 3 && cursor.getInt(0) != 0) {
                    error("Unable to checkpoint browser data database")
                }
            }
            helper.close()
            database = null
            closed = true
            return block(dbFile)
        } finally {
            if (closed) {
                database = StoreDatabase(context).apply { setWriteAheadLoggingEnabled(true) }
                requireDatabase().writableDatabase
                publishCounts()
            }
        }
    }

    @Synchronized
    fun save(datasetId: String, label: String, snapshot: BrowserDataSnapshot) {
        require(datasetId.isNotBlank())
        val db = requireDatabase().writableDatabase
        db.beginTransaction()
        try {
            db.delete("dataset_records", "dataset_id=?", arrayOf(datasetId))
            db.insertWithOnConflict("datasets", null, ContentValues().apply {
                put("id", datasetId)
                put("label", label)
                put("source", snapshot.source.name)
                put("schema_version", snapshot.schemaVersion)
                put("exported_at", snapshot.exportedAtEpochMillis)
                put("updated_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE)

            val insert = db.compileStatement(
                "INSERT OR IGNORE INTO records(dedup_key,kind,title,url,detail,primary_time,secondary_time,flag,transition) " +
                    "VALUES(?,?,?,?,?,?,?,?,?)"
            )
            val find = db.compileStatement("SELECT id FROM records WHERE dedup_key=?")
            val link = db.compileStatement("INSERT OR IGNORE INTO dataset_records(dataset_id,record_id) VALUES(?,?)")
            val source = db.compileStatement("INSERT OR IGNORE INTO record_sources(record_id,browser) VALUES(?,?)")
            var inserted = 0
            snapshot.bookmarks.forEach { item ->
                insertBookmarkFolders(db, snapshot.source, item.folderPath)
                insertOrLink(insert, find, link, source, datasetId, snapshot.source,
                    BrowserRecordIdentity.bookmark(item.url, item.title, item.folderPath),
                    IntermediateItemKind.BOOKMARK, item.title, item.url, item.folderPath,
                    item.createdAtEpochMillis, null, false, null)
                inserted++
                if (inserted % 500 == 0) db.yieldIfContendedSafely()
            }
            snapshot.history.forEach { item ->
                insertOrLink(insert, find, link, source, datasetId, snapshot.source,
                    BrowserRecordIdentity.history(item.url, item.visitedAtEpochMillis),
                    IntermediateItemKind.HISTORY, item.title, item.url, "",
                    item.visitedAtEpochMillis, null, false, item.transition)
                inserted++
                if (inserted % 500 == 0) db.yieldIfContendedSafely()
            }
            snapshot.openTabs.forEach { item ->
                insertOrLink(insert, find, link, source, datasetId, snapshot.source,
                    BrowserRecordIdentity.openTab(item.url, item.openedAtEpochMillis),
                    IntermediateItemKind.OPEN_TAB, item.title, item.url, "",
                    item.openedAtEpochMillis, item.lastActiveAtEpochMillis, item.pinned, null)
                inserted++
                if (inserted % 500 == 0) db.yieldIfContendedSafely()
            }
            pruneOldDatasets(db)
            // Dataset snapshots are bounded task history. The managed library is
            // user-owned long-term data and must only shrink after an explicit delete.
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        publishCounts()
    }

    @Synchronized
    fun snapshot(datasetId: String): BrowserDataSnapshot? {
        val db = requireDatabase().readableDatabase
        val metadata = db.rawQuery(
            "SELECT source,schema_version,exported_at FROM datasets WHERE id=?",
            arrayOf(datasetId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else Triple(
                BrowserId.valueOf(cursor.getString(0)), cursor.getInt(1), cursor.getLong(2)
            )
        } ?: return null

        val bookmarks = mutableListOf<CanonicalBookmark>()
        val history = mutableListOf<CanonicalHistoryVisit>()
        val tabs = mutableListOf<CanonicalOpenTab>()
        db.rawQuery(
            "SELECT r.kind,r.title,r.url,r.detail,r.primary_time,r.secondary_time,r.flag,r.transition " +
                "FROM records r JOIN dataset_records d ON d.record_id=r.id " +
                "WHERE d.dataset_id=? ORDER BY r.id ASC",
            arrayOf(datasetId),
        ).use { cursor ->
            while (cursor.moveToNext()) when (IntermediateItemKind.fromDatabase(cursor.getInt(0))) {
                IntermediateItemKind.BOOKMARK -> bookmarks += CanonicalBookmark(
                    cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.nullableLong(4)
                )
                IntermediateItemKind.HISTORY -> history += CanonicalHistoryVisit(
                    cursor.getString(1), cursor.getString(2), cursor.getLong(4), cursor.nullableString(7)
                )
                IntermediateItemKind.OPEN_TAB -> tabs += CanonicalOpenTab(
                    cursor.getString(1), cursor.getString(2), cursor.getLong(4),
                    cursor.nullableLong(5) ?: cursor.getLong(4), cursor.getInt(6) != 0
                )
            }
        }
        return BrowserDataSnapshot(metadata.second, metadata.first, metadata.third, bookmarks, history, tabs)
    }

    @Synchronized
    fun query(
        kind: IntermediateItemKind,
        search: String,
        offset: Int,
        limit: Int = 100,
        browser: BrowserId? = null,
    ): ManagedDataPage {
        val db = requireDatabase().readableDatabase
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, PAGE_LIMIT_MAX)
        val normalized = search.trim()
        val total = count(db, kind, normalized, browser)
        if (safeOffset >= total) return ManagedDataPage(emptyList(), total, safeOffset, false)
        val rows = runCatching { queryFts(db, kind, normalized, safeOffset, safeLimit, browser) }
            .recoverCatching { queryLike(db, kind, normalized, safeOffset, safeLimit, browser) }
            .getOrThrow()
        return ManagedDataPage(rows, total, safeOffset, safeOffset + rows.size < total)
    }

    /**
     * Reads stable managed-record IDs for browser export. A non-null selection
     * never falls back to all rows; IDs deleted after task creation are simply absent.
     */
    @Synchronized
    fun rowsForExport(
        kind: IntermediateItemKind,
        selectedRecordIds: Set<Long>? = null,
    ): List<ManagedDataRow> {
        require(selectedRecordIds == null || selectedRecordIds.isNotEmpty()) {
            "Selected record IDs must not be empty"
        }
        val db = requireDatabase().readableDatabase
        val projection = "SELECT r.id,r.title,r.url,r.detail,r.primary_time," + sourcesSql("r") +
            " FROM records r WHERE r.kind=?"
        if (selectedRecordIds == null) {
            return db.rawQuery(
                "$projection GROUP BY r.id ORDER BY r.id ASC",
                arrayOf(kind.databaseValue.toString()),
            ).use { readRows(it, kind) }
        }
        return selectedRecordIds.sorted().chunked(400).flatMap { ids ->
            val placeholders = ids.joinToString(",") { "?" }
            db.rawQuery(
                "$projection AND r.id IN ($placeholders) GROUP BY r.id ORDER BY r.id ASC",
                (listOf(kind.databaseValue.toString()) + ids.map(Long::toString)).toTypedArray(),
            ).use { readRows(it, kind) }
        }
    }

    /** Returns the first history-row offset at or before the selected local calendar day. */
    @Synchronized
    fun historyOffsetForDate(
        dayStartEpochMillis: Long,
        search: String = "",
        browser: BrowserId? = null,
    ): Int {
        val selectedDay = Calendar.getInstance().apply {
            timeInMillis = dayStartEpochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextDayStart = (selectedDay.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val normalizedSearch = search.trim()
        val pattern = "%${escapeLike(normalizedSearch)}%"
        val sourceCondition = sourceCondition("r", browser)
        return requireDatabase().readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM records r WHERE r.kind=? AND COALESCE(r.primary_time,0)>=? " +
                "AND (?='' OR r.title LIKE ? ESCAPE '\\' OR r.url LIKE ? ESCAPE '\\') " +
                "AND $sourceCondition",
            (listOf(
                IntermediateItemKind.HISTORY.databaseValue.toString(), nextDayStart.toString(),
                normalizedSearch, pattern, pattern,
            ) + browser?.let { listOf(it.name) }.orEmpty()).toTypedArray(),
        ).use { it.moveToFirst(); it.getInt(0) }
    }

    /** Returns every matching history record on one local calendar day, independent of paging. */
    @Synchronized
    fun historyRefsForLocalDay(
        dayStartEpochMillis: Long,
        search: String = "",
        browser: BrowserId? = null,
    ): Set<IntermediateItemRef> {
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = dayStartEpochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextDayStart = (dayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val normalizedSearch = search.trim()
        val pattern = "%${escapeLike(normalizedSearch)}%"
        val sourceCondition = sourceCondition("r", browser)
        return requireDatabase().readableDatabase.rawQuery(
            "SELECT DISTINCT r.id FROM records r WHERE r.kind=? " +
                "AND COALESCE(r.primary_time,0)>=? AND COALESCE(r.primary_time,0)<? " +
                "AND (?='' OR r.title LIKE ? ESCAPE '\\' OR r.url LIKE ? ESCAPE '\\') " +
                "AND $sourceCondition ORDER BY r.id",
            (listOf(
                IntermediateItemKind.HISTORY.databaseValue.toString(),
                dayStart.timeInMillis.toString(), nextDayStart.toString(),
                normalizedSearch, pattern, pattern,
            ) + browser?.let { listOf(it.name) }.orEmpty()).toTypedArray(),
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(IntermediateItemRef(cursor.getLong(0), IntermediateItemKind.HISTORY))
                }
            }
        }
    }

    /** Returns immediate child folders plus direct records for a real folder page. */
    @Synchronized
    fun queryBookmarkFolder(
        folderPath: String,
        search: String,
        offset: Int,
        limit: Int = 100,
        browser: BrowserId? = null,
    ): ManagedDataPage {
        val db = requireDatabase().readableDatabase
        val folder = normalizeFolderPath(folderPath)
        val normalizedSearch = search.trim()
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, PAGE_LIMIT_MAX)
        val folderExpression = normalizedFolderSql("r")
        val subtreePattern = if (folder.isEmpty()) "%" else "${escapeLike(folder)}/%"
        val subtreeCondition = if (folder.isEmpty()) "1=1" else
            "($folderExpression=? OR $folderExpression LIKE ? ESCAPE '\\')"
        val subtreeArgs = if (folder.isEmpty()) emptyList() else listOf(folder, subtreePattern)
        val sourceCondition = sourceCondition("r", browser)
        val sourceArgs = browser?.let { listOf(it.name) }.orEmpty()

        if (normalizedSearch.isNotEmpty()) {
            val pattern = "%${escapeLike(normalizedSearch)}%"
            val where = "$subtreeCondition AND $sourceCondition AND " +
                "(r.title LIKE ? ESCAPE '\\' OR r.url LIKE ? ESCAPE '\\' OR r.detail LIKE ? ESCAPE '\\')"
            val args = (listOf(IntermediateItemKind.BOOKMARK.databaseValue.toString()) + subtreeArgs + sourceArgs +
                listOf(pattern, pattern, pattern)).toTypedArray()
            val total = db.rawQuery(
                "SELECT COUNT(*) FROM records r WHERE r.kind=? AND $where", args
            ).use { it.moveToFirst(); it.getInt(0) }
            val rows = db.rawQuery(
                "SELECT r.id,r.title,r.url,r.detail,r.primary_time," + sourcesSql("r") +
                    " FROM records r WHERE r.kind=? AND $where GROUP BY r.id " +
                    "ORDER BY r.id DESC LIMIT ? OFFSET ?",
                (args.toList() + listOf(safeLimit.toString(), safeOffset.toString())).toTypedArray(),
            ).use { readRows(it, IntermediateItemKind.BOOKMARK) }
            return ManagedDataPage(rows, total, safeOffset, safeOffset + rows.size < total)
        }

        val directCount = db.rawQuery(
            "SELECT COUNT(*) FROM records r WHERE r.kind=? AND $folderExpression=? AND $sourceCondition",
            (listOf(IntermediateItemKind.BOOKMARK.databaseValue.toString(), folder) + sourceArgs).toTypedArray(),
        ).use { it.moveToFirst(); it.getInt(0) }
        val rows = if (safeOffset >= directCount) emptyList() else db.rawQuery(
            "SELECT r.id,r.title,r.url,r.detail,r.primary_time," + sourcesSql("r") +
            " FROM records r WHERE r.kind=? AND $folderExpression=? AND $sourceCondition GROUP BY r.id " +
                "ORDER BY r.id DESC LIMIT ? OFFSET ?",
            (listOf(
                IntermediateItemKind.BOOKMARK.databaseValue.toString(), folder,
            ) + sourceArgs + listOf(safeLimit.toString(), safeOffset.toString())).toTypedArray(),
        ).use { readRows(it, IntermediateItemKind.BOOKMARK) }

        val childCounts = linkedMapOf<String, Int>()
        db.rawQuery(
            "SELECT r.detail,COUNT(*) FROM records r WHERE r.kind=? AND $sourceCondition GROUP BY r.detail",
            (listOf(IntermediateItemKind.BOOKMARK.databaseValue.toString()) + sourceArgs).toTypedArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val candidate = normalizeFolderPath(cursor.getString(0))
                val remainder = when {
                    folder.isEmpty() -> candidate
                    candidate.startsWith("$folder/") -> candidate.removePrefix("$folder/")
                    else -> ""
                }
                if (remainder.isEmpty()) continue
                val child = remainder.substringBefore('/')
                childCounts[child] = childCounts.getOrDefault(child, 0) + cursor.getInt(1)
            }
        }
        val manualFolderQuery = if (browser == null) {
            "SELECT path FROM bookmark_folders"
        } else {
            "SELECT path FROM bookmark_folders WHERE browser=?"
        }
        db.rawQuery(manualFolderQuery, browser?.let { arrayOf(it.name) }).use { cursor ->
            while (cursor.moveToNext()) {
                val candidate = normalizeFolderPath(cursor.getString(0))
                val remainder = when {
                    folder.isEmpty() -> candidate
                    candidate.startsWith("$folder/") -> candidate.removePrefix("$folder/")
                    else -> ""
                }
                if (remainder.isNotEmpty()) childCounts.putIfAbsent(remainder.substringBefore('/'), 0)
            }
        }
        val folders = childCounts.map { (name, count) ->
            BookmarkFolderEntry(name, if (folder.isEmpty()) name else "$folder/$name", count)
        }
        val subtreeTotal = directCount + folders.sumOf { it.recordCount }
        return ManagedDataPage(
            rows, subtreeTotal, safeOffset, safeOffset + rows.size < directCount, folders
        )
    }

    @Synchronized
    fun update(ref: IntermediateItemRef, title: String, url: String, detail: String): Boolean {
        val cleanUrl = url.trim()
        if (!isWebUrl(cleanUrl)) return false
        val db = requireDatabase().writableDatabase
        val current = db.rawQuery(
            "SELECT detail,primary_time FROM records WHERE id=? AND kind=?",
            arrayOf(ref.rowId.toString(), ref.kind.databaseValue.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0) to cursor.nullableLong(1)
        } ?: return false
        val cleanTitle = title.trim()
        val cleanDetail = if (ref.kind == IntermediateItemKind.BOOKMARK) detail.trim() else current.first
        val newKey = when (ref.kind) {
            IntermediateItemKind.BOOKMARK -> BrowserRecordIdentity.bookmark(cleanUrl, cleanTitle, cleanDetail)
            IntermediateItemKind.HISTORY -> BrowserRecordIdentity.history(cleanUrl, current.second ?: 0L)
            IntermediateItemKind.OPEN_TAB -> BrowserRecordIdentity.openTab(cleanUrl, current.second ?: 0L)
        }
        var changed = false
        db.beginTransaction()
        try {
            val existingId = db.rawQuery(
                "SELECT id FROM records WHERE dedup_key=?", arrayOf(newKey)
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            if (existingId != null && existingId != ref.rowId) {
                db.execSQL("INSERT OR IGNORE INTO dataset_records(dataset_id,record_id) " +
                    "SELECT dataset_id,? FROM dataset_records WHERE record_id=?", arrayOf(existingId, ref.rowId))
                db.execSQL("INSERT OR IGNORE INTO record_sources(record_id,browser) " +
                    "SELECT ?,browser FROM record_sources WHERE record_id=?", arrayOf(existingId, ref.rowId))
                changed = db.delete("records", "id=?", arrayOf(ref.rowId.toString())) == 1
            } else {
                changed = db.update(
                    "records",
                    ContentValues().apply {
                        put("dedup_key", newKey); put("title", cleanTitle); put("url", cleanUrl)
                        put("detail", cleanDetail)
                    },
                    "id=? AND kind=?",
                    arrayOf(ref.rowId.toString(), ref.kind.databaseValue.toString()),
                ) == 1
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (changed) publishCounts()
        return changed
    }

    @Synchronized
    fun delete(ref: IntermediateItemRef): Boolean {
        val changed = requireDatabase().writableDatabase.delete(
            "records", "id=? AND kind=?",
            arrayOf(ref.rowId.toString(), ref.kind.databaseValue.toString()),
        ) == 1
        if (changed) publishCounts()
        return changed
    }

    @Synchronized
    fun delete(refs: Collection<IntermediateItemRef>): Int {
        if (refs.isEmpty()) return 0
        val db = requireDatabase().writableDatabase
        var deleted = 0
        db.beginTransaction()
        try {
            refs.distinct().forEach { ref ->
                deleted += db.delete(
                    "records",
                    "id=? AND kind=?",
                    arrayOf(ref.rowId.toString(), ref.kind.databaseValue.toString()),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (deleted > 0) publishCounts()
        return deleted
    }

    /** Moves selected bookmarks while preserving the existing collision/source merge rules. */
    @Synchronized
    fun moveBookmarks(refs: Collection<IntermediateItemRef>, folderPath: String): Int {
        val folder = normalizeFolderPath(folderPath)
        if (!BookmarkFolderPathRules.isSafe(folder)) return 0
        val db = requireDatabase().writableDatabase
        var changed = 0
        db.beginTransaction()
        try {
            refs.filter { it.kind == IntermediateItemKind.BOOKMARK }.distinct().forEach { ref ->
                db.rawQuery(
                    "SELECT browser FROM record_sources WHERE record_id=?",
                    arrayOf(ref.rowId.toString()),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        BrowserId.entries.firstOrNull { it.name == cursor.getString(0) }?.let {
                            insertBookmarkFolders(db, it, folder)
                        }
                    }
                }
                val row = db.rawQuery(
                    "SELECT title,url FROM records WHERE id=? AND kind=?",
                    arrayOf(ref.rowId.toString(), IntermediateItemKind.BOOKMARK.databaseValue.toString()),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else cursor.getString(0) to cursor.getString(1)
                } ?: return@forEach
                val newKey = BrowserRecordIdentity.bookmark(row.second, row.first, folder)
                val existingId = db.rawQuery(
                    "SELECT id FROM records WHERE dedup_key=?", arrayOf(newKey)
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
                if (existingId != null && existingId != ref.rowId) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO dataset_records(dataset_id,record_id) " +
                            "SELECT dataset_id,? FROM dataset_records WHERE record_id=?",
                        arrayOf(existingId, ref.rowId),
                    )
                    db.execSQL(
                        "INSERT OR IGNORE INTO record_sources(record_id,browser) " +
                            "SELECT ?,browser FROM record_sources WHERE record_id=?",
                        arrayOf(existingId, ref.rowId),
                    )
                    changed += db.delete("records", "id=?", arrayOf(ref.rowId.toString()))
                } else {
                    changed += db.update(
                        "records",
                        ContentValues().apply { put("dedup_key", newKey); put("detail", folder) },
                        "id=? AND kind=?",
                        arrayOf(ref.rowId.toString(), IntermediateItemKind.BOOKMARK.databaseValue.toString()),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (changed > 0) publishCounts()
        return changed
    }

    /** Copies selected bookmarks into a folder without changing the source records. */
    @Synchronized
    fun copyBookmarks(refs: Collection<IntermediateItemRef>, folderPath: String): Int {
        val folder = normalizeFolderPath(folderPath)
        if (!BookmarkFolderPathRules.isSafe(folder) || refs.isEmpty()) return 0
        val db = requireDatabase().writableDatabase
        var copied = 0
        db.beginTransaction()
        try {
            refs.filter { it.kind == IntermediateItemKind.BOOKMARK }.distinct().forEach { ref ->
                val row = db.rawQuery(
                    "SELECT title,url,primary_time,secondary_time,flag,transition FROM records " +
                        "WHERE id=? AND kind=?",
                    arrayOf(ref.rowId.toString(), IntermediateItemKind.BOOKMARK.databaseValue.toString()),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else arrayOf<Any?>(
                        cursor.getString(0), cursor.getString(1), cursor.nullableLong(2),
                        cursor.nullableLong(3), cursor.getInt(4) != 0, cursor.nullableString(5),
                    )
                } ?: return@forEach
                val title = row[0] as String
                val url = row[1] as String
                val newKey = BrowserRecordIdentity.bookmark(url, title, folder)
                val existingId = db.rawQuery(
                    "SELECT id FROM records WHERE dedup_key=?", arrayOf(newKey),
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
                var rowChanged = existingId == null
                val destinationId = existingId ?: db.insertOrThrow(
                    "records", null, ContentValues().apply {
                        put("dedup_key", newKey)
                        put("kind", IntermediateItemKind.BOOKMARK.databaseValue)
                        put("title", title)
                        put("url", url)
                        put("detail", folder)
                        putNullableLong("primary_time", row[2] as Long?)
                        putNullableLong("secondary_time", row[3] as Long?)
                        put("flag", if (row[4] as Boolean) 1 else 0)
                        put("transition", row[5] as String?)
                    },
                )
                db.rawQuery(
                    "SELECT browser FROM record_sources WHERE record_id=?",
                    arrayOf(ref.rowId.toString()),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val browserName = cursor.getString(0)
                        val sourceInsert = db.insertWithOnConflict(
                            "record_sources", null, ContentValues().apply {
                                put("record_id", destinationId)
                                put("browser", browserName)
                            }, SQLiteDatabase.CONFLICT_IGNORE,
                        )
                        rowChanged = rowChanged || sourceInsert != -1L
                        BrowserId.entries.firstOrNull { it.name == browserName }?.let {
                            insertBookmarkFolders(db, it, folder)
                        }
                    }
                }
                if (rowChanged) copied++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (copied > 0) publishCounts()
        return copied
    }

    @Synchronized
    fun createBookmarkFolder(browser: BrowserId, folderPath: String): Boolean {
        val folder = normalizeFolderPath(folderPath)
        if (folder.isEmpty() || !BookmarkFolderPathRules.isSafe(folder)) return false
        val db = requireDatabase().writableDatabase
        val before = db.rawQuery(
            "SELECT COUNT(*) FROM bookmark_folders WHERE browser=? AND path=?",
            arrayOf(browser.name, folder),
        ).use { it.moveToFirst(); it.getInt(0) }
        insertBookmarkFolders(db, browser, folder)
        if (before == 0) publishCounts()
        return before == 0
    }

    @Synchronized
    fun inspectBookmarkFolder(browser: BrowserId, folderPath: String): BookmarkFolderContents? {
        val folder = normalizeFolderPath(folderPath)
        if (folder.isEmpty() || !BookmarkFolderPathRules.isSafe(folder)) return null
        val db = requireDatabase().readableDatabase
        val subtree = "${escapeLike(folder)}/%"
        val folderCount = db.rawQuery(
            "SELECT COUNT(*) FROM bookmark_folders WHERE browser=? AND " +
                "(path=? OR path LIKE ? ESCAPE '\\')",
            arrayOf(browser.name, folder, subtree),
        ).use { it.moveToFirst(); it.getInt(0) }
        val folderExpression = normalizedFolderSql("r")
        val recordCount = db.rawQuery(
            "SELECT COUNT(DISTINCT r.id) FROM records r JOIN record_sources s ON s.record_id=r.id " +
                "WHERE r.kind=? AND s.browser=? AND ($folderExpression=? OR " +
                "$folderExpression LIKE ? ESCAPE '\\')",
            arrayOf(
                IntermediateItemKind.BOOKMARK.databaseValue.toString(), browser.name, folder, subtree,
            ),
        ).use { it.moveToFirst(); it.getInt(0) }
        if (folderCount == 0 && recordCount == 0) return null
        return BookmarkFolderContents(folder, recordCount, folderCount)
    }

    /** Deletes a real folder subtree. Non-empty folders require an explicit second call. */
    @Synchronized
    fun deleteBookmarkFolder(
        browser: BrowserId,
        folderPath: String,
        confirmed: Boolean = false,
    ): BookmarkFolderOperationResult {
        val folder = normalizeFolderPath(folderPath)
        if (folder.isEmpty()) {
            return BookmarkFolderOperationResult.Rejected(BookmarkFolderRejection.ROOT_PROTECTED)
        }
        if (!BookmarkFolderPathRules.isSafe(folder)) {
            return BookmarkFolderOperationResult.Rejected(BookmarkFolderRejection.UNSAFE_PATH)
        }
        val contents = inspectBookmarkFolder(browser, folder)
            ?: return BookmarkFolderOperationResult.Rejected(BookmarkFolderRejection.SOURCE_NOT_FOUND)
        if (!confirmed && !contents.isEmpty) {
            return BookmarkFolderOperationResult.ConfirmationRequired(contents)
        }
        val db = requireDatabase().writableDatabase
        val subtree = "${escapeLike(folder)}/%"
        val recordIds = bookmarkRecordIdsInFolder(db, browser, folder, subtree)
        var affectedRecords = 0
        var affectedFolders = 0
        db.beginTransaction()
        try {
            recordIds.forEach { recordId ->
                val sourceCount = db.rawQuery(
                    "SELECT COUNT(*) FROM record_sources WHERE record_id=?",
                    arrayOf(recordId.toString()),
                ).use { it.moveToFirst(); it.getInt(0) }
                if (sourceCount <= 1) {
                    affectedRecords += db.delete("records", "id=?", arrayOf(recordId.toString()))
                } else {
                    db.execSQL(
                        "DELETE FROM dataset_records WHERE record_id=? AND dataset_id IN " +
                            "(SELECT id FROM datasets WHERE source=?)",
                        arrayOf<Any>(recordId, browser.name),
                    )
                    affectedRecords += db.delete(
                        "record_sources", "record_id=? AND browser=?",
                        arrayOf(recordId.toString(), browser.name),
                    )
                }
            }
            affectedFolders = db.delete(
                "bookmark_folders",
                "browser=? AND (path=? OR path LIKE ? ESCAPE '\\')",
                arrayOf(browser.name, folder, subtree),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (affectedRecords > 0 || affectedFolders > 0) publishCounts()
        return BookmarkFolderOperationResult.Completed(affectedRecords, affectedFolders)
    }

    /** Moves a complete folder subtree into a destination parent with cycle protection. */
    @Synchronized
    fun moveBookmarkFolder(
        browser: BrowserId,
        sourcePath: String,
        destinationParentPath: String,
    ): BookmarkFolderOperationResult {
        val plan = BookmarkFolderPathRules.move(sourcePath, destinationParentPath)
        if (plan is BookmarkFolderMovePlan.Rejected) {
            return BookmarkFolderOperationResult.Rejected(plan.reason)
        }
        plan as BookmarkFolderMovePlan.Move
        val contents = inspectBookmarkFolder(browser, plan.source)
            ?: return BookmarkFolderOperationResult.Rejected(BookmarkFolderRejection.SOURCE_NOT_FOUND)
        val db = requireDatabase().writableDatabase
        val subtree = "${escapeLike(plan.source)}/%"
        val recordIds = bookmarkRecordIdsInFolder(db, browser, plan.source, subtree)
        val oldFolders = mutableListOf<String>()
        db.rawQuery(
            "SELECT path FROM bookmark_folders WHERE browser=? AND " +
                "(path=? OR path LIKE ? ESCAPE '\\') ORDER BY LENGTH(path)",
            arrayOf(browser.name, plan.source, subtree),
        ).use { cursor -> while (cursor.moveToNext()) oldFolders += normalizeFolderPath(cursor.getString(0)) }
        var affectedRecords = 0
        db.beginTransaction()
        try {
            recordIds.forEach { recordId ->
                if (moveBookmarkSource(db, browser, recordId, plan.source, plan.destination)) {
                    affectedRecords++
                }
            }
            db.delete(
                "bookmark_folders", "browser=? AND (path=? OR path LIKE ? ESCAPE '\\')",
                arrayOf(browser.name, plan.source, subtree),
            )
            oldFolders.ifEmpty { listOf(plan.source) }.forEach { oldPath ->
                insertBookmarkFolders(
                    db, browser,
                    BookmarkFolderPathRules.relocate(oldPath, plan.source, plan.destination),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        publishCounts()
        return BookmarkFolderOperationResult.Completed(affectedRecords, oldFolders.size.coerceAtLeast(1))
    }

    @Synchronized
    fun browserSpaceCounts(kind: IntermediateItemKind): List<BrowserSpaceCount> {
        val db = requireDatabase().readableDatabase
        val counts = BrowserId.entries.associateWith { 0 }.toMutableMap()
        db.rawQuery(
            "SELECT s.browser,COUNT(DISTINCT s.record_id) FROM record_sources s " +
                "JOIN records r ON r.id=s.record_id WHERE r.kind=? GROUP BY s.browser",
            arrayOf(kind.databaseValue.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                BrowserId.entries.firstOrNull { it.name == cursor.getString(0) }?.let {
                    counts[it] = cursor.getInt(1)
                }
            }
        }
        return BrowserId.entries.map { BrowserSpaceCount(it, counts.getValue(it)) }
    }

    @Synchronized
    fun clear() {
        val db = requireDatabase().writableDatabase
        db.beginTransaction()
        try {
            db.delete("records", null, null)
            db.delete("datasets", null, null)
            db.delete("bookmark_folders", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        publishCounts()
    }

    private fun queryFts(
        db: SQLiteDatabase, kind: IntermediateItemKind, search: String, offset: Int, limit: Int,
        browser: BrowserId?,
    ): List<ManagedDataRow> {
        if (search.isEmpty()) return queryLike(db, kind, search, offset, limit, browser)
        val match = search.split(Regex("\\s+")).filter(String::isNotBlank)
            .joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
        val sourceCondition = sourceCondition("r", browser)
        return db.rawQuery(
            "SELECT r.id,r.title,r.url,r.detail,r.primary_time," + sourcesSql("r") + " FROM records r " +
                "JOIN records_fts f ON f.rowid=r.id WHERE r.kind=? AND records_fts MATCH ? " +
                "AND $sourceCondition " +
                "GROUP BY r.id " +
                "ORDER BY COALESCE(r.primary_time,0) DESC,r.id DESC LIMIT ? OFFSET ?",
            (listOf(kind.databaseValue.toString(), match) + browser?.let { listOf(it.name) }.orEmpty() +
                listOf(limit.toString(), offset.toString())).toTypedArray(),
        ).use { readRows(it, kind) }
    }

    private fun queryLike(
        db: SQLiteDatabase, kind: IntermediateItemKind, search: String, offset: Int, limit: Int,
        browser: BrowserId?,
    ): List<ManagedDataRow> {
        val pattern = "%${escapeLike(search)}%"
        val sourceCondition = sourceCondition("r", browser)
        return db.rawQuery(
            "SELECT r.id,r.title,r.url,r.detail,r.primary_time," + sourcesSql("r") + " FROM records r WHERE r.kind=? " +
                "AND (?='' OR title LIKE ? ESCAPE '\\' OR url LIKE ? ESCAPE '\\' OR detail LIKE ? ESCAPE '\\') " +
                "AND $sourceCondition " +
                "GROUP BY r.id ORDER BY COALESCE(r.primary_time,0) DESC,r.id DESC LIMIT ? OFFSET ?",
            (listOf(kind.databaseValue.toString(), search, pattern, pattern, pattern) +
                browser?.let { listOf(it.name) }.orEmpty() + listOf(limit.toString(), offset.toString())).toTypedArray(),
        ).use { readRows(it, kind) }
    }

    private fun count(
        db: SQLiteDatabase,
        kind: IntermediateItemKind,
        search: String,
        browser: BrowserId?,
    ): Int {
        val sourceCondition = sourceCondition("r", browser)
        val sourceArgs = browser?.let { listOf(it.name) }.orEmpty()
        if (search.isEmpty()) return db.rawQuery(
            "SELECT COUNT(*) FROM records r WHERE kind=? AND $sourceCondition",
            (listOf(kind.databaseValue.toString()) + sourceArgs).toTypedArray(),
        ).use { it.moveToFirst(); it.getInt(0) }
        return try {
            val match = search.split(Regex("\\s+")).filter(String::isNotBlank)
                .joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
            db.rawQuery(
                "SELECT COUNT(*) FROM records r JOIN records_fts f ON f.rowid=r.id " +
                    "WHERE r.kind=? AND records_fts MATCH ? AND $sourceCondition",
                (listOf(kind.databaseValue.toString(), match) + sourceArgs).toTypedArray(),
            ).use { it.moveToFirst(); it.getInt(0) }
        } catch (_: SQLiteException) {
            val pattern = "%${escapeLike(search)}%"
            db.rawQuery(
                "SELECT COUNT(*) FROM records r WHERE kind=? AND " +
                    "(title LIKE ? ESCAPE '\\' OR url LIKE ? ESCAPE '\\' OR detail LIKE ? ESCAPE '\\') " +
                    "AND $sourceCondition",
                (listOf(kind.databaseValue.toString(), pattern, pattern, pattern) + sourceArgs).toTypedArray(),
            ).use { it.moveToFirst(); it.getInt(0) }
        }
    }

    private fun readRows(cursor: Cursor, kind: IntermediateItemKind): List<ManagedDataRow> = buildList {
        while (cursor.moveToNext()) add(ManagedDataRow(
            IntermediateItemRef(cursor.getLong(0), kind),
            cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.nullableLong(4),
            cursor.getString(5).split(',').mapNotNull { value ->
                BrowserId.entries.firstOrNull { it.name == value }
            }.toSet(),
        ))
    }

    private fun publishCounts() {
        val db = requireDatabase().readableDatabase
        val counts = IntArray(3)
        db.rawQuery("SELECT kind,COUNT(*) FROM records GROUP BY kind", null).use { cursor ->
            while (cursor.moveToNext()) counts[cursor.getInt(0)] = cursor.getInt(1)
        }
        mutableState.value = IntermediateDataState(
            counts[0], counts[1], counts[2], mutableState.value.revision + 1L
        )
    }

    private fun pruneOldDatasets(db: SQLiteDatabase) {
        db.rawQuery(
            "SELECT id FROM datasets ORDER BY updated_at DESC LIMIT -1 OFFSET $MAX_DATASETS", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                db.delete("datasets", "id=?", arrayOf(id))
            }
        }
    }

    private fun migrateAlpha09Json(context: Context) {
        val legacy = File(context.noBackupFilesDir, "intermediate_data/browser-data.json")
        if (!legacy.isFile) return
        runCatching {
            val root = JSONObject(legacy.readText())
            val datasets = root.getJSONArray("datasets")
            for (index in 0 until datasets.length()) {
                val dataset = datasets.getJSONObject(index)
                save(dataset.getString("id"), dataset.getString("label"), dataset.getJSONObject("snapshot").legacySnapshot())
            }
            legacy.delete()
        }
    }

    private fun JSONObject.legacySnapshot(): BrowserDataSnapshot {
        val bookmarksJson = getJSONArray("bookmarks")
        val historyJson = getJSONArray("history")
        val tabsJson = getJSONArray("open_tabs")
        return BrowserDataSnapshot(
            getInt("schema_version"), BrowserId.valueOf(getString("source")), getLong("exported_at"),
            buildList { for (i in 0 until bookmarksJson.length()) bookmarksJson.getJSONObject(i).let {
                add(CanonicalBookmark(it.getString("title"), it.getString("url"), it.optString("folder"),
                    if (it.isNull("created_at")) null else it.getLong("created_at")))
            } },
            buildList { for (i in 0 until historyJson.length()) historyJson.getJSONObject(i).let {
                add(CanonicalHistoryVisit(it.getString("title"), it.getString("url"), it.getLong("visited_at"),
                    if (it.isNull("transition")) null else it.getString("transition")))
            } },
            buildList { for (i in 0 until tabsJson.length()) tabsJson.getJSONObject(i).let {
                add(CanonicalOpenTab(it.getString("title"), it.getString("url"), it.getLong("opened_at"),
                    it.getLong("last_active_at"), it.getBoolean("pinned")))
            } },
        )
    }

    private fun requireDatabase() = requireNotNull(database) { "Data repository is not initialized" }
    private fun isWebUrl(url: String) = url.startsWith("http://") || url.startsWith("https://")
    private fun escapeLike(value: String) = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    private fun normalizeFolderPath(value: String) = BookmarkFolderPathRules.normalize(value)
    private fun normalizedFolderSql(alias: String) = "TRIM(REPLACE($alias.detail, '\\', '/'), '/')"
    private fun Cursor.nullableLong(index: Int) = if (isNull(index)) null else getLong(index)
    private fun Cursor.nullableString(index: Int) = if (isNull(index)) null else getString(index)
    private fun sourcesSql(recordAlias: String) =
        "(SELECT GROUP_CONCAT(browser) FROM record_sources WHERE record_id=$recordAlias.id)"

    private fun sourceCondition(recordAlias: String, browser: BrowserId?) = if (browser == null) {
        "1=1"
    } else {
        "EXISTS(SELECT 1 FROM record_sources sf WHERE sf.record_id=$recordAlias.id AND sf.browser=?)"
    }

    private fun insertBookmarkFolders(db: SQLiteDatabase, browser: BrowserId, rawPath: String) {
        val normalized = normalizeFolderPath(rawPath)
        if (normalized.isEmpty()) return
        var current = ""
        normalized.split('/').filter(String::isNotBlank).forEach { segment ->
            current = if (current.isEmpty()) segment else "$current/$segment"
            db.insertWithOnConflict("bookmark_folders", null, ContentValues().apply {
                put("browser", browser.name)
                put("path", current)
                put("created_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun bookmarkRecordIdsInFolder(
        db: SQLiteDatabase,
        browser: BrowserId,
        folder: String,
        subtree: String,
    ): List<Long> {
        val folderExpression = normalizedFolderSql("r")
        return buildList {
            db.rawQuery(
                "SELECT DISTINCT r.id FROM records r JOIN record_sources s ON s.record_id=r.id " +
                    "WHERE r.kind=? AND s.browser=? AND ($folderExpression=? OR " +
                    "$folderExpression LIKE ? ESCAPE '\\')",
                arrayOf(
                    IntermediateItemKind.BOOKMARK.databaseValue.toString(), browser.name, folder, subtree,
                ),
            ).use { cursor -> while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
    }

    /** Moves one browser's ownership while preserving other browsers on a shared canonical row. */
    private fun moveBookmarkSource(
        db: SQLiteDatabase,
        browser: BrowserId,
        recordId: Long,
        sourceFolder: String,
        destinationFolder: String,
    ): Boolean {
        val row = db.rawQuery(
            "SELECT title,url,detail,primary_time,secondary_time,flag,transition FROM records " +
                "WHERE id=? AND kind=?",
            arrayOf(recordId.toString(), IntermediateItemKind.BOOKMARK.databaseValue.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else arrayOf<Any?>(
                cursor.getString(0), cursor.getString(1), normalizeFolderPath(cursor.getString(2)),
                cursor.nullableLong(3), cursor.nullableLong(4), cursor.getInt(5) != 0,
                cursor.nullableString(6),
            )
        } ?: return false
        val oldPath = row[2] as String
        if (oldPath != sourceFolder && !oldPath.startsWith("$sourceFolder/")) return false
        val newPath = BookmarkFolderPathRules.relocate(oldPath, sourceFolder, destinationFolder)
        val title = row[0] as String
        val url = row[1] as String
        val newKey = BrowserRecordIdentity.bookmark(url, title, newPath)
        val existingId = db.rawQuery(
            "SELECT id FROM records WHERE dedup_key=?", arrayOf(newKey),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        val sourceCount = db.rawQuery(
            "SELECT COUNT(*) FROM record_sources WHERE record_id=?", arrayOf(recordId.toString()),
        ).use { it.moveToFirst(); it.getInt(0) }

        if (sourceCount <= 1 && existingId == null) {
            db.update(
                "records",
                ContentValues().apply { put("dedup_key", newKey); put("detail", newPath) },
                "id=?", arrayOf(recordId.toString()),
            )
            insertBookmarkFolders(db, browser, newPath)
            return true
        }

        val destinationId = existingId ?: db.insertOrThrow(
            "records", null, ContentValues().apply {
                put("dedup_key", newKey)
                put("kind", IntermediateItemKind.BOOKMARK.databaseValue)
                put("title", title)
                put("url", url)
                put("detail", newPath)
                putNullableLong("primary_time", row[3] as Long?)
                putNullableLong("secondary_time", row[4] as Long?)
                put("flag", if (row[5] as Boolean) 1 else 0)
                put("transition", row[6] as String?)
            },
        )
        db.insertWithOnConflict(
            "record_sources", null, ContentValues().apply {
                put("record_id", destinationId)
                put("browser", browser.name)
            }, SQLiteDatabase.CONFLICT_IGNORE,
        )
        db.execSQL(
            "INSERT OR IGNORE INTO dataset_records(dataset_id,record_id) " +
                "SELECT dr.dataset_id,? FROM dataset_records dr JOIN datasets d ON d.id=dr.dataset_id " +
                "WHERE dr.record_id=? AND d.source=?",
            arrayOf<Any>(destinationId, recordId, browser.name),
        )
        db.execSQL(
            "DELETE FROM dataset_records WHERE record_id=? AND dataset_id IN " +
                "(SELECT id FROM datasets WHERE source=?)",
            arrayOf<Any>(recordId, browser.name),
        )
        if (sourceCount <= 1) {
            db.execSQL(
                "INSERT OR IGNORE INTO record_sources(record_id,browser) " +
                    "SELECT ?,browser FROM record_sources WHERE record_id=?",
                arrayOf(destinationId, recordId),
            )
            db.delete("records", "id=?", arrayOf(recordId.toString()))
        } else {
            db.delete(
                "record_sources", "record_id=? AND browser=?",
                arrayOf(recordId.toString(), browser.name),
            )
        }
        insertBookmarkFolders(db, browser, newPath)
        return true
    }

    private fun insertOrLink(
        insert: android.database.sqlite.SQLiteStatement,
        find: android.database.sqlite.SQLiteStatement,
        link: android.database.sqlite.SQLiteStatement,
        source: android.database.sqlite.SQLiteStatement,
        datasetId: String,
        browser: BrowserId,
        key: String,
        kind: IntermediateItemKind,
        title: String,
        url: String,
        detail: String,
        primaryTime: Long?,
        secondaryTime: Long?,
        flag: Boolean,
        transition: String?,
    ) {
        insert.bindRecord(key, kind, title, url, detail, primaryTime, secondaryTime, flag, transition)
        insert.executeInsert()
        find.clearBindings(); find.bindString(1, key)
        val rowId = find.simpleQueryForLong()
        link.clearBindings(); link.bindString(1, datasetId); link.bindLong(2, rowId); link.executeInsert()
        source.clearBindings(); source.bindLong(1, rowId); source.bindString(2, browser.name); source.executeInsert()
    }

    private class StoreDatabase(context: Context) : SQLiteOpenHelper(
        context, File(context.noBackupFilesDir, "browser_data.sqlite").absolutePath, null, 2
    ) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE datasets(id TEXT PRIMARY KEY,label TEXT NOT NULL,source TEXT NOT NULL," +
                "schema_version INTEGER NOT NULL,exported_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE records(id INTEGER PRIMARY KEY AUTOINCREMENT,dedup_key TEXT NOT NULL UNIQUE," +
                "kind INTEGER NOT NULL,title TEXT NOT NULL,url TEXT NOT NULL,detail TEXT NOT NULL DEFAULT ''," +
                "primary_time INTEGER,secondary_time INTEGER,flag INTEGER NOT NULL DEFAULT 0,transition TEXT)")
            db.execSQL("CREATE TABLE dataset_records(dataset_id TEXT NOT NULL,record_id INTEGER NOT NULL," +
                "PRIMARY KEY(dataset_id,record_id),FOREIGN KEY(dataset_id) REFERENCES datasets(id) ON DELETE CASCADE," +
                "FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE record_sources(record_id INTEGER NOT NULL,browser TEXT NOT NULL," +
                "PRIMARY KEY(record_id,browser),FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE CASCADE)")
            createBookmarkFoldersTable(db)
            db.execSQL("CREATE INDEX records_kind_time ON records(kind,primary_time DESC,id DESC)")
            db.execSQL("CREATE INDEX dataset_records_record ON dataset_records(record_id,dataset_id)")
            db.execSQL("CREATE INDEX records_url ON records(kind,url)")
            db.execSQL("CREATE VIRTUAL TABLE records_fts USING fts4(title,url,detail)")
            db.execSQL("CREATE TRIGGER records_ai AFTER INSERT ON records BEGIN " +
                "INSERT INTO records_fts(rowid,title,url,detail) VALUES(new.id,new.title,new.url,new.detail); END")
            db.execSQL("CREATE TRIGGER records_ad AFTER DELETE ON records BEGIN " +
                "DELETE FROM records_fts WHERE rowid=old.id; END")
            db.execSQL("CREATE TRIGGER records_au AFTER UPDATE ON records BEGIN " +
                "UPDATE records_fts SET title=new.title,url=new.url,detail=new.detail WHERE rowid=new.id; END")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                createBookmarkFoldersTable(db)
                db.execSQL(
                    "INSERT OR IGNORE INTO bookmark_folders(browser,path,created_at) " +
                        "SELECT s.browser,TRIM(REPLACE(r.detail,'\\','/'),'/'),? " +
                        "FROM records r JOIN record_sources s ON s.record_id=r.id " +
                        "WHERE r.kind=? AND TRIM(r.detail)<>''",
                    arrayOf<Any>(System.currentTimeMillis(), IntermediateItemKind.BOOKMARK.databaseValue),
                )
            }
        }

        private fun createBookmarkFoldersTable(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS bookmark_folders(" +
                    "browser TEXT NOT NULL,path TEXT NOT NULL,created_at INTEGER NOT NULL," +
                    "PRIMARY KEY(browser,path))"
            )
        }
    }
}

private fun android.database.sqlite.SQLiteStatement.bindRecord(
    dedupKey: String, kind: IntermediateItemKind, title: String, url: String, detail: String,
    primaryTime: Long?, secondaryTime: Long?, flag: Boolean, transition: String?,
) {
    clearBindings()
    bindString(1, dedupKey); bindLong(2, kind.databaseValue.toLong()); bindString(3, title)
    bindString(4, url); bindString(5, detail)
    if (primaryTime == null) bindNull(6) else bindLong(6, primaryTime)
    if (secondaryTime == null) bindNull(7) else bindLong(7, secondaryTime)
    bindLong(8, if (flag) 1L else 0L)
    if (transition == null) bindNull(9) else bindString(9, transition)
}

private fun ContentValues.putNullableLong(key: String, value: Long?) {
    if (value == null) putNull(key) else put(key, value)
}
